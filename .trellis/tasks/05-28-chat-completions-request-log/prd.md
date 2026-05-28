# Chat Completions Request Log Persistence Baseline

## Classification

Complex Task.

Reason: this task touches database migration, persistence entity/mapper, service boundaries, gateway request flow, OpenAI-compatible error handling, observability safety rules, and tests. It is intentionally scoped as a backend-only persistence baseline and must not expand into admin log query APIs or frontend UI.

## Goal

Persist one safe request-log summary row for each authenticated non-streaming `POST /v1/chat/completions` request, covering success and expected gateway failures after the gateway request context is available.

The persisted log should extend the already implemented structured application log contract. It should capture operational fields that are safe to store and useful for later admin request-log UI, metrics, and debugging.

## Product Fit

This implements the project-level `ApiRequestLog` core domain model and the roadmap item `request logs`.

It serves the lightweight OpenAI-compatible RAG gateway by improving observability without storing full prompts, private documents, app API keys, upstream API keys, provider error bodies, or full user messages.

## Scope

In scope:

- Add a Flyway migration for a request-log table, preferably `rag_request_log` to match current database guidelines.
- Add backend persistence model and MyBatis-Plus mapper under the existing package conventions.
- Add a request-log service in the `log` module with success/failure-oriented methods or equivalent command-style input.
- Integrate persistence into non-streaming `POST /v1/chat/completions` completion handling.
- Persist safe fields from the current gateway context and response/error boundary.
- Cover success, validation failure, upstream 502, upstream 504, parse failure, config failure, and safety assertions in tests where practical.
- Update executable specs after implementation.

Out of scope:

- No frontend request-log UI.
- No admin request-log query API.
- No streaming request-log persistence.
- No RAG retrieval, prompt augmentation, embeddings, token estimation, rate limit, quota, Redis, MQ, or async logging infrastructure.
- No change to public `/v1/chat/completions` request or response shape.
- No provider-specific behavior.
- No storage of full request body, full messages, full prompts, private document content, app API key plaintext/hash, upstream key plaintext/encrypted value, Authorization header, or provider raw error body.

## API / Command / Payload Contract

No public HTTP API contract changes are required.

Existing endpoint remains:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

The internal persistence service should expose a narrow application command rather than accepting controller DTOs directly. Suggested command fields:

| Field | Required | Source | Safety |
|---|---:|---|---|
| `requestId` | yes | `GatewayRequestContext.requestId` | Safe UUID/string |
| `userId` | yes when context exists | `GatewayRequestContext.userId` | Safe ID |
| `appId` | yes when context exists | `GatewayRequestContext.appId` | Safe ID |
| `apiKeyId` | yes when context exists | `GatewayRequestContext.apiKeyId` | Safe ID |
| `model` | no | resolved `ModelConfigEntity.chatModel` when available, otherwise request `model` only if safe/short | Safe bounded string |
| `providerName` | no | resolved `ModelConfigEntity.providerName` when available | Safe bounded string |
| `status` | yes | `success` or `failure` | Safe enum/string |
| `errorCode` | no | gateway error code such as `invalid_request`, `upstream_error`, `upstream_timeout`, `model_config_not_ready` | Safe string |
| `latencyMs` | yes | controller total elapsed time | Safe numeric |
| `upstreamLatencyMs` | no | upstream client result/timing if available | Safe numeric |
| `promptTokens` | no | upstream response usage | Safe numeric |
| `completionTokens` | no | upstream response usage | Safe numeric |
| `totalTokens` | no | upstream response usage | Safe numeric |
| `messagesCount` | no | request message count | Safe numeric |
| `questionSummary` | no | bounded summary/prefix of final user message if implemented | Must be bounded and sanitized |
| `hitChunkIds` | no | later RAG retrieval IDs | Should default null/empty in this baseline |
| `createdAt` | yes | DB/service timestamp | Safe timestamp |

