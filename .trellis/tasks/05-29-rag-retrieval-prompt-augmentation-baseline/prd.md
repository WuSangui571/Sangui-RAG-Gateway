# RAG Retrieval and Prompt Augmentation Baseline

## Task Classification

Complex Task.

This task crosses public gateway API behavior, app/knowledge-base association, embedding client usage, pgvector SQL, RAG prompt construction, upstream forwarding, request logging, tests, and project/backend specs. Implementation must not start until this PRD and Trellis context are prepared.

## Goal

Turn the existing document embedding/vector storage baseline into a usable MVP RAG chat path:

```text
user question -> query embedding -> tenant-scoped pgvector retrieval -> chunk filtering/truncation -> prompt augmentation -> upstream chat -> grounded answer
```

The external caller should still use the existing OpenAI-compatible `POST /v1/chat/completions` endpoint with the same app API key. The gateway should internally retrieve knowledge-base context for the authenticated app and forward an augmented message list to the configured upstream chat model.

## Current Project State Summary

- `GET /v1/models` is implemented for authenticated apps.
- `POST /v1/chat/completions` supports non-streaming and streaming pass-through to an OpenAI-compatible upstream provider.
- App API keys authenticate `/v1/*` through `GatewayAuthFilter` and populate `GatewayRequestContext`.
- Request logs are persisted safely for authenticated chat requests; `question_summary` and `hit_chunk_ids` are currently baseline placeholders.
- Knowledge-base and document upload admin APIs exist for txt/md/markdown.
- Document ingestion now performs synchronous parsing, chunking, embedding, vector persistence, and marks documents/KBs `READY`.
- Vectors are stored in `rag_document_chunk_embedding` with duplicated `user_id`, `knowledge_base_id`, `document_id`, and `chunk_id`.
- App has a default chat model config but does not yet bind a default knowledge base.
- Retrieval, prompt augmentation, citations, admin retrieval config UI, retries, async jobs, PDF/DOCX, and public `/v1/embeddings` remain out of scope.
- Local working tree currently has untracked manual upload artifacts under `backend/data/uploads/knowledge/3/`, `4/`, and `5/`; implementation must ignore them unless the user explicitly asks to clean them.

## In Scope

1. Add an app-to-knowledge-base binding for the MVP default RAG source.
2. Add retrieval configuration defaults used by the gateway chat path.
3. Add a retrieval service that creates a query embedding and performs SQL-level tenant/knowledge-base-scoped vector retrieval.
4. Filter, deduplicate, and truncate retrieved chunks by topK, similarity threshold, max context chunks, max single chunk size, and max context size.
5. Add a prompt builder that preserves original messages and injects an internal RAG context message.
6. Upgrade `POST /v1/chat/completions` from pure pass-through to RAG augmentation when the authenticated app has a ready default KB.
7. Preserve streaming and non-streaming OpenAI-compatible response behavior.
8. Persist safe request log fields, especially bounded `question_summary` and `hit_chunk_ids`.
9. Update project/backend/frontend specs for the new API, DB, RAG, logging, and future frontend type contracts.
10. Add focused tests for query embedding, tenant-safe vector SQL, prompt construction, no-hit behavior, KB readiness, and chat regressions.

## Out of Scope

- Frontend page implementation.
- Multiple knowledge bases per app.
- Source citations in the public answer.
- Reranking, hybrid search, ANN indexes, HNSW/IVFFlat tuning.
- Public `/v1/embeddings`.
- PDF/DOCX ingestion.
- Async document processing, queues, retry/backoff.
- Admin request-log UI.
- Changing app API key authentication semantics.
- Exposing full document content, augmented prompts, embedding vectors, or upstream provider bodies in logs or API responses.
- Provider-specific retrieval logic outside the generic OpenAI-compatible contracts.

## Proposed Data Contract

### Database

Add a migration after `V6__create_document_chunk_embedding_table.sql`.

Minimum MVP option:

```text
rag_app.default_knowledge_base_id BIGINT NULL
```

Expected index:

```text
idx_rag_app_default_knowledge_base on rag_app(default_knowledge_base_id)
```

Ownership rule:

```text
rag_app.user_id must equal rag_knowledge_base.user_id when assigning or resolving default_knowledge_base_id.
```

If retrieval configuration is persisted in this task, prefer columns with conservative defaults:

```text
rag_app.retrieval_top_k INTEGER DEFAULT 5
rag_app.retrieval_similarity_threshold NUMERIC(4,3) DEFAULT 0.700
rag_app.retrieval_max_context_chunks INTEGER DEFAULT 5
rag_app.retrieval_max_context_chars INTEGER DEFAULT 12000
rag_app.retrieval_max_single_chunk_chars INTEGER DEFAULT 3000
rag_app.no_hit_policy VARCHAR(32) DEFAULT 'STRICT_RAG'
```

