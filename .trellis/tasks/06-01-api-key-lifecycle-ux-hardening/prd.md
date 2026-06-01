# API Key Lifecycle UX Hardening

## Classification

Complex Task.

This task crosses frontend security UX, Admin API usage, gateway smoke verification, and README/Admin runbook documentation. It should remain a frontend-plus-docs hardening task unless implementation research proves a missing backend contract. Do not change database schema, API key hashing, gateway authentication semantics, RAG retrieval, request-log persistence, or upstream provider behavior for this task.

## Goal

Make the app API key lifecycle safer and easier to operate from creation through copy, smoke validation, disable/revoke, leakage response, and post-revocation verification.

The backend security model is already correct: full app API keys are returned only once on creation, only a hash is stored, normal list/detail responses expose only `key_prefix`, and disabled/revoked/expired keys fail public `/v1/*` calls as `401 invalid_api_key`. This task hardens the frontend and docs around that model so demo and external-system onboarding are less likely to fail because of lost, leaked, or unverified keys.

## Scope

In scope:

- API key list UX in the Admin console.
- API key create success dialog and post-copy next-step guidance.
- Disable and revoke confirmation dialogs.
- Smoke page temporary plaintext key input and local validation flow.
- README/Admin runbook updates for lost/leaked key handling.
- Frontend typecheck/build and focused smoke or component-level validation where practical.

Out of scope:

- Backend API behavior changes unless a direct contract bug is discovered and explicitly approved.
- Database schema, migrations, API key hash format, plaintext key recovery, rate limits, quotas, auth filter behavior, gateway error semantics, or request-log persistence changes.
- Persistent storage of plaintext API keys in frontend state, localStorage, sessionStorage, query params, route state, logs, or README examples.
- Expanding the smoke page into a general chat playground or changing the RAG prompt/retrieval behavior.
- Adding new app-level credential concepts such as regeneration unless a later task defines the backend contract.

## User Requirements

- API Key list must more clearly distinguish `ACTIVE`, `REVOKED`, and `DISABLED`, and should surface recently created and last-used information.
- Disable and revoke actions must require confirmation and explain the operation impact and irreversible boundary.
- Create success dialog must keep the current one-time display safety and add “copy then next step” guidance, such as navigating to Smoke Test or showing the usable base URL.
- README/Admin manual must document what to do when a key is lost or leaked.
- Smoke page may accept a temporarily pasted plaintext key for local testing, but must not write it to persistent browser storage.
- Validation must include frontend typecheck/build. Add component tests or Playwright UI smoke if the project/test stack supports it without adding unrelated infrastructure.

## Product Contracts

### API / Command / Payload Fields

Existing backend contracts should be reused:

| Operation | Contract | Request fields | Response fields required by UI |
|---|---|---|---|
| Create API key | `POST /api/admin/apps/{appId}/api-keys` with `X-Admin-User-Id` | `name`, `expires_at` | `ApiResponse<ApiKeyCreateVO>`; `data.key` shown once, plus `id`, `app_id`, `user_id`, `name`, `key_prefix`, `status`, `expires_at`, `last_used_at`, `revoked_at`, `created_at`, `updated_at` |
| List API keys | `GET /api/admin/apps/{appId}/api-keys` with `X-Admin-User-Id` | path `appId` | `ApiResponse<ApiKeyVO[]>`; must not include plaintext `key` or `key_hash` |
| Disable API key | `POST /api/admin/api-keys/{id}/disable` with `X-Admin-User-Id` | path `id` | `ApiResponse<ApiKeyVO>`; `status=DISABLED`; no plaintext `key` or `key_hash` |
| Revoke API key | `POST /api/admin/api-keys/{id}/revoke` with `X-Admin-User-Id` | path `id` | `ApiResponse<ApiKeyVO>`; `status=REVOKED`, `revoked_at`; no plaintext `key` or `key_hash` |
| Smoke chat | `POST /v1/chat/completions` with `Authorization: Bearer <plaintext-app-key>` | `model`, `messages`, `stream=false` | OpenAI-compatible success or `OpenAiErrorResponse`; disabled/revoked/invalid key must be `401 invalid_api_key` |

Frontend type contracts:

- `ApiKeyStatus = 'ACTIVE' | 'DISABLED' | 'EXPIRED' | 'REVOKED'`.
- Only `ApiKeyCreateVO` may include `key`.
- `ApiKeyVO` must not grow a plaintext secret field.
- Smoke page plaintext key value must be local component state only and cleared on app change, page reload, or explicit clear.

### Validation / Error Matrix

