# #7 Upload Rollback Orphan File Cleanup

## Goal

Eliminate orphan original files created when document upload writes to local/object storage successfully but the later database transaction, document row insert, processing-task creation, or knowledge-base status update fails.

This is a backend storage/transaction consistency task. The implementation must keep the document upload contract asynchronous: valid upload returns `DocumentVO.status=UPLOADED` with `processing_task_status=PENDING`; parsing, chunking, and embedding still happen later through the durable processing task.

## Classification

- Scope: Complex Task
- Reason: The change crosses an external side effect boundary (`FileStorageService.save/delete`) and database/task transaction boundaries. It is not a UI change and must not be handled as a local hotfix that only silences one exception.
- Fix type: Structural boundary fix. Express the invariant in one upload lifecycle boundary and test all failure points that occur after storage save.

## Current Problem

`DocumentService.uploadAndEnqueue(...)` currently validates the request, saves the uploaded bytes through `FileStorageService.save(...)`, then creates the `rag_document` row, creates `rag_document_processing_task`, and updates the knowledge base status to `PROCESSING`.

If any operation after `save(...)` fails, the database work may roll back or remain incomplete while the physical/object storage file has already been written. That leaves an orphan file that is not tracked by `rag_document.storage_path` and cannot be cleaned by existing document or knowledge-base delete flows.

## Required Invariant

After a document upload attempt returns failure to the caller:

- If no durable `rag_document` row and processing task were successfully created, any file written by `FileStorageService.save(...)` must be deleted.
- If cleanup delete reports missing object/file, treat it as cleanup complete because storage delete is idempotent by contract.
- If cleanup delete itself fails for a real storage/backend error, do not hide it as success. The upload must still fail visibly, and logs must include only safe identifiers (`storageKey`, user/document/KB IDs when available, exception class), never credentials, absolute local paths, or file content.
- Parse/embedding failures after a successful queued upload are not part of this cleanup path; the original file is a durable ingestion artifact and must remain until explicit document/KB deletion.

## API / Command / Payload Fields

No public or admin API shape change is expected.

Affected API remains:

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <admin-jwt>
Content-Type: multipart/form-data

