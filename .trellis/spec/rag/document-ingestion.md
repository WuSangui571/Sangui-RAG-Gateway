# RAG Document Ingestion

> Document ingestion must produce traceable, tenant-safe chunks and embeddings without promising high-quality parsing for every document type.

## 1. Scope / Trigger

Use this spec before changing:

- document upload, parser selection, text normalization, or chunking
- document or knowledge-base status transitions
- embedding generation or vector persistence
- chunk metadata, chunk IDs, and reprocessing behavior
- parser support for PDF, DOCX, CSV, Excel, or complex tables
- synchronous versus asynchronous ingestion behavior

This task only records the spec. It does not implement new ingestion features.

## 2. Current Hard Specification

- Chunk size and overlap must be configurable.
- Default chunk size should stay in the range of 500-800 Chinese characters or equivalent token range.
- Default chunk overlap should stay in the range of 80-120 Chinese characters.
- Every generated chunk must record `chunk_index`, `document_id`, and `knowledge_base_id`.
- Chunks must never mix content across tenants or knowledge bases.
- Reprocessing a document must avoid mixing old chunks with new chunks.
- Long documents must not be directly injected into prompts; retrieval must select chunk-level context.
- Prompt context assembly must apply similarity threshold filtering, context budget control, and deduplication where needed.
- Embedding fine-tuning is not a default solution for V0.2 beta. It is a later high-cost path that needs enough business data and evaluation sets.
- Supported document claims must be conservative. Basic PDF/DOCX support, if present, is not equivalent to high-quality structured parsing.

## 3. Signatures

Document ingestion flow:

```text
upload
  -> file storage
  -> document row
  -> parser
  -> text cleaning
  -> chunking
  -> embedding
  -> chunk/vector rows
  -> document/knowledge-base status
  -> frontend status display
```

Required document status machine:

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

Chunk metadata contract:

```text
user_id
knowledge_base_id
document_id
chunk_index
content
token_count or character-count equivalent
metadata
created_at
updated_at
```

Embedding vector persistence uses `PgVectorFormatter` (`com.sangui.raggateway.common.util.PgVectorFormatter`) as the single `float[]` -> pgvector `VECTOR` literal boundary before `DocumentChunkEmbeddingMapper.insertEmbedding(...)` applies `#{embedding}::vector`. The formatter emits `[c0,c1,...,cn]` with fixed 8 decimal places using `Locale.ROOT` and fails visibly for null, empty, `NaN`, or infinite vectors.

## 4. Contracts

| Contract | Required behavior |
|----------|-------------------|
| Parser selection | Deterministic and based on supported content type or filename rules. |
| Empty or unsupported files | Fail clearly; do not create ready chunks from empty content. |
| Chunk sizing | Uses configured size and overlap; avoids uncontrolled whole-document chunks. |
| Chunk identity | `document_id` plus `chunk_index` identifies order within a document. |
| Tenant safety | Chunk and embedding rows carry `user_id` and `knowledge_base_id`. |
| Reprocessing safety | Old chunks/vectors are deleted or version-isolated before new chunks become active. |
| Embedding failure | Document moves to `FAILED` or an explicit retryable state with bounded `error_message`. |
| Request blocking | Upload should not block indefinitely waiting for large embedding jobs. |

## 5. Validation & Error Matrix

| Scenario | Expected behavior | Assertion point |
|----------|-------------------|-----------------|
| Valid txt/md document | Parsed, chunked, embedded, status reaches `READY` | Document service test |
| Empty document | Fails clearly; no ready chunks | Parser/service test |
| Unsupported extension | Admin API returns safe validation error | Controller test |
| Embedding provider failure | Document status becomes `FAILED`; no fake ready state | Document service test |
| Reprocess same document | Old and new chunks cannot be mixed in retrieval | Service/mapper test |
| Cross-user document ID access | Returns 403/404 according to admin contract; no chunks exposed | Controller/service test |
| Large document | Processing is bounded or moved to an async-capable path | Service test or design spec |

## 6. Good/Base/Bad Cases

| Case | Expected result |
|------|-----------------|
| Good | Supported text-like document becomes ordered chunks with safe metadata, matching embeddings, and explicit status transitions. |
| Base | Parser or embedding fails: document is visible as `FAILED` with bounded error details, and existing ready KB content is not corrupted. |
| Bad | Upload accepts complex tables as high-accuracy QA input, chunks cross knowledge bases, old chunks remain active after reprocessing, or the service marks a failed embedding document as `READY`. |

