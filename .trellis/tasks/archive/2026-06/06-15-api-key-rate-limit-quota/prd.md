# API Key Rate Limit And Quota

## Goal

Add API-key scoped runtime cost protection for `POST /v1/chat/completions`.

The gateway already authenticates app API keys and admin auth is now JWT-backed. The remaining exposed cost risk is that a valid app API key can call the public gateway without per-key request and token limits. This task adds a first runtime guardrail using Redis atomic counters while preserving OpenAI-compatible public API behavior and the existing secret-safe logging boundary.

## Task Classification

Complex Task.

Reasons:

- Crosses gateway auth/context, chat completion flow, Redis integration, database fields or policy config, public OpenAI-compatible error behavior, request logs, security logging, specs, and tests.
- Touches runtime cost-control invariants for externally exposed `/v1/chat/completions`.
- Requires code-spec updates and a focused check pass.

## Collaboration Boundary

Codex planning session boundary:

- Codex may create/update this Trellis task, PRD, and task context files.
- Codex must not edit business implementation files in this planning session.
- DeepSeek will implement the code after this PRD/context handoff.
- Codex will later run check / finish-work after DeepSeek completes implementation.

## Product Scope

Required V1 scope:

- Enforce API key scoped limits on `POST /v1/chat/completions`.
- Use Redis for atomic counters. Prefer Lua script for multi-counter check/increment; if not using Lua, the implementation must document why the command sequence remains atomic enough.
- Return OpenAI-compatible `429 rate_limit_exceeded` when a limit is hit.
- Log rate-limit hits with safe metadata only: `request_id`, `user_id`, `app_id`, `api_key_id`, `limit_type`, `window`.
- Do not log prompt content, answer content, provider keys, Authorization headers, plaintext app keys, key hashes, full request bodies, chunk content, provider raw bodies, or stack traces in client responses.
- Add focused backend tests and update backend/security/gateway specs.

Default out-of-scope for this task:

- Frontend configuration UI is not required for V1 unless the implementer deliberately chooses the full productized path and keeps it small.
- No admin analytics dashboard, billing, provider fallback, retry policy, distributed lock framework, or new metrics platform.
- No changes to RAG retrieval behavior, prompt construction, document ingestion, upstream provider routing, or streaming output capture.
- Do not make `/api/admin/**` accept app API keys or `/v1/*` accept admin JWTs.

## Proposed Architecture

Keep gateway authentication and cost enforcement as separate responsibilities:

1. `GatewayAuthFilter` continues to extract and validate app API keys, then sets `GatewayRequestContext`.
2. `OpenAiChatCompletionsController` generates `request_id` and validates/deserializes the request as today.
3. Before any embedding/retrieval/upstream chat call, a new API-key limit service evaluates the parsed chat request and `GatewayRequestContext`.
4. The limit service uses Redis atomic counters keyed by `api_key_id`, not raw key, key prefix, or key hash.
5. If allowed, the normal chat flow continues.
6. If rejected, the controller/service raises or returns a `GatewayException` with HTTP 429, type `rate_limit_error`, code `rate_limit_exceeded`, and a safe message.
7. Request-log persistence should record an authenticated rejected request as `failure` with `error_code=rate_limit_exceeded` where the request reaches the controller logging boundary.

Token limits should use a clear policy:

- Request count limits (`requests per minute`, `requests per day`) are checked before upstream calls and count accepted gateway attempts.
- Token limits (`tokens per minute`, `daily token quota`) should use a bounded preflight reservation based on request messages and `max_tokens`, then reconcile with upstream `usage.total_tokens` when available.
- If upstream usage is unavailable, keep the preflight reservation rather than silently treating usage as zero.
- If the request is rejected by validation before the limiter runs, it should not consume quota.
- If upstream fails after reservation, release or reconcile token reservation explicitly. Do not silently lose large token reservations unless documented in tests.

## Configuration And Data Contract

The implementation may choose either of these two bounded approaches. Prefer A if feasible.

### Option A: Persisted Per-Key Fields Plus Explicit Defaults

Add migration `V13__add_api_key_rate_limit_quota.sql` to `rag_api_key`:

| Column | Type | Required | Meaning |
|---|---|---:|---|
| `requests_per_minute` | `INTEGER` | no | Null means use configured default; positive value overrides default. |
| `tokens_per_minute` | `INTEGER` | no | Null means use configured default; positive value overrides default. |
| `daily_request_quota` | `INTEGER` | no | Null means use configured default; positive value overrides default. |
| `daily_token_quota` | `INTEGER` | no | Null means use configured default; positive value overrides default. |

Add explicit backend properties under `rag.gateway.api-key-limits`:

```yaml
rag:
  gateway:
    api-key-limits:
      enabled: true
      default-requests-per-minute: 60
      default-tokens-per-minute: 60000
      default-daily-request-quota: 1000
      default-daily-token-quota: 1000000
      default-completion-token-reservation: 1024
```

Rules:

