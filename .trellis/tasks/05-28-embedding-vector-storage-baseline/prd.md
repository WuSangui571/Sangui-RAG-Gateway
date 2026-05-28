# Embedding and Vector Storage Baseline

## Task Classification

Complex Task.

Reason: this task crosses database migration, pgvector storage, document ingestion state transitions, upstream OpenAI-compatible embedding HTTP calls, model configuration contracts, tenant isolation, error handling, safe logging, tests, and project spec updates. Codex prepares requirements/context only in this round. DeepSeek implements code in a later round.

## Goal

Extend the current document ingestion baseline so uploaded and chunked documents proceed through embedding generation and persist pgvector vectors for each chunk.

Current baseline:

- Knowledge bases can be created with `embedding_model` and `embedding_dimension`.
- Documents can be uploaded, parsed, chunked, and stored.
- `rag_document_chunk` stores chunk content and metadata, but no vector.
- Document status currently reaches `PARSED`, while RAG-ready vector persistence is not implemented.

Target baseline:

- After parsing/chunking succeeds, the ingestion pipeline calls an OpenAI-compatible `/v1/embeddings` upstream using a same-user enabled model config whose embedding contract matches the knowledge base.
- Chunk vectors are stored in PostgreSQL/pgvector with tenant-safe columns.
- Document state transitions are explicit: `UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY`; failures from parsing or embedding end in `FAILED` with bounded `error_message`.
- Knowledge base readiness reflects whether at least one document is vector-ready.
- This task stops before retrieval and prompt augmentation.

## Non-Goals

- Do not implement vector retrieval, similarity search endpoints, prompt augmentation, citations, no-hit policy, or chat RAG behavior.
- Do not implement async jobs, queues, retries, schedulers, Redis task state, or background workers.
- Do not add frontend pages/components.
- Do not expose a public `/v1/embeddings` gateway API to external app callers.
- Do not support PDF/DOCX or new parser types.
- Do not introduce provider-specific embedding APIs beyond OpenAI-compatible `/v1/embeddings`.
- Do not log or return embedding vectors, full chunk content, app API keys, upstream plaintext keys, encrypted upstream keys, raw provider error bodies, or stack traces in API responses.

## Proposed Architecture

Preferred design:

1. Keep vectors on `rag_document_chunk` by adding an `embedding vector(<dimension>)` column if the project chooses a single MVP dimension.
2. If fixed per-KB dimensions must vary, use a separate `rag_document_chunk_embedding` table with:
   - `id`
   - `user_id`
   - `knowledge_base_id`
   - `document_id`
   - `chunk_id`
   - `embedding_model`
   - `embedding_dimension`
   - `embedding vector`
   - timestamps
   - unique `chunk_id`

Decision guidance for implementer:

- PostgreSQL `vector(n)` requires a fixed dimension per column. Since the current KB contract allows per-KB `embedding_dimension`, a separate vector table is safer if multiple dimensions must be supported in one deployment.
- If DeepSeek chooses a single fixed MVP column dimension on `rag_document_chunk`, it must update PRD/spec to document that all KBs must use that dimension for now and validation must reject other dimensions. This is less flexible.
- Preferred PRD contract is a new vector table because it preserves the existing per-KB dimension strategy without mixing dimensions.

## Database Contract

Preferred new migration: `backend/src/main/resources/db/migration/V6__create_document_chunk_embedding_table.sql`.

Preferred table: `rag_document_chunk_embedding`.

