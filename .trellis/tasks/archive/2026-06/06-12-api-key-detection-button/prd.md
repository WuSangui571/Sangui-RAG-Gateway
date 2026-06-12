# API Key Detection Button

## Classification

Complex Task.

Reason: this touches backend Admin API, API key status semantics, frontend API-key table UX, frontend typed contracts, and spec updates. It does not require a database migration, RAG pipeline changes, upstream provider calls, deployment changes, or public `/v1/*` behavior changes.

## Goal

Add a safe "detect" action to the Admin API key management page so an admin can check whether an existing app API key record is currently usable for `/v1/*` gateway authentication, without re-entering or revealing the full plaintext key.

Detection answers the management question:

```text
If the caller still has the originally issued plaintext key, would this key record pass the gateway auth metadata checks right now?
```

It is not a public gateway diagnostic endpoint and must not expose detailed gateway auth failure reasons to `/v1/*` clients.

## Current Project State

- Current branch: `codex/api-key-detection`.
- Trellis context before this task: no active task and clean working tree.
- Recent journal state: V0.2 RC smoke/tag/release publication work is completed and archived. The canonical release is `v0.2.0-rc.1`; no backend/frontend implementation task is active.
- This task starts from the stabilized API key lifecycle semantics: `ACTIVE`, `DISABLED`, `EXPIRED`, `REVOKED`.

## Product Requirements

- Add an Admin-only API key detection endpoint.
- Add a "detect" button/action to each API key row in the Admin API key page.
- Do not ask the admin to input the full key again.
- Do not display, return, log, or persist plaintext API keys or key hashes.
- Keep public `/v1/*` authentication failures generic: invalid, disabled, expired, revoked, malformed, missing, and disabled-app keys still return OpenAI-compatible `401 invalid_api_key`.
- Make row-level UI results clear for `ACTIVE`, `DISABLED`, `EXPIRED`, and `REVOKED`.
- For `REVOKED`, the UI may disable the detect button and show terminal unusable state directly; backend should still be safe if the endpoint is called.
- For `DISABLED` and `EXPIRED`, detection should return/display not usable without implying the key text is recoverable or invalidating the row.
- Do not change API key generation, hashing, storage, revoke, disable, or last-used update behavior.

## Non-Goals / Forbidden Scope

- Do not add a DB migration.
- Do not change `/v1/models`, `/v1/chat/completions`, streaming, upstream forwarding, model config, retrieval, prompt construction, request-log persistence, demo smoke scripts, Docker, CI, or infra.
- Do not call `/v1/*` internally with stored key material; plaintext key is not available after creation.
- Do not add any fallback that marks a key usable when core metadata is inconsistent.
- Do not expose failure reason fields such as `failure_reason`, `auth_failure_detail`, raw exception messages, SQL errors, provider errors, or stack traces.
- Do not introduce a second source of truth for key validity; reuse or align with existing `ApiKeyService.isValid(...)` and `AppService.isEnabled(...)` semantics.
- Do not make frontend the source of truth for usability. Frontend may short-circuit `REVOKED` display, but backend endpoint remains authoritative for detection calls.

## Proposed Admin API Contract

Endpoint:

```http
POST /api/admin/api-keys/{id}/detect
X-Admin-User-Id: <admin-user-id>
```

Request body: none.

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "key_id": 1,
    "app_id": 10,
    "usable": true,
    "status": "ACTIVE",
    "app_enabled": true,
    "expires_at": null,
    "checked_at": "2026-06-12T15:00:00"
  }
}
```

Fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `key_id` | number | yes | Safe key metadata ID. |
| `app_id` | number | yes | Owning app ID. |
| `usable` | boolean | yes | `true` only when the key record is valid by gateway-auth metadata and the owning app is enabled. |
| `status` | `ACTIVE \| DISABLED \| EXPIRED \| REVOKED` | yes | Current key status. |
| `app_enabled` | boolean | yes | Whether the owning app is currently enabled. |
| `expires_at` | string or null | yes | Existing management-visible expiry timestamp. |
| `checked_at` | string | yes | Server-side check timestamp. |

Usability invariant:

```text
usable = ApiKeyService.isValid(apiKey) && AppService.isEnabled(app)
```

`ApiKeyService.isValid(apiKey)` must preserve the existing gateway-auth semantics for status and expiry. Detection must not update `last_used_at`, because no public gateway request was authenticated.

## Validation / Error Matrix

| Scenario | HTTP | Code / Shape | Expected behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` Admin envelope | Same admin identity handling as existing API key endpoints. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` Admin envelope | No key query should proceed. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` Admin envelope | Validate before service detection. |
| API key id does not exist | 404 | `NOT_FOUND` Admin envelope | Safe missing resource message. |
| API key id belongs to another user | 403 | `FORBIDDEN` Admin envelope | Generic `Access denied`; no detection metadata returned. |
| Key `ACTIVE`, not expired, app `ENABLED` | 200 | `OK` | `usable=true`, safe metadata returned. |
| Key `ACTIVE`, expired by `expires_at` | 200 | `OK` | `usable=false`, `status` should reflect persisted/effective expired semantics as implemented. If service currently computes expiry without mutating status, document the exact behavior in tests. |
| Key `DISABLED`, app `ENABLED` | 200 | `OK` | `usable=false`, no public-style `invalid_api_key` reason returned. |
| Key `REVOKED` | 200 | `OK` | `usable=false`; UI may also short-circuit this state. |
| Key `ACTIVE`, app `DISABLED` | 200 | `OK` | `usable=false`, `app_enabled=false`. |
| Orphaned key app reference | Prefer visible invariant failure | Do not silently fake success | Existing FK should prevent this. If code path can occur, fail safely without exposing internals. |

