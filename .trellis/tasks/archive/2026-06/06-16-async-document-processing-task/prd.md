# P1 Async Document Processing Task

## Goal

Split document ingestion from the synchronous upload request path. Upload should only validate ownership/input, save the original file, create the document row, create the processing task, and return observable state. Parsing, chunking, embedding, retry, and recovery must run through an explicit lightweight worker/scheduler without adding MQ in this task.

This task directly follows the implemented original-file storage, explicit delete APIs, and local/object storage backend baseline. It must improve timeout resistance, retryability, restart recovery, and status consistency without expanding object-storage features or changing the public `/v1/*` gateway contract.

## Scope Classification

Complex Task.

Reasons:

- Crosses Admin API, service boundaries, DB migration, status enums, scheduler/worker behavior, storage abstraction, frontend status typing, docs/spec, and tests.
- Adds a new durable task state machine and recovery rules.
- Changes upload semantics from synchronous processing result to async queued processing result.
- Touches deletion behavior while tasks may be queued or running.

## Current State Summary

- Branch: `feature/async-document-processing`.
- Working tree was clean at task start.
- Last recorded work in `.trellis/workspace/sangui/journal-2.md` is "Object Storage And File Lifecycle", committed as `9f55944e feat: object storage and file lifecycle`.
- That work added storage backend selection, object storage, original file retention, explicit document delete, and KB delete.
- Current upload still calls `DocumentService.uploadAndProcess(...)`, which creates document/chunks and embeds before the HTTP response returns.

## Non-Goals

- Do not introduce Kafka/RabbitMQ/Redis Streams/MQ in this task.
- Do not add Source Citations or Retrieval Evaluation.
- Do not redesign parser quality, add PDF/DOCX/OCR/table extraction, or expand supported file types.
- Do not add object-storage download/browser/signed URL features.
- Do not change `/v1/models` or `/v1/chat/completions` payloads.
- Do not silently pass through failed ingestion or mark failed embedding as ready.
- Do not add hidden fallback, mock-success, or swallowed retry failures.
- Do not make broad frontend redesigns; only update document/task status display and polling if needed.

## Domain Model Contract

### Document Status

Keep `rag_document.status` as the user-visible document processing summary:

```text
UPLOADED
PARSING
PARSED
EMBEDDING
READY
FAILED
```

Expected document transitions:

```text
upload request:
  null -> UPLOADED

worker:
  UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
  UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED

retry request:
  FAILED -> UPLOADED or PARSING only through a task reset and worker claim
```

`rag_document.status` must never be the only durable source of retry state.

### Processing Task Status

Add a durable task status machine, stored separately from `rag_document.status`.

Recommended Java enum:

```text
DocumentProcessingTaskStatus
  PENDING
  PROCESSING
  SUCCEEDED
  RETRYABLE
  FAILED
  CANCELED
```

State transitions:

```text
PENDING -> PROCESSING
PROCESSING -> SUCCEEDED
PROCESSING -> RETRYABLE
PROCESSING -> FAILED
RETRYABLE -> PROCESSING
RETRYABLE -> PENDING (optional if implementation prefers explicit requeue)
PENDING/RETRYABLE -> CANCELED (delete boundary)
FAILED -> PENDING or PROCESSING only through explicit retry
```

Rules:

- `SUCCEEDED`, `FAILED`, and `CANCELED` are terminal unless explicit retry is requested for `FAILED`.
- `RETRYABLE` means the task failed but has attempts remaining and is eligible when `next_attempt_at <= now`.
- `FAILED` means attempts are exhausted or a non-retryable failure occurred.
- `PROCESSING` must include a worker lock marker (`locked_by`, `locked_at`) so restart recovery can identify stale work.
- Task errors must be bounded and admin-safe; no provider body, chunk content, stack trace, storage absolute path, credentials, vectors, or prompt content.

### Suggested New Table

Add a Flyway migration after the latest migration, for example:

```text
backend/src/main/resources/db/migration/V14__create_document_processing_task_table.sql
```

