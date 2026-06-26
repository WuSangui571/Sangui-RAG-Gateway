# IllegalArgumentException Error Safety

## Status

Planning-only handoff prepared by Codex. Do not implement from this PRD until the implementation agent has read the task context and required specs.

## Classification

Complex Task.

Reason: the issue is a shared public/Admin error boundary and security boundary. It crosses the global exception handler, controller-to-service validation flow, service exception taxonomy, specs, and multiple controller/service regression tests. It should be treated as a structural backend fix, not a local hotfix that only changes one assertion.

## Goal

Prevent raw `IllegalArgumentException` messages from being returned directly to public or Admin clients unless the message is an explicitly safe validation message owned by the request boundary.

The implementation should preserve clear user-facing validation errors for ordinary invalid input while ensuring internal exception text, provider/decryption details, JSON parse internals, path/storage details, SQL details, stack traces, and arbitrary service exception messages are not surfaced through response bodies or sensitive logs.

## Background

The previous completed task hardened request-log persistence failures so gateway responses remain unchanged while logs expose only safe operational metadata. This task continues the same quality line at the error-response boundary.

Current research found these relevant paths:

- `GlobalExceptionHandler.handleIllegalArgumentException(...)` returns `ex.getMessage()` as `ApiResponse.message`.
- Several Admin controllers catch `IllegalArgumentException` and wrap it as `BusinessException("INVALID_REQUEST", e.getMessage())`.
- Some `IllegalArgumentException` messages are safe validation messages such as `name is required`.
- Other messages can carry internal or unsafe context, such as decryption failures, parser/JSON parse internals, file/storage path details, provider/client internals, or values interpolated from caller input.

## Requirements

- Define a single, documented policy for safe versus unsafe `IllegalArgumentException` messages.
- Update `.trellis/spec/backend/error-handling.md` with the policy and validation/error matrix.
- Update `.trellis/spec/backend/logging-guidelines.md` and `.trellis/spec/security/rag-security.md` only where needed to align error-message and logging safety.
- Centralize public/Admin mapping so raw `IllegalArgumentException#getMessage()` is not used as a default response body.
- Keep `BusinessException` and `GatewayException` as the explicit safe exception types for client-facing messages.
- Preserve existing OpenAI-compatible error shape for `/v1/*` gateway errors.
- Preserve Admin `ApiResponse` envelope for `/api/admin/**` errors.
- Keep validation failures visible. Do not add silent fallbacks, mock success, or broad catch blocks that hide bugs.
- Avoid duplicating allowlists or message-safety rules across controllers.
- Update focused tests that currently expect raw `IllegalArgumentException` message pass-through.

## Non-Goals

- No frontend changes.
- No database schema or migration changes.
- No infra, Docker, nginx, or environment variable changes.
- No request/response DTO field additions.
- No API route additions or route shape changes.
- No broad rewrite of all service validation to `BusinessException` unless directly required to remove raw message exposure.
- No conversion of expected domain failures into `500`.
- No provider fallback, retry, circuit breaker, or health endpoint work.
- No README work unless implementation discovers a direct documented error-contract mismatch.

## API / Command / Payload Fields

No new API endpoints, commands, payload fields, response fields, database fields, or environment variables are expected.

Existing shapes must remain:

- Public gateway `/v1/*` errors: `OpenAiErrorResponse` with top-level `error.message`, `error.type`, and `error.code`.
- Admin/common errors: `ApiResponse<Void>` with top-level `code`, `message`, and `data=null`.

Expected stable generic messages:

- Admin/raw unsafe `IllegalArgumentException`: `code=INVALID_REQUEST`, `message=Invalid request`.
- Public `/v1/*` raw unsafe `IllegalArgumentException`, if it reaches MVC handler: OpenAI-compatible `400 invalid_request` with safe generic message such as `Invalid request.`
- Unexpected exceptions: unchanged `500 INTERNAL_ERROR` / `Internal server error`.

Safe validation messages should be carried by explicit boundary exceptions, not by raw `IllegalArgumentException` default handling:

- Admin validation: `BusinessException("INVALID_REQUEST", "<safe validation message>")`.
- Gateway validation: `GatewayException("<safe validation message>", "invalid_request_error", "invalid_request", BAD_REQUEST)`.

## Validation / Error Matrix

