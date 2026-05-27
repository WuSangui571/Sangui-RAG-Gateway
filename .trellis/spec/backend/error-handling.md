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
| Valid key, unimplemented `/v1/*` route such as `/v1/chat/completions` | 404 | Existing safe admin 404 is acceptable until the route is implemented | Do not fake chat responses. |

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
| `Exception` (generic) | 500 | `ApiResponse<Void>` | `code=INTERNAL_ERROR`, `message=Internal server error`, no stack trace |

All handlers log safe context only (request IDs when available, error codes, non-sensitive messages). Stack traces are logged at ERROR level for unexpected exceptions but never returned to clients.

### Test Coverage

`GlobalExceptionHandlerTest` (unit, 8 tests):

- `shouldReturnOpenAiCompatibleShapeForGatewayException` — BAD_REQUEST with `$.error.message`, `$.error.type`, `$.error.code`; absence of `$.code`, `$.message`, `$.data`; no `Exception`/`java.` in body.
- `shouldReturn401ForGatewayInvalidApiKey` — 401 with code `invalid_api_key`, type `invalid_request_error`; no admin envelope fields.
- `shouldHandleBusinessExceptionWithApiResponse` — BAD_REQUEST with admin envelope; `$.error` doesNotExist.
- `shouldHideStackTraceForUnexpectedErrors` — 500 with `INTERNAL_ERROR`/`Internal server error`; no stack trace.
- `shouldReturn404ForUnimplementedRoute`, `shouldReturn404ForV1ChatCompletions` — 404 admin envelope; no `$.error`; no fake chat data.
- `shouldReturn404ForFavicon`, `shouldReturn404ForUnmappedUnknownRoute` — 404 admin envelope.

`GlobalExceptionHandlerIntegrationTest` (integration, 4 tests):

- `shouldReturnSafe404ForUnknownRoute` — Real Spring context 404.
- `shouldReturnSafe404ForFavicon` — Real 404 for `/favicon.ico`.
- `shouldReturnSafe404ForModelsRouteInTestProfile` — under the `test` profile, `/v1/models` is not registered and still returns the safe 404 envelope.
- `shouldReturnSafe404ForUnimplementedChatCompletionsRoute` — `/v1/chat/completions` returns safe 404, no `chat.completion` content.

Run targeted tests with `mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test` from `backend/`.

## Streaming Errors

For `stream=true`:

- If authentication, app loading, or retrieval fails before streaming starts, return a normal error response.
- If upstream fails after streaming begins, emit the most compatible error event possible and close the stream.
- If the client disconnects, cancel upstream forwarding and avoid logging it as an internal server error.

Usage data may be missing in MVP streaming responses; document this limitation.

## Upstream Error Handling

Upstream provider failures should be normalized:

```text
connection failure -> upstream_error
timeout -> upstream_timeout
provider 4xx -> upstream_error unless it maps to a safe config issue
provider 5xx -> upstream_error
invalid upstream API key -> upstream_error for public gateway callers; admin APIs may show a masked configuration error
```

Do not pass through upstream response bodies blindly. They may include provider details or sensitive request fragments.

## Document Pipeline Errors

Document processing must update status and error reason:

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

Store a bounded error message suitable for admin display. Full stack traces belong in server logs only.

## Forbidden Patterns

- Returning Java stack traces to clients.
- Logging complete API keys, upstream API keys, full private documents, or full augmented prompts.
- Treating tenant access failures as retriable internal errors.
- Swallowing embedding failures and marking documents as ready.
- Wrapping every exception as `500 internal_error` when the client should receive `401`, `429`, `409`, or upstream error classes.
