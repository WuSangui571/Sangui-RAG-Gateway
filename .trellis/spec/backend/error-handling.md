# Error Handling

> Public gateway errors should be OpenAI-compatible. Admin console errors may use a normal application response envelope, but must still avoid leaking secrets or stack traces.

## Error Families

Common gateway error codes:

```text
invalid_api_key
rate_limit_exceeded
app_not_found
knowledge_base_not_ready
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

## Gateway HTTP Status Mapping

Use conventional statuses where possible:

```text
401 invalid_api_key
403 app disabled, key revoked, forbidden tenant access
404 app_not_found when it is safe to reveal
409 knowledge_base_not_ready
429 rate_limit_exceeded
502 upstream_error
504 upstream_timeout
500 internal_error
```

Do not expose internal implementation details in `message`.

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
- `shouldReturn404ForV1Models`, `shouldReturn404ForV1ChatCompletions` — 404 admin envelope; no `$.error`; no fake model/chat data.
- `shouldReturn404ForFavicon`, `shouldReturn404ForUnmappedUnknownRoute` — 404 admin envelope.

`GlobalExceptionHandlerIntegrationTest` (integration, 4 tests):

- `shouldReturnSafe404ForUnknownRoute` — Real Spring context 404.
- `shouldReturnSafe404ForFavicon` — Real 404 for `/favicon.ico`.
- `shouldReturnSafe404ForUnimplementedModelsRoute` — `/v1/models` returns safe 404, no fake model list.
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
