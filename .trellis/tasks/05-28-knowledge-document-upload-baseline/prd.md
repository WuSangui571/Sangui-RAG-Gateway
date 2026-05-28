# Knowledge Base and Document Upload Baseline

## Classification

Complex Task.

This task crosses backend API contracts, database migration, local storage, document ingestion, parser/chunker behavior, tenant isolation, logging safety, and spec/test updates. Codex planning scope for this round is PRD, research, Trellis context, and DeepSeek handoff only. Business implementation must be done later by DeepSeek.

## Goal

Add the first backend baseline for private knowledge ingestion so Sangui-RAG-Gateway moves from an OpenAI-compatible LLM gateway toward a RAG gateway.

The baseline must support:

- Creating tenant-scoped knowledge bases.
- Uploading `txt` and `md` documents through admin APIs.
- Saving original file metadata and original file bytes through a local storage abstraction.
- Parsing text/markdown into plain text.
- Cleaning and chunking text into persisted document chunks.
- Persisting document status transitions through `UPLOADED -> PARSING -> PARSED`, with `FAILED` for processing failures.

Out of scope for this task:

- Embedding API calls.
- pgvector embedding storage.
- Retrieval.
- Prompt augmentation.
- Frontend UI implementation.
- PDF/DOCX parsing.
- MinIO implementation.
- Async queue processing.
- App-to-knowledge-base binding unless already required by an existing schema dependency. Do not add it opportunistically.

## Product Context

The project already has:

- App and app API key admin baseline.
- Upstream model config admin baseline.
- Authenticated `GET /v1/models`.
- Authenticated non-streaming and streaming `POST /v1/chat/completions`.
- Safe request logging for chat completions.

The next missing core value is private document ingestion. This task intentionally stops before embedding/retrieval so later RAG retrieval can build on stable tables, statuses, and admin contracts.

## API Contracts

All endpoints below are admin APIs and must use the existing admin `ApiResponse<T>` envelope, not OpenAI-compatible error objects.

All endpoints require the temporary admin identity header:

```http
X-Admin-User-Id: <positive long>
```

### Create Knowledge Base

```http
POST /api/admin/knowledge-bases
Content-Type: application/json
X-Admin-User-Id: 100
```

Request:

```json
{
  "name": "Product Docs",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536
}
```

Rules:

- `name` is required, trimmed, and must not be blank.
- `embedding_model` is required for this baseline because the knowledge base must declare its eventual embedding contract before chunks are created.
- `embedding_dimension` is required and must be positive.
- `status` is created as `EMPTY`.
- Duplicate names for the same `user_id` should be rejected if a unique constraint is added. Cross-user duplicate names are allowed.

Response data (`KnowledgeBaseVO`):

```json
{
  "id": 1,
  "user_id": 100,
  "name": "Product Docs",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536,
  "status": "EMPTY",
  "created_at": "2026-05-28T10:00:00",
  "updated_at": "2026-05-28T10:00:00"
}
```

### List Knowledge Bases

```http
GET /api/admin/knowledge-bases?status=EMPTY
X-Admin-User-Id: 100
```

Rules:

- Return only rows owned by `X-Admin-User-Id`.
- `status` filter is optional.
- Valid status values: `EMPTY`, `PROCESSING`, `READY`, `FAILED`.
- Order by `created_at DESC`.

### Get Knowledge Base Detail

```http
GET /api/admin/knowledge-bases/{id}
X-Admin-User-Id: 100
```

Rules:

- Same-user access returns `KnowledgeBaseVO`.
- Existing but cross-user knowledge base returns `403 FORBIDDEN` with a generic access-denied message.
- Missing knowledge base returns `404 NOT_FOUND`.