| Scenario | Expected UX / behavior | Assertion point |
|---|---|---|
| Create succeeds | One-time dialog opens, key is focused/selected, copy action has visible success/failure state, list refreshes, plaintext is cleared when confirmed closed | `ApiKeyOneTimeSecret`, `ApiKeyPage` |
| Clipboard write fails | Dialog keeps key selected and instructs manual `Ctrl+C`; no fake success | `ApiKeyOneTimeSecret` |
| User closes one-time dialog | Full key is removed from React state and cannot be recovered from list | `ApiKeyPage`, `ApiKeyOneTimeSecret` |
| Active key in list | Distinct active status, created time visible, last-used visible or `Never`, actions available with confirmations | `ApiKeyPage`, `StatusTag` |
| Disabled key in list | Visually distinct from active and revoked; disable should not be presented as a destructive primary action | `ApiKeyPage`, `StatusTag` |
| Revoked key in list | Visually terminal/irreversible; no revoke action shown; disable action hidden | `ApiKeyPage` |
| Disable confirmed | Calls existing disable API, refreshes list, explains key stops working for `/v1/*` and can be separately revoked later | `ApiKeyPage` |
| Disable cancelled | No API call; list unchanged | `ApiKeyPage` |
| Disable revoked key | UI should avoid the action; backend `400 INVALID_REQUEST` still shown if encountered | `ApiKeyPage`, existing backend contract |
| Revoke confirmed | Calls existing revoke API, refreshes list, explains operation is terminal and old plaintext key must fail `401 invalid_api_key` | `ApiKeyPage` |
| Revoke cancelled | No API call; list unchanged | `ApiKeyPage` |
| Smoke temporary key pasted | Request uses pasted key only in `Authorization`; not stored in localStorage/sessionStorage; clear behavior exists | `SmokeTestPage`, `api/openai.ts` |
| Smoke with revoked/disabled key | Error panel shows `HTTP 401` and `invalid_api_key` without exposing the key | `SmokeTestPage` |
| README lost key runbook | Says keys cannot be recovered; create a fresh key and update clients | README |
| README leaked key runbook | Says revoke leaked key, verify `401 invalid_api_key`, create fresh key, update clients, remove plaintext artifacts | README |

### Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | User creates a key, copies it once, immediately jumps to Smoke Test or uses shown base URL, validates a request, sees last-used update after refresh, then can disable/revoke with confirmation and verify revoked key rejection. No plaintext key persists after the modal closes. |
| Base | User only needs to inspect existing keys: list makes status, creation time, expiry, last-used, and available actions clear without requiring backend changes. Smoke page accepts a pasted key and clears it when switching apps or manually clearing. |
| Bad | UI implies a lost plaintext key can be recovered, hides the difference between disable and revoke, allows accidental revoke without confirmation, stores the plaintext key in browser storage, logs it to console, or docs include real/generated `sk-sangui-*` values. |

## Acceptance Criteria

- [ ] API key list visually distinguishes `ACTIVE`, `DISABLED`, `EXPIRED`, and `REVOKED`; `ACTIVE`, `DISABLED`, and `REVOKED` must not look interchangeable.
- [ ] API key list exposes enough timing context to spot new/recently used keys: `created_at`, `last_used_at`, `expires_at`, and `revoked_at` where relevant.
- [ ] Disable action has a confirmation dialog explaining that the key stops authenticating against `/v1/*`, but revocation remains the terminal leaked-key action.
- [ ] Revoke action has a confirmation dialog explaining that the operation is irreversible/terminal and should be followed by a `401 invalid_api_key` verification.
- [ ] Create success dialog preserves one-time secret display behavior, explicit copy feedback, no overlay/Esc accidental close, and clearing plaintext on close.
- [ ] Create success dialog adds a clear next step: go to Smoke Test with the selected app, show/copy gateway base URL, or both.
- [ ] Smoke page accepts a temporary pasted key for local testing, never persists it to browser storage, and provides an explicit clear path.
- [ ] Smoke page error display remains safe and shows `401 invalid_api_key` for disabled/revoked/invalid keys without echoing the key.
- [ ] README/Admin runbook documents lost-key and leaked-key flows, including revoke, verify revoked key, create fresh key, update clients, and clear plaintext artifacts.
- [ ] No backend schema/auth/RAG behavior changes are made unless explicitly approved after discovering a direct contract bug.
- [ ] Frontend `npm run typecheck` and `npm run build` pass from `frontend/`.
- [ ] If tests are added, they assert secret clearing, confirmation boundaries, and non-persistence of pasted key.

## Required Tests and Assertion Points

Primary validation:

```bash
cd frontend
npm run typecheck
npm run build
```

Recommended static/security scans after implementation:

```bash
rg -n "localStorage|sessionStorage|console\\.log|console\\.debug|sk-sangui-[A-Za-z0-9_-]{20,}" frontend/src README.md scripts
git diff --check
```

Backend regression tests are not required if no backend files change. If backend API key code is touched, run:

```bash
cd backend
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,ApiKeyAdminControllerTest,GatewayAuthFilterTest" test
```

Optional manual UI smoke:

