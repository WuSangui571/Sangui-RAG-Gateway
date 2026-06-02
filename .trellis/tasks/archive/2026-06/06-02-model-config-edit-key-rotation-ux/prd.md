# Model Config Edit and Upstream API Key Rotation UX

## Goal

Complete the Admin Model Config editing experience so operators can update an existing upstream model configuration and intentionally rotate the upstream API key from the Model Config page.

The backend already exposes `PUT /api/admin/model-configs/{id}` with optional `api_key` rotation semantics. This task should primarily wire the existing frontend page to that contract and verify the cross-layer secret-handling boundary.

## Scope Classification

Complex Task.

Reasons:
- Cross-layer contract: frontend form -> typed API client -> backend DTO -> encrypted secret persistence -> masked VO display.
- Security-sensitive UX: blank key must mean preserve on the frontend, while explicit non-blank key rotates the encrypted upstream key.
- Regression risk: accidental empty-string submission could produce a backend validation error or imply a key-clear operation that must not exist.

## Current Project State

- Previous task `Admin status lifecycle actions` is complete and archived.
- Commit `6680526` added App disable/enable and Model Config disable/enable lifecycle actions.
- The previous manual acceptance noted that Model Config upstream API key editing/rotation remains a page limitation.
- Current backend preserve-key behavior is already documented in `README.md`: omitted `api_key` preserves the existing encrypted key; blank `api_key` is invalid.
- Current frontend Model Config page supports create, list, disable, and enable, but not edit/rotation.

## Requirements

- Add an Edit action to `frontend/src/pages/model-configs/ModelConfigPage.tsx`.
- Editing must support:
  - `name`
  - `provider_name`
  - `base_url`
  - `chat_model`
  - `embedding_model`
  - `embedding_dimension`
  - optional upstream `api_key` rotation
- The edit form must prefill non-secret fields from the selected row.
- The upstream API key input must be empty by default and clearly communicate: leaving it blank preserves the existing upstream key.
- On submit:
  - If API key input is blank or whitespace-only, do not include `api_key` in `UpdateModelConfigDTO`.
  - If API key input contains non-blank text, include trimmed `api_key` to rotate the upstream key.
  - Never attempt to send `api_key: ""`.
- After successful update:
  - close the edit modal/drawer,
  - clear the key input from frontend state,
  - refresh the model config list,
  - show the returned masked key in the table if it changed.
- Preserve existing create, enable, disable, filter, loading, empty, and error behavior.
- Keep the page operational and compact; use existing Ant Design patterns.

## Explicit Non-Goals

- Do not change database schema or migrations.
- Do not change upstream key encryption, masking, or decrypt logic.
- Do not change public `/v1/*` gateway behavior.
- Do not add provider-specific presets, provider autodetection, key testing, or upstream health checks.
- Do not add API key plaintext display after save.
- Do not persist upstream API keys in local/session storage or global stores.
- Do not change App binding, Knowledge Base, RAG retrieval, prompt construction, request logs, Docker, Redis, MQ, or deployment behavior.
- Do not refactor unrelated admin pages.

## API / Payload Contract

### Endpoint

```http
PUT /api/admin/model-configs/{id}
X-Admin-User-Id: <adminUserId>
Content-Type: application/json
```

### Frontend DTO

Existing TypeScript type:

```ts
export interface UpdateModelConfigDTO {
  name?: string
  provider_name?: string
  base_url?: string
  api_key?: string
  chat_model?: string
  embedding_model?: string | null
  embedding_dimension?: number | null
}
```

### Required Frontend Submission Semantics

Preserve existing key:

```json
{
  "name": "demo-chat",
  "provider_name": "openai-compatible",
  "base_url": "https://api.sanguicode.com",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": null,
  "embedding_dimension": null
}
```

Rotate key:

```json
{
  "name": "demo-chat",
  "provider_name": "openai-compatible",
  "base_url": "https://api.sanguicode.com",
  "api_key": "<new-upstream-provider-key>",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": null,
  "embedding_dimension": null
}
```

Forbidden frontend payload:

```json
{
  "api_key": ""
}
```

### Backend Contract To Preserve

- Missing `api_key`: preserve existing `api_key_encrypted` and `api_key_masked`.
- Non-blank `api_key`: encrypt and mask the new value.
- Blank `api_key`: reject with admin `ApiResponse` error, HTTP 400, `code=INVALID_REQUEST`.
- Response VO includes `api_key_masked` only; never `api_key`, `api_key_encrypted`, provider raw body, or stack trace.

## Validation / Error Matrix