Required columns:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary. |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)`. |
| `document_id` | `BIGINT` | yes | FK to `rag_document(id)`. |
| `chunk_id` | `BIGINT` | yes | FK to `rag_document_chunk(id)`, unique. |
| `embedding_model` | `VARCHAR(255)` | yes | Must match the KB embedding model used at ingestion time. |
| `embedding_dimension` | `INTEGER` | yes | Must match KB dimension and actual vector length. |
| `embedding` | `VECTOR` or fixed `VECTOR(n)` | yes | pgvector vector value. |
| `created_at` | `TIMESTAMP` | yes | Default/current timestamp. |
| `updated_at` | `TIMESTAMP` | yes | Default/current timestamp. |

Required indexes:

- Unique index on `chunk_id`.
- Tenant-scoped lookup index on `(user_id, knowledge_base_id)`.
- Document lookup index on `(document_id)`.
- Future retrieval index only if a fixed operator class is selected. If not implementing retrieval yet, document that vector ANN index is deferred until retrieval metric is chosen.

Tenant safety:

- Every vector row must duplicate `user_id` and `knowledge_base_id`.
- Future vector queries must include both `user_id` and `knowledge_base_id` in SQL before ordering by vector distance.
- Java-only tenant filtering after vector operations is forbidden.

Dimension safety:

- The number of vectors returned by the embedding provider must equal the number of input chunks.
- Every vector length must equal `rag_knowledge_base.embedding_dimension`.
- The model config used for embedding must have:
  - same `user_id`
  - `status=ENABLED`
  - non-blank `embedding_model`
  - `embedding_dimension` equal to the KB dimension
  - usable encrypted upstream API key

## Admin API / Payload Contract

No new admin endpoint is required in this baseline.

Existing API contracts that must remain compatible:

```http
POST /api/admin/knowledge-bases
GET  /api/admin/knowledge-bases
GET  /api/admin/knowledge-bases/{id}
POST /api/admin/knowledge-bases/{kbId}/documents
GET  /api/admin/knowledge-bases/{kbId}/documents?status=...
GET  /api/admin/documents/{documentId}
```

Existing payload fields that must remain aligned:

```json
CreateKnowledgeBaseDTO {
  "name": "Product Docs",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536
}
```

```json
KnowledgeBaseVO {
  "id": 1,
  "user_id": 100,
  "name": "Product Docs",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536,
  "status": "READY",
  "created_at": "...",
  "updated_at": "..."
}
```

```json
DocumentVO {
  "id": 10,
  "user_id": 100,
  "knowledge_base_id": 1,
  "original_filename": "manual.md",
  "content_type": "text/markdown",
  "file_size": 1234,
  "status": "READY",
  "chunk_count": 3,
  "error_message": null,
  "created_at": "...",
  "updated_at": "..."
}
```

Status enum updates:

- `DocumentStatus` must include `EMBEDDING` and `READY`.
- `DocumentVO.status` may now return `EMBEDDING` or `READY`.
- Existing document status filter validation must accept the new statuses.
- Frontend type safety spec must be updated to include these values.
- `KnowledgeBaseStatus` may keep `PROCESSING` as the active ingestion state and `READY` as at least one vector-ready document exists. Add `EMBEDDING` to KB only if the implementation needs more precise admin display; if added, update backend/frontend/spec/tests together.

## Embedding Upstream Contract

Implement an internal OpenAI-compatible embedding client, preferably under:

```text
backend/src/main/java/com/sangui/raggateway/embedding/
```

Expected upstream request:

```http
POST {base_url}/v1/embeddings
Authorization: Bearer <decrypted-upstream-api-key>
Content-Type: application/json
```

Base URL normalization must follow the existing chat upstream pattern:

| Input `base_url` | Final embedding URL |
|---|---|
| `https://api.example.com` | `https://api.example.com/v1/embeddings` |
| `https://api.example.com/` | `https://api.example.com/v1/embeddings` |
| `https://api.example.com/v1` | `https://api.example.com/v1/embeddings` |
| `https://api.example.com/v1/` | `https://api.example.com/v1/embeddings` |

Expected request body:

```json
{
  "model": "text-embedding-3-small",
  "input": ["chunk text 1", "chunk text 2"]
}
```

Expected successful response shape:

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": [0.1, 0.2, 0.3]
    }
  ],
  "model": "text-embedding-3-small",
  "usage": {
    "prompt_tokens": 10,
    "total_tokens": 10
  }
}
```

Client behavior:

- Batch chunks in one or more embedding requests. A simple configurable batch size is acceptable; default can be conservative.
- Preserve input order by `data[].index`.
- Validate response count, indexes, and vector dimensions.
- Normalize upstream non-2xx/network errors to a safe embedding failure.
- Timeout must be configured and tested. Reuse `rag.gateway.upstream.timeout-seconds` or add a clearly documented `rag.gateway.embedding.timeout-seconds`.
- Provider response bodies must not be returned to clients or logged.

## Model Config / KB Contract

Embedding config resolution must be explicit.

Recommended MVP resolution:

- For a document upload, use the same-user enabled `rag_model_config` whose `embedding_model` and `embedding_dimension` match the target KB.
- If multiple configs match, choose the most deterministic existing app/model-config path only if already available; otherwise add a small service method that finds a unique enabled config by user/model/dimension.
- If zero configs match, mark the document `FAILED` after parse/chunk and set bounded admin-safe `error_message`, e.g. `Embedding model config is not ready`.
- If multiple configs match and no deterministic selection exists, fail safely with bounded `error_message`, or enforce uniqueness in service logic. Do not randomly choose one.

Validation:

- KB `embedding_model` must match model config `embedding_model`.
- KB `embedding_dimension` must match model config `embedding_dimension`.
- Model config must be `ENABLED`.
- Upstream key decrypt failure must mark embedding as failed with safe message.
- Do not use caller-provided chat model or app default chat model for embedding unless it also satisfies the embedding contract.

## Document State Contract

Happy path:

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
```

