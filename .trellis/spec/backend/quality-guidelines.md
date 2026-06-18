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
- [ ] Admin endpoints require `Authorization: Bearer <admin-jwt>` and derive tenant identity from `AdminAuthContextHolder`, not request headers.
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
- [ ] API-key rate limits validate request payloads before quota reservation, so malformed or invalid requests do not consume quota.
- [ ] Redis limiter failures are visible OpenAI-compatible failures, not silent bypasses or admin-envelope responses.
- [ ] Token reservation reconciliation and release use the same minute/day Redis windows as the preflight reservation.

API key rate-limit regression checks:

```bash
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,GatewayAuthFilterTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

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

## Embedding and Vector Storage Baseline Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=ModelConfigServiceTest" test
```

RAG retrieval and prompt augmentation baseline tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest,DocumentServiceTest,DocumentAdminControllerTest,ModelConfigServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest,ApiRequestLogServiceTest" test
mvn test
```

Tested areas:
- Embedding client URL construction for base URL variants.
- Safe logging (no vectors, chunk content, upstream keys, provider bodies).
- Response count/index/dimension validation.
- Non-2xx, timeout, and malformed response handling.
- Document happy path through EMBEDDING to READY.
- Vector persistence per chunk with tenant-safe fields.
- Missing/disabled/mismatched embedding config safe failure.
- Embedding failure safe error_message and KB status preservation.
- Admin status filter accepts EMBEDDING and READY.

## Request Log Observability Admin API Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
```

Tested areas:
- List endpoint with default pagination, status filter (success/failure/cancelled case-insensitive), error_code, start_time/end_time range.
- Invalid page/page_size/status/time format/time range returns `400 INVALID_REQUEST`.
- Missing app returns `404 NOT_FOUND`; cross-user app returns `403 FORBIDDEN` (no log query executed).
- Detail endpoint returns safe fields only (no prompt, messages, api_key, key_hash, upstream_api_key, chunk_content, embedding, provider_response_body, stack_trace).
- Hit chunk endpoint returns tenant-scoped summaries with bounded content (200 chars).
- App with no default KB returns `400 INVALID_REQUEST` for hit-chunks.
- Empty/null hit_chunk_ids returns empty summary list.
- JSONB `hit_chunk_ids` parsed to numeric array; sensitive fields absent from responses.
- Admin auth validation (missing/non-Bearer/invalid/expired JWT returns `401 UNAUTHORIZED`; controller fallback without context returns `401 UNAUTHORIZED`).

Regression tests must still pass:
```bash
mvn -q "-Dtest=AppAdminControllerTest,DocumentAdminControllerTest,RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn test
```

## Source Citation and Retrieval Evaluation Tests

Required targeted tests after changing citation/evidence/evaluation behavior:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```

Tested areas:

- Default `/v1/chat/completions` response omits `sangui_citations` and otherwise preserves OpenAI-compatible response serialization.
- Opt-in non-streaming responses include bounded `sangui_citations` only.
- `stream=true` does not emit P1 citation SSE events, but request logs still persist retrieval evidence.
- Citation IDs and order match final injected chunks and `hit_chunk_ids`.
- Request-log `retrieval_evidence` stores safe metadata only and malformed JSON fails visibly.
- Retrieval SQL keeps user and knowledge-base scope when loading citation source filenames.
- Retrieval evaluation reports safe precision/recall/MRR metadata without chunk content, prompts, embeddings, keys, storage paths, or provider raw bodies.

## Object Storage and Delete Lifecycle Tests

Required targeted tests after changing storage backend selection or delete lifecycle:

```bash
cd backend
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest,DocumentConfigTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest" test
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
```

Tested areas:

- Local storage keeps UUID-based non-guessable keys, path traversal protection, and idempotent delete.
- Object storage uses the same opaque logical key shape, calls S3 put/delete APIs, and treats `NoSuchKey` or S3 `404` from `headObject` as cleanup-complete.
- Object storage non-404 S3 failures remain visible exceptions.
- `DocumentConfig` selects only `local` or `object`, rejects unknown types, and names missing object config properties without echoing secret values.
- Document delete returns `404` for missing documents, `403` for cross-user documents, and never calls storage cleanup before ownership is verified.
- Successful document delete attempts storage cleanup before deleting embeddings, chunks, and document rows, then updates KB status.
- Knowledge-base delete rejects same-user app references with `409 KNOWLEDGE_BASE_IN_USE`.
- Delete API responses do not expose `storage_path`, object endpoint, bucket, access key, secret key, or absolute local paths.