### Upload Document

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
Content-Type: multipart/form-data
X-Admin-User-Id: 100
```

Multipart field:

```text
file=<uploaded .txt or .md file>
```

Rules:

- Knowledge base must exist and belong to the current user.
- Only `.txt`, `.md`, and `.markdown` filenames are supported in this baseline.
- Content type may be `text/plain`, `text/markdown`, `application/octet-stream`, or blank when the filename extension is supported.
- Empty files are rejected before storage when size is zero.
- For non-empty files, save the original file through the storage abstraction and persist internal `storage_path`.
- Do not return raw `storage_path` in admin responses unless a future download API is explicitly designed.
- Processing is synchronous for this baseline:
  - create document row as `UPLOADED`,
  - move to `PARSING`,
  - parse and clean text,
  - create chunks,
  - move document to `PARSED`,
  - update chunk count,
  - update knowledge base status to `READY` after successful parsed chunks.
- If parsing/chunking fails after a document row is created, mark the document `FAILED`, store a bounded admin-safe `error_message`, update the knowledge base to `FAILED` only when there are no successfully parsed documents, and return a successful `ApiResponse<DocumentVO>` with `status=FAILED`.
- Validation failures before a document row is created return `400 INVALID_REQUEST`.

Response data (`DocumentVO`):

```json
{
  "id": 10,
  "user_id": 100,
  "knowledge_base_id": 1,
  "original_filename": "faq.md",
  "content_type": "text/markdown",
  "file_size": 2048,
  "status": "PARSED",
  "chunk_count": 6,
  "error_message": null,
  "created_at": "2026-05-28T10:00:00",
  "updated_at": "2026-05-28T10:00:02"
}
```

### List Documents For Knowledge Base

```http
GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents?status=PARSED
X-Admin-User-Id: 100
```

Rules:

- Verify the knowledge base belongs to the current user before listing documents.
- Return only documents with matching `user_id` and `knowledge_base_id`.
- Optional status filter accepts `UPLOADED`, `PARSING`, `PARSED`, `FAILED`.
- Order by `created_at DESC`.

### Get Document Detail

```http
GET /api/admin/documents/{documentId}
X-Admin-User-Id: 100
```

Rules:

- Same-user access returns `DocumentVO`.
- Cross-user existing document returns `403 FORBIDDEN`.
- Missing document returns `404 NOT_FOUND`.
- Do not return chunk content in this endpoint.

## Database Contract

Add a new Flyway migration after the current latest migration `V4__create_request_log_table.sql`.

Suggested file:

```text
backend/src/main/resources/db/migration/V5__create_knowledge_document_tables.sql
```

### `rag_knowledge_base`

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `user_id` | `BIGINT` | yes | Tenant boundary |
| `name` | `VARCHAR(255)` | yes | Admin display name |
| `embedding_model` | `VARCHAR(255)` | yes | Future embedding contract |
| `embedding_dimension` | `INTEGER` | yes | Must be positive |
| `status` | `VARCHAR(32)` | yes | `EMPTY`, `PROCESSING`, `READY`, `FAILED` |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |

Required indexes/constraints:

```text
idx_rag_knowledge_base_user_status on (user_id, status)
idx_rag_knowledge_base_user_created_at on (user_id, created_at DESC)
optional unique idx_rag_knowledge_base_user_name on (user_id, name)
```

### `rag_document`

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `user_id` | `BIGINT` | yes | Tenant boundary |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)` |
| `original_filename` | `VARCHAR(512)` | yes | Safe filename only |
| `content_type` | `VARCHAR(255)` | no | Client-provided content type |
| `file_size` | `BIGINT` | yes | Uploaded size |
| `storage_path` | `VARCHAR(1024)` | yes | Internal storage key/path, never exposed by VO |
| `status` | `VARCHAR(32)` | yes | `UPLOADED`, `PARSING`, `PARSED`, `FAILED` |
| `chunk_count` | `INTEGER` | yes | Default `0` |
| `error_message` | `VARCHAR(512)` | no | Bounded admin-safe message |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |

Required indexes:

```text
idx_rag_document_user_status on (user_id, status)
idx_rag_document_kb_status on (knowledge_base_id, status)
idx_rag_document_user_kb_created_at on (user_id, knowledge_base_id, created_at DESC)
```

### `rag_document_chunk`

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `user_id` | `BIGINT` | yes | Tenant boundary, denormalized for SQL-level future retrieval |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)` |
| `document_id` | `BIGINT` | yes | FK to `rag_document(id)` |
| `chunk_index` | `INTEGER` | yes | 0-based index within document |
| `content` | `TEXT` | yes | Chunk text |
| `token_count` | `INTEGER` | no | Baseline placeholder; may use character count until tokenizer exists |
| `metadata` | `JSONB` | no | Source filename, parser, char offsets if available |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |

Required indexes/constraints:

```text
idx_rag_document_chunk_user_kb on (user_id, knowledge_base_id)
idx_rag_document_chunk_document on (document_id)
unique idx_rag_document_chunk_document_index on (document_id, chunk_index)
```

Do not add an embedding vector column in this task.

## Storage Contract

Introduce a storage abstraction but only implement local storage:

```java
public interface FileStorageService {
    StoredFile save(String ownerType, Long ownerId, String originalFilename, InputStream inputStream);
}
```

Expected baseline behavior:

- Store files under a configured local root path.
- Generate a non-guessable storage key/path; do not trust original filenames as physical paths.
- Sanitize original filenames for metadata.
- Reject path traversal attempts.
- Leave a future seam for MinIO without adding MinIO dependencies.

Suggested configuration:

```yaml
rag:
  gateway:
    storage:
      type: local
      local-path: ${FILE_STORAGE_LOCAL_PATH:./data/uploads}
    document:
      chunk-size: ${RAG_DOCUMENT_CHUNK_SIZE:800}
      chunk-overlap: ${RAG_DOCUMENT_CHUNK_OVERLAP:100}
      max-file-size-bytes: ${RAG_DOCUMENT_MAX_FILE_SIZE_BYTES:1048576}