If persisted config is judged too large for this baseline, use application properties first and document that per-app settings are deferred. Do not introduce both a broad config table and frontend UI in this task.

### Admin API

An app/KB binding endpoint is required so MVP users can attach a KB to an app.

Recommended endpoint:

```http
PUT /api/admin/apps/{appId}/knowledge-base
X-Admin-User-Id: <user id>
Content-Type: application/json
```

Request:

```json
{
  "knowledge_base_id": 123
}
```

Alternative accepted if it better matches existing app model-config binding patterns:

```http
POST /api/admin/apps/{appId}/default-knowledge-base
```

Response should use existing `ApiResponse<AppVO>` or a narrow binding VO, following current admin API style. `AppVO` should expose `default_knowledge_base_id` only if that is consistent with existing app/model config exposure.

Validation:

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing/non-numeric/non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing admin header handling. |
| Missing/null/non-positive `knowledge_base_id` | 400 | `INVALID_REQUEST` | No app mutation. |
| App missing | 404 | `NOT_FOUND` | Safe admin envelope. |
| App belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`. |
| KB missing | 404 | `NOT_FOUND` | Safe admin envelope. |
| KB belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`. |
| KB exists but not `READY` | 400 or 409 | `KNOWLEDGE_BASE_NOT_READY` | Binding should fail for MVP unless specs explicitly allow prebinding. |
| App and READY KB same user | 200 | `OK` | Persist binding and update `updated_at`. |

### Public Gateway API

Endpoint remains:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Supported request fields remain:

| Field | Required | Behavior |
|---|---:|---|
| `model` | no | Accepted for client compatibility; upstream uses `App.default_model_config.chat_model`. |
| `messages` | yes | Preserve all original messages; use the last user message for retrieval query. |
| `temperature` | no | Forwarded. |
| `max_tokens` | no | Forwarded. |
| `top_p` | no | Forwarded. |
| `stream` | no | Existing non-streaming/streaming behavior preserved. |

The gateway must internally augment the upstream request only. The public response remains upstream OpenAI-compatible JSON or SSE and is not wrapped.

### Query Extraction

- Use the last message with role `user` as the retrieval query.
- If no user message exists after request validation, return `400 invalid_request`.
- Retrieval query may be truncated for `question_summary`, but the embedding input should use the validated user content unless a configured maximum query length is introduced.

### Retrieval Service Contract

Recommended service-level flow:

```text
resolve app + model config + default KB
validate KB READY
resolve embedding model config using KB.embedding_model + KB.embedding_dimension
call EmbeddingClient with last user message
run pgvector SQL scoped by user_id + knowledge_base_id
convert distance to similarity
filter by similarity_threshold
deduplicate by chunk_id/content as needed
limit topK and max context chunks
truncate max single chunk and total context chars/tokens
return RetrievalResult with chunks, hit_chunk_ids, timing, no-hit flag
```

Vector SQL must enforce tenant and KB boundaries before ordering:

```sql
SELECT ...
FROM rag_document_chunk_embedding e
JOIN rag_document_chunk c ON c.id = e.chunk_id
WHERE e.user_id = ?
  AND e.knowledge_base_id = ?
ORDER BY e.embedding <=> ?
LIMIT ?
```

Do not retrieve globally and filter in Java.

### Prompt Augmentation Contract

Original messages must be preserved in order.

Recommended injected message:

```json
{
  "role": "system",
  "content": "<internal RAG instruction and retrieved context>"
}
```

Insertion point:

- Preserve original system prompt.
- Add the RAG context system message after existing system messages and before user/assistant conversation messages.
- Do not mutate user/assistant message content.

Prompt content requirements:

- Clearly label the injected section as private knowledge-base context.
- Include chunk IDs/source labels for internal grounding, but do not expose full document metadata beyond safe identifiers/filenames.
- For hits, instruct upstream to answer based on the provided context when relevant.
- For no hits under MVP `STRICT_RAG`, still call upstream but inject an internal statement that no valid KB context was retrieved and the assistant should say the knowledge base does not contain enough information when the question requires private KB facts.

### No-Hit Policy

MVP default is `STRICT_RAG`.

Behavior:

| Scenario | Public behavior |
|---|---|
| Retrieval hits above threshold | Call upstream with context. |
| No hits above threshold | Still call upstream with a no-hit RAG context message. |
| App has no default KB | Return `409 knowledge_base_not_ready` for this RAG baseline unless user explicitly decides pass-through should remain supported. |
| Default KB is not `READY` | Return `409 knowledge_base_not_ready`. |