Suggested DB columns:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `request_id` | `VARCHAR(64)` | yes | Unique or indexed request correlation ID |
| `user_id` | `BIGINT` | yes | Tenant owner boundary |
| `app_id` | `BIGINT` | yes | App boundary |
| `api_key_id` | `BIGINT` | yes | Safe key metadata ID only |
| `model` | `VARCHAR(255)` | no | Resolved model where available |
| `provider_name` | `VARCHAR(128)` | no | Resolved provider where available |
| `status` | `VARCHAR(32)` | yes | `success` or `failure` |
| `error_code` | `VARCHAR(64)` | no | Stable gateway error code |
| `latency_ms` | `BIGINT` | no | Total request latency |
| `upstream_latency_ms` | `BIGINT` | no | Upstream latency if available |
| `prompt_tokens` | `INTEGER` | no | Usage from upstream success |
| `completion_tokens` | `INTEGER` | no | Usage from upstream success |
| `total_tokens` | `INTEGER` | no | Usage from upstream success |
| `messages_count` | `INTEGER` | no | Count only |
| `question_summary` | `VARCHAR(512)` or `TEXT` with service limit | no | Bounded summary only, not full prompt |
| `hit_chunk_ids` | `JSONB` | no | Empty/null for current pass-through baseline |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` if following table rules |

Suggested indexes:

- `idx_rag_request_log_app_created_at` on `(app_id, created_at DESC)`.
- `idx_rag_request_log_user_created_at` on `(user_id, created_at DESC)`.
- `idx_rag_request_log_request_id` on `request_id`, unique if exactly one row per request is guaranteed.
- Optional `idx_rag_request_log_api_key_created_at` on `(api_key_id, created_at DESC)` if useful.

## Validation / Error Matrix

| Scenario | Existing HTTP result | Persist log? | Required persisted status |
|---|---:|---:|---|
| Missing/invalid app API key in `GatewayAuthFilter` | 401 `invalid_api_key` | No for this baseline unless the filter has enough safe context, which it usually does not | N/A |
| Valid key, malformed JSON before controller body validation | 400 `invalid_request` | No unless request ID/context are available in the current implementation path | N/A |
| Valid key, null body | 400 `invalid_request` | Yes if controller creates request ID before validation | `failure`, `error_code=invalid_request` |
| Valid key, empty messages | 400 `invalid_request` | Yes | `failure`, `error_code=invalid_request` |
| Valid key, unsupported role | 400 `invalid_request` | Yes | `failure`, `error_code=invalid_request` |
| Valid key, `stream=true` | 400 `invalid_request` | Yes | `failure`, `error_code=invalid_request` |
| Missing/disabled model config | 409 `model_config_not_ready` | Yes | `failure`, `error_code=model_config_not_ready` |
| Missing/decrypt-failed upstream key | 409 `model_config_not_ready` | Yes | `failure`, `error_code=model_config_not_ready` |
| Upstream non-2xx or network failure | 502 `upstream_error` | Yes | `failure`, `error_code=upstream_error` |
| Upstream timeout | 504 `upstream_timeout` | Yes | `failure`, `error_code=upstream_timeout` |
| Invalid upstream success body / parse failure | 502 `upstream_error` | Yes | `failure`, `error_code=upstream_error` |
| Upstream success | 200 chat completion | Yes | `success`, `error_code=null`, usage fields when present |
| Log persistence insert fails | Existing gateway response should not be replaced by logging failure | Should be safely logged in application logs only | Do not leak or change public response |

## Good / Base / Bad Cases

Good cases:

- Authenticated app with enabled model config receives upstream success, returns existing OpenAI-compatible response, and inserts one `success` request-log row with request ID, user/app/key IDs, model, provider, latency, message count, and usage when present.
- Authenticated request receives upstream 502/504; public error response remains unchanged and one `failure` request-log row is inserted with safe error code and latency.

Base cases:

- Existing structured application logs remain intact.
- Existing `/v1/models`, admin app/API key APIs, model config APIs, and global error responses remain unchanged.
- `hit_chunk_ids` remains null/empty until RAG retrieval is implemented.
- `question_summary` is null or strictly bounded; if implemented, it uses only the final user message summary/prefix and never stores the full request body.

Bad cases:

- Validation failure after authenticated context is available inserts one `failure` row and does not call upstream.
- Malformed JSON and auth failures may remain unpersisted in this baseline if request context is unavailable; this limitation must be documented in spec.
- Persisted rows must not contain app API key plaintext, key hash, upstream key plaintext, encrypted upstream key, Authorization header, raw provider body, full messages, or full prompt content.
- Insert failure must not convert a 200/specific gateway error into 500.

## Acceptance Criteria

- [ ] A migration creates `rag_request_log` or an explicitly justified equivalent table with required safe fields and indexes.
- [ ] Entity/mapper/service exist under package conventions, with controller/client SQL avoided.
- [ ] Non-streaming Chat Completions success writes exactly one safe request-log row.
- [ ] Authenticated validation failures handled in the controller path write failure rows where context/request ID exists.
- [ ] Model config failures write failure rows.
- [ ] Upstream `502 upstream_error` and `504 upstream_timeout` failures write failure rows.
- [ ] Parse failure maps to `upstream_error` and writes a failure row without provider body content.
- [ ] Public OpenAI-compatible response shapes and status codes are unchanged.
- [ ] Request-log persistence failure does not break the gateway response.
- [ ] Tests assert sensitive fields are absent from persisted records.
- [ ] Specs are updated with the concrete DB and logging/error contracts.

## Expected Files To Modify

Likely implementation files:

- `backend/src/main/resources/db/migration/V4__create_request_log_table.sql`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogStatus.java` if an enum is useful
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java` only if needed to expose safe resolved model/provider/usage/timing data
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java` only if needed for upstream latency/parse failure timing already not exposed