- `enabled=false` may disable enforcement for local emergency/debug use, but the default must be enabled.
- A configured limit value `<= 0` is invalid and must fail startup or service validation visibly.
- Existing keys with null columns still receive explicit configured defaults at runtime.
- Admin create/list VO may expose these numeric fields if productizing configuration; otherwise keep this as backend-only runtime policy and document SQL/manual seed behavior in spec.

### Option B: Global Strategy Entry Only

Use only `rag.gateway.api-key-limits.*` properties for all keys in V1, with no DB migration.

This option is acceptable only if the PRD is updated to explain why per-key configuration is deferred. It still must enforce by `api_key_id` and must update specs.

## Public API Contract

Endpoint affected:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

No request payload fields are added to the OpenAI-compatible public endpoint.

When a limit is hit:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
```

```json
{
  "error": {
    "message": "Rate limit exceeded for this API key.",
    "type": "rate_limit_error",
    "code": "rate_limit_exceeded"
  }
}
```

Recommended response headers:

| Header | Required | Meaning |
|---|---:|---|
| `Retry-After` | yes for minute-window limits | Seconds until the relevant minute window resets. |
| `X-RateLimit-Limit-Requests` | optional | Effective RPM limit. |
| `X-RateLimit-Remaining-Requests` | optional | Remaining request count when safe to expose. |
| `X-RateLimit-Reset-Requests` | optional | Unix seconds or ISO reset time. |

Headers must not expose app key plaintext, key hash, internal Redis keys, prompts, or provider details.

Admin API contract if productized:

```http
POST /api/admin/apps/{appId}/api-keys
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "name": "prod-client",
  "expires_at": "2026-12-31T23:59:59",
  "requests_per_minute": 60,
  "tokens_per_minute": 60000,
  "daily_request_quota": 1000,
  "daily_token_quota": 1000000
}
```

Normal `ApiKeyVO` may include:

```json
{
  "requests_per_minute": 60,
  "tokens_per_minute": 60000,
  "daily_request_quota": 1000,
  "daily_token_quota": 1000000
}
```

Do not return `key_hash` or plaintext key except the existing one-time `key` creation field.

## Redis Counter Contract

Redis keys must use safe IDs only:

```text
rag:api-key-limit:{apiKeyId}:rpm:{yyyyMMddHHmm}
rag:api-key-limit:{apiKeyId}:tpm:{yyyyMMddHHmm}
rag:api-key-limit:{apiKeyId}:daily-requests:{yyyyMMdd}
rag:api-key-limit:{apiKeyId}:daily-tokens:{yyyyMMdd}
```

Rules:

- Do not include raw API key, key prefix, key hash, request text, model prompt, or provider key in Redis keys or values.
- Minute keys expire slightly after the minute window, e.g. 120 seconds.
- Daily keys expire after the daily window plus a small buffer, e.g. 2 days.
- Lua script should check all relevant counters and increment/reserve only when every limit can pass.
- On rejection, no counters should be incremented for that rejected attempt.
- The script result should identify `allowed`, `limit_type`, remaining values, and reset seconds for safe error/log handling.

## Validation And Error Matrix

| Scenario | HTTP | Error code | Counter behavior | Assertion point |
|---|---:|---|---|---|
| Missing/invalid API key | 401 | `invalid_api_key` | No limiter call | `GatewayAuthFilterTest` |
| Valid key under all limits | 200 or normal downstream error | n/a | Counters/reservation increment atomically before upstream | limiter service test, controller test |
| RPM exceeded | 429 | `rate_limit_exceeded` | Rejected request does not increment counters | limiter service test, controller test |
| TPM exceeded | 429 | `rate_limit_exceeded` | Rejected request does not reserve tokens | limiter service test |
| Daily request quota exceeded | 429 | `rate_limit_exceeded` | Rejected request does not increment counters | limiter service test |
| Daily token quota exceeded | 429 | `rate_limit_exceeded` | Rejected request does not reserve tokens | limiter service test |
| Malformed JSON | 400 | `invalid_request` | No limiter call | controller/global handler test |
| Valid JSON but invalid messages | 400 | `invalid_request` | Prefer validation before limiter; no quota consumed | `ChatCompletionGatewayServiceTest` or controller test |
| Upstream success with usage | 200 | n/a | Token reservation reconciled to actual total_tokens | limiter service test |
| Upstream success without usage | 200 | n/a | Keep reserved token count | limiter service test |
| Upstream failure after reservation | 502/504 | `upstream_error` / `upstream_timeout` | Token reservation released or explicitly reconciled by documented rule | controller/limiter test |
| Redis unavailable | 500 or explicit gateway error | `internal_error` unless a stricter code is added to spec | Do not silently bypass limits | limiter service test |
| Enforcement disabled by config | Normal existing behavior | n/a | No counters touched; log a safe disabled state only if useful | property test |

## Good / Base / Bad Cases

Good:

- Active app API key calls `/v1/chat/completions` below all configured limits; gateway returns the existing OpenAI-compatible response, request log records success, Redis counters use only `api_key_id` keys.
- Rate-limited key receives HTTP 429 with OpenAI-compatible error shape and no admin envelope.
- Safe logs include `request_id`, `app_id`, `user_id`, `api_key_id`, `limit_type`, `window`, and omit all prompt/answer/key/provider raw content.

Base:

- Existing API keys without persisted per-key limit values are protected by explicit configured defaults.
- `GET /v1/models` remains unchanged unless the implementer deliberately expands enforcement and documents/tests it.
- Admin key create/list remains unchanged if UI/API productization is deferred.

Bad:

- A Redis outage silently allows unlimited traffic.
- Limit counters are keyed by plaintext API key, key hash, or key prefix.
- Rejected requests return admin `ApiResponse`, raw Java exception text, or provider/raw request content.
- TPM/daily token logic consumes zero quota when upstream usage is missing.
- Broad catch/fallback hides limiter failures or converts them into successful upstream calls.

## Relevant Specs To Update

Required spec updates:

- `.trellis/spec/backend/database-guidelines.md`: API key limit fields or global strategy contract, migration/index notes, validation cases.
- `.trellis/spec/backend/error-handling.md`: 429 `rate_limit_exceeded` behavior for `/v1/chat/completions`, request-log error mapping, optional headers.
- `.trellis/spec/backend/logging-guidelines.md`: safe rate-limit hit log fields and forbidden fields.
- `.trellis/spec/backend/quality-guidelines.md`: required limiter/auth/controller tests.
- `.trellis/spec/gateway/resilience.md`: rate-limit boundary as pre-upstream runtime guard, Redis failure behavior.
- `.trellis/spec/security/rag-security.md`: API-key cost guard, Redis key safety, no app/admin auth mixing.

Conditional spec updates if Admin API/frontend productization is included:

- `.trellis/spec/frontend/type-safety.md`: API key limit DTO/VO fields.
- `.trellis/spec/frontend/state-management.md`: limit form state is local/server state, no secrets.
- `.trellis/spec/frontend/quality-guidelines.md`: UI validation and tests for numeric bounds.

## Files Likely To Modify

Backend, required:

- `backend/src/main/resources/db/migration/V13__add_api_key_rate_limit_quota.sql` if Option A is chosen.
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyEntity.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayRequestContext.java` only if passing request/limit metadata requires it.
- New focused limiter classes, likely under `backend/src/main/java/com/sangui/raggateway/apikey/` or `backend/src/main/java/com/sangui/raggateway/gateway/ratelimit/`, e.g. `ApiKeyLimitProperties`, `ApiKeyLimitPolicy`, `ApiKeyRateLimitService`, `ApiKeyRateLimitResult`.
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java` only if more fields are needed; do not add sensitive fields.
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java` only if central 429/header handling is needed.

