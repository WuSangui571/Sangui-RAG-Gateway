# Chat Completions 请求日志与上游错误可观测性增强

## Task Classification

Complex Task.

Reason: the change touches the public `/v1/chat/completions` gateway path, gateway authentication context, upstream forwarding, OpenAI-compatible error mapping, safe structured logging, and backend specs. It must be planned and tested before implementation.

## Current Project State

- App API key admin baseline is implemented. App API keys are hashed, returned only once, and gateway auth uses `GatewayAuthFilter` for `/v1/*`.
- Admin model config baseline is implemented. Upstream API keys are encrypted at rest and decrypted only for outbound upstream calls.
- Non-streaming `POST /v1/chat/completions` pass-through is implemented and uses the app default model config, not the caller's requested model.
- Upstream `base_url` handling supports both provider root URLs and `/v1` API-root URLs.
- Current observability relies on ad hoc application logs such as controller receipt, model config readiness, upstream URL, and upstream error logs.

## Goal

Add safe, structured observability for non-streaming `POST /v1/chat/completions` so local联调 and future production troubleshooting can answer:

- Which app/API key/model/provider handled a request?
- What safe upstream host/path did the request target?
- Which stage failed?
- How long did the request and upstream call take?
- Was the failure an upstream non-2xx, network failure, timeout, invalid upstream success body, validation failure, or config readiness issue?
- Did logs avoid app keys, upstream keys, authorization headers, full messages/prompts, and provider raw error bodies?

## Scope

### In Scope

- Add request-level `request_id` for gateway Chat Completions logs.
- Add safe structured logs for these stages:
  - authentication completed
  - app/default model config resolved
  - upstream request started
  - upstream success
  - upstream failure
  - response parse success
  - response parse failure
  - request completed with final status/error code
- Add safe fields where available:
  - `request_id`
  - `user_id`
  - `app_id`
  - `api_key_id`
  - `provider_name`
  - `model`
  - safe upstream URL data: host + path, or a sanitized full URL without query/userinfo
  - `status`
  - `error_code`
  - `latency_ms`
  - `upstream_latency_ms`
  - `messages_count`
- Keep current OpenAI-compatible public response shapes and status mappings.
- Preserve current upstream error classification:
  - upstream non-2xx -> `502 upstream_error`
  - network failure -> `502 upstream_error`
  - timeout -> `504 upstream_timeout`
  - invalid upstream success body -> `502 upstream_error`
- Update specs:
  - `.trellis/spec/backend/logging-guidelines.md`
  - `.trellis/spec/backend/error-handling.md`

### Explicitly Out Of Scope

- No RAG retrieval, embedding, prompt augmentation, or context injection.
- No `stream=true` implementation.
- No frontend request log UI.
- No provider-specific routing or provider-specific error pass-through.
- No public API payload/response shape changes.
- No app API key or upstream API key storage changes.
- No new dependencies unless absolutely necessary.
- No database persistence in this task.
- No `rag_request_log` table or migration in this task.
- No logging of raw request bodies, full messages, prompts, upstream provider error bodies, app API keys, upstream API keys, encrypted upstream keys, or `Authorization` headers.

## API / Command / Payload Contract

### Public API

Endpoint remains unchanged:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Request payload remains unchanged:

| Field | Required | Behavior |
|---|---:|---|
| `model` | no | Accepted for compatibility but not trusted for upstream selection. |
| `messages` | yes | Non-empty array. Baseline roles remain `system`, `user`, `assistant`. |
| `temperature` | no | Forwarded when present. |
| `max_tokens` | no | Forwarded when present. |
| `top_p` | no | Forwarded when present. |
| `stream` | no | `true` remains rejected with `400 invalid_request`. |

No new public request field and no new public response field are required.

### Internal Observability Contract

Structured log events should be plain application logs using existing SLF4J patterns. Prefer consistent event names in the message text, for example:

```text
gateway.chat.auth_completed
gateway.chat.config_resolved
gateway.chat.upstream_started
gateway.chat.upstream_succeeded
gateway.chat.upstream_failed
gateway.chat.response_parse_succeeded
gateway.chat.response_parse_failed
gateway.chat.completed
```

Each event should include only safe key/value fields:

```text
request_id=<uuid-or-generated-id>
user_id=<long>
app_id=<long>
api_key_id=<long>
provider_name=<string>
model=<string>
upstream_url=<host-and-path-or-sanitized-url>
status=<success|failure>
error_code=<stable-code-or-null>
latency_ms=<long>
upstream_latency_ms=<long>
messages_count=<int>
```

`request_id` may be generated per request in the gateway layer. If later HTTP request-id header support is added, it must still avoid trusting or logging unsafe arbitrary header values without normalization.

## Validation / Error Matrix

