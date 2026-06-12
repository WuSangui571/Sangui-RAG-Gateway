# API Key Lifecycle Enable

## Goal

Make disabled app API keys recoverable while keeping revoked keys terminal.

Admins must be able to temporarily disable an API key, later enable it again, and still permanently revoke leaked or retired keys. The backend, frontend, tests, and specs must clearly distinguish reversible `DISABLED` from terminal `REVOKED`.

## Scope Classification

- Type: Complex Task
- Reason: Cross-layer lifecycle contract touching backend state machine, Admin API, frontend API client/types/page behavior, auth/readiness semantics, tests, and spec updates.
- Codex phase: planning and handoff only. Do not implement business code in this phase.

## Product Rationale

Current API key management makes disabling too close to permanent revocation from an operator workflow perspective. That increases the cost of admin mistakes and makes later API key detection, readiness checks, and smoke entry semantics less precise.

This task keeps the gateway lightweight and operational: it does not add rotation automation, key detection, request-log output capture, new auth infrastructure, or schema expansion unless existing code proves it is required.

## Current Baseline Found

- Branch: `feature/api-key-lifecycle-enable`.
- `rag_api_key.status` already exists and supports `ACTIVE`, `DISABLED`, `EXPIRED`, `REVOKED`.
- `rag_api_key.revoked_at` already exists.
- Backend has `ApiKeyService.disable(...)` and `ApiKeyService.revoke(...)`.
- Backend has `ApiKeyAdminController` endpoints:
  - `POST /api/admin/api-keys/{id}/disable`
  - `POST /api/admin/api-keys/{id}/revoke`
- Frontend API key page currently supports Create, Disable, Revoke only.
- App readiness treats "all keys disabled, revoked, or expired" as `DISABLED` and tells admins to create a new key; this message should reflect that disabled keys can now be enabled.

## Explicit Non-Goals

- Do not change plaintext key generation, hashing, prefixes, or one-time display semantics.
- Do not return plaintext API keys outside create response.
- Do not expose `key_hash`, Authorization headers, full key values, or secrets in responses/logs/spec examples.
- Do not add the API key detection/check button.
- Do not move smoke test UX into deeper pages.
- Do not add request-log output/body persistence.
- Do not change `/v1/*` public error shape.
- Do not change DB schema unless implementation finds the current `status` and `revoked_at` columns are unusable. Current research indicates no migration is needed.
- Do not introduce a second source of truth for lifecycle rules in frontend-only logic. Frontend may mirror display rules only.

## API Contract

### New Admin Action

```http
POST /api/admin/api-keys/{id}/enable
X-Admin-User-Id: <positive long>
Content-Type: application/json
```

Request body: none.

