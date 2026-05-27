# 修复未匹配路由的安全 404 处理

## Classification

Complex Task

Reason: The code change is likely small, but the behavior touches global exception handling, public gateway route boundaries, logging severity, tests, and README/spec baseline documentation. It must not accidentally implement or imply support for OpenAI-compatible `/v1/*` APIs before the gateway feature is ready.

## Goal

Unknown or currently unimplemented routes must return a safe 404 response instead of being mapped to `500 INTERNAL_ERROR`. This includes ordinary unknown paths, `/favicon.ico`, and currently unimplemented future gateway routes such as `/v1/models` and `/v1/chat/completions`.

The fix must avoid exposing stack traces to clients and must avoid logging expected 404 route misses as `ERROR` with full stack traces.

## Scope

In scope:

- Backend global exception handling for Spring MVC no-route/no-resource 404 exceptions.
- Focused MockMvc tests for unmatched routes and currently unimplemented `/v1/*` endpoints.
- README and project spec baseline documentation updates if behavior changes from 500 to 404.
- Clarifying that the current baseline does not implement gateway APIs yet.

Out of scope:

- Do not implement `GET /v1/models`.
- Do not implement `POST /v1/chat/completions`.
- Do not add gateway authentication, API key validation, provider forwarding, RAG retrieval, streaming, or OpenAI response models.
- Do not change database schema, migrations, Redis, Docker Compose, frontend files, or admin API behavior beyond the shared error envelope.
- Do not add broad logging infrastructure or request ID middleware in this task.

## API / Command / Payload Contract

Existing implemented endpoint:

```http
GET /api/health
```

Expected behavior must remain unchanged:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "status": "UP",
    "service": "sangui-rag-gateway"
  }
}
```

Unmatched route behavior for current baseline:

```http
GET /unknown-path
GET /favicon.ico
GET /v1/models
POST /v1/chat/completions
```

Expected HTTP status:

```text
404 Not Found
```

Expected current baseline response shape:

```json
{
  "code": "NOT_FOUND",
  "message": "Resource not found",
  "data": null
}
```

Notes:

- Use the existing admin `ApiResponse` envelope for unmatched baseline routes because no gateway controller exists yet.
- Do not introduce the future OpenAI-compatible error shape for `/v1/*` in this task. That belongs with the actual gateway API implementation.
- When real `/v1/*` gateway endpoints are implemented later, supported gateway errors should move to the OpenAI-compatible error shape defined in `.trellis/spec/backend/error-handling.md` and `.trellis/spec/sangui-rag-gateway.md`.

## Validation / Error Matrix

| Case | Route | Method | Expected Status | Expected Body | Logging Expectation |
|---|---|---:|---:|---|---|
| Good | `/api/health` | GET | 200 | `code=OK`, `data.status=UP` | normal behavior unchanged |
| Base | `/unknown-route` | GET | 404 | `code=NOT_FOUND`, generic message, no stack trace fields | not logged as unexpected `ERROR` |
| Base | `/favicon.ico` | GET | 404 | `code=NOT_FOUND`, generic message, no stack trace fields | not logged as unexpected `ERROR` |
| Base | `/v1/models` | GET | 404 | `code=NOT_FOUND`, generic message, no fake model list | not logged as unexpected `ERROR` |
| Base | `/v1/chat/completions` | POST | 404 | `code=NOT_FOUND`, generic message, no fake chat response | not logged as unexpected `ERROR` |
| Bad | controller throws unexpected runtime exception | any mapped test route | 500 | `code=INTERNAL_ERROR`, generic message | still logged as unexpected `ERROR` |
| Bad | controller throws `BusinessException` | mapped test route | 400 | existing business error envelope | existing `WARN` behavior preserved |

## Good / Base / Bad Cases

Good case:

- `GET /api/health` remains a 200 admin envelope response and existing health tests continue to pass.

Base cases:

- Unknown baseline routes return safe 404 JSON.
- `/favicon.ico` returns safe 404 JSON.
- Currently unimplemented `/v1/models` and `/v1/chat/completions` return safe 404 JSON and do not accidentally expose fake OpenAI-compatible success behavior.

Bad cases:

- Unexpected controller exceptions still return 500 `INTERNAL_ERROR`, hide stack traces from clients, and remain logged as unexpected errors.
- `BusinessException` still returns 400 with its explicit application code/message.

## Expected Implementation Direction

Likely implementation file:

```text
backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java
```

Likely test files:

```text
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java
```

Possible documentation files:

```text
README.md
.trellis/spec/sangui-rag-gateway.md
```

Implementation notes for DeepSeek:

- Prefer handling Spring MVC 404/no-resource exceptions explicitly before the generic `Exception` handler.
- The likely Spring Boot 3 / Spring Framework 6 exception is `org.springframework.web.servlet.resource.NoResourceFoundException`.
- Consider whether `org.springframework.web.servlet.NoHandlerFoundException` or `org.springframework.web.server.ResponseStatusException` should also be handled if relevant to the current configuration, but keep scope focused on current observed behavior and tests.
- Use `HttpStatus.NOT_FOUND`.
- Use `ApiResponse.error("NOT_FOUND", "Resource not found")` or an equally generic safe message.
- Log expected 404 misses at `WARN` or lower, and do not pass the exception object if that causes stack traces for expected route misses.

## Required Tests And Assertion Points

Must add or update backend tests so `mvn test` covers:

- `GET /unknown-route` returns HTTP 404.
- `GET /favicon.ico` returns HTTP 404.
- `GET /v1/models` returns HTTP 404 and does not return a fake model list.
- `POST /v1/chat/completions` returns HTTP 404 and does not return a fake chat completion.
- 404 body uses safe current baseline envelope: `code=NOT_FOUND`, generic message, `data` empty/null.
- 404 response body does not include Java exception class names, stack trace text, or implementation details.
- Existing `BusinessException` and unexpected exception tests still pass.
- Existing health endpoint test still passes.

Recommended command:

```bash
cd backend
mvn test
```

Optional focused command while iterating:

```bash
cd backend
mvn -q "-Dtest=GlobalExceptionHandlerTest,HealthControllerTest" test
```

## Documentation Acceptance Criteria

- README current status or API notes state that `/v1/models` and `/v1/chat/completions` are not yet implemented and currently return safe 404 instead of 500.
- Project spec baseline validation matrix records unmatched route behavior as safe 404/no stack trace for the current baseline.
- Docs must not imply that OpenAI-compatible gateway APIs are already implemented.

## Acceptance Criteria

- [ ] Unknown routes return 404 `NOT_FOUND` JSON instead of 500 `INTERNAL_ERROR`.
- [ ] `/favicon.ico` returns 404 `NOT_FOUND` JSON instead of 500 `INTERNAL_ERROR`.
- [ ] Current unimplemented `/v1/models` and `/v1/chat/completions` return 404 `NOT_FOUND` JSON and no fake success payload.
- [ ] Expected 404 route misses are not logged as unexpected `ERROR` stack traces.
- [ ] Existing `BusinessException` and generic unexpected exception behavior remains intact.
- [ ] README/spec baseline documentation is updated for the new 404 behavior.
- [ ] `mvn test` passes under `backend/`.