| Scenario | HTTP | Error code | Log status | Required logging behavior |
|---|---:|---|---|---|
| Valid authenticated non-streaming request, upstream success, parse success | 200 | none | success | Log auth/config/upstream started/upstream success/parse success/completed with safe IDs, model/provider, safe upstream host/path, latency. |
| Missing/invalid/disabled app API key | 401 | `invalid_api_key` | failure | Gateway auth may log safe failure reason only; never log raw token/header. If request id is not available at filter level, do not force unsafe coupling. |
| Missing/disabled default model config | 409 | `model_config_not_ready` | failure | Log app/api key IDs and safe config status, no upstream call. |
| Missing encrypted upstream key or decrypt failure | 409 | `model_config_not_ready` | failure | Log config id and exception class only; never log encrypted key or plaintext key. |
| Malformed JSON/null body/invalid messages/unsupported role/`stream=true` | 400 | `invalid_request` | failure | Log validation failure with request id and error code, no messages/prompt/body echo. |
| Upstream non-2xx | 502 | `upstream_error` | failure | Log upstream HTTP status and safe upstream host/path; never log provider body. |
| Upstream network failure | 502 | `upstream_error` | failure | Log exception class and safe upstream host/path; never log headers/body/key. |
| Upstream timeout | 504 | `upstream_timeout` | failure | Log timeout classification and safe upstream host/path. |
| Invalid upstream success body | 502 | `upstream_error` | failure | Log parse failure with exception class, model/provider, request id; never log full upstream body. |
| Unexpected internal gateway exception | 502 or 500 depending existing boundary | `upstream_error` or `internal_error` | failure | Preserve existing public-safe behavior; log stack trace only where existing unexpected-error policy allows, but never include secrets/body/messages. |

## Good / Base / Bad Cases

### Good Cases

- Active app API key, enabled app, enabled default model config, encrypted upstream key decrypts, upstream returns valid chat completion JSON.
- Logs contain `request_id`, `app_id`, `api_key_id`, provider/model, safe upstream URL data, success status, and latency fields.
- Existing OpenAI-compatible success response remains unchanged.

### Base Cases

- Existing `GET /v1/models` behavior remains unchanged.
- Existing Chat Completions validation behavior remains unchanged.
- Existing base URL normalization behavior remains unchanged for:
  - `https://api.example.com`
  - `https://api.example.com/`
  - `https://api.example.com/v1`
  - `https://api.example.com/v1/`
- Logs may include message count but not message content.

### Bad Cases

- Upstream 502 with provider body containing `provider-secret`, `Authorization`, or user message text must still produce public `502 upstream_error`, and logs must not contain the provider body.
- Timeout must produce `504 upstream_timeout` and logs must classify it as timeout.
- Invalid upstream success JSON must produce `502 upstream_error` and logs must not contain raw upstream body.
- Any log captured during request handling must not contain:
  - app plaintext API key
  - upstream plaintext API key
  - encrypted upstream key
  - `Authorization`
  - full request body
  - `messages` content
  - provider raw error body

## Suggested Implementation Shape

- Prefer a small gateway observability helper under `com.sangui.raggateway.log` or `com.sangui.raggateway.gateway` if it keeps logging consistent without spreading ad hoc strings.
- Keep controllers thin. `OpenAiChatCompletionsController` may generate/request `request_id` and log request completion, but business-stage logging should remain in service/client layers.
- Consider adding a lightweight request context field or dedicated observability context object if `request_id` needs to cross controller/service/client boundaries. Do not make `common` depend on business packages.
- `OpenAiCompatibleUpstreamClient` should expose/log a sanitized upstream target, never a URL with userinfo/query and never headers.
- If testing logs directly, use Spring Boot test `OutputCaptureExtension` or a focused appender approach already compatible with current dependencies.

## Files Likely To Modify

Expected implementation files:

```text
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
backend/src/main/java/com/sangui/raggateway/common/security/GatewayRequestContext.java
backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java
backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/sangui/raggateway/log/*              (optional helper package)
```

Expected tests:

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java
```

Expected spec updates:

```text
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/error-handling.md
```

Do not add DB migration files for this task.

## Required Tests And Assertion Points

### Focused Tests

- `OpenAiCompatibleUpstreamClientTest`
  - upstream non-2xx maps to `upstream_error`
  - upstream provider body is not logged
  - upstream API key and Authorization header are not logged
  - safe upstream URL contains host/path only
  - timeout maps to `upstream_timeout`
- `ChatCompletionGatewayServiceTest`
  - successful request logs safe fields and not message content/key/header
  - config resolved stage logs provider/model/app/api key IDs
  - invalid upstream success body maps to `upstream_error` and does not log body/messages
  - upstream `upstream_timeout` is preserved
- `OpenAiChatCompletionsControllerTest`
  - successful request remains OpenAI-compatible
  - request id/completion log contains safe IDs/status/latency
  - public error responses remain unchanged
- `GatewayAuthFilterTest`
  - authentication completed log includes safe IDs if implemented at filter level
  - auth failure logs reason but not raw token/header
- `GlobalExceptionHandlerTest`
  - GatewayException log/response remains safe and OpenAI-compatible

### Required Commands

Run from `backend/`:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Acceptance Criteria

- [ ] `POST /v1/chat/completions` emits safe structured logs for the required stages.
- [ ] Each request has a `request_id` visible across Chat Completions stage logs.
- [ ] Success logs include safe operational fields and latency.
- [ ] Upstream non-2xx/network/timeout/invalid-success-body paths are classified as specified.
- [ ] Logs and public responses never include app API keys, upstream API keys, Authorization headers, full request messages, prompts, or provider raw error bodies.
- [ ] Public API behavior and OpenAI-compatible response/error shapes remain unchanged.
- [ ] No DB migration is added in this task.
- [ ] `.trellis/spec/backend/logging-guidelines.md` documents the new structured gateway log contract.
- [ ] `.trellis/spec/backend/error-handling.md` documents the observable upstream error classification behavior.
- [ ] Required focused tests and full Maven test suite pass.

## Planning Self-Check

- Acceptance criteria are explicit.
- Forbidden modification scope is explicit.
- Expected implementation and test files are listed.
- Required test commands are listed.
- Concrete backend guidelines were read, not just indexes.
- No unresolved frontend/API/DTO mismatch is expected because public API shape does not change.
- DB is intentionally out of scope; no `rag_request_log` persistence in this task.
