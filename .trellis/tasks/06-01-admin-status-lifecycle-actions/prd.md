# Admin Status Lifecycle Actions

## Goal

Complete the Admin lifecycle operations for App and Model Config status so existing `ENABLED` / `DISABLED` contracts are operable from the console, documented in the runbook, and reflected consistently in public gateway behavior.

This task is intentionally scoped to status lifecycle usability and contract alignment. It must not expand the product into a broader admin workflow platform, change RAG retrieval semantics, or introduce unrelated configuration features.

## Task Classification

Complex Task.

Reason: the work can touch frontend pages, typed API clients, backend Admin API contracts, service status transitions, gateway default model resolution, `/v1/models` and `/v1/chat/completions` behavior, tests, and README/admin runbook. Backend API coverage is incomplete, so contract-first implementation is required.

## Current State From Research

- API Key lifecycle has already been hardened in the previous completed task. It includes frontend disable/revoke confirmation UX, backend disable/revoke APIs, one-time key handling, and README/runbook coverage.
- Model Config backend already has:
  - `POST /api/admin/model-configs/{id}/disable`
  - `ModelConfigService.disableAdminConfig`
  - `findEnabledByIdAndUserId`, which excludes disabled configs from `/v1/models` and chat default model resolution.
  - Frontend `ModelConfigPage` already has a disable button, but no confirmation modal and no enable action.
- App backend currently has create/list/detail/key/bind APIs, but no app enable/disable Admin API.
- App status already exists in schema and service:
  - `rag_app.status`: `ENABLED | DISABLED`
  - `AppService.isEnabled`
  - `GatewayAuthFilter` rejects disabled apps as public `401 invalid_api_key`.
- `StatusTag` currently maps global `DISABLED` to Ant Design `warning`, used by App, Model Config, API Key, KB, Document, and request log related UI paths where applicable.
- README documents API key disable/revoke and model config disable endpoint, but does not yet clearly document how to temporarily disable an App or the gateway effect of disabling a bound Model Config.

## Requirements

1. App page lifecycle operations
   - Add explicit Admin operations for disabling and enabling Apps, but only after backend API contracts are implemented.
   - The App list must show status and provide confirmation before disabling an enabled App.
   - Enabling a disabled App should be explicit and must not silently create or bind model configs, knowledge bases, or API keys.
   - Disabling an App must immediately make its public `/v1/*` keys fail as `401 invalid_api_key` through existing gateway auth behavior.

2. Model Config page lifecycle operations
   - Keep existing disable API behavior, but add confirmation UX before disable.
   - Add an enable lifecycle operation if backend contract is implemented in this task.
   - Disabled model configs must not appear in bind-default-model-config choices.
   - Disabled bound model configs must remain stored as `default_model_config_id`, but `/v1/models` and `/v1/chat/completions` must treat them as not ready through existing enabled-resolution logic.
   - Preserve-key update boundary must be documented: `PUT /api/admin/model-configs/{id}` without `api_key` preserves the encrypted key; blank `api_key` is invalid; disabling/enabling must not rotate or clear upstream keys.

3. Status display
   - Confirm whether the shared `StatusTag` global mapping `DISABLED = warning` is acceptable for App, Model Config, and API Key.
   - Do not split domain-specific status components unless the implementation finds domain-specific labels, colors, or accessibility text are needed.
   - If `StatusTag` remains shared, keep typed status unions aligned with backend enum values and retain a safe fallback for unknown statuses.

4. Documentation
   - README/Admin runbook must document how to temporarily disable an App and Model Config, and the effect on public gateway calls.
   - Documentation must distinguish:
     - App disabled: all app API keys fail `/v1/*` auth with `401 invalid_api_key`.
     - Model Config disabled: app key may authenticate, but default model resolution fails with `409 model_config_not_ready` for `/v1/models` and chat.
     - API Key disabled/revoked: only that key fails `/v1/*` auth with `401 invalid_api_key`.