file=<multipart file>
```

Successful response remains:

```json
{
  "code": "OK",
  "data": {
    "id": 10,
    "user_id": 100,
    "knowledge_base_id": 1,
    "original_filename": "test.md",
    "content_type": "text/markdown",
    "file_size": 11,
    "status": "UPLOADED",
    "chunk_count": 0,
    "error_message": null,
    "processing_task_id": 20,
    "processing_task_status": "PENDING"
  }
}
```

Fields that must remain forbidden in upload/list/detail responses:

```text
storage_path, object endpoint, bucket, access key, secret key,
absolute local filesystem path, uploaded file content, chunk content,
embedding vector, stack_trace
```

No frontend DTO/type, database migration, Docker/env, or public `/v1/*` contract change is expected unless the implementation proves a new executable lifecycle contract is required. If a spec update is needed, prefer `.trellis/spec/rag/document-ingestion.md`, `.trellis/spec/backend/error-handling.md`, `.trellis/spec/backend/logging-guidelines.md`, `.trellis/spec/backend/quality-guidelines.md`, and the project spec object storage baseline.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Unsupported filename/content type, empty file, oversized file | Fail before storage save; no storage delete needed | `DocumentServiceTest`, `DocumentAdminControllerTest` |
| `FileStorageService.save(...)` fails | Upload fails visibly; no cleanup delete because no storage key exists | `DocumentServiceTest` |
| `documentMapper.insert(...)` fails after storage save | Upload fails; saved storage key is deleted exactly once | `DocumentServiceTest` |
| `taskService.createTask(...)` fails after document insert | Upload fails; saved storage key is deleted exactly once; DB transaction must not leave a usable partial upload | `DocumentServiceTest` |
| `knowledgeBaseService.updateStatus(...PROCESSING)` fails after task creation | Upload fails; saved storage key is deleted exactly once | `DocumentServiceTest` |
| Cleanup delete sees missing local file/object or S3 `NoSuchKey`/404 | Treat cleanup as complete; original upload failure still propagates | `DocumentServiceTest` plus existing storage tests |
| Cleanup delete fails with real storage/backend error | Upload fails visibly; do not return success or fabricate `DocumentVO` | `DocumentServiceTest` |
| Normal valid upload | File saved, document row created, processing task created, KB set `PROCESSING`, no cleanup delete | `DocumentServiceTest`, `DocumentAdminControllerTest` |
| Parse/embedding worker later fails | Original file remains stored; no orphan cleanup on worker failure | Existing `processDocument` tests remain valid |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Valid Markdown upload saves one original file, creates `rag_document`, creates one `PENDING` processing task, updates KB to `PROCESSING`, returns `DocumentVO` without `storage_path`, and never calls storage delete. |
| Base | Any post-storage DB/task/status failure deletes the just-written storage key and propagates the original failure or an explicitly visible cleanup failure. No orphan storage object remains untracked. |
| Bad | The service saves the file, DB/task creation fails, and upload returns failure while storage delete is never called; or cleanup failure is swallowed and upload reports success; or the fix deletes originals on parser/embedding failure after a valid queued upload. |

## Files Likely To Modify

Expected implementation files:

- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`: centralize upload enqueue transaction/cleanup boundary around `fileStorageService.save(...)`, DB insert, task creation, and KB status update.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`: add failure-path tests for document insert, task creation, KB status update, cleanup missing-idempotent behavior, cleanup real failure, and happy-path no cleanup.
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`: add or preserve idempotent delete coverage if missing for local storage.
- `backend/src/test/java/com/sangui/raggateway/document/storage/ObjectFileStorageServiceTest.java`: preserve existing `NoSuchKey` and 404 idempotent delete coverage.

Possible spec files if implementation establishes or clarifies lifecycle contract:

- `.trellis/spec/rag/document-ingestion.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/sangui-rag-gateway.md`

## Explicit Non-Goals

- Do not modify public `/v1/*` gateway behavior.
- Do not change frontend upload DTOs, `DocumentVO`, or TypeScript types unless a verified backend contract change requires it.
- Do not introduce a new storage table, orphan scanner, background cleanup job, MQ, retry queue, or compensation status unless the direct transaction-boundary fix is proven insufficient.
- Do not delete original files for normal parse, chunking, embedding, or worker retry failures after upload has been successfully queued.
- Do not make storage cleanup a silent fallback. Cleanup failures must remain visible.
- Do not log storage credentials, signed URLs, absolute local paths, raw uploaded bytes, chunk content, embeddings, provider bodies, or stack traces in API responses.
- Do not broaden into #4 retrieval SQL/ANN work or #5 duplicate chunk/retry semantics.

## Required Tests and Assertion Points

Run with a hard 60-second timeout per backend unit-test command when feasible.

```bash
cd backend
mvn -q "-Dtest=DocumentServiceTest" test
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest" test
mvn -q "-Dtest=DocumentAdminControllerTest" test
mvn -q "-Dtest=DocumentProcessingTaskServiceTest,DocumentProcessingWorkerTest" test
mvn -q -DskipTests compile
git diff --check
```

Assertion points:

- Failure before storage validation never calls `fileStorageService.save(...)`.
- Post-storage failure calls `fileStorageService.delete(savedStorageKey)` exactly once.
- Happy path calls save but never delete.
- Delete idempotency remains owned by storage implementations; missing object/file is not treated as cleanup failure.
- Real cleanup failure is visible and not converted to upload success.
- `DocumentVO` still does not expose `storage_path`.

## Branch and Handoff Notes

The previous retrieval-threshold-single-source task was completed and recorded on `feature/retrieval-threshold-single-source`. Coding for this task should happen from a clean `main` after that feature is merged, using a dedicated branch such as:

```text
feature/upload-rollback-orphan-file-cleanup
```

This PRD/context preparation intentionally makes no business-code edits.
