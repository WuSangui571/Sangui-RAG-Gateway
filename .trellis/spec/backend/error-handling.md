# Error Handling

> Public gateway errors should be OpenAI-compatible. Admin console errors may use a normal application response envelope, but must still avoid leaking secrets or stack traces.

## Error Families

Common gateway error codes:

```text
invalid_api_key
rate_limit_exceeded
app_not_found
knowledge_base_not_ready
model_config_not_ready
embedding_failed
upstream_timeout
upstream_error
internal_error
```

OpenAI-compatible error shape:

```json
{
  "error": {
    "message": "Specific error message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Rate-limit example:

```json
{
  "error": {
    "message": "Rate limit exceeded for this API key.",
    "type": "rate_limit_error",
    "code": "rate_limit_exceeded"
  }
}
```

## `/v1/models` Endpoint

`GET /v1/models` is implemented and returns:

- **200** with OpenAI-compatible model list on success: `{"object":"list","data":[{"id":"<chat_model>","object":"model","created":0,"owned_by":"<provider_name>"}]}`
- **409** `model_config_not_ready` when an authenticated app has no enabled default model config.
- **401** `invalid_api_key` (from GatewayAuthFilter) for missing/invalid/disabled keys.

The controller reuses `GatewayRequestContextHolder`; it does not re-authenticate.

## `/v1/chat/completions` Endpoint

`POST /v1/chat/completions` is implemented for non-streaming pass-through and returns OpenAI-compatible responses:

- **200** with chat completion JSON on upstream success. Do not wrap with `ApiResponse`.
- **400** `invalid_request` for malformed JSON, null body, missing/empty `messages`, missing `role`, unsupported role, missing `content`, or `stream=true`.
- **401** `invalid_api_key` for authentication failures from `GatewayAuthFilter`; a defensive missing-context guard may return the same shape but must not parse or validate tokens in the controller.
- **409** `model_config_not_ready` when the authenticated app has no enabled default model config, no encrypted upstream key, or an undecryptable upstream key.
- **502** `upstream_error` for upstream non-2xx, network errors, or invalid upstream success bodies.
- **504** `upstream_timeout` for upstream timeout.

Gateway chat errors must not expose raw request messages, authorization headers, plaintext upstream keys, encrypted upstream keys, provider error bodies, or stack traces.

## Gateway HTTP Status Mapping

Use conventional statuses where possible:

```text
400 invalid_request (admin validation failures)
401 invalid_api_key
403 app disabled, key revoked, forbidden tenant access
404 app_not_found / model_config_not_found when it is safe to reveal
409 knowledge_base_not_ready
409 model_config_not_ready
429 rate_limit_exceeded
502 upstream_error
504 upstream_timeout
500 internal_error
```

Do not expose internal implementation details in `message`.

## Admin Model Config API Error Codes

The admin model config endpoints (`/api/admin/model-configs/**`) and app binding endpoint (`/api/admin/apps/**`) use the `ApiResponse` envelope with these error codes:

| Scenario | HTTP | Code | Notes |
|---|---|---|---|
| Missing `X-Admin-User-Id` header | 400 | `INVALID_REQUEST` | Caught by `MissingRequestHeaderException` handler. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Caught by `MethodArgumentTypeMismatchException` handler. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Validated in controller. |
| Malformed JSON request body | 400 | `INVALID_REQUEST` | Caught by `HttpMessageNotReadableException`; response message is `Malformed request body` and does not echo body content. |
| Blank required field (name, provider_name, base_url, chat_model, api_key) | 400 | `INVALID_REQUEST` | Service-level validation. |
| Invalid embedding dimension | 400 | `INVALID_REQUEST` | Reuses existing embedding validation. |
| Config not found by id | 404 | `NOT_FOUND` | Config does not exist at all. |
| Config belongs to different user | 403 | `FORBIDDEN` | Config exists but owned by another user. |
| App not found for binding | 404 | `NOT_FOUND` | App does not exist. |
| App belongs to different user | 403 | `FORBIDDEN` | App exists but owned by another user. |
| Model config disabled for binding | 400 | `MODEL_CONFIG_NOT_READY` | Config exists but is not enabled. |
| Invalid status filter | 400 | `INVALID_REQUEST` | Only `ENABLED` or `DISABLED` accepted. |

`BusinessException` now supports an optional `HttpStatus` parameter. The default constructor (code + message) returns 400 BAD_REQUEST. The extended constructor (code + message + httpStatus) returns the specified status. This allows 403 FORBIDDEN and 404 NOT_FOUND responses while maintaining backward compatibility with all existing callers.

Secret-safe error responses:

- Error response bodies must not include `api_key`, `api_key_encrypted`, raw upstream secrets, or stack traces.
- Exception messages for validation failures (e.g., "name is required") are safe to return.
- Cross-user access failures use the generic "Access denied" message to avoid information leakage.

## Admin App API Key API Error Codes

The app and app API key admin endpoints use the same `ApiResponse` envelope and temporary `X-Admin-User-Id` identity contract:

```http
POST /api/admin/apps
GET  /api/admin/apps
GET  /api/admin/apps/{id}
POST /api/admin/apps/{appId}/api-keys
GET  /api/admin/apps/{appId}/api-keys
POST /api/admin/api-keys/{id}/disable
POST /api/admin/api-keys/{id}/revoke
```

Validation and error matrix:

| Scenario | HTTP | Code | Notes |
|---|---:|---|---|
| Missing `X-Admin-User-Id` header | 400 | `INVALID_REQUEST` | Caught by `MissingRequestHeaderException`. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Caught by `MethodArgumentTypeMismatchException`. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Validated before business mutation. |
| Malformed JSON or null request body | 400 | `INVALID_REQUEST` | Body content must not be echoed. |
| Create app blank `name` | 400 | `INVALID_REQUEST` | No app row inserted. |
| App status filter outside `ENABLED|DISABLED` | 400 | `INVALID_REQUEST` | Do not echo arbitrary filter values. |
| App id does not exist | 404 | `NOT_FOUND` | Applies to detail and key creation/listing. |
| App id belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`. |
| Create key blank `name` | 400 | `INVALID_REQUEST` | No plaintext key or hash returned. |
| Create key `expires_at` is not in the future | 400 | `INVALID_REQUEST` | No key inserted. |
| Key id does not exist | 404 | `NOT_FOUND` | Applies to disable/revoke. |
| Key id belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`. |
| Disable revoked key | 400 | `INVALID_REQUEST` | Revoked is terminal for disable. |
| Disable active/disabled key | 200 | `OK` | Response omits `key` and `key_hash`. |
| Revoke active/disabled/revoked key | 200 | `OK` | Response omits `key` and `key_hash`; revoked rows keep or set `revoked_at`. |

Secret-safe app API key responses:

- `key` appears only in `ApiKeyCreateVO` from `POST /api/admin/apps/{appId}/api-keys`.
- `key_hash` is never returned by Admin APIs.
- Gateway failures for disabled, revoked, expired, unknown, or malformed app keys still use OpenAI-compatible `401 invalid_api_key`; they must not use the admin envelope.

## Gateway API Key Auth Baseline

The `/v1/*` authentication boundary is implemented as a servlet filter, not Spring Security:

```text
backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java
backend/src/main/java/com/sangui/raggateway/common/config/GatewayAuthConfig.java
```

Filter registration:

```text
url pattern: /v1/*
request header: Authorization: Bearer <plaintext-api-key>
```

The plaintext key format is produced by `ApiKeyGenerator`:

```text
sk-sangui-<base64url-token>
```

Successful authentication must:

```text
1. Extract the Bearer token.
2. Reject missing, blank, non-Bearer, empty, or non-sk-sangui tokens.
3. Hash the full plaintext key with ApiKeyHasher.
4. Lookup ApiKeyService.findByHash(keyHash).
5. Require ApiKeyService.isValid(apiKey).
6. Lookup AppService.findById(apiKey.appId).
7. Require AppService.isEnabled(app).
8. Update ApiKeyService.updateLastUsed(apiKey.id).
9. Set GatewayRequestContextHolder with appId, userId, apiKeyId, apiKeyPrefix.
10. Clear GatewayRequestContextHolder in a finally block.
```

Request context fields:

| Field | Type | Source |
|---|---|---|
| `appId` | `Long` | `rag_app.id` |
| `userId` | `Long` | `rag_app.user_id` |
| `apiKeyId` | `Long` | `rag_api_key.id` |
| `apiKeyPrefix` | `String` | `rag_api_key.key_prefix` |

Authentication failures are written directly by the filter because filter exceptions do not reliably pass through MVC `@RestControllerAdvice`.

Required failure response:

```json
{
  "error": {
    "message": "Invalid API key.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Failure matrix:

| Case | HTTP | Response shape | Secret handling |
|---|---:|---|---|
| Missing `Authorization` | 401 | OpenAI-compatible `invalid_api_key` | Do not include header name/value in response body. |
| Non-Bearer scheme | 401 | OpenAI-compatible `invalid_api_key` | Do not echo scheme payload. |
| Empty Bearer token | 401 | OpenAI-compatible `invalid_api_key` | Do not echo token. |
| Malformed prefix | 401 | OpenAI-compatible `invalid_api_key` | Do not echo token. |
| Unknown hash | 401 | OpenAI-compatible `invalid_api_key` | Do not reveal lookup reason to client. |
| Disabled/revoked/expired key | 401 | OpenAI-compatible `invalid_api_key` | Do not expose status or expiry to client. |
| Missing/disabled app | 401 | OpenAI-compatible `invalid_api_key` | Avoid app enumeration. |
| Valid key, implemented `/v1/models` route | 200 or 409 | OpenAI-compatible success/error shape | Return configured model list or `model_config_not_ready`; do not use admin envelope. |
| Valid key, implemented `/v1/chat/completions` route | 200, 400, 409, 502, or 504 | OpenAI-compatible success/error shape | Do not use admin envelope; do not fake RAG behavior in the pass-through baseline. |
| Valid key, other unimplemented `/v1/*` route | 404 | Existing safe admin 404 is acceptable until a route is implemented | Do not fake unsupported OpenAI APIs. |

Good/base/bad cases:

| Category | Case | Expected result |
|---|---|---|
| Good | Active key for enabled app | Filter chain continues and context is available during the request. |
| Base | `/api/health`, `/actuator/**`, admin/common paths | Gateway API key auth is not applied. |
| Base | Valid key for route without a controller | Existing safe 404 behavior remains acceptable. |
| Bad | Missing, malformed, unknown, disabled, revoked, expired key, disabled app | 401 OpenAI-compatible `invalid_api_key`; no admin envelope fields. |

Required tests:

```text
backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyGeneratorTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyHasherTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java
```

Run these checks after changing gateway auth:

```bash
cd backend
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Implemented Baseline

The current error handling baseline enforces a strict boundary between gateway and admin response shapes through concrete classes in `common`.

### Response Models

`OpenAiError` (`common/response/OpenAiError.java`):

| Field | Type | Description |
|-------|------|-------------|
| `message` | `String` | Safe client-facing message. |
| `type` | `String` | OpenAI-compatible family (e.g. `invalid_request_error`, `server_error`). |
| `code` | `String` | Stable machine-readable code (e.g. `invalid_api_key`, `invalid_request`). |

`OpenAiErrorResponse` (`common/response/OpenAiErrorResponse.java`):

| Field | Type | Description |
|-------|------|-------------|
| `error` | `OpenAiError` | Top-level OpenAI-compatible error container. |

Use `OpenAiErrorResponse.of(message, type, code)` for construction.

`ApiResponse<T>` (`common/response/ApiResponse.java`) remains the admin/common envelope with fields `code`, `message`, `data`.

### Exception Types

`GatewayException` (`common/exception/GatewayException.java`):

| Constructor param | Type | Description |
|-------------------|------|-------------|
| `message` | `String` | Safe message for the client. |
| `type` | `String` | OpenAI-compatible error type. |
| `code` | `String` | Stable machine-readable error code. |
| `httpStatus` | `HttpStatus` | HTTP status to return to the client. |
| `cause` (optional) | `Throwable` | Root cause for logging, never exposed to clients. |

The `message`, `type`, `code`, and `httpStatus` constructor arguments are required and must be non-null.

`BusinessException` (`common/exception/BusinessException.java`) remains the admin exception type with `code` and `message` fields.

### GlobalExceptionHandler Response Mapping

| Exception caught | HTTP status | Response shape | Key fields |
|---|---|---|---|
| `BusinessException` | 400 | `ApiResponse<Void>` | `code`, `message`, `data` (null); no `error` field |
| `GatewayException` | carried by exception | `OpenAiErrorResponse` | `error.message`, `error.type`, `error.code`; no `code`/`message`/`data` |
| `NoResourceFoundException` | 404 | `ApiResponse<Void>` | `code=NOT_FOUND`, `message=Resource not found`, no `error` field |
| `NoHandlerFoundException` | 404 | `ApiResponse<Void>` | `code=NOT_FOUND`, `message=Resource not found`, no `error` field |
| `HttpMessageNotReadableException` on `/v1/*` | 400 | `OpenAiErrorResponse` | `error.code=invalid_request`; no body echo, no admin envelope |
| `HttpMessageNotReadableException` outside `/v1/*` | 400 | `ApiResponse<Void>` | `code=INVALID_REQUEST`, `message=Malformed request body` |
| `Exception` (generic) | 500 | `ApiResponse<Void>` | `code=INTERNAL_ERROR`, `message=Internal server error`, no stack trace |

All handlers log safe context only (request IDs when available, error codes, non-sensitive messages). Stack traces are logged at ERROR level for unexpected exceptions but never returned to clients.

### Test Coverage

`GlobalExceptionHandlerTest` (unit, 8 tests):

- `shouldReturnOpenAiCompatibleShapeForGatewayException` — BAD_REQUEST with `$.error.message`, `$.error.type`, `$.error.code`; absence of `$.code`, `$.message`, `$.data`; no `Exception`/`java.` in body.
- `shouldReturn401ForGatewayInvalidApiKey` — 401 with code `invalid_api_key`, type `invalid_request_error`; no admin envelope fields.
- `shouldHandleBusinessExceptionWithApiResponse` — BAD_REQUEST with admin envelope; `$.error` doesNotExist.
- `shouldHideStackTraceForUnexpectedErrors` — 500 with `INTERNAL_ERROR`/`Internal server error`; no stack trace.
- `shouldReturn404ForUnimplementedRoute`, `shouldReturn404ForUnimplementedV1Route` — 404 admin envelope; no `$.error`; no fake chat data.
- `shouldReturn404ForFavicon`, `shouldReturn404ForUnmappedUnknownRoute` — 404 admin envelope.

`GlobalExceptionHandlerIntegrationTest` (integration, 4 tests):

- `shouldReturnSafe404ForUnknownRoute` — Real Spring context 404.
- `shouldReturnSafe404ForFavicon` — Real 404 for `/favicon.ico`.
- `shouldReturnSafe404ForModelsRouteInTestProfile` — under the `test` profile, `/v1/models` is not registered and still returns the safe 404 envelope.
- `shouldReturnSafe404ForChatCompletionsRouteInTestProfile` — under the `test` profile, `/v1/chat/completions` is not registered and still returns the safe 404 envelope.

Run targeted tests with `mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test` from `backend/`.

## Streaming Errors

For `stream=true` (implemented baseline):

- Pre-stream errors (validation, model config not ready, upstream non-2xx before the response stream is ready, upstream connection failure, upstream timeout) return OpenAI-compatible JSON via `GatewayException`.
- The controller starts upstream work on a virtual thread but waits until the upstream response is confirmed as 2xx before returning the `SseEmitter`, so upstream setup failures can still use `GlobalExceptionHandler`.
- If upstream fails after a 2xx stream has started, emit an SSE error event (`data: {"error":{"message":"...","type":"server_error","code":"upstream_error"}}`) and close the stream.
- If the client disconnects (detected as `IOException` on `emitter.send()`), upstream reading is stopped and the stream is closed quietly. This is logged as `gateway.chat.stream_cancelled` at INFO level, not as an internal server error.
- If the upstream closes without `data: [DONE]`, treat it as a detectable post-start `upstream_error`.
- Usage data is null for streaming in this baseline. The limitation is documented in tests and specs.
- Streaming error events do not expose raw provider bodies or stack traces.

Implementation uses Spring MVC `SseEmitter` and `RestClient.exchange()` with background `Thread.ofVirtual()` for streaming. The `GlobalExceptionHandler` is involved only for errors detected before the SSE response is committed.

## Upstream Error Handling

Upstream provider failures should be normalized:

```text
connection failure -> upstream_error
timeout -> upstream_timeout
provider 4xx -> upstream_error unless it maps to a safe config issue
provider 5xx -> upstream_error
invalid upstream API key -> upstream_error for public gateway callers; admin APIs may show a masked configuration error
```

All upstream failures map to `502 upstream_error` (or `504 upstream_timeout` for timeouts). Upstream provider response bodies must never be passed through to public gateway callers — they may include provider internals, sensitive request fragments, or API key context.

### Implemented Upstream Error Classification

`OpenAiCompatibleUpstreamClient` classifies upstream failures as follows:

| Scenario | GatewayException code | HTTP status | Log event | Log fields |
|---|---|---|---|---|
| Upstream non-2xx (any) | `upstream_error` | 502 | `gateway.chat.upstream_failed` | request_id, safe upstream_url, upstream status, model, upstream_latency_ms |
| Network failure (connection refused, etc.) | `upstream_error` | 502 | `gateway.chat.upstream_failed` | request_id, safe upstream_url, error_class, error_code, upstream_latency_ms |
| Socket timeout | `upstream_timeout` | 504 | `gateway.chat.upstream_failed` | request_id, safe upstream_url, error_class, error_code=upstream_timeout, upstream_latency_ms |
| Invalid upstream success body (parse failure) | `upstream_error` | 502 | `gateway.chat.response_parse_failed` | request_id, model, error_class |
| Unexpected internal exception | `upstream_error` | 502 | `gateway.chat.upstream_failed` | request_id, safe upstream_url, error_class, error_code, upstream_latency_ms |

`ChatCompletionGatewayService` forwards GatewayException from upstream/client through the controller to GlobalExceptionHandler. The completed log in the controller captures the final success/failure status, error_code, and total latency_ms.

Public gateway responses remain OpenAI-compatible for all error cases. Upstream provider body content is never included in logs or client responses. Upstream client and response-parse failure logs record exception class names only, not throwable messages or stack traces, because those exception messages can contain raw upstream URL or body fragments.

### Implemented Request Log Persistence Error Classification

`OpenAiChatCompletionsController` persists one `rag_request_log` row per authenticated non-streaming request, mapping GatewayException codes to persisted `error_code`:

| GatewayException code | Persisted status | Persisted error_code | model/provider populated |
|---|---|---|---|
| (success, no exception) | `success` | null | yes |
| `invalid_request` | `failure` | `invalid_request` | no |
| `model_config_not_ready` | `failure` | `model_config_not_ready` | no |
| `upstream_error` | `failure` | `upstream_error` | no |
| `upstream_timeout` | `failure` | `upstream_timeout` | no |

Known unpersisted scenarios:

| Scenario | Reason |
|---|---|
| Malformed JSON body (400) | Request body cannot be deserialized; request ID is not generated before `HttpMessageNotReadableException` handling in `GlobalExceptionHandler`. |
| Gateway auth filter failure (401) | `GatewayAuthFilter` writes the OpenAI-compatible 401 response directly without reaching the controller's persistence boundary. |

Log persistence insert failure: `ApiRequestLogService.record()` catches all exceptions internally and logs at ERROR. The public gateway response is never affected by a logging persistence failure. Application-level log events (`gateway.chat.completed`) remain unchanged.

## Document Pipeline Errors

Document processing must update status and error reason:

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

Store a bounded error message suitable for admin display. Full stack traces belong in server logs only.

### Embedding Failure Behavior

Embedding failures are handled inline during document ingestion, not via separate error endpoints:

| Scenario | API response | Document status | KB status | Vectors persisted |
|---|---|---|---|---|
| Missing/disabled/mismatched embedding config | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` if prior ready docs exist | no |
| Embedding dimension mismatch with KB | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` | no |
| Provider returns wrong count or dimension | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` | no |
| Provider non-2xx or network error | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` | no |
| Provider timeout | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` | no |
| Upstream key decrypt failure | 200 `DocumentVO.status=FAILED` | `FAILED` | `FAILED` or `READY` | no |

`error_message` is bounded to 512 characters. Provider raw bodies, upstream keys, and stack traces are never exposed in `error_message`.

## Forbidden Patterns

- Returning Java stack traces to clients.
- Logging complete API keys, upstream API keys, full private documents, or full augmented prompts.
- Treating tenant access failures as retriable internal errors.
- Swallowing embedding failures and marking documents as ready.
- Wrapping every exception as `500 internal_error` when the client should receive `401`, `429`, `409`, or upstream error classes.

## Knowledge Base and Document Admin API Error Codes

The knowledge base and document admin endpoints use the `ApiResponse` envelope with these error codes:

| Scenario | HTTP | Code | Notes |
|---|---|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Caught by `MissingRequestHeaderException`. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Caught by `MethodArgumentTypeMismatchException`. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Validated in controller. |
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
| Unsupported filename | 400 | `INVALID_REQUEST` | No document row. |
| Parse/chunk failure after document row | 200 | `OK` with `DocumentVO.status=FAILED` | Bounded `error_message`. |
| Invalid document status filter | 400 | `INVALID_REQUEST` | Do not echo arbitrary input. |
| Get missing document | 404 | `NOT_FOUND` | Safe admin envelope. |
| Get cross-user document | 403 | `FORBIDDEN` | Generic access denied. |