## 7. Wrong vs Correct

### Wrong

```text
Convert any complex PDF, Excel, or database-like data to plain text and advertise accurate table QA.
```

This overstates parser quality and pushes the project toward a heavier data-agent platform.

### Correct

```text
State that V0.2 beta focuses on text-like RAG flow. Treat complex PDF, Excel, table QA, and Text-to-SQL as future specialized capabilities with separate contracts.
```

## 8. Complex Documents and Tables

Current limitation statements:

- V0.2 beta primarily supports the main RAG flow for markdown, txt, and text-like documents.
- PDF and DOCX parsing must not be advertised as reliable structured extraction unless specifically implemented and tested.
- Complex PDFs with cross-page tables, image-text layouts, headers, footers, footnotes, or two-column layout may produce extraction errors.
- Excel, complex tables, and database-like data should not be converted to plain text and then promised as high-accuracy QA.
- Complex table QA, Text-to-SQL, and Table Agent behavior are not MVP goals.
- Supporting complex tables must not break the lightweight gateway positioning.

## 9. Large Ingestion and Async Processing

Current hard rules:

- Document processing must have explicit states.
- Failures must persist bounded `error_message`.
- Repeated processing must avoid dirty chunks.
- Upload requests must not parse, chunk, or embed before returning. They validate ownership/input, save the original file, create the `rag_document` row with `UPLOADED`, create one durable processing task with `PENDING`, set the knowledge base to `PROCESSING`, and return observable state.
- `rag_document_processing_task` is the durable retry/recovery source of truth. Task statuses are `PENDING`, `PROCESSING`, `SUCCEEDED`, `RETRYABLE`, `FAILED`, and `CANCELED`.
- A lightweight in-process scheduler/worker may poll the database; Kafka, RabbitMQ, Redis Streams, or another MQ are not required for the baseline.
- The worker must claim tasks with a conditional database update, read the original file through `FileStorageService.read(...)`, and persist document transitions before long-running parser or embedding work.
- `PROCESSING` tasks must carry `locked_by` and `locked_at`; stale `PROCESSING` tasks must recover to `RETRYABLE` while attempts remain or `FAILED` when exhausted.
- `attempt_count` counts processing attempts started by worker claim. Explicit retry of a terminal `FAILED` task resets the task to `PENDING` and clears prior attempt/error scheduling state.
- Retry/reprocessing must clear old chunks and embeddings before producing new active rows.
- Task and document errors must be bounded and admin-safe. Do not persist provider raw bodies, chunk content, stack traces, storage absolute paths, credentials, vectors, prompts, or uploaded file content.

### Retry/Reprocessing Matrix

| Trigger | Cleanup boundary | Behavior |
|---|---|---|
| Automatic worker claim (PENDING or RETRYABLE → PROCESSING) | `DocumentService.processDocument(...)` calls `clearChunksAndEmbeddings(documentId)` at attempt start, before parseDocumentContent writes new chunks | Removes any stale chunks/embeddings from prior failed attempts. On the first attempt, the same boundary performs empty deletes so there is one processing path. Cleanup failure is visible and propagated to the worker. |
| Explicit admin retry (FAILED task) | `DocumentService.retryDocument(...)` calls `clearChunksAndEmbeddings(documentId)`, resets document to `UPLOADED`, resets task to `PENDING`, sets KB to `PROCESSING` | Same as before this task; unchanged. |
| Explicit admin retry (PENDING / RETRYABLE task) | No cleanup; no state mutation | Returns current document idempotently. |
| Explicit admin retry (PROCESSING task) | Rejected `409 DOCUMENT_PROCESSING` | No cleanup or mutation. |
| Explicit admin retry (SUCCEEDED + document READY) | Rejected `400 INVALID_REQUEST` | No cleanup or reprocessing. Replacement/reprocess of successful documents is a future contract. |

`clearChunksAndEmbeddings(documentId)` deletes embeddings then chunks for the given document. This is not wrapped in a catch block in either path; cleanup failures are surfaced as exceptions and cause the attempt (or explicit retry) to fail visibly.

Future roadmap only:

- batch embedding
- concurrent workers
- document processing progress
- external queue-backed ingestion
- replacement/reprocess semantics for already `READY` documents

## 10. Future Enhancement Roadmap