If maintaining pass-through for apps without a KB is desired, this must be explicitly documented and tested. Default recommendation for MVP is to surface `409 knowledge_base_not_ready` because the endpoint is becoming the RAG gateway path.

## Validation / Error Matrix

| Scenario | HTTP / response | Error code | Required assertions |
|---|---:|---|---|
| Missing/invalid API key | 401 OpenAI error | `invalid_api_key` | Existing filter behavior unchanged. |
| Missing/disabled chat model config | 409 OpenAI error | `model_config_not_ready` | Existing behavior unchanged. |
| App has no default KB | 409 OpenAI error | `knowledge_base_not_ready` | No embedding call, no upstream call. |
| Default KB missing/cross-user | 409 OpenAI error | `knowledge_base_not_ready` | Do not reveal ownership details to public caller. |
| Default KB not `READY` | 409 OpenAI error | `knowledge_base_not_ready` | No embedding/retrieval/upstream call. |
| Missing/disabled/mismatched embedding config | 409 or 502 OpenAI error | `embedding_failed` or `model_config_not_ready` | Safe message; no upstream chat call. |
| Query embedding provider non-2xx/network error | 502 OpenAI error | `embedding_failed` | Safe message; no provider body. |
| Query embedding timeout | 504 OpenAI error | `embedding_failed` or `upstream_timeout` | Choose and document one stable code. |
| Malformed chat request | 400 OpenAI error | `invalid_request` | Existing validation remains. |
| No user message | 400 OpenAI error | `invalid_request` | Retrieval query cannot be extracted. |
| Retrieval SQL returns no rows | 200 upstream response | none | No-hit `STRICT_RAG` message injected and upstream still called. |
| All chunks below threshold | 200 upstream response | none | Same as no-hit. |
| Upstream non-2xx/network | 502 OpenAI error | `upstream_error` | Existing normalization. |
| Upstream timeout | 504 OpenAI error | `upstream_timeout` | Existing normalization. |
| Streaming setup failure before SSE starts | JSON OpenAI error | matching code | Existing pre-stream boundary preserved. |
| Upstream stream failure after SSE starts | SSE error event | `upstream_error` | Existing behavior preserved. |

## Good / Base / Bad Cases

Good cases:

- Active app API key, enabled app, enabled chat model config, READY default KB, matching enabled embedding config, retrieval hits, non-streaming upstream success.
- Same setup with `stream=true`, upstream SSE chunks forwarded after prompt augmentation.
- Admin binds same-user READY KB to same-user app.
- Request log stores safe `question_summary` and `hit_chunk_ids` for retrieval hits.

Base cases:

- Request has multiple system/user/assistant messages; original order is preserved except internal RAG system message insertion.
- Retrieval returns no chunks above threshold; gateway still calls upstream with no-hit context.
- Existing `/v1/models` behavior remains unaffected.
- Existing document ingestion and embedding storage tests remain unaffected.

Bad cases:

- App has no default KB.
- KB is `EMPTY`, `PROCESSING`, or `FAILED`.
- KB belongs to another user or was deleted/missing.
- Embedding config for KB model/dimension is missing, disabled, duplicated, undecryptable, or provider fails.
- Vector SQL accidentally omits `user_id` or `knowledge_base_id`.
- Prompt builder logs or persists full augmented prompt.
- Request log stores private document chunks or full user messages.
- Admin tries to bind cross-user KB to app.

## Required Tests And Assertion Points

### Retrieval

- `RetrievalServiceTest`
  - Calls `EmbeddingClient` with the last user message content.
  - Resolves embedding config by same `user_id`, KB `embedding_model`, and KB `embedding_dimension`.
  - Requires KB `READY`.
  - Applies topK, threshold, max context chunks, max single chunk chars, and total context chars.
  - No-hit result is explicit and not an exception.
  - Embedding failures are normalized safely.

- Mapper SQL test or focused unit around SQL provider
  - SQL includes `user_id = ?` and `knowledge_base_id = ?` before `ORDER BY embedding <=>`.
  - Cross-tenant rows are not returned.
  - Low similarity rows are filtered or excluded as designed.

### Prompt

- `RagPromptBuilderTest`
  - Preserves original messages.
  - Preserves original system prompt.
  - Inserts internal RAG context message in the documented position.
  - Builds no-hit `STRICT_RAG` context.
  - Does not mutate user content.
  - Bounds/truncates context.

### Admin App/KB Binding