Suggested table:

```sql
CREATE TABLE IF NOT EXISTS rag_document_processing_task (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    knowledge_base_id   BIGINT NOT NULL REFERENCES rag_knowledge_base(id),
    document_id         BIGINT NOT NULL REFERENCES rag_document(id),
    status              VARCHAR(32) NOT NULL,
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 3,
    last_error_message  VARCHAR(512),
    locked_by           VARCHAR(128),
    locked_at           TIMESTAMP,
    next_attempt_at     TIMESTAMP,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_doc_proc_task_document
    ON rag_document_processing_task(document_id);

CREATE INDEX IF NOT EXISTS idx_rag_doc_proc_task_status_next
    ON rag_document_processing_task(status, next_attempt_at, created_at);

CREATE INDEX IF NOT EXISTS idx_rag_doc_proc_task_user_kb_status
    ON rag_document_processing_task(user_id, knowledge_base_id, status);
```

If the implementation chooses task history instead of one task per document, it must still enforce at most one active task per document and document how retry selects the active task.

## API Contract

### Upload Document

Endpoint remains:

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <admin-jwt>
Content-Type: multipart/form-data

file=<uploaded file>
```

Behavior changes:

- Validate admin JWT and KB ownership before storage write.
- Validate multipart file, filename, content type, extension, and size before document/task creation.
- Save original file through `FileStorageService`.
- Create `rag_document` with `status=UPLOADED`, `chunk_count=0`, and `error_message=null`.
- Create one processing task with `status=PENDING`.
- Set KB status to `PROCESSING`.
- Return immediately without parsing/chunking/embedding.

Response shape remains `ApiResponse<DocumentVO>`, but `DocumentVO` should expose task observability fields:

```json
{
  "code": "OK",
  "message": "OK",
  "data": {
    "id": 10,
    "user_id": 100,
    "knowledge_base_id": 1,
    "original_filename": "guide.md",
    "content_type": "text/markdown",
    "file_size": 1024,
    "status": "UPLOADED",
    "chunk_count": 0,
    "error_message": null,
    "processing_task_id": 20,
    "processing_task_status": "PENDING",
    "processing_attempt_count": 0,
    "processing_next_attempt_at": null,
    "processing_started_at": null,
    "processing_finished_at": null,
    "created_at": "2026-06-16T10:00:00",
    "updated_at": "2026-06-16T10:00:00"
  }
}
```

Forbidden response fields:

```text
storage_path
object endpoint
bucket
access key
secret key
absolute filesystem path
chunk content
embedding vector
provider response body
stack trace
```

### List/Get Documents

Existing endpoints:

```http
GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents?status=<DocumentStatus>
GET /api/admin/documents/{documentId}
```

Requirements:

- Return the same `DocumentVO` task observability fields as upload.
- Keep tenant checks unchanged.
- Existing document status filter remains based on `rag_document.status`.
- If a task is missing for old data, response must be explicit and non-misleading. Prefer `processing_task_status=null` for legacy rows rather than fabricating success.

### Retry Document Processing

Add an explicit admin retry endpoint:

```http
POST /api/admin/documents/{documentId}/processing-task/retry
Authorization: Bearer <admin-jwt>
```

Request body:

```text
empty
```

Response:

```text
ApiResponse<DocumentVO>
```

Behavior:

- Missing admin JWT: `401 UNAUTHORIZED`.
- Missing document: `404 NOT_FOUND`.
- Cross-user document: `403 FORBIDDEN`.
- Document has `PROCESSING` task: `409 DOCUMENT_PROCESSING`.
- Document has `PENDING` or `RETRYABLE` task: idempotently return current queued/retryable state.
- Document task is `SUCCEEDED` and document is `READY`: `400 INVALID_REQUEST` unless a future reprocess task defines full replacement semantics.
- Document task is `FAILED`: reset task to `PENDING`, clear `last_error_message`, clear `next_attempt_at`, keep/increment attempt semantics explicitly defined by service, set document `status=UPLOADED`, clear document `error_message`, set KB `PROCESSING`.
- Retry must not duplicate chunks or embeddings. Before processing a retry attempt, worker must clear old chunks/embeddings for the document or prove none exist.

### Delete Document

Existing endpoint:

```http
DELETE /api/admin/documents/{documentId}
```

Processing boundary:

- `PENDING` or `RETRYABLE`: mark task `CANCELED`, then proceed with storage/chunk/embedding/document cleanup.
- `PROCESSING`: reject with `409 DOCUMENT_PROCESSING`; do not delete storage or DB rows while the worker may still be reading/writing.
- `SUCCEEDED`, `FAILED`, `CANCELED`, or no task: existing delete cleanup may proceed.
- Cross-user and missing resource behavior remains unchanged.
- Storage cleanup failures remain visible `500 INTERNAL_ERROR`; do not report success.

### Delete Knowledge Base

Existing endpoint:

```http
DELETE /api/admin/knowledge-bases/{id}
```

Processing boundary:

- Preserve existing `409 KNOWLEDGE_BASE_IN_USE` app-reference rejection.
- If any owned document under the KB has a `PROCESSING` task, reject with `409 DOCUMENT_PROCESSING` or `409 KNOWLEDGE_BASE_PROCESSING`.
- For `PENDING` or `RETRYABLE` tasks under the KB, mark them `CANCELED` before cleanup.
- Then cleanup original storage, embeddings, chunks, documents, and KB row.
- Do not rely on FK errors or background worker races as the contract.

## Worker / Scheduler Contract

Use a lightweight in-process worker/scheduler. Recommended structure:

```text
DocumentUploadService or DocumentService.uploadAndEnqueue(...)
DocumentProcessingTaskEntity
DocumentProcessingTaskMapper
DocumentProcessingTaskService
DocumentProcessingWorker
DocumentProcessingScheduler
DocumentProcessingProperties
```

Recommended configuration:

```yaml
rag:
  document-processing:
    worker:
      enabled: true
      poll-fixed-delay-ms: 5000
      stale-processing-timeout-ms: 900000
      max-attempts: 3
      retry-backoff-ms: 60000
      worker-id: ${HOSTNAME:local}
