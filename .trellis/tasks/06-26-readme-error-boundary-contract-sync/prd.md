# README Error Boundary Contract Sync

## Goal

Synchronize `README.md` with the current backend error-boundary, gateway, admin-auth, security, deployment, and validation contracts after the IllegalArgumentException error-safety work.

This is a documentation-contract task. It should make README accurate for deployment, demos, and handoff without changing runtime behavior.

## Classification

Complex Task.

Reason: the expected implementation is documentation-only, but the content crosses public `/v1/*` API behavior, Admin API envelopes, auth identity source, error response safety, request-log evidence boundaries, deployment commands, and validation command contracts.

## Current Project State

- Current branch is `feature/readme-error-boundary-sync`, a non-`main` task branch.
- Working tree was clean at task start.
- Previous recorded journal session completed IllegalArgumentException error safety in commit `e8f601b4`.
- Current error contract: raw `IllegalArgumentException` messages are unsafe by default at HTTP boundaries and must not be returned or logged by the global HTTP handler.
- Safe client-facing validation messages must be carried by `BusinessException` for Admin/common APIs or `GatewayException` for public `/v1/*` gateway APIs.

## Requirements

- Review `README.md` for stale or incomplete text around:
  - OpenAI-compatible `/v1/models` and `/v1/chat/completions`.
  - Admin API auth and `ApiResponse<T>` envelope.
  - Error response shape and error boundary rules.
  - IllegalArgumentException, BusinessException, and GatewayException behavior.
  - Request-log and runtime evidence safe/forbidden fields.
  - Smoke, targeted test, compile, build, Docker Compose, and diff-check commands.
  - Deployment and local runtime guidance.
- Align README with these sources of truth:
  - `.trellis/spec/backend/error-handling.md`
  - `.trellis/spec/gateway/resilience.md`
  - `.trellis/spec/security/rag-security.md`
  - `.trellis/spec/backend/logging-guidelines.md`
  - `.trellis/spec/backend/quality-guidelines.md`
  - `.trellis/spec/frontend/type-safety.md`
  - `.trellis/spec/frontend/quality-guidelines.md`
  - `.trellis/spec/guides/cross-layer-thinking-guide.md`
  - `.trellis/spec/sangui-rag-gateway.md`
- Replace stale Admin API identity guidance that still refers to `X-Admin-User-Id` with the current `Authorization: Bearer <admin-jwt>` contract.
- Keep README explicit that `/v1/*` uses OpenAI-compatible success/error shapes and must not be wrapped in `ApiResponse`.
- Keep README explicit that Admin APIs use `ApiResponse<T>` with `code`, `message`, and `data`.
- Document the IllegalArgumentException safety rule in README at the user/operator-facing level:
  - raw `IllegalArgumentException#getMessage()` is not client-safe by default;
  - Admin/common raw IAE returns `INVALID_REQUEST` with generic `Invalid request`;
  - `/v1/*` raw IAE returns OpenAI-compatible `invalid_request` with generic `Invalid request.`;
  - safe messages belong in `BusinessException` or `GatewayException`.
- Update validation commands so README does not imply an outdated or too-narrow targeted test set.
- Preserve README's existing metadata-only runtime evidence posture.
- Do a documentation-level security scan after editing.

## Non-Goals

- Do not modify backend runtime implementation files.
- Do not modify frontend implementation files.
- Do not change database schema, migrations, DTO/VO fields, API paths, env keys, Docker Compose behavior, test code, scripts, or CI workflows unless the user explicitly reopens scope.
- Do not introduce new behavior, fallback behavior, mock success paths, or silent error handling.
- Do not create or commit real `.env`, API keys, upstream provider secrets, generated app API keys, provider response bodies, prompt/message bodies, chunk content, uploaded files, stack traces, or local absolute path examples.
- Do not run `$record-session`, commit, push, archive, or finish the task in this Codex planning pass.

## API / Command / Payload Fields

### Public Gateway API

```http
GET /v1/models
Authorization: Bearer sk-sangui-...
```

Success: OpenAI-compatible model list. Error: OpenAI-compatible `{"error": {...}}`.

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Supported MVP request fields:

```text
model
messages
temperature
max_tokens
top_p
stream
```

Unsupported APIs/features must not be advertised as implemented:

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

### Admin API

```http
Authorization: Bearer <admin-jwt>
```

Admin APIs return:

```json
{
  "code": "OK",
  "message": "OK",
  "data": {}
}
```

Admin login remains the source of the admin JWT:

```http
POST /api/admin/auth/login
Content-Type: application/json

{
  "username": "<admin-username>",
  "password": "<admin-password>"
}
```

README must not present `X-Admin-User-Id` as the active Admin API identity contract.

### Validation Commands To Document

Primary documentation validation:

```bash
git diff --check
```

Backend command sanity checks when README command text changes:

```bash
cd backend
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest,AppAdminControllerTest,ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,GatewayAuthFilterTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
mvn -q -DskipTests compile
```

Optional broader checks if implementation changes unexpectedly:

```bash
cd backend
mvn -q test
```

Docker/Compose documentation sanity checks when deployment text changes:

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

Frontend command references must remain Windows-compatible:

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

## Validation / Error Matrix