Failure path:

```text
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

Required behavior:

- `PARSED` means text chunks were produced.
- `EMBEDDING` means external embedding call/vector persistence is in progress.
- `READY` means all chunks for that document have valid persisted vectors.
- Do not mark a document `READY` if any chunk lacks a vector.
- Store bounded `error_message` on failure, maximum 512 characters to match schema.
- If one document fails but the KB already has at least one `READY` document, keep the KB `READY`.
- If no document is vector-ready after a failure, set KB `FAILED`.

## Validation / Error Matrix

| Scenario | API response | Document status | KB status | Persist vectors | Required notes |
|---|---|---|---|---:|---|
| Valid txt/md upload, matching enabled embedding config, provider returns correct vectors | 200 admin envelope with `DocumentVO.status=READY` | `READY` | `READY` | yes | All chunk vectors persisted. |
| Parse/chunk succeeds but no enabled matching embedding config | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` if older ready docs exist | no | `error_message` bounded and safe. |
| Model config embedding dimension differs from KB | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or preserved `READY` | no | Explicit dimension mismatch assertion. |
| Provider returns fewer/more embeddings than inputs | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or preserved `READY` | no | No partial ready state. |
| Provider returns vector with wrong dimension | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or preserved `READY` | no | No partial ready state. |
| Provider non-2xx/network error | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or preserved `READY` | no | Do not expose provider body. |
| Provider timeout | 200 admin envelope with `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or preserved `READY` | no | Safe timeout message. |
| Unsupported file/content type | 400 admin envelope | no document row | unchanged | no | Existing behavior preserved. |
| Cross-user KB upload | 403 admin envelope | no document row | unchanged | no | Existing behavior preserved. |
| Missing KB upload | 404 admin envelope | no document row | unchanged | no | Existing behavior preserved. |

## Good / Base / Bad Cases

Good cases:

- Upload a markdown file into a KB with matching enabled embedding config; all chunks become vector rows and document becomes `READY`.
- Batch embedding preserves chunk order and maps vector row to correct `chunk_id`.
- Existing KB with one ready document remains `READY` when a later document embedding fails.

Base cases:

- Existing knowledge base create/list/detail APIs keep snake_case fields and admin envelope.
- Existing upload validation for unsupported extension/content type still rejects before file storage or DB insert.
- Existing chat completions pass-through and streaming behavior remains unchanged.
- Existing model config create/update keeps upstream key encryption/masking behavior.

Bad cases:

- Missing/disabled/mismatched embedding config fails safely without vector insert.
- Provider body contains secrets or raw input text; logs and API response must not contain those values.
- Dimension mismatch is detected before marking document `READY`.
- Cross-tenant document/vector rows are never written.

## Required Tests and Assertion Points

New or updated unit tests:

- `EmbeddingClientTest`
  - Builds `/v1/embeddings` URL correctly for base URL variants.
  - Sends Authorization header without logging the plaintext key.
  - Parses batch embeddings and preserves `index` order.
  - Throws/returns normalized failure for non-2xx, timeout/network error, malformed body.
  - Rejects response count mismatch and dimension mismatch.

- `DocumentServiceTest`
  - Happy path transitions through embedding and returns `READY`.
  - Persists one vector row per chunk with `user_id`, `knowledge_base_id`, `document_id`, `chunk_id`, `embedding_model`, `embedding_dimension`.
  - Sets `FAILED` and bounded `error_message` for missing config, dimension mismatch, provider failure, response count mismatch.
  - Preserves KB `READY` if a prior `READY` document exists.
  - Does not mark `READY` on partial vector persistence failure.

- `DocumentAdminControllerTest`
  - `GET /api/admin/knowledge-bases/{kbId}/documents?status=READY|EMBEDDING` filters are accepted.
  - Invalid status still returns 400.
  - Upload response may return `READY` on success and `FAILED` on embedding failure.

- `KnowledgeBaseServiceTest`
  - Status update behavior remains explicit and same-user safe where applicable.

- `ModelConfigServiceTest`
  - Existing embedding model/dimension validation still passes.
  - New lookup method for enabled matching embedding config, if added, scopes by `user_id`, `embedding_model`, `embedding_dimension`, and `ENABLED`.

- Mapper/schema tests if the repo has an existing pattern. If not, migration/schema assertions can be covered by service tests plus migration review.

Regression tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

Optional final checks:

```bash
cd backend
mvn -q -DskipTests compile
git diff --check
```

## Expected Files To Modify

Likely new files:

- `backend/src/main/resources/db/migration/V6__create_document_chunk_embedding_table.sql`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingRequest.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingResponse.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingResult.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingException.java` or reuse safe service exception pattern.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEmbeddingEntity.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEmbeddingMapper.java`
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentChunkEmbeddingMapperTest.java` if mapper integration style exists or becomes necessary.