Backend, conditional Admin API productization:

- `backend/src/main/java/com/sangui/raggateway/apikey/dto/CreateApiKeyDTO.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyVO.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyCreateVO.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`

Frontend, conditional only:

- `frontend/src/types/api-key.ts`
- `frontend/src/api/api-keys.ts`
- API key management page/component files under `frontend/src/pages` or `frontend/src/components/domain`.
- `frontend/src/app/i18n/dict.ts` if adding UI labels.

Tests, required:

- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java`
- New limiter unit tests, e.g. `ApiKeyRateLimitServiceTest`.
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java` if request-log behavior changes.
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java` if handler behavior changes.

## Required Tests And Assertion Points

Minimum targeted backend tests:

```bash
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,GatewayAuthFilterTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

If Admin API productization is included:

```bash
cd backend
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,ApiKeyServiceTest" test
cd ../frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Before handoff back to Codex finish-work, run if feasible:

```bash
cd backend
mvn test
```

Assertion points:

- 429 response has `$.error.type=rate_limit_error`, `$.error.code=rate_limit_exceeded`, no `$.code`, `$.message`, `$.data`.
- Limit hits do not call upstream chat or embedding/retrieval work.
- Auth failures do not call limiter.
- Request-log failure row is recorded for authenticated limiter rejection with `error_code=rate_limit_exceeded`.
- Logs and response bodies do not contain raw API keys, key hashes, Authorization headers, prompts, answers, provider raw bodies, or stack traces.
- Redis/Lua service tests cover all four dimensions: RPM, TPM, daily requests, daily tokens.
- Existing invalid key, disabled/revoked/expired key, disabled app, model config not ready, upstream timeout/error tests still pass.

## Planning Self-Check

- Acceptance criteria are explicit above.
- Forbidden scope is listed under Product Scope and Collaboration Boundary.
- Expected files are listed under Files Likely To Modify.
- Required tests and assertion points are listed.
- Specific backend/frontend/gateway/security/guides guidelines were read during planning, not just indexes.
- No blocking requirement question remains for V1 if backend-only runtime guard is accepted.
- API/DB/frontend DTO alignment is defined as Option A required fields and conditional frontend/Admin API work.