```

If introducing these config keys, update `.env.example`, README/config docs if applicable, and the project spec.

## Parser And Chunker Contract

### Parser

Use an abstraction aligned with the project spec:

```java
public interface DocumentParser {
    boolean supports(String contentType, String filename);
    ParsedDocument parse(InputStream inputStream);
}
```

Baseline parsers:

- `PlainTextDocumentParser` supports `.txt`.
- `MarkdownDocumentParser` supports `.md` and `.markdown`.

Both may read UTF-8 text directly. Do not add PDFBox or Apache POI in this task.

### Cleaner

Baseline cleaning:

- Normalize CRLF/CR to LF.
- Trim leading/trailing whitespace.
- Collapse excessive blank lines conservatively.
- Do not log or persist full cleaned text outside chunks.

### Chunker

Baseline chunking:

- Default chunk size: 800 characters.
- Default overlap: 100 characters.
- Reject invalid config where chunk size <= 0, overlap < 0, or overlap >= chunk size.
- Preserve deterministic chunk order.
- Skip blank chunks.
- For each chunk persist:
  - `chunk_index`,
  - `content`,
  - `token_count` placeholder,
  - metadata such as parser type and source filename if available.

## Validation And Error Matrix

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler response. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler response. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Controller validation before mutation. |
| Create KB with null/blank name | 400 | `INVALID_REQUEST` | No row inserted. |
| Create KB with blank embedding model | 400 | `INVALID_REQUEST` | No row inserted. |
| Create KB with null/non-positive dimension | 400 | `INVALID_REQUEST` | No row inserted. |
| Invalid KB status filter | 400 | `INVALID_REQUEST` | Do not echo arbitrary input. |
| Get missing KB | 404 | `NOT_FOUND` | Safe admin envelope. |
| Get cross-user KB | 403 | `FORBIDDEN` | Generic access denied. |
| Upload to missing KB | 404 | `NOT_FOUND` | No file write, no document row. |
| Upload to cross-user KB | 403 | `FORBIDDEN` | No file write, no document row. |
| Missing multipart file | 400 | `INVALID_REQUEST` | No document row. |
| Empty multipart file | 400 | `INVALID_REQUEST` | No document row. |
| Unsupported filename/content type | 400 | `INVALID_REQUEST` | No document row. |
| Local storage write failure | 500 | `INTERNAL_ERROR` or bounded `STORAGE_ERROR` | No sensitive path leakage. |
| Parse/chunk failure after document row | 200 | `OK` with `DocumentVO.status=FAILED` | Document row records bounded `error_message`; no raw content in response/logs. |
| Empty parsed text after cleaning | 200 | `OK` with `DocumentVO.status=FAILED` | Document row records bounded reason such as `Document has no readable text`. |
| Invalid document status filter | 400 | `INVALID_REQUEST` | Do not echo arbitrary input. |
| Get missing document | 404 | `NOT_FOUND` | Safe admin envelope. |
| Get cross-user document | 403 | `FORBIDDEN` | Generic access denied. |

## Good / Base / Bad Cases

Good cases:

- User `100` creates a knowledge base with embedding model and dimension; response status is `EMPTY`.
- User `100` uploads a Markdown file to their knowledge base; document reaches `PARSED`, chunks are inserted with sequential indexes, and knowledge base becomes `READY`.
- User `100` lists their knowledge bases and documents; only their rows are returned.

Base cases:

- `.txt` file with short text creates exactly one chunk.
- Long `.md` file creates multiple overlapping chunks with deterministic order.
- Markdown content is treated as text; no Markdown AST or HTML rendering is required.
- `application/octet-stream` is accepted only when filename extension is supported.

Bad cases:

- User `200` cannot access or upload to user `100` knowledge base.
- Unsupported `.pdf` or `.docx` upload returns `400 INVALID_REQUEST` and does not create document/chunks.
- Empty upload returns `400 INVALID_REQUEST`.
- Parsed empty text results in a `FAILED` document with bounded error message and zero chunks.
- Logs and responses never include raw file content, full chunk list, local absolute storage paths, or stack traces.

## Required Tests And Assertion Points

Service/unit tests:

- `KnowledgeBaseServiceTest`
  - create persists `user_id`, `name`, `embedding_model`, positive `embedding_dimension`, `EMPTY`.
  - reject blank name, blank embedding model, missing/non-positive dimension.
  - list/find methods include tenant scope.
  - status update rules for `EMPTY -> PROCESSING/READY/FAILED`.
- `DocumentServiceTest`
  - upload/process happy path creates document and chunks, transitions `UPLOADED -> PARSING -> PARSED`.
  - parse failure transitions to `FAILED` with bounded `error_message`.
  - list/detail methods include `user_id` and `knowledge_base_id` scope.
  - storage path is internal and not exposed by VO.
- `PlainTextDocumentParserTest` and `MarkdownDocumentParserTest`
  - supported extensions/content types.
  - UTF-8 parsing.
  - unsupported types rejected by parser selection.
- `TextChunkerTest`
  - short text one chunk.
  - long text multiple chunks with overlap.
  - blank text no chunks or explicit failure depending service contract.
  - invalid chunk config rejected.
- `LocalFileStorageServiceTest`
  - saves under configured root.
  - generated path is non-guessable and does not trust original filename.
  - traversal-like filenames are sanitized.

Controller tests:

- `KnowledgeBaseAdminControllerTest`
  - create/list/detail success.
  - missing/non-positive admin user header handling.
  - validation failures return `ApiResponse` with `INVALID_REQUEST`.
  - missing vs cross-user resources return `404` vs `403`.
- `DocumentAdminControllerTest`
  - multipart upload success returns `DocumentVO` with no `storage_path`.
  - unsupported/empty/missing file failure.
  - missing vs cross-user knowledge base for upload.
  - document list/detail tenant boundary.

Regression tests:

- Existing admin and gateway tests must still pass.
- `OpenAiChatCompletionsControllerTest` and streaming tests should remain unaffected because ingestion is not yet wired into chat completions.

Required test commands:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,ModelConfigAdminControllerTest,ModelConfigServiceTest,AppServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Spec Updates Required

Update these after implementation:

- `.trellis/spec/sangui-rag-gateway.md`
  - implemented knowledge/document admin API baseline.
  - document status and knowledge status behavior.
  - local storage config keys if added.
- `.trellis/spec/backend/database-guidelines.md`
  - concrete `rag_knowledge_base`, `rag_document`, `rag_document_chunk` schema.
  - tenant-safe query rules and indexes.
- `.trellis/spec/backend/error-handling.md`
  - admin knowledge/document API error matrix.
- `.trellis/spec/backend/logging-guidelines.md`
  - document upload/parse log contract and sensitive data exclusions.
- `.trellis/spec/backend/quality-guidelines.md`
  - ingestion baseline test commands and boundaries.
- `.trellis/spec/frontend/type-safety.md`
  - future frontend `KnowledgeBaseVO`, `DocumentVO`, `KnowledgeBaseStatus`, and baseline `DocumentStatus` unions if API contract changes need frontend alignment.

## Non-Goals And Guardrails

- Do not implement embeddings or retrieval.
- Do not change `/v1/chat/completions` behavior.
- Do not add prompt augmentation.
- Do not add PDF/DOCX dependencies.
- Do not add MinIO dependencies.
- Do not implement frontend UI.
- Do not expose `storage_path`, raw document content, complete chunk content lists, API keys, upstream keys, stack traces, or local absolute paths in responses/logs.
- Do not refactor existing app/model/api-key/chat modules beyond the minimum needed to compile with shared helpers.
- Do not modify archived task history except through the new task files/context created for this task.

## Acceptance Criteria

- [ ] `rag_knowledge_base`, `rag_document`, and `rag_document_chunk` migrations exist with tenant-safe indexes.
- [ ] Admin API can create/list/detail same-user knowledge bases.
- [ ] Admin API can upload `.txt` and `.md/.markdown` files to same-user knowledge bases.
- [ ] Uploaded documents persist metadata, local storage path internally, status, chunk count, and bounded error message.
- [ ] Parsed documents produce deterministic chunks with chunk indexes and content.
- [ ] Tenant isolation is enforced for all knowledge/document admin reads and mutations.
- [ ] Admin responses use `ApiResponse` and snake_case fields.
- [ ] Document/file content and storage paths are not leaked in logs or responses.
- [ ] Parser/chunker/storage/controller/service tests cover good/base/bad cases.
- [ ] Existing app/model/api-key/gateway tests still pass.
- [ ] Specs are updated with implemented contract.