Public gateway auth matrix remains unchanged:

| `/v1/*` case | Required response |
|---|---|
| Missing, malformed, unknown, disabled, revoked, expired key, or disabled app | HTTP 401 OpenAI-compatible `{"error":{"message":"Invalid API key.","type":"invalid_request_error","code":"invalid_api_key"}}` |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Admin detects an `ACTIVE` non-expired key under an enabled app. Backend returns `usable=true` with only safe metadata. Frontend shows a row-level success result. No full key/hash appears in network response, logs, or UI state. |
| Base | Admin detects `DISABLED`, expired, `REVOKED`, or app-disabled key. Backend returns `usable=false` with status/app/expiry metadata. Frontend shows clear unusable status; `REVOKED` can be displayed as terminal without requiring a call. |
| Bad | Detection requires full plaintext key, updates `last_used_at`, changes public `/v1/*` error detail, exposes `key_hash` or plaintext key, logs Authorization/key material, or implements a separate inconsistent validity rule. |

## Backend Implementation Notes

Expected files to inspect/modify:

```text
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyEntity.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyStatus.java
backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyVO.java
backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyDetectionVO.java   (new if needed)
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java
```

Implementation constraints:

- Controller validates `X-Admin-User-Id` consistently with existing API key admin endpoints.
- Service enforces same-user ownership before returning metadata.
- Detection reuses existing key/app validity helpers where possible.
- Detection must not call `updateLastUsed(...)`.
- Detection response VO must not contain `key`, `key_hash`, plaintext, encrypted key material, Authorization, stack traces, provider bodies, prompts, or document fields.
- If adding a mapper query, it must stay tenant-scoped by `user_id`; prefer reusing existing admin lookup patterns.

## Frontend Implementation Notes

Expected files to inspect/modify:

```text
frontend/src/api/api-keys.ts
frontend/src/types/api-key.ts
frontend/src/pages/api-keys/ApiKeysPage.tsx
frontend/src/components/domain/... status/action component if the page already uses one
frontend/src/app/i18n/dict.ts if visible strings are dictionary-backed
```

UI requirements:

- Add a row-level detect action near existing disable/revoke actions.
- Use existing table/action/status patterns.
- Show loading state only for the row being detected.
- Show success/unusable/error feedback in-page without persisting it globally.
- Disable or replace detect action for `REVOKED` with terminal unusable state if consistent with existing row UX.
- Do not persist detection result in localStorage/sessionStorage.
- Do not store any generated plaintext key or key hash in detection state.
- Use explicit TypeScript types and the existing `ApiKeyStatus` union.

## Spec Updates Required During Implementation

Update these specs after implementation:

```text
.trellis/spec/backend/error-handling.md
.trellis/spec/frontend/component-guidelines.md
```

Backend spec update should record:

- Admin detection endpoint path and response fields.
- Detection uses Admin `ApiResponse`, not OpenAI-compatible public error shape.
- Public `/v1/*` invalid key behavior remains generic `401 invalid_api_key`.
- Detection returns only safe metadata and does not expose failure detail or secrets.

Frontend spec update should record:

- API key table includes a detect action.
- Row-level loading/result states are local UI state.
- `REVOKED` is terminal and may show disabled detection directly.
- Detection UI must not persist secrets or detection results globally.

## Required Tests and Assertion Points

Backend targeted tests:

```powershell
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest" test
mvn -q -DskipTests compile
```

Backend assertions:

- `ACTIVE` + unexpired + enabled app => `usable=true`.
- `DISABLED` => `usable=false`.
- `REVOKED` => `usable=false`.
- Expired key => `usable=false`.
- App disabled => `usable=false`, `app_enabled=false`.
- Missing/cross-user key id returns `404`/`403` using Admin envelope.
- Missing/non-numeric/non-positive admin header returns `400 INVALID_REQUEST`.
- Detection response omits `key`, `key_hash`, `authorization`, and any secret fields.
- Detection does not update `last_used_at`.
- Existing `GatewayAuthFilterTest` still proves public `/v1/*` invalid cases return generic `401 invalid_api_key`.

Frontend targeted tests/checks:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Frontend assertions:

- API response type includes `usable`, `status`, `app_enabled`, `expires_at`, `checked_at`.
- Detect button/action is visible for states where detection is meaningful.
- `REVOKED` row is shown as terminal unusable or detect action is disabled.
- Row-level loading and result states do not affect other rows.
- No plaintext key/hash fields are modeled on normal API key list/detection types.

Optional smoke:

- With backend/frontend running and a known app/key set, click detect on an active key and a disabled/revoked key; verify network responses contain only safe metadata and UI result matches status.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: DB, RAG, gateway public error semantics, upstream, infra, and key storage changes are prohibited.
- Expected files: listed for backend, frontend, tests, and specs.
- Required tests: listed with assertion points.
- Concrete guidelines read: backend, frontend, gateway, security, and cross-layer specs were read before this PRD.
- Open questions: none requiring user confirmation before implementation. Endpoint path `POST /api/admin/api-keys/{id}/detect` is chosen to match existing admin key action style.
- API / frontend type alignment: proposed VO fields use snake_case to match backend `@JsonProperty` style and existing frontend contracts.