The following are valid later enhancements, not V0.2 beta requirements:

- Markdown heading-aware splitting
- paragraph splitting
- recursive character splitting
- code block protection
- table preservation
- sentence window retrieval
- PDF structured parsing
- Markdown or HTML intermediate conversion
- Excel or CSV specialized parsing
- Text-to-SQL or Table QA as separate extension capabilities
- embedding fine-tuning after enough data and evaluation sets exist

## 11. File Lifecycle and Explicit Deletion

Original uploaded files are durable ingestion artifacts. Parse or embedding failure keeps the stored original and marks the document `FAILED`; cleanup happens only through explicit document or knowledge-base deletion.

Storage abstraction:

```java
StoredFile save(String ownerType, Long ownerId, String originalFilename, InputStream inputStream);
InputStream read(String storageKey);
void delete(String storageKey);
```

Upload rollback flow:

```text
admin upload
  -> validate owner, filename, content type, and file size
  -> FileStorageService.save(...)
  -> short database transaction:
       rag_document insert with status=UPLOADED
       rag_document_processing_task insert with status=PENDING
       knowledge base status update to PROCESSING
  -> return DocumentVO without storage_path
```

If any operation in the short database transaction fails after `save(...)`
returns a storage key, `DocumentService.uploadAndEnqueue(...)` must call
`FileStorageService.delete(storageKey)` exactly once and then propagate the
original upload failure. The database transaction must roll back so the upload
does not leave a usable `UPLOADED` document without a durable processing task.
Cleanup delete remains idempotent for missing local files or S3 objects; real
storage/backend cleanup failures are logged with safe identifiers and must not
turn the upload into success.

Upload rollback validation matrix:

| Scenario | Expected behavior | Required assertion |
|---|---|---|
| Unsupported filename/content type, empty file, oversized file | Fail before `FileStorageService.save(...)` | `DocumentServiceTest` verifies no storage interaction. |
| `FileStorageService.save(...)` fails | Upload fails visibly; no cleanup delete because no storage key exists | Service/controller tests. |
| `rag_document` insert fails after save | Delete the saved storage key exactly once; return/throw the original failure boundary as `DATABASE_ERROR` with bounded message | `DocumentServiceTest`. |
| Processing task creation fails after document insert | Roll back the upload metadata transaction, delete the saved storage key exactly once, do not update KB status, and return/throw `DATABASE_ERROR` with bounded original task error summary | `DocumentServiceTest` asserts rollback and cleanup. |
| KB status update fails after task creation | Roll back upload metadata/task work, delete the saved storage key exactly once, and return/throw `DATABASE_ERROR` with bounded original KB status error summary | `DocumentServiceTest`. |
| Cleanup delete also fails | Log safe `storageKey`, KB/user IDs, and cleanup exception class; preserve the original `DATABASE_ERROR` instead of replacing it with cleanup failure | `DocumentServiceTest`. |
| Parser/embedding worker fails after a queued upload | Keep the stored original; worker failure is not upload rollback | Worker/process-document tests. |

Deletion flow:

```text
document delete
  -> admin auth context
  -> document ownership check
  -> reject if processing task is PROCESSING
  -> cancel PENDING/RETRYABLE task when present
  -> storage delete (idempotent missing file/object)
  -> delete embeddings
  -> delete chunks
  -> delete document row
  -> recalculate knowledge-base status
```

```text
knowledge-base delete
  -> admin auth context
  -> knowledge-base ownership check
  -> reject if same-user app references KB
  -> reject if any owned document has a PROCESSING task
  -> cancel PENDING/RETRYABLE tasks under the KB
  -> for each document: storage delete, embeddings delete, chunks delete, document delete
  -> delete knowledge-base row
```

Status after document delete:

| Remaining documents | KB status |
|---|---|
| none | `EMPTY` |
| at least one `READY` document | `READY` |
| only failed/non-ready documents | `FAILED` |

Good/base/bad cases:

| Case | Expected result |
|---|---|
| Good | Deleting an owned document removes the stored original, vectors, chunks, and document row, and updates KB status from remaining documents. |
| Base | Missing storage object/file is treated as cleanup-complete so retries can proceed after partial external cleanup. |
| Bad | Cross-user delete attempts storage cleanup, storage cleanup failure reports success, a processing worker races with deletion, or parse/embedding failure silently deletes the original before explicit deletion. |
