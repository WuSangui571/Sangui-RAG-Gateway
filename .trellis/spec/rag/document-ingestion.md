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
- Large-document handling should leave clear async extension points.

Future roadmap only:

- async task queue
- batch embedding
- concurrent workers
- retry with backoff
- document processing progress
- ingestion task cancellation

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
void delete(String storageKey);
```

Deletion flow:

```text
document delete
  -> admin auth context
  -> document ownership check
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
| Bad | Cross-user delete attempts storage cleanup, storage cleanup failure reports success, or parse/embedding failure silently deletes the original before explicit deletion. |
