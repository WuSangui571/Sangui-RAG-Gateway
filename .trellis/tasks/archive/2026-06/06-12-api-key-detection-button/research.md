# Focused Code Research: API Key Detection Button

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, API key security, admin console scope, public `/v1/*` gateway auth flow.
- `.trellis/spec/backend/directory-structure.md`: API key code belongs under `apikey`; cross-cutting response/security stays in `common`.
- `.trellis/spec/backend/database-guidelines.md`: API key status enum and storage rules; no plaintext storage; tenant-scoped admin queries.
- `.trellis/spec/backend/error-handling.md`: Admin API uses `ApiResponse`; gateway auth failures must remain OpenAI-compatible `401 invalid_api_key`; API key admin error matrix.
- `.trellis/spec/backend/logging-guidelines.md`: key/hash/Authorization must not be logged; only safe key prefix/IDs are allowed.
- `.trellis/spec/backend/quality-guidelines.md`: API key auth, tenant isolation, secret safety, and tests are required.
- `.trellis/spec/frontend/directory-structure.md`: frontend API key workflow lives in `frontend/src/pages/api-keys`, `frontend/src/api/api-keys.ts`, and `frontend/src/types/api-key.ts`.
- `.trellis/spec/frontend/component-guidelines.md`: API key table actions should have explicit loading/error/disabled states and no hidden side effects.
- `.trellis/spec/frontend/type-safety.md`: explicit `ApiKeyStatus` union and no secret fields on normal list/detail/detection VOs.
- `.trellis/spec/frontend/state-management.md`: detection result should be page-local state; no global store or persistence.
- `.trellis/spec/frontend/quality-guidelines.md`: API key lifecycle UI must be clear and secret-safe.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this spans Admin API, service, frontend type/client/page, and specs.
- `.trellis/spec/security/rag-security.md`: applies to API key and error/observability safety even though RAG retrieval is out of scope.
- `.trellis/spec/gateway/resilience.md`: confirms public `/v1/*` error shape and safe logging boundaries; no upstream behavior should change.

## Code Patterns Found

- Backend Admin key actions:
  - `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
  - Existing pattern: `POST /api/admin/api-keys/{id}/disable` and `/revoke` validate `X-Admin-User-Id`, pre-check `findById`, distinguish `404 NOT_FOUND` vs `403 FORBIDDEN`, then call service and return `ApiResponse.success(ApiKeyVO.from(...))`.

- Backend key validity source of truth:
  - `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
  - Existing `isValid(ApiKeyEntity)` returns true only for `ACTIVE` and not past `expires_at`; `DISABLED`, `REVOKED`, expired active keys, and null keys are invalid.

- Public gateway auth boundary:
  - `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
  - Existing `authenticate(...)` does: token prefix -> hash lookup -> `apiKeyService.isValid(...)` -> `appService.findById(...)` -> `appService.isEnabled(...)` -> `updateLastUsed(...)` -> context. Detection should reuse the same metadata checks but must not call `updateLastUsed(...)`.

- App enabled helper:
  - `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - Existing `isEnabled(AppEntity)` returns true only for `AppStatus.ENABLED`.

- Backend tests to extend:
  - `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java`
  - `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyAdminControllerTest.java`
  - `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
  - Existing controller tests use standalone `MockMvc` + `GlobalExceptionHandler` and assert no `key` / `key_hash` in responses.

- Frontend typed client/page:
  - `frontend/src/types/api-key.ts` has `ApiKeyStatus`, `ApiKeyVO`, `ApiKeyCreateVO`, and `CreateApiKeyDTO`.
  - `frontend/src/api/api-keys.ts` has typed `listApiKeys`, `createApiKey`, `disableApiKey`, `revokeApiKey` using `apiGet/apiPost`.
  - `frontend/src/pages/api-keys/ApiKeyPage.tsx` uses Ant Design `Table`, row actions for disable/revoke, local state for dialogs/errors, `StatusTag`, and dictionary-backed text via `useI18n`.
  - `frontend/src/app/i18n/dict.ts` contains both zh-CN and en-US API key labels.

## Files Likely To Modify

Backend:

- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`: add `POST /{id}/detect`; inject `AppService` or call a service method that resolves app enabled state.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`: add detection service method or helper that returns safe metadata and reuses `isValid`.
- `backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyDetectionVO.java`: new safe response VO with `key_id`, `app_id`, `usable`, `status`, `app_enabled`, `expires_at`, `checked_at`.
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyServiceTest.java`: cover active/disabled/revoked/expired/app-disabled detection and assert no last-used update.
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyAdminControllerTest.java`: cover endpoint success, false cases, 403/404/400, and secret omission.
- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`: should remain passing; optionally add/keep assertion that public invalid key behavior is unchanged.

Frontend:

- `frontend/src/types/api-key.ts`: add `ApiKeyDetectionVO`.
- `frontend/src/api/api-keys.ts`: add `detectApiKey(id, adminUserId)`.
- `frontend/src/pages/api-keys/ApiKeyPage.tsx`: add detect action, row-level loading/result state, result display, revoked terminal handling.
- `frontend/src/app/i18n/dict.ts`: add zh-CN/en-US labels for detect action and results.
- Possibly `frontend/src/components/domain/StatusTag.tsx`: only if existing statuses cannot express detection result; prefer local result text/Alert/Tag first.

Specs:

- `.trellis/spec/backend/error-handling.md`: document Admin detection endpoint and unchanged public gateway invalid-key response.
- `.trellis/spec/frontend/component-guidelines.md`: document API key table detect action and local result-state rules.

## Risk / Boundary Notes

- Do not require or reconstruct plaintext key. The detection endpoint checks the stored key record's current metadata, assuming the caller still has the originally issued plaintext key.
- Do not call `/v1/*` internally; it would require plaintext and could update `last_used_at`.
- Do not update `last_used_at` from detection.
- Do not expose `key_hash`, plaintext `key`, Authorization, or specific public auth failure reason.
- Be careful with app-disabled detection: the Admin endpoint can show safe `app_enabled=false`, but public gateway still returns generic `invalid_api_key`.
- If the service currently returns persisted `status=ACTIVE` for an expired key, document/test that `usable=false` is due to expiry while `status` remains the stored status. Do not silently mutate status unless the existing state-machine task already established that behavior.
- Existing controller constructor currently only accepts `ApiKeyService`; adding `AppService` or a composed service will require updating tests.
- Frontend row actions currently hide all actions for `REVOKED`. Detection UX should explicitly show terminal unusable state or add a disabled detect action so the result is clear.
- Actions column width is currently `180`; adding detect may require widening to prevent cramped controls.

## Required Tests

Backend:

```powershell
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest" test
mvn -q -DskipTests compile
```

Frontend:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Assertions:

- `ACTIVE` + unexpired + enabled app => `usable=true`.
- `DISABLED`, `REVOKED`, expired, or app-disabled => `usable=false`.
- Detection omits `key`, `key_hash`, Authorization, and all secret fields.
- Detection does not update `last_used_at`.
- Public `/v1/*` invalid key cases remain `401 invalid_api_key` with OpenAI-compatible error shape.
- Frontend types match backend snake_case fields.
- Row-level detection loading/result state is local to the row and not persisted.