```

Rules:

- The scheduler only orchestrates; business logic belongs in service/worker classes.
- Claim one eligible task with a conditional DB update before processing.
- Do not hold a DB transaction open while parsing large input or calling embedding provider.
- Persist each visible state transition before external or long-running work.
- On startup or each poll, recover stale `PROCESSING` tasks whose `locked_at` is older than `stale-processing-timeout-ms`.
- Recovery must mark stale tasks `RETRYABLE` if attempts remain, otherwise `FAILED`, and must update the matching document/KB state consistently.
- Processing must read the original file through storage abstraction. Extend `FileStorageService` with a read/open method; local and object backends must implement it without exposing storage internals.
- Worker logs may include `task_id`, `document_id`, `knowledge_base_id`, `user_id`, `attempt_count`, status, safe storage key, and exception class. Do not log file content, chunks, vectors, provider bodies, secrets, signed URLs, or absolute local paths.

## Validation / Error Matrix

| Scenario | HTTP / status | Code / task status | Required behavior |
|---|---:|---|---|
| Upload valid supported file | 200 | `DocumentVO.status=UPLOADED`, `processing_task_status=PENDING` | Original file saved, document row and task row created, KB becomes `PROCESSING`, no parse/embed before response. |
| Upload to missing KB | 404 | `NOT_FOUND` | No storage write, no document row, no task row. |
| Upload to cross-user KB | 403 | `FORBIDDEN` | Ownership checked before storage write. |
| Missing/empty file | 400 | `INVALID_REQUEST` | No storage write, no document row, no task row. |
| Unsupported filename/content type | 400 | `INVALID_REQUEST` | No storage write, no task row. |
| Storage save fails during upload | 500 | `INTERNAL_ERROR` | No fake queued task; failure visible. |
| Worker parses valid text | task success | `SUCCEEDED`, document `READY` | Chunks and embeddings saved, KB status recalculated to `READY`. |
| Parser finds no readable text | task terminal failure | `FAILED`, document `FAILED` | Bounded error message, no ready chunks. |
| Embedding missing config | task retry/terminal according to retry policy | `RETRYABLE` or `FAILED`, document `FAILED` | No vectors persisted; KB status recalculated. |
| Embedding provider timeout/non-2xx/network | task retryable until exhausted | `RETRYABLE` then `FAILED` | Safe error message, no provider body. |
| App restarts during `PROCESSING` | recovery | stale `PROCESSING` -> `RETRYABLE` or `FAILED` | No permanent stuck state. |
| Retry terminal failed document | 200 | `PENDING` | Clears retryable state safely and queues work once. |
| Retry ready document | 400 | `INVALID_REQUEST` | Do not implement implicit reprocess/replace. |
| Delete pending/retryable document | 200 | `CANCELED` then deleted | No worker later processes deleted document. |
| Delete processing document | 409 | `DOCUMENT_PROCESSING` | No storage cleanup or DB row deletion. |
| KB delete with processing document | 409 | `DOCUMENT_PROCESSING` or `KNOWLEDGE_BASE_PROCESSING` | No partial cleanup. |
| KB delete with queued tasks only | 200 | queued tasks canceled | Cleanup remains visible; storage failures fail request. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Upload returns quickly with `UPLOADED/PENDING`; scheduler claims the task, transitions through `PARSING/PARSED/EMBEDDING`, stores chunks/vectors, marks task `SUCCEEDED`, document `READY`, KB `READY`, and frontend polling stops only after terminal task/document state. |
| Base | Parse or embedding fails; original file remains stored, document becomes `FAILED`, task becomes `RETRYABLE` or `FAILED` with bounded safe error, KB reflects whether prior ready docs exist, and retry can safely enqueue without duplicate active chunks. |
| Bad | Upload blocks until embedding completes, worker swallows provider failure and marks ready, stale processing tasks stay stuck forever, retry creates duplicate chunks/vectors, delete races with a processing worker, or API responses expose `storage_path`/content/secrets. |

## Required Code Research Findings

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, document ingestion flow, storage baseline, object storage/delete lifecycle contract.
- `.trellis/spec/backend/directory-structure.md`: service/controller/mapper/package responsibilities.
- `.trellis/spec/backend/database-guidelines.md`: migrations, tenant-safe tables, status enums, transaction boundary rule.
- `.trellis/spec/backend/error-handling.md`: admin error envelope and document/KB error matrix.
- `.trellis/spec/backend/logging-guidelines.md`: safe document processing/storage logs.
- `.trellis/spec/backend/quality-guidelines.md`: ingestion, object storage, and regression test requirements.
- `.trellis/spec/rag/document-ingestion.md`: async ingestion trigger, status requirements, failure persistence, deletion contract.
- `.trellis/spec/gateway/resilience.md`: embedding failure/retry visibility and no hidden fallback.
- `.trellis/spec/security/rag-security.md`: tenant, storage, evidence, and error safety boundaries.
- `.trellis/spec/frontend/type-safety.md`: document/KB status unions and API payload alignment.
- `.trellis/spec/frontend/state-management.md`: document processing status is server state; poll only while needed.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required API/DB/frontend/test contract mapping.

### Code Patterns Found

- `DocumentAdminController.upload(...)` currently validates ownership/input, reads multipart bytes, then calls `DocumentService.uploadAndProcess(...)`.
- `DocumentService.uploadAndProcess(...)` currently runs `uploadAndParse(...)` in a transaction and then calls `embedAndFinalize(...)` before returning.
- `DocumentService` already uses explicit document statuses and avoids holding one transaction around embedding calls; this should be preserved when moving work to a worker.
- `FileStorageService` currently supports `save(...)` and `delete(...)` only; async processing needs a read/open method for stored originals.
- `KnowledgeBasePage.tsx` already polls while document status is non-terminal; it must be updated to respect task non-terminal states if retryable/queued work is exposed separately.
- `StatusTag.tsx` and frontend `DocumentStatus` types currently know only document status values.

### Files Likely To Modify

Backend:

- `backend/src/main/resources/db/migration/V14__create_document_processing_task_table.sql`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentEntity.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentMapper.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java`
- `backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java`
- `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java`
- `backend/src/main/java/com/sangui/raggateway/document/storage/ObjectFileStorageService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskStatus.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskEntity.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskMapper.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskService.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingWorker.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingScheduler.java` (new)
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentProcessingProperties.java` or existing document config properties.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`
- `backend/src/main/resources/application.yml`