- Create an API key in Admin UI.
- Copy key from the one-time dialog.
- Follow the dialog next step to Smoke Test.
- Paste key only into the Smoke page temporary input.
- Run non-streaming smoke; verify success and no key appears in visible result output.
- Revoke the key from API Key page, confirm the dialog text, refresh list.
- Re-run smoke with the revoked key and verify `HTTP 401` plus `invalid_api_key`.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, API key security model, OpenAI-compatible gateway auth.
- `.trellis/spec/frontend/index.md`: frontend pre-development checklist.
- `.trellis/spec/frontend/directory-structure.md`: page/domain component/API client organization.
- `.trellis/spec/frontend/component-guidelines.md`: one-time API key component, status tags, dialogs, explicit states.
- `.trellis/spec/frontend/state-management.md`: local-only one-time secret state and no persistent key storage.
- `.trellis/spec/frontend/type-safety.md`: `ApiKeyVO` vs `ApiKeyCreateVO`, explicit status unions.
- `.trellis/spec/frontend/quality-guidelines.md`: secret safety, API key one-time display, revoke/disable test expectations.
- `.trellis/spec/backend/error-handling.md`: Admin API key error matrix and gateway `invalid_api_key` behavior.
- `.trellis/spec/backend/logging-guidelines.md`: do not log complete API keys or auth headers.
- `.trellis/spec/backend/database-guidelines.md`: API key storage contract and status values.
- `.trellis/spec/backend/quality-guidelines.md`: API key plaintext/hash safety and test commands if backend changes.
- `.trellis/spec/security/rag-security.md`: secret-safe observability and forbidden fields.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: API key and frontend secret flow boundary checks.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: reuse existing components and API clients.

## Code Patterns Found

- `frontend/src/pages/api-keys/ApiKeyPage.tsx`: current list/create/disable/revoke workflow; plaintext key kept in component state and cleared by `handleSecretClose`.
- `frontend/src/components/domain/ApiKeyOneTimeSecret.tsx`: one-time display modal; already disables close icon, Esc, and mask close; copy success/failure states exist.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: current temporary pasted key flow; state resets on selected app changes; uses `smokeChatCompletions`.
- `frontend/src/api/api-keys.ts`: typed API client functions for existing create/list/disable/revoke endpoints.
- `frontend/src/api/openai.ts`: smoke request sends pasted key only in `Authorization` header and normalizes OpenAI-compatible errors.
- `frontend/src/types/api-key.ts`: status union and `ApiKeyCreateVO` secret-only-on-create type.
- `frontend/src/components/domain/StatusTag.tsx`: central status color mapping reused across domains.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`: existing disable/revoke API contract.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`: `ACTIVE` is the only valid gateway key status; revoked is terminal; disable revoked fails.
- `README.md`: existing Admin API runbook and key rotation/revocation sections to extend.

## Files Likely To Modify

- `frontend/src/pages/api-keys/ApiKeyPage.tsx`: list timing/status UX; confirmation dialogs; next-step wiring into shell/smoke.
- `frontend/src/components/domain/ApiKeyOneTimeSecret.tsx`: post-copy next-step guidance, base URL display/copy, optional “Go to Smoke Test” callback.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: make temporary key handling explicit, add clear action, possibly consume selected app guidance.
- `frontend/src/components/domain/StatusTag.tsx`: refine status mapping if needed for `ACTIVE` vs `DISABLED` vs `REVOKED`.
- `frontend/src/types/api-key.ts`: only if existing field typing is insufficient; do not add plaintext key to `ApiKeyVO`.
- `README.md`: lost/leaked key runbook and UI-oriented acceptance instructions.

Do not modify unless explicitly necessary:

- `backend/src/main/java/com/sangui/raggateway/apikey/**`
- `backend/src/main/java/com/sangui/raggateway/common/security/**`
- `backend/src/main/resources/db/migration/**`
- RAG retrieval, prompt, request-log persistence, gateway streaming, Docker/infra files.

## Risk / Boundary Notes

- The biggest risk is creating a second source of truth for secret lifecycle in the UI. Backend remains authoritative for key status; frontend only guides users.
- Do not make revoked/disabled error messages in public `/v1/*` more specific; gateway intentionally returns generic `401 invalid_api_key`.
- Do not store the pasted smoke key in `selectedKeyPrefix`, shell context, URL, browser storage, or logs.
- Confirmation dialogs should prevent accidental security mutations but not hide real backend failures.
- README examples must keep placeholder keys only. Avoid generated-looking `sk-sangui-*` values that might be mistaken for real secrets.
- The UI can show `key_prefix` and IDs; it must never imply prefix is sufficient for authentication.

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden modification scope is explicit.
- [x] Expected files to modify are listed.
- [x] Required test commands are listed.
- [x] Concrete guideline files, not only spec indexes, were read before planning.
- [x] No open requirements need user confirmation before Qwen implementation.
- [x] API, DTO, frontend type, and error/status fields are aligned with existing contracts.