Likely updated files:

- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEntity.java` only if storing vector directly on chunk.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java` only if needed for new status handling; do not expose vectors.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseStatus.java` only if adding KB `EMBEDDING`.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/resources/application.yml` only if adding embedding batch/timeout properties.
- `.env.example` only if adding new env keys.
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- Existing tests listed above.

## Spec Updates Required

Update specs after implementation so future sessions know the contract:

- `.trellis/spec/sangui-rag-gateway.md`
  - Add implemented embedding/vector storage baseline, state flow, upstream embeddings contract, and limitations.
- `.trellis/spec/backend/database-guidelines.md`
  - Add vector table/column schema, dimension rules, indexes, tenant-safe vector persistence.
- `.trellis/spec/backend/error-handling.md`
  - Add embedding failure normalization and admin upload behavior.
- `.trellis/spec/backend/logging-guidelines.md`
  - Add safe embedding log fields and forbidden vector/chunk-content logging.
- `.trellis/spec/backend/quality-guidelines.md`
  - Add required embedding/vector tests and regression commands.
- `.trellis/spec/frontend/type-safety.md`
  - Align `DocumentStatus` union with backend (`EMBEDDING`, `READY`).

## Implementation Plan

1. Add DB migration and persistence model for chunk embeddings.
2. Add embedding client DTOs/client with OpenAI-compatible `/v1/embeddings` URL construction, timeout, safe logging, and normalized errors.
3. Add model config lookup/validation for same-user enabled matching embedding config.
4. Extend document status enum and ingestion pipeline:
   - store chunks
   - mark `PARSED`
   - mark `EMBEDDING`
   - call embedding client in batches
   - validate count/order/dimension
   - persist vectors
   - mark document `READY`
   - update KB status
5. Ensure failure paths mark document `FAILED`, bound error message, and keep KB `READY` when prior vector-ready docs exist.
6. Update admin status validation/tests for `EMBEDDING` and `READY`.
7. Update project/backend/frontend specs for the new contract.
8. Run targeted and full regression tests.

## Acceptance Criteria

- [ ] Uploading a supported text/markdown document with matching enabled embedding config returns `DocumentVO.status=READY`.
- [ ] Every chunk for a ready document has exactly one persisted vector row with matching tenant IDs and dimension.
- [ ] Missing/disabled/mismatched embedding config returns upload 200 with `DocumentVO.status=FAILED` and bounded `error_message`.
- [ ] Provider non-2xx, timeout, malformed body, count mismatch, or dimension mismatch fails the document safely and does not expose provider body.
- [ ] No vectors, full chunk content, upstream keys, app keys, Authorization headers, or provider raw bodies appear in logs or API responses.
- [ ] Existing unsupported file/content-type, tenant 403/404, model config, app/key, chat completion, request log, and global error tests still pass.
- [ ] Spec docs are updated with the implemented embedding/vector contract.

## Planning Self-Check

- Acceptance criteria are explicit: yes.
- Forbidden scope is explicit: no retrieval, no prompt augmentation, no frontend implementation, no public `/v1/embeddings` endpoint, no async/retry.
- Expected files are listed: yes.
- Required tests and commands are listed: yes.
- Specific guidelines were read, not only spec indexes: yes.
- Open questions: vector storage choice is documented with a preferred new table approach; no user confirmation needed unless the implementer wants a fixed-dimension shortcut.
- API/DB/frontend DTO alignment is called out: yes, especially `DocumentStatus` and frontend type-safety spec.