- `AppAdminControllerTest` and/or `AppServiceTest`
  - Same-user READY KB can bind to app.
  - Missing app/KB returns 404.
  - Cross-user app/KB returns 403.
  - Non-ready KB fails with stable error code.
  - Binding updates only safe app fields and `updated_at`.

### Gateway Chat

- `ChatCompletionGatewayServiceTest`
  - Non-streaming path calls retrieval before upstream.
  - Upstream request uses augmented messages, not original-only pass-through.
  - Existing chat model resolution remains app default model config.
  - No-hit still calls upstream with no-hit context.
  - Missing KB or non-ready KB returns OpenAI-compatible `knowledge_base_not_ready`.
  - Embedding/retrieval failure does not call upstream chat.

- `OpenAiChatCompletionsControllerTest`
  - Response shape remains OpenAI-compatible.
  - Request log records safe `question_summary` and `hit_chunk_ids`.
  - Validation failures and model config failures retain existing behavior.

- Streaming regression tests
  - `stream=true` uses augmented upstream request.
  - Pre-stream KB/embedding validation failures return JSON errors.
  - Existing SSE forwarding and cancellation tests still pass.

### Regression

Run targeted suites:

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

## Expected Spec Updates

- `.trellis/spec/sangui-rag-gateway.md`
  - Mark retrieval/prompt augmentation baseline implemented.
  - Document app default KB binding, chat flow, no-hit policy, request log hit IDs.

- `.trellis/spec/backend/database-guidelines.md`
  - Document new app/KB binding schema and retrieval SQL contract.

- `.trellis/spec/backend/error-handling.md`
  - Document `knowledge_base_not_ready` and query embedding/retrieval failure behavior in chat flow.

- `.trellis/spec/backend/logging-guidelines.md`
  - Document retrieval/prompt log fields and forbidden sensitive data.

- `.trellis/spec/backend/quality-guidelines.md`
  - Add retrieval and prompt baseline test commands/assertions.

- `.trellis/spec/frontend/type-safety.md`
  - Update future `AppVO`/binding DTO type notes if app default KB field is exposed.

## Implementation Plan For DeepSeek

1. Search existing app/model-config binding code and mirror its admin API/service style for default KB binding.
2. Add DB migration and entity/VO fields for app default KB binding and optionally retrieval config defaults.
3. Add retrieval module classes:
   - query/config/result value objects
   - retrieval mapper SQL joining chunk embeddings to chunks
   - service orchestration using `EmbeddingClient`
4. Add `rag.prompt` builder for augmented messages.
5. Integrate retrieval and prompt builder into chat gateway service for non-streaming and streaming paths before upstream request construction.
6. Update request log recording to include bounded question summary and hit chunk IDs.
7. Add tests listed above.
8. Update specs listed above.
9. Run required targeted tests and full `mvn test`.

## Planning Self-Check

- Acceptance criteria: defined below.
- Forbidden modification scope: business implementation is forbidden for Codex in this planning session; DeepSeek must keep implementation within listed scope.
- Expected modified files: listed in the handoff/research output.
- Required tests: listed above.
- Concrete guidelines read: project spec, backend guidelines, frontend guidelines, cross-layer guide, code reuse guide.
- Open requirement issue: whether app-without-KB should fail with `409 knowledge_base_not_ready` or remain pass-through. This PRD recommends `409` for MVP RAG closure; confirm before implementation if that behavior is controversial.
- API/DB/frontend contract alignment: DB and public/admin API contracts are specified above; frontend implementation is out of scope, but future type notes must be updated if `AppVO` changes.

## Acceptance Criteria

- [ ] A same-user READY knowledge base can be bound as an app's default KB through an admin API.
- [ ] `/v1/chat/completions` uses the authenticated app's default KB to generate query embedding and retrieve chunks.
- [ ] Retrieval SQL scopes by `user_id` and `knowledge_base_id` before vector ordering.
- [ ] Retrieved chunks respect topK, threshold, context count, and context size limits.
- [ ] Original chat messages are preserved and an internal RAG context message is injected.
- [ ] No-hit `STRICT_RAG` behavior still calls upstream with explicit no-hit context.
- [ ] Missing/unready KB fails safely with OpenAI-compatible `knowledge_base_not_ready`.
- [ ] Embedding/retrieval failures are normalized and do not leak provider bodies, vectors, prompts, document content, or secrets.
- [ ] Request logs contain safe `question_summary` and `hit_chunk_ids`, not full messages or prompts.
- [ ] Streaming and non-streaming chat regression behavior remains compatible.
- [ ] Backend/project/frontend specs are updated to match the implemented contract.
- [ ] Required targeted tests and full backend test suite pass.