| Scenario | Expected Behavior | Assertion Point |
|---|---|---|
| Edit with non-secret fields only | `PUT` omits `api_key`; backend preserves existing encrypted/masked key; list refresh succeeds | frontend submit payload, `ModelConfigServiceTest.shouldUpdateWithoutApiKeyPreserveExistingEncryptedKey` |
| Edit with new non-blank key | `PUT` includes trimmed `api_key`; backend rotates encrypted/masked key; response/table shows masked key only | frontend submit payload, `ModelConfigServiceTest.shouldUpdateWithApiKeyReplaceEncryptedAndMasked` |
| API key input blank/whitespace | frontend omits `api_key`; does not send empty string | frontend implementation review or UI smoke |
| Backend receives blank `api_key` | HTTP 400 `INVALID_REQUEST`; no rotation | existing service/controller tests or targeted addition if needed |
| Missing required text field in edit form | frontend validation blocks submit or backend returns actionable error | UI smoke/typecheck |
| Embedding model without dimension | frontend blocks or backend returns `INVALID_REQUEST` | existing create/update validation pattern |
| Embedding dimension without model | frontend blocks or backend returns `INVALID_REQUEST` | existing create/update validation pattern |
| Cross-user config id | backend returns 403 `FORBIDDEN`; frontend surfaces error safely | `ModelConfigAdminControllerTest` |
| Missing config id | backend returns 404 `NOT_FOUND`; frontend surfaces error safely | `ModelConfigAdminControllerTest` |
| Response body inspection | no plaintext key, encrypted key, provider body, or stack trace | controller tests / code review |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | User edits an existing enabled config, updates base URL/chat model, leaves API key blank, saves successfully, and subsequent `/v1/chat/completions` still works with the preserved upstream key. |
| Good | User edits the same config, enters a new upstream key, saves successfully, table shows a masked key value, and subsequent gateway chat uses the rotated key. |
| Base | User edits only display/model fields; status is unchanged; enable/disable actions keep working separately. |
| Base | User clears optional embedding fields; frontend sends `embedding_model: null` and `embedding_dimension: null` only if the existing backend update semantics support clearing. If clearing is not supported by current backend semantics, leave those fields unchanged and document the limitation in handoff. |
| Bad | Frontend sends `api_key: ""`, stores upstream key in persistent browser storage, renders plaintext key after save, or introduces a second key-rotation API separate from existing PUT. |

## Implementation Approach

Prefer a targeted frontend change:

- Reuse `updateModelConfig` from `frontend/src/api/model-configs.ts`.
- Import `UpdateModelConfigDTO` in `ModelConfigPage.tsx`.
- Add edit modal/drawer state local to the page.
- Reuse the existing create form layout where practical, but keep create and edit semantics separate because `api_key` is required on create and optional on update.
- Add `Edit` button beside Disable/Enable in the table action column.
- Build the update payload explicitly so `api_key` is included only when non-blank.
- Clear edit form state and especially the API key field on close/success.

Backend likely needs no production changes unless research during implementation finds a gap between existing tests and behavior. If a backend gap is found, keep it limited to `model` module tests/validation and do not alter schema or encryption primitives.

## Files Likely To Modify

- `frontend/src/pages/model-configs/ModelConfigPage.tsx`: add edit action, modal/form, payload construction, secret clearing, update call, list refresh.
- `frontend/src/types/model-config.ts`: verify no type change is needed; change only if the page needs a more precise local edit form type.
- `frontend/src/api/model-configs.ts`: likely no change; `updateModelConfig` already exists.
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java`: likely no change; existing preserve/rotate/blank tests appear sufficient.
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`: add only if controller-level blank `api_key` or response-secret assertion is missing and Qwen judges it necessary.
- `README.md`: no change required unless implementation changes user-facing instructions; existing preserve-key documentation already exists.

## Required Specs / Context

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Required Tests

Backend, 60 second hard timeout per command:

```powershell
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest" test
```

Run from `backend/`.

Frontend:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

Run from `frontend/`.

Optional broader regression if backend code changes:

```powershell
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,AppServiceTest,AppAdminControllerTest,OpenAiModelsControllerTest" test
```

Manual smoke:

1. Open Model Config page.
2. Create or select an existing config with a valid upstream key.
3. Click Edit.
4. Change a non-secret field and leave Upstream API Key empty.
5. Save; verify success, modal closes, list refreshes, masked key remains present, no `api_key` plaintext appears in UI.
6. Use the bound app to call `/v1/chat/completions`; verify it still succeeds, proving the key was preserved.
7. Edit again, enter a fresh upstream key, save.
8. Verify masked key updates and a subsequent `/v1/chat/completions` succeeds with the rotated key.
9. Check browser devtools or captured request payload during smoke if available: blank key edit must omit `api_key`, not send `api_key: ""`.

## Acceptance Criteria

- [ ] Model Config table has an Edit action for each row.
- [ ] Edit form pre-populates all non-secret fields.
- [ ] Edit API key field is empty by default and says leaving it blank preserves the existing upstream key.
- [ ] Saving with blank/whitespace API key omits `api_key` from `UpdateModelConfigDTO`.
- [ ] Saving with a non-blank API key includes trimmed `api_key` and rotates the upstream key.
- [ ] Frontend never displays, persists, or logs upstream API key plaintext after save.
- [ ] Existing enable/disable and create flows still work.
- [ ] Backend preserve/rotate/blank-key tests pass.
- [ ] Frontend typecheck and build pass.
- [ ] Manual edit smoke validates preserve and rotate behavior.

## Planning Self-Check

- Acceptance criteria are explicit: yes.
- Forbidden scope is explicit: yes.
- Expected modified files are listed: yes.
- Required tests are listed: yes.
- Specific guideline files, not only indexes, have been read: yes.
- Unclear requirement needing user confirmation: none currently.
- API / DTO / frontend type alignment: `UpdateModelConfigDTO` exists in backend and frontend; API client already exposes `updateModelConfig`.
- Potential open technical edge: current backend update logic may not clear existing embedding fields because it only mutates embedding fields when `dto.getEmbeddingModel() != null`. Qwen should verify desired clear behavior before changing backend. For this task, do not broaden into backend semantics unless clearing optional embedding fields is required by implementation smoke.