Likely test/spec files:

- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md` only if a new testing contract is introduced

## Required Tests And Assertion Points

Focused tests:

- `ApiRequestLogServiceTest`
  - inserts success row with safe IDs, status, model/provider, latency, message count, and token usage.
  - inserts failure row with error code.
  - truncates or omits `question_summary` beyond configured bound.
  - does not persist known sensitive sample values.
  - handles insert failure safely if the service intentionally swallows persistence errors.

- `OpenAiChatCompletionsControllerTest`
  - success inserts one log row and response shape remains OpenAI-compatible.
  - validation failure such as `stream=true` or unsupported role inserts failure row when authenticated context exists.
  - model config failure inserts `model_config_not_ready`.
  - no logged/persisted content includes Authorization value, app API key plaintext, upstream key plaintext/encrypted value, or message content beyond allowed summary.

- `OpenAiCompatibleUpstreamClientTest` or service/controller equivalent
  - upstream 502 path persists `upstream_error`.
  - timeout path persists `upstream_timeout`.
  - invalid upstream success body/parse failure persists `upstream_error`.
  - provider body is not persisted.

Required commands from `backend/` after implementation:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Spec Update Plan

Update after implementation:

- `.trellis/spec/sangui-rag-gateway.md`: add implemented request-log persistence baseline and limitations.
- `.trellis/spec/backend/database-guidelines.md`: add `rag_request_log` concrete schema/index/entity contracts.
- `.trellis/spec/backend/logging-guidelines.md`: add persisted request-log safe field contract.
- `.trellis/spec/backend/error-handling.md`: document which failure categories are persisted and which remain unpersisted due to missing safe context.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: frontend UI, admin query API, streaming, RAG retrieval, rate limits, quotas, and business implementation outside request-log persistence are out of scope.
- Expected files: listed above.
- Required tests: listed above with assertion points.
- Required guidelines: backend directory, database, error handling, logging, quality, project spec, and cross-layer guide must be read before implementation.
- Open question: whether to persist malformed JSON and auth failures in this baseline. Recommended answer: do not persist auth failures or malformed JSON unless safe request context is available without refactoring the filter/error boundary. Document the limitation.
