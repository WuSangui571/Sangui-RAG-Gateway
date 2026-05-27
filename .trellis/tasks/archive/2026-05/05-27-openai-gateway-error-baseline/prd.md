# OpenAI-compatible Gateway Error Baseline

## Task Classification

Complex Task.

Reason: this task defines a public `/v1/*` gateway API error contract and changes the global exception boundary. It affects response models, exception handling, tests, and backend specs. It must not be treated as a small controller-only change.

## Goal

Establish the baseline for OpenAI-compatible error responses used by future public gateway APIs while keeping admin/common APIs on the existing `ApiResponse` envelope.

This task should make the boundary explicit:

- Admin/common APIs continue returning:

```json
{
  "code": "TEST_ERROR",
  "message": "Test business error message",
  "data": null
}
```

- Implemented gateway API failures return:

```json
{
  "error": {
    "message": "Specific error message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

- Unmatched routes, including currently unimplemented `/v1/models` and `/v1/chat/completions`, continue returning the current safe 404 admin envelope:

```json
{
  "code": "NOT_FOUND",
  "message": "Resource not found",
  "data": null
}
```

## Requirements

- Add gateway error response model/helper for OpenAI-compatible shape.
- Add a minimal gateway exception type that carries:
  - safe message
  - OpenAI error `type`
  - error `code`
  - HTTP status
- Update `GlobalExceptionHandler` to:
  - convert gateway exceptions to OpenAI-compatible error response
  - keep `BusinessException` mapped to the admin `ApiResponse` envelope
  - keep unmatched route handlers mapped to safe 404 admin envelope
  - hide stack traces and internal details from all client responses
- Keep current unmatched route behavior unchanged; do not make unimplemented `/v1/*` routes look like implemented gateway APIs.
- Add focused tests for gateway error shape, admin business error shape, and unmatched route safe 404 shape.
- Update `.trellis/spec/backend/error-handling.md` with real class names, fields, status mapping, and test assertions introduced by the implementation.

## API / Payload Contract

### Gateway Error Response

Response body:

```json
{
  "error": {
    "message": "Specific error message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Fields:

| Field | Required | Type | Notes |
|---|---:|---|---|
| `error` | yes | object | Top-level OpenAI-compatible error container. |
| `error.message` | yes | string | Safe client-facing message. Do not include stack traces, secrets, SQL, class names, raw provider bodies, prompts, or private document content. |
| `error.type` | yes | string | OpenAI-compatible family such as `invalid_request_error`, `rate_limit_error`, `server_error`, or equivalent baseline names documented in spec. |
| `error.code` | yes | string | Stable machine-readable code such as `invalid_api_key`, `rate_limit_exceeded`, `upstream_timeout`, `internal_error`. |

### Admin Error Response

Existing admin/common response body remains unchanged:

```json
{
  "code": "TEST_ERROR",
  "message": "Test business error message",
  "data": null
}
```

### Unmatched Route Response

Existing safe 404 behavior remains unchanged for unknown routes and unimplemented `/v1/*` paths:

```json
{
  "code": "NOT_FOUND",
  "message": "Resource not found",
  "data": null
}
```

## Validation / Error Matrix

| Scenario | Exception / Source | HTTP Status | Response Shape | Key Assertions |
|---|---|---:|---|---|
| Gateway invalid request baseline | `GatewayException` or equivalent minimal gateway exception | status carried by exception, e.g. 400 | OpenAI-compatible `error` object | `$.error.message`, `$.error.type`, `$.error.code`; no top-level `code`, `message`, `data`. |
| Gateway invalid API key baseline | `GatewayException` configured as 401 `invalid_api_key` | 401 | OpenAI-compatible `error` object | code is `invalid_api_key`; type is compatible with invalid request/auth family; no secret exposure. |
| Admin business exception | existing `BusinessException` | 400 | admin `ApiResponse` envelope | `$.code`, `$.message`, `$.data`; no top-level `error`. |
| Unmapped unknown route | Spring `NoResourceFoundException` / `NoHandlerFoundException` | 404 | admin `ApiResponse` envelope | `code=NOT_FOUND`, `message=Resource not found`, no stack trace. |
| Unimplemented `/v1/models` | no controller mapping | 404 | admin `ApiResponse` envelope | remains current safe 404; must not return OpenAI `error`; must not return fake models list. |
| Unimplemented `/v1/chat/completions` | no controller mapping | 404 | admin `ApiResponse` envelope | remains current safe 404; must not return OpenAI `error`; must not return chat completion shape. |
| Unexpected server exception | generic `Exception` | 500 | admin `ApiResponse` envelope for now | `INTERNAL_ERROR`, no stack trace. Gateway-specific unexpected normalization can be introduced later when real gateway controllers exist. |

## Good / Base / Bad Cases

### Good Case

A mapped test gateway route throws the new gateway exception. The response status and body match:

```json
{
  "error": {
    "message": "Invalid request for gateway test",
    "type": "invalid_request_error",
    "code": "invalid_request"
  }
}
```

The response must not include `data`, Java exception names, stack traces, authorization headers, or raw internal messages.

### Base Case

An admin/common test route throws `BusinessException`. It still returns the existing admin envelope:

```json
{
  "code": "TEST_ERROR",
  "message": "Test business error message",
  "data": null
}
```

This confirms the public gateway error baseline does not overwrite admin behavior.

### Bad Cases

- Unknown route or unimplemented `/v1/*` returns OpenAI-compatible `error`, causing clients to infer the endpoint exists.
- Gateway exception returns admin envelope, making future OpenAI clients parse incompatible errors.
- Admin business exception returns OpenAI-compatible error and breaks admin/frontend expectations.
- Error response leaks stack traces, Java class names, SQL, provider response bodies, prompts, API keys, or authorization headers.

## Expected Implementation Approach

Use existing project style and keep the change minimal.

Suggested files:

- `backend/src/main/java/com/sangui/raggateway/common/response/OpenAiError.java`
- `backend/src/main/java/com/sangui/raggateway/common/response/OpenAiErrorResponse.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GatewayException.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java` only if needed to preserve/clarify safe 404 behavior
- `.trellis/spec/backend/error-handling.md`

Implementation notes:

- Prefer simple immutable or getter-based model classes consistent with current `ApiResponse`.
- Keep `common` independent from business modules.
- `GatewayException` should not require any future domain/service classes.
- If using factory methods for common gateway errors, keep them small and avoid adding unused future-specific subclasses unless tests need them.
- Do not add new dependencies.
- Do not add actual `/v1/models` or `/v1/chat/completions` controllers in this task.
- Do not introduce API key authentication, app config, rate limiting, upstream forwarding, streaming, database schema, Redis behavior, or frontend code.

## Acceptance Criteria

- [ ] Gateway exception returns OpenAI-compatible body with top-level `error`.
- [ ] Gateway exception response includes `error.message`, `error.type`, and `error.code`.
- [ ] Gateway exception response uses the HTTP status carried by the exception.
- [ ] Admin `BusinessException` still returns existing admin `ApiResponse` envelope.
- [ ] Unknown routes still return 404 `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.
- [ ] Unimplemented `/v1/models` still returns safe 404 admin envelope, not OpenAI `error`.
- [ ] Unimplemented `/v1/chat/completions` still returns safe 404 admin envelope, not OpenAI `error`.
- [ ] Error responses do not expose stack traces, Java class names, secrets, prompts, or raw internal details.
- [ ] `.trellis/spec/backend/error-handling.md` documents the concrete classes and test assertions.
- [ ] Focused Maven tests pass.

## Required Tests And Assertion Points

Run from `backend/`.

Required commands:

```bash
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

Focused test assertions:

- Gateway exception route:
  - expected status, e.g. `400` or `401`
  - `$.error.message`
  - `$.error.type`
  - `$.error.code`
  - no top-level `$.code`
  - no top-level `$.message`
  - no top-level `$.data`
  - response body does not contain `Exception` or `java.`
- Admin business exception route:
  - `status().isBadRequest()`
  - `$.code == TEST_ERROR`
  - `$.message == Test business error message`
  - `$.data` empty/null
  - no top-level `$.error`
- Unmatched route and unimplemented `/v1/*` routes:
  - `status().isNotFound()`
  - `$.code == NOT_FOUND`
  - `$.message == Resource not found`
  - `$.data` empty/null
  - no top-level `$.error`
  - response body does not contain `Exception`, `java.`, `chat.completion`, or fake model list data

## Out Of Scope

- Implementing `GET /v1/models`.
- Implementing `POST /v1/chat/completions`.
- API key authentication or authorization filters.
- App/model/knowledge-base lookup.
- RAG retrieval or prompt construction.
- Upstream provider forwarding.
- Streaming/SSE behavior.
- Database tables or migrations.
- Redis rate limiting.
- Frontend/admin UI changes.
- README updates unless implementation discovers a user-visible behavior gap that must be documented.

## Planning Self-Check

- Acceptance criteria are explicit.
- Prohibited implementation scope is explicit.
- Expected modified files are listed.
- Required tests and assertion points are listed.
- Concrete backend guidelines have been read, not only indexes.
- No current requirement requires user clarification.
- No DB/frontend DTO/type contract is introduced by this task.