5. Boundaries
   - Do not add DB migrations unless an existing column is insufficient. Current `rag_app.status` and `rag_model_config.status` appear sufficient.
   - Do not change app API key hash/storage, upstream key encryption, gateway request logging, RAG retrieval, prompt construction, document ingestion, or smoke script behavior unless directly required by status lifecycle tests.
   - Do not introduce silent fallbacks. Disabled resources must fail through explicit existing or newly tested error boundaries.

## API Contracts

### Existing Model Config Disable

```http
POST /api/admin/model-configs/{id}/disable
X-Admin-User-Id: <userId>
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 10,
    "user_id": 100,
    "status": "DISABLED"
  }
}
```

### Required Model Config Enable

Implement only if backend API contract is accepted in the implementation pass:

```http
POST /api/admin/model-configs/{id}/enable
X-Admin-User-Id: <userId>
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 10,
    "user_id": 100,
    "status": "ENABLED"
  }
}
```

Semantics:

- Same-user owned config only.
- `DISABLED -> ENABLED` is allowed.
- `ENABLED -> ENABLED` is idempotent.
- Enable must not modify encrypted upstream API key, masked key, provider, base URL, chat model, embedding model, or embedding dimension.
- If an existing config has no encrypted upstream key, enable should fail with `400 INVALID_REQUEST` rather than making gateway failures confusing. This is a defensive contract check only if the state can occur in current data.

### Required App Disable

```http
POST /api/admin/apps/{id}/disable
X-Admin-User-Id: <userId>
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "user_id": 100,
    "status": "DISABLED"
  }
}
```

Semantics:

- Same-user owned app only.
- `ENABLED -> DISABLED` is allowed.
- `DISABLED -> DISABLED` is idempotent.
- Disable must not revoke, disable, delete, or rotate app API keys.
- Disable must not clear `default_model_config_id`, `default_knowledge_base_id`, or retrieval settings.
- Public `/v1/*` calls with any key under this app must fail as existing `401 invalid_api_key`.

### Required App Enable

```http
POST /api/admin/apps/{id}/enable
X-Admin-User-Id: <userId>
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "user_id": 100,
    "status": "ENABLED"
  }
}
```

Semantics:

- Same-user owned app only.
- `DISABLED -> ENABLED` is allowed.
- `ENABLED -> ENABLED` is idempotent.
- Enable must not create, rotate, or re-enable API keys.
- Enable must not require a default model config or knowledge base; those readiness checks remain owned by `/v1/models`, `/v1/chat/completions`, and bind endpoints.

## Validation / Error Matrix

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler behavior. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler behavior. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Controller validates before mutation. |
| App id missing | 404 | `NOT_FOUND` | `App not found`. |
| App id belongs to another user | 403 | `FORBIDDEN` | Generic access denied; no mutation. |
| Disable enabled App | 200 | `OK` | App status becomes `DISABLED`; `updated_at` changes. |
| Disable disabled App | 200 | `OK` | Idempotent safe response; `updated_at` may change if consistent with service pattern. |
| Enable disabled App | 200 | `OK` | App status becomes `ENABLED`; existing bindings and keys unchanged. |
| Enable enabled App | 200 | `OK` | Idempotent safe response. |
| Model config id missing | 404 | `NOT_FOUND` | `Model config not found`. |
| Model config belongs to another user | 403 | `FORBIDDEN` | Generic access denied; no mutation. |
| Disable enabled Model Config | 200 | `OK` | Status becomes `DISABLED`; default binding remains but enabled lookup excludes it. |
| Disable disabled Model Config | 200 | `OK` | Idempotent safe response. |
| Enable disabled Model Config | 200 | `OK` | Status becomes `ENABLED`; encrypted/masked upstream key preserved. |
| Enable enabled Model Config | 200 | `OK` | Idempotent safe response. |
| Bind disabled Model Config to App | 400 | `MODEL_CONFIG_NOT_READY` | Existing binding contract remains. |
| `/v1/models` with disabled bound Model Config | 409 | `model_config_not_ready` | OpenAI-compatible error shape. |
| `/v1/chat/completions` with disabled bound Model Config | 409 | `model_config_not_ready` | OpenAI-compatible error shape before upstream call. |
| `/v1/*` with disabled App | 401 | `invalid_api_key` | OpenAI-compatible auth failure; no admin envelope. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Admin disables an App from the App page after confirmation. Existing API keys remain listed, but public `/v1/models` or chat calls with those keys return `401 invalid_api_key`. Admin re-enables the App and normal readiness behavior resumes. |
| Good | Admin disables a bound Model Config from the Model Config page after confirmation. App binding remains visible, but `/v1/models` and chat return `409 model_config_not_ready`. Admin re-enables the config and the same app binding becomes usable again. |
| Base | Admin disables an already disabled App or Model Config and receives a safe idempotent success response. |
| Base | Admin updates a Model Config without `api_key`; encrypted upstream key is preserved. Disabling or enabling does not rotate keys. |
| Bad | Disabling an App revokes keys, clears bindings, or returns a fake success while gateway auth still accepts the app. |
| Bad | Disabled Model Config remains bindable or remains returned by enabled lookup for `/v1/models` or chat. |
| Bad | Frontend performs optimistic status changes without refreshing server state, hides backend errors, or uses untyped string statuses. |
| Bad | README implies unsupported `/v1/*` endpoints or exposes real secrets in runbook examples. |