| Boundary | Scenario | Expected README Contract |
|---|---|---|
| `/v1/*` auth | Missing, invalid, disabled, revoked, or expired app API key | `401` OpenAI-compatible error with `error.code=invalid_api_key`; no Admin envelope; no key/status leak. |
| `/v1/chat/completions` payload | Malformed JSON, null body, missing/empty messages, missing role/content, unsupported role | `400` OpenAI-compatible error with `error.code=invalid_request`; invalid payloads do not consume rate quota. |
| `/v1/*` raw IAE | Raw `IllegalArgumentException` escapes to HTTP handler | `400` OpenAI-compatible `invalid_request` with generic `Invalid request.`; raw message not exposed. |
| Admin raw IAE | Raw `IllegalArgumentException` escapes to HTTP handler outside `/v1/*` | `400` Admin `ApiResponse` with `code=INVALID_REQUEST`, generic `Invalid request`; raw message not exposed. |
| Admin validation | Known safe operator validation failure | `BusinessException` carries safe `code` and `message` in Admin `ApiResponse`. |
| Gateway validation/upstream | Known safe gateway failure | `GatewayException` carries safe OpenAI-compatible `message`, `type`, `code`, and HTTP status. |
| Gateway upstream timeout | Upstream chat timeout | `504` OpenAI-compatible `upstream_timeout`; no provider body. |
| Gateway upstream error | Provider non-2xx, network failure, malformed upstream success body | `502` OpenAI-compatible `upstream_error`; no provider body or stack trace. |
| Request-log insert failure | Persistence fails after gateway request reaches logging boundary | Gateway response unchanged; stable safe log event `request_log.persist_failed`; no exception message, stack trace, command fields, prompt, chunks, or keys. |
| Admin APIs | Missing/non-Bearer/invalid/expired admin JWT | `401 UNAUTHORIZED` Admin `ApiResponse`; no controller mutation. |
| Admin cross-user access | Authenticated user guesses another user's resource | `403 FORBIDDEN` with generic `Access denied` where applicable. |
| Runtime evidence | README examples and evidence checklist | Only safe metadata; no raw answers, raw SSE payloads, prompts, messages, provider bodies, keys, hashes, embeddings, chunk content, stack traces, or storage paths. |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | README accurately states the current `/v1/*` OpenAI-compatible subset, Admin JWT + `ApiResponse` envelope, IAE safety rule, BusinessException/GatewayException safe-message boundary, request-log persistence-failure observability, runtime evidence safe/forbidden fields, and up-to-date validation commands. `git diff --check` passes and no secrets are introduced. |
| Base | README changes are documentation-only. Backend compile or targeted test sanity can be run to prove documented commands are valid; if a broad full test is skipped due to time, README still lists the correct targeted commands and final report states the limitation. |
| Bad | README still tells users to authenticate Admin APIs with `X-Admin-User-Id`, implies `/v1/*` errors use Admin `ApiResponse`, exposes raw IllegalArgumentException messages as acceptable, advertises unsupported OpenAI APIs, includes real-looking secrets/endpoints beyond placeholders, weakens safe evidence rules, or documents a stale targeted-test set. |

## Files Likely To Modify

- `README.md`: expected only business-facing file change.

## Files To Read / Cross-Check

- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/BusinessException.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GatewayException.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/AdminAuthFilter.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
- `scripts/demo-smoke.ps1`
- `docs/runtime-evidence-checklist.md`
- `.env.example`
- `deploy/docker-compose.yml`

## Required Tests And Assertion Points

- `git diff --check`
  - Assertion: no whitespace errors in README/Trellis changes.
- Documentation security scan with `rg`
  - Assertion: no newly committed real-looking keys/secrets, provider body examples, raw prompt/message/chunk content examples, stack traces, `storage_path`, or internal absolute local paths in README diff.
- Targeted backend tests if README validation commands are changed or if implementer wants command sanity evidence:
  - `mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test`
    - Assertion: Admin and `/v1/*` error shapes and raw IAE sanitization remain as documented.
  - `mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,GatewayAuthFilterTest,OpenAiModelsControllerTest" test`
    - Assertion: gateway error mapping and OpenAI-compatible shapes remain as documented.
  - `mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest,AppAdminControllerTest,ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest" test`
    - Assertion: Admin validation uses safe envelope and BusinessException-visible messages where expected.
  - `mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test`
    - Assertion: README streaming/runtime smoke guidance remains tied to the current RANDOM_PORT runtime smoke.
  - `mvn -q -DskipTests compile`
    - Assertion: backend command in README remains valid.
- Compose config sanity when deployment/run docs are touched:
  - `docker compose --env-file .env.example -f deploy/docker-compose.yml config`
    - Assertion: documented default Compose contract still renders.

## Planning Self-Check

- Acceptance criteria are explicit in Good/Base/Bad cases and the validation matrix.
- Forbidden scope is explicit: README/Trellis docs only; no business implementation changes in this planning pass.
- Expected modified file is explicit: `README.md`.
- Required tests and assertion points are listed.
- Concrete guideline files, not only spec indexes, must be read before implementation.
- No unresolved API/DB/frontend DTO mismatch is expected because the task should not change runtime contracts; README must align to existing contracts.
- No user clarification is currently required because the requested scope is narrow and documentation-only.