| Scenario | HTTP | Shape | Code | Message rule | Assertion points |
|---|---:|---|---|---|---|
| Admin service/controller throws explicit `BusinessException("INVALID_REQUEST", "name is required")` | 400 | `ApiResponse` | `INVALID_REQUEST` | Preserve safe message | `GlobalExceptionHandlerTest`, relevant controller test |
| Admin raw `IllegalArgumentException("expiresAt must be in the future")` reaches global handler | 400 | `ApiResponse` | `INVALID_REQUEST` | Do not default to raw message unless explicitly classified safe at boundary | `GlobalExceptionHandlerTest` |
| Admin raw `IllegalArgumentException("Failed to decrypt upstream API key")` reaches handler/controller wrapper | 400 or existing mapped status | `ApiResponse` | Existing explicit code or `INVALID_REQUEST` | Must not contain decrypt detail, ciphertext, provider body, stack trace, path, SQL, token, or raw input | `GlobalExceptionHandlerTest`, `ModelConfigAdminControllerTest` or service-specific test |
| Admin malformed JSON body | 400 | `ApiResponse` | `INVALID_REQUEST` | Keep `Malformed request body`; no body echo/parser class | Existing `GlobalExceptionHandlerTest` |
| Gateway explicit `GatewayException("messages must be a non-empty array.", ...)` | 400 | `OpenAiErrorResponse` | `invalid_request` | Preserve explicit safe gateway validation message | `OpenAiChatCompletionsControllerTest`, `GlobalExceptionHandlerTest` |
| `/v1/*` raw `IllegalArgumentException` reaches handler | 400 | `OpenAiErrorResponse` | `invalid_request` | Safe generic message; no admin envelope | New/updated `GlobalExceptionHandlerTest` |
| Unexpected runtime exception | 500 | `ApiResponse` | `INTERNAL_ERROR` | `Internal server error`; no stack trace | Existing `GlobalExceptionHandlerTest` |
| Logging of unsafe raw `IllegalArgumentException` | n/a | logs only | n/a | Log safe metadata and exception class; avoid raw message when it may carry sensitive text | logging/spec-aligned test where feasible |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Request-boundary validation errors are represented as `BusinessException` or `GatewayException`, response bodies remain helpful and safe, and tests prove raw `IllegalArgumentException` cannot leak sensitive message text by default. |
| Base | Existing safe Admin validation responses like `name is required`, invalid status filter, malformed body, cross-user `Access denied`, and gateway invalid chat payload keep their current shapes and codes. |
| Bad | A service throws `IllegalArgumentException` containing decrypt/provider/path/SQL/raw input details and the response body or sensitive log event contains that raw message. |

## Likely Implementation Approach

The implementation agent should validate this approach against current code before editing:

1. Introduce or reuse a central exception-response policy in `common.exception`, for example a small safe-message mapper/helper used by `GlobalExceptionHandler`.
2. Treat raw `IllegalArgumentException` as unsafe by default at the global handler.
3. Convert controller-owned validation to explicit `BusinessException` or `GatewayException` with known-safe messages before service calls where useful.
4. Remove controller wrappers that simply rethrow `BusinessException("INVALID_REQUEST", e.getMessage())` when they create a second unsafe source of truth.
5. Keep service-level `IllegalArgumentException` for internal invariants where unit tests assert internal behavior, but do not expose those messages automatically at HTTP boundaries.
6. Update specs after implementation choices are final.

## Files Likely To Modify

Expected production/spec files:

- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/security/rag-security.md`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`
- Optional new helper in `backend/src/main/java/com/sangui/raggateway/common/exception/`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`
- Possibly `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- Possibly service/controller call sites discovered by `rg "IllegalArgumentException|e.getMessage()"`.

Expected test files:

- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java` if app/API-key validation behavior is touched.
- Representative service tests only if service exception taxonomy changes.

## Required Tests

Run from `backend/` unless noted:

```bash
mvn -q "-Dtest=GlobalExceptionHandlerTest" test
mvn -q "-Dtest=ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest,AppAdminControllerTest,ApiKeyAdminControllerTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q -DskipTests compile
```

If implementation touches shared exception classes or broad controller behavior, also run:

```bash
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q test
```

Repository-level hygiene:

```bash
git diff --check
```

Backend unit test commands should keep the 60 second timeout policy where feasible. If full `mvn -q test` times out, report that as a validation limit and keep targeted passing evidence.

## Acceptance Criteria

- [ ] Spec states that raw `IllegalArgumentException` messages are unsafe by default at HTTP boundaries.
- [ ] Spec distinguishes explicit safe validation exceptions from internal invariant exceptions.
- [ ] Global handler no longer returns raw `IllegalArgumentException#getMessage()` as the default response message.
- [ ] `/v1/*` raw `IllegalArgumentException` cannot accidentally return Admin envelope or raw details.
- [ ] Admin raw `IllegalArgumentException` cannot leak sensitive/internal message text.
- [ ] Existing safe Admin validation messages remain available through explicit `BusinessException`.
- [ ] Existing safe Gateway validation messages remain available through explicit `GatewayException`.
- [ ] Controller wrappers do not create duplicate message-safety rules.
- [ ] Tests assert both preservation of safe explicit messages and redaction/generic mapping of unsafe raw messages.
- [ ] No DB/frontend/infra/API-field changes are introduced.
- [ ] Targeted backend tests, compile, and `git diff --check` pass or any limitations are documented.

## Planning Self-Check

- Acceptance criteria are explicit.
- Forbidden scope is explicit.
- Expected modified files are listed.
- Required tests are listed.
- Concrete backend/gateway/security/guides specs were read before handoff.
- No open user clarification is required before implementation.
- No API, DB, frontend type, or DTO field alignment is expected because the task changes error mapping only.