## Acceptance Criteria

- [ ] App page has status lifecycle actions only when backend App enable/disable API exists.
- [ ] App disable/enable actions use confirmation or explicit modal copy and refresh server state after mutation.
- [ ] Model Config page disable action has confirmation text describing gateway impact.
- [ ] Model Config page supports enable if backend enable API is implemented.
- [ ] Frontend typed API clients expose lifecycle functions with explicit response types.
- [ ] `StatusTag` remains shared with `DISABLED=warning`, unless a domain-specific split is justified in implementation notes.
- [ ] Disabled App public gateway behavior is covered by backend tests: `/v1/*` auth returns `401 invalid_api_key`.
- [ ] Disabled default Model Config behavior is covered by backend tests: `/v1/models` and chat readiness return `409 model_config_not_ready`.
- [ ] README/Admin runbook documents temporary disable operations and the distinct gateway effects for App, Model Config, and API Key.
- [ ] No business-code changes outside lifecycle, status display, tests, and docs.

## Expected Files To Modify

Backend, if API contracts are implemented:

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiModelsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`

Frontend:

- `frontend/src/api/apps.ts`
- `frontend/src/api/model-configs.ts`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/components/domain/StatusTag.tsx` only if the shared mapping needs a small accessibility or domain-specific adjustment.
- `frontend/src/types/app.ts` and `frontend/src/types/model-config.ts` only if new DTO/VO types are needed.

Docs:

- `README.md`

Spec updates:

- Only update `.trellis/spec/**` if implementation discovers a durable contract mismatch not already covered by this PRD and current specs.

## Required Tests

Run after implementation:

```powershell
mvn -q "-Dtest=AppAdminControllerTest,AppServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=ModelConfigAdminControllerTest,ModelConfigServiceTest,OpenAiModelsControllerTest,ChatCompletionGatewayServiceTest" test
cmd /c npm run typecheck
cmd /c npm run build
git diff --check
```

If backend implementation is limited to frontend-only because an API is intentionally deferred, run only frontend checks and document that App enable/disable remains contract-only.

## Manual Smoke Plan

1. Create or use an enabled App with an active API key and enabled default Model Config.
2. Disable the App from the App page.
3. Call `GET /v1/models` or `POST /v1/chat/completions` with the app key; expect `401 invalid_api_key`.
4. Re-enable the App; confirm normal gateway readiness behavior resumes.
5. Disable the bound Model Config from the Model Config page.
6. Call `GET /v1/models`; expect `409 model_config_not_ready`.
7. Re-enable the Model Config; confirm `/v1/models` succeeds again when the app remains enabled.
8. Confirm README examples use placeholders only and no real `sk-sangui-*` or provider keys are committed.

## Out Of Scope

- API key regeneration.
- Model Config delete.
- App delete.
- Knowledge Base enable/disable.
- Request-log schema changes.
- RAG retrieval, prompt, no-hit, embedding, or document ingestion changes.
- Real admin authentication replacing `X-Admin-User-Id`.
- Database migration unless current status columns are proven insufficient.
