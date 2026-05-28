# Backend Quality Guidelines

> Quality is judged by API compatibility, tenant isolation, RAG correctness, security, streaming behavior, and operational clarity.

## Testing Requirements

MVP should include focused tests for:

```text
API key authentication
document chunking
knowledge retrieval
prompt construction
OpenAI-compatible request parsing
non-streaming upstream forwarding
streaming forwarding
multi-tenant isolation
```

High-priority unit tests:

```text
RagPromptBuilder
TextChunker
ApiKeyHasher
RetrievalService
OpenAiResponseAdapter
DocumentParser implementations
```

High-priority integration test:

```text
upload document -> parse/chunk/embed -> call chat completions -> return enhanced answer
```

## Review Checklist

Before completing backend work, verify:

- [ ] Public gateway responses remain OpenAI-compatible for the supported subset.
- [ ] API keys and upstream keys are never stored or logged in plaintext.
- [ ] Upstream API keys are encrypted at rest with AES-256-GCM using `RAG_GATEWAY_SECRET_KEY`.
- [ ] Admin API responses only return `api_key_masked`, never `api_key_encrypted` or plaintext upstream keys.
- [ ] Admin endpoints use `X-Admin-User-Id` header for tenant isolation (temporary, until real admin auth exists).
- [ ] Admin model config CRUD endpoints enforce same-user ownership with 404/403 distinction.
- [ ] App-model config binding validates same-user ownership of both app and model config.
- [ ] Disabled model configs are excluded from `/v1/models` resolution.
- [ ] Queries touching tenant data include user/app/knowledge-base boundaries.
- [ ] Vector retrieval is scoped in SQL, not filtered after retrieval in Java.
- [ ] Document status transitions are explicit and failure states are persisted.
- [ ] Upstream HTTP calls have timeouts and normalized error handling.
- [ ] Streaming requests cancel upstream calls when clients disconnect (tested via `SseEmitter` send `IOException` handling in `OpenAiCompatibleUpstreamClient`).
- [ ] Request logs avoid full prompts and document content.
- [ ] New database fields have migration notes and indexes where needed.
- [ ] Tests cover both success and relevant failure paths.

## RAG Pipeline Quality

For ingestion changes, verify:

- Parser selection is deterministic.
- Unsupported file types fail clearly.
- Chunk size and overlap are configurable.
- Embedding dimension is validated against the knowledge base.
- Large documents can be handled asynchronously when needed.

For retrieval changes, verify:

- `top_k`, similarity threshold, max context tokens, and max single chunk tokens are respected.
- Duplicate chunks or repeated source content are controlled.
- Retrieval never crosses tenant or knowledge-base boundaries.

For prompt changes, verify:

- Original user messages are preserved.
- Original user system prompt is preserved.
- RAG context is clearly separated from user input.
- No-hit behavior follows the configured policy, defaulting to `STRICT_RAG`.

## API Compatibility

MVP supports only:

```text
GET /v1/models
POST /v1/chat/completions
```

Do not accidentally imply support for unsupported OpenAI APIs in docs or responses.

Unsupported MVP fields/features:

```text
/v1/responses
/v1/embeddings
/v1/images
tools
function_call
vision
audio
response_format
parallel_tool_calls
```

If unsupported fields are received, either ignore them only when safe and documented, or return a compatible error.

## Performance Rules

- Do not hold database transactions open across upstream HTTP calls.
- Use bounded context size before calling upstream chat models.
- Avoid loading full documents into gateway request logs.
- Batch embedding calls where provider and limits allow.
- Add pagination to admin list APIs.
- Use indexes for common admin and gateway lookup paths.

## Forbidden Patterns

- Plaintext API keys in database, logs, responses, or exceptions.
- Controller methods containing RAG orchestration details.
- Global vector search without tenant and knowledge-base filters.
- Hard-coded provider-specific logic inside generic gateway code.
- Prompt construction mixed into retrieval SQL or upstream client code.
- Silent fallback to pass-through when the app is configured for strict RAG.
- Adding broad platform features that do not serve the lightweight RAG gateway goal.

## Knowledge Base and Document Ingestion Baseline Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
```

Tested areas:
- Knowledge base create/list/detail with tenant isolation and status validation.
- Document upload (txt/md/markdown) with sync processing (UPLOADED->PARSING->PARSED/FAILED).
- Parser selection by filename extension.
- UTF-8 text parsing and text normalization.
- Deterministic character-based chunking with overlap.
- Local file storage with path traversal prevention and UUID-based keys.
- Document VO excludes `storage_path`.
- Controller 403/404/400 error matrix for admin endpoints.
- Empty file, unsupported extension, and parse failure handling.

Regression tests must still pass:
- All existing admin tests (app, API key, model config).
- All existing gateway tests (chat completions, upstream forwarding).
- All existing auth and error handler tests.