Frontend if task status is exposed:

- `frontend/src/types/document.ts`
- `frontend/src/api/documents.ts`
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
- `frontend/src/components/domain/StatusTag.tsx`
- `frontend/src/app/i18n/dict.ts`

Docs/spec:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/rag/document-ingestion.md`
- `.trellis/spec/security/rag-security.md`
- `README.md`

Tests:

- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentProcessingTaskServiceTest.java` (new)
- `backend/src/test/java/com/sangui/raggateway/document/DocumentProcessingWorkerTest.java` (new)
- `backend/src/test/java/com/sangui/raggateway/document/DocumentProcessingSchedulerTest.java` (new if scheduler class has behavior)
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/storage/ObjectFileStorageServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminControllerTest.java`

## Required Tests and Assertion Points

Backend targeted commands:

```bash
cd backend
mvn -q "-Dtest=DocumentProcessingTaskServiceTest,DocumentProcessingWorkerTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest" test
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest,DocumentConfigTest" test
mvn -q "-Dtest=RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q -DskipTests compile
```

Frontend commands if frontend files change:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Final broader backend check if targeted tests pass:

```bash
cd backend
mvn -q test
```

Cross-file check:

```bash
git diff --check
```

Required assertions:

- Upload service returns before parser/embedding is invoked.
- Upload creates exactly one document row and one task row.
- Task claim is conditional and avoids duplicate workers claiming the same task.
- Worker reads from storage abstraction, not from request memory.
- Worker clears stale chunks/embeddings before retry processing to avoid mixed versions.
- Retry endpoint does not create duplicate active tasks.
- Stale `PROCESSING` recovery updates task, document, and KB consistently.
- Delete rejects active `PROCESSING` tasks before storage cleanup.
- Delete cancels queued retryable tasks before cleanup.
- Admin responses never expose storage internals, file content, chunks, vectors, provider bodies, stack traces, or secrets.
- Frontend polling continues while task status is `PENDING`, `PROCESSING`, or due `RETRYABLE`; it stops on `SUCCEEDED`, `FAILED`, or `CANCELED`.

## Implementation Notes

- Prefer reusing existing parsing/chunking/embedding helper logic by extracting narrow private/package-private methods from `DocumentService` or moving processing internals into a dedicated worker service. Avoid duplicate parse/embed pipelines.
- Keep `DocumentAdminController` thin: validation and response mapping only.
- Keep DB transaction boundaries short. Claim/update task states in transactions; parse and embedding calls happen outside long DB transactions.
- Do not use `@Async` as the only durability mechanism. The durable source of truth is the DB task row.
- Scheduler must be disabled or controlled under tests where needed, following existing test profile isolation patterns.
- If object storage read/open requires S3 `getObject`, test it with mocked S3 client only; no MinIO dependency is required for unit tests.

## Planning Self-Check

- Acceptance criteria are explicit in API, DB, worker, deletion, and test sections.
- Forbidden scope is listed under Non-Goals.
- Expected modified files are listed.
- Required tests and assertion points are listed.
- Specific guideline files were read, not only indexes.
- No open product question currently blocks implementation.
- API/DB/frontend DTO fields are aligned in the contract above.