Success response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 10,
    "app_id": 1,
    "user_id": 100,
    "name": "client-key",
    "key_prefix": "sk-sangui-abc123",
    "status": "ACTIVE",
    "expires_at": null,
    "last_used_at": null,
    "revoked_at": null,
    "created_at": "...",
    "updated_at": "..."
  }
}
```

Response must not contain:

```text
key, key_hash, api_key, authorization, upstream_api_key, api_key_encrypted, stack_trace
```

### Existing Admin Actions To Preserve

```http
POST /api/admin/api-keys/{id}/disable
POST /api/admin/api-keys/{id}/revoke
```

Disable remains reversible. Revoke remains terminal.

### Public Gateway Auth Contract

For `/v1/*`, all invalid API key states remain indistinguishable to callers:

```json
{
  "error": {
    "message": "Invalid API key.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Disabled, revoked, expired, missing, malformed, cross-app, or disabled-app keys must not leak their reason to public callers.

## Backend State Machine

Lifecycle statuses:

```text
ACTIVE
DISABLED
EXPIRED
REVOKED
```

Required transition matrix:

| Action | From | To | HTTP/Admin result | Notes |
|---|---|---|---|---|
| create | n/a | ACTIVE | 200 OK | Full plaintext key returned once only. |
| disable | ACTIVE | DISABLED | 200 OK | Reversible stop. |
| disable | DISABLED | DISABLED | 200 OK | Existing idempotent behavior may remain. |
| disable | REVOKED | REVOKED | 400 INVALID_REQUEST | Revoked is terminal. |
| disable | EXPIRED | EXPIRED | 400 INVALID_REQUEST | Avoid converting expired stored state into disabled. |
| enable | DISABLED | ACTIVE | 200 OK | New behavior. |
| enable | ACTIVE | ACTIVE | 400 INVALID_REQUEST | Only `DISABLED -> ACTIVE` is allowed. |
| enable | REVOKED | REVOKED | 400 INVALID_REQUEST | Terminal; never clear `revoked_at`. |
| enable | EXPIRED | EXPIRED | 400 INVALID_REQUEST | Expired keys should not be silently revived. |
| revoke | ACTIVE | REVOKED | 200 OK | Set `revoked_at` if not already set. |
| revoke | DISABLED | REVOKED | 200 OK | Terminalize a disabled leaked key. |
| revoke | REVOKED | REVOKED | 200 OK | Existing idempotent behavior may remain. |
| revoke | EXPIRED | REVOKED | 200 OK | Expired keys may be terminalized. |

`ApiKeyService.isValid(...)` remains strict:

```text
valid == status ACTIVE and not past expires_at
```

`updated_at` must change on every successful lifecycle mutation. `last_used_at`, `key_hash`, and `key_prefix` must not change during disable, enable, or revoke.

## Validation / Error Matrix

| Scenario | Endpoint | HTTP | Code | Assertion |
|---|---|---:|---|---|
| Missing `X-Admin-User-Id` | enable/disable/revoke | 400 | INVALID_REQUEST | No service mutation. |
| Non-numeric `X-Admin-User-Id` | enable/disable/revoke | 400 | INVALID_REQUEST | No service mutation. |
| Non-positive `X-Admin-User-Id` | enable/disable/revoke | 400 | INVALID_REQUEST | No service mutation. |
| Key id missing | enable/disable/revoke | 404 | NOT_FOUND | No status leak. |
| Key id belongs to another user | enable/disable/revoke | 403 | FORBIDDEN | Message is generic `Access denied`. |
| Enable disabled key | enable | 200 | OK | Response status ACTIVE, no secret fields. |
| Enable active key | enable | 400 | INVALID_REQUEST | No mutation. |
| Enable revoked key | enable | 400 | INVALID_REQUEST | No mutation; `revoked_at` remains. |
| Enable expired stored status | enable | 400 | INVALID_REQUEST | No mutation. |
| Disable active key | disable | 200 | OK | Response status DISABLED, no secret fields. |
| Disable disabled key | disable | 200 | OK | Idempotent response status DISABLED. |
| Disable revoked key | disable | 400 | INVALID_REQUEST | No mutation. |
| Disable expired stored status | disable | 400 | INVALID_REQUEST | No mutation. |
| Revoke active key | revoke | 200 | OK | Response status REVOKED and `revoked_at` set. |
| Revoke disabled key | revoke | 200 | OK | Response status REVOKED and `revoked_at` set. |
| Revoke revoked key | revoke | 200 | OK | Idempotent; do not overwrite existing `revoked_at` unless current code already does not. |
| Revoke expired stored status | revoke | 200 | OK | Terminalize with `revoked_at`. |
| `/v1/*` with disabled key | gateway auth | 401 | invalid_api_key | OpenAI-compatible shape only. |
| `/v1/*` after enabling same key | gateway auth | success path | n/a | Auth proceeds when app is enabled and key not expired. |
| `/v1/*` with revoked key | gateway auth | 401 | invalid_api_key | Still rejected after attempted enable. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Admin disables an active key, public `/v1/*` calls fail with 401 `invalid_api_key`; admin enables the same disabled key, public `/v1/*` auth succeeds again when app is enabled and key not expired; admin revokes the key, public auth fails permanently. |
| Base | Existing create/list/revoke flows keep their response shapes and secret safety. App readiness reports missing active key but hints that disabled keys can be enabled or a new key can be created. |
| Bad | Revoked key can be enabled, `revoked_at` is cleared on a revoked row, frontend shows Enable for revoked keys, public auth reveals disabled vs revoked reason, or backend returns `key`/`key_hash` from lifecycle actions. |

## Frontend Requirements

API client:

- Add `enableApiKey(id, adminUserId)` in `frontend/src/api/api-keys.ts`.
- Keep typed response as `ApiResponse<ApiKeyVO>`.
- Do not add plaintext key fields to normal lifecycle responses.

Types:

- Keep `ApiKeyStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'REVOKED'`.
- No new status enum is required.

API key page:

- ACTIVE row: show `Disable` and `Revoke`.
- DISABLED row: show `Enable` or `Restore` and `Revoke`.
- REVOKED row: show no lifecycle action.
- EXPIRED row: show `Revoke` only; do not show Enable.
- Disable copy must read as reversible, warning-level.
- Revoke copy must read as irreversible/terminal, danger-level.
- Enable copy should be non-danger and make clear the key will authenticate again if the app is enabled and the key is not expired.
- After successful enable/disable/revoke, refresh the list.
- Full key one-time state must remain unchanged and must still be cleared on dialog/app changes.

Dictionary/i18n:

- Add both `zh-CN` and `en-US` keys to preserve compile-time dictionary parity.
- Keep enum/status contract keys untranslated at the API/type layer.

Button visibility test target:

- Prefer adding a focused frontend test that mocks API key list data and asserts action visibility for ACTIVE/DISABLED/REVOKED/EXPIRED rows.
- If adding a new frontend test runner is too much for this task, use the existing Playwright setup with mocked network responses and keep it backend-independent.

## Readiness Requirement

Backend readiness should continue to require at least one valid active key.

When there are keys but none are valid, the `active_api_key` check may still return `DISABLED`, but the message must not say the only remedy is creating a new key. It should mention enabling a disabled key or creating a new active key. Do not expose key prefixes or counts beyond existing safe metadata unless tests already cover them.

## Spec Updates Required

Update at least:

- `.trellis/spec/backend/error-handling.md`

Recommended additional target if needed:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/frontend/type-safety.md`

Spec update must record:

- New `POST /api/admin/api-keys/{id}/enable` endpoint.
- `DISABLED` is reversible.
- `REVOKED` is terminal.
- `enable` allows only `DISABLED -> ACTIVE`.
- Gateway auth continues returning generic 401 `invalid_api_key` for disabled/revoked/expired states.
- Lifecycle responses never return plaintext keys or key hashes.

## Files Likely To Modify

Backend:

- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`

Frontend:

- `frontend/src/api/api-keys.ts`
- `frontend/src/types/api-key.ts` only if helper types are needed; current status union already matches.
- `frontend/src/pages/api-keys/ApiKeyPage.tsx`
- `frontend/src/app/i18n/dict.ts`
- Optional focused test under `frontend/tests/...` using existing Playwright setup.

Spec:

- `.trellis/spec/backend/error-handling.md`
- Optional additional spec files listed above if implementation reveals a better home for lifecycle contract.

## Required Tests And Assertion Points

Backend targeted tests:

```bash
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest,GatewayAuthFilterTest,AppServiceTest" test
```

Assertion points:

- `ApiKeyServiceTest` covers `DISABLED -> ACTIVE`, active/revoked/expired enable rejection, disable/revoke existing behavior, `updated_at`, and no key hash/prefix mutation.
- `ApiKeyAdminControllerTest` covers enable endpoint 200/400/403/404 and secret-field absence.
- `GatewayAuthFilterTest` covers disabled key 401, enabled key success path, revoked key still 401.
- `AppServiceTest` covers readiness messaging/status with disabled-only keys after enable semantics.

Backend compile:

```bash
cd backend
mvn -q -DskipTests compile
```

Frontend checks:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Frontend action visibility:

```bash
cd frontend
cmd /c npm run test:visual
```

Use `test:visual` only if the implementation adds or updates a Playwright test for API key lifecycle actions; otherwise document why frontend action visibility could not be automatically asserted and provide the exact manual check.

Full regression before finish-work:

```bash
cd backend
mvn test
cd ../frontend
cmd /c npm run typecheck
cmd /c npm run build
```

## Implementation Notes For DeepSeek

- Keep the lifecycle rule in `ApiKeyService` as the backend source of truth.
- Controller should keep the existing 404/403 ownership pre-check style unless refactoring it removes duplication without changing behavior.
- Do not rely on frontend button hiding for security; backend must enforce illegal transitions.
- Preserve admin `ApiResponse` shape for `/api/admin/**`.
- Preserve OpenAI-compatible shape for `/v1/*`.
- Avoid broad catch-all or silent fallback behavior. Illegal transitions should fail visibly with `INVALID_REQUEST`.
- Search all references to `disable`, `revoke`, `ApiKeyStatus`, and `/api/admin/api-keys` before editing.

## Planning Self-Check

- Acceptance criteria: defined in state machine, error matrix, and Good/Base/Bad cases.
- Forbidden scope: defined in Explicit Non-Goals.
- Expected files: listed above.
- Required tests: listed above.
- Specific guidelines read: backend, frontend, security, gateway, cross-layer, code-reuse.
- Open questions: none requiring user confirmation.
- API/DB/frontend alignment: DB fields already exist; API enable endpoint is new; frontend status union already exists.
