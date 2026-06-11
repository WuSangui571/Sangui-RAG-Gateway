# V0.3 Model Config Capability Split and Checks

## Classification

Complex Task / fullstack / cross-layer.

This task touches Admin API contracts, DTO/VO fields, database schema, model-config service invariants, app default-model binding, readiness semantics, embedding provider probing, frontend TypeScript contracts, Model Config UX, App binding UX, and spec/test matrices. Codex planning only; implementation is delegated to DeepSeek.

## Current Project State

- Branch: `feature/v0-3-model-config-capability-split`.
- Working tree was clean before task creation.
- No active Trellis task existed before this task.
- Recent journal entries close V0.2 RC smoke/tag/release work; no backend/frontend implementation task is currently open.
- Existing V0.2 smoke/readiness path depends on a split-provider setup, but current model-config UI still forces embedding configs to carry a meaningless `chat_model`.

## Problem

`rag_model_config` currently mixes chat and embedding concerns in one implicit shape:

- `chat_model` is required by schema, backend validation, frontend type, and Model Config form.
- `embedding_model` and `embedding_dimension` are optional add-ons on the same config.
- App default model binding lists all enabled model configs, so an embedding-only intent can be bound as the app's default chat model.
- Embedding dimension is expected to be manually known before saving a config or creating a KB.
- There is no admin-side provider check button to verify base URL, model name, upstream key, and embedding dimension before KB upload or smoke readiness.

This creates upstream failures in the operational chain: create model config -> create KB -> upload document -> bind app -> readiness -> smoke.

## Goal

Introduce explicit model-config capability semantics and safe admin checks so operators can configure chat and embedding providers separately, bind only chat-capable configs to apps, and discover embedding dimensions through a provider check before document upload.

## Product Scope

In scope:

- Add explicit model config capability mode.
- Allow embedding-capable configs to omit `chat_model`.
- Allow chat-capable configs to omit embedding fields.
- Support a combined capability for providers/configs that intentionally serve both chat and embeddings.
- Filter app default model binding to chat-capable, enabled configs.
- Add model config check APIs and frontend check buttons.
- Detect actual embedding dimension from an embedding request and surface it safely.
- Reuse detected embedding dimension in Model Config UX, and where practical in KB creation UX by selecting an enabled embedding-capable config to auto-fill model/dimension.
- Update backend/frontend specs and tests to match the new contract.

Out of scope for this first phase:

- Automatic provider model catalog discovery as a durable feature.
- Multi-provider registry, provider-specific adapters, provider fallback, retry, or routing.
- Public `/v1/embeddings` gateway endpoint.
- Changing public `/v1/chat/completions` request/response shape.
- Full runtime smoke execution with real provider secrets.
- Storing or returning raw provider response bodies, raw prompts, API keys, embeddings, or answer text.

## Capability Contract

Recommended enum:

```text
CHAT
EMBEDDING
CHAT_EMBEDDING
```

Rules:

| Capability | Required fields | Optional fields | Binding behavior |
|---|---|---|---|
| `CHAT` | `chat_model` | `embedding_model`, `embedding_dimension` must normally be null | Eligible for app default model binding |
| `EMBEDDING` | `embedding_model`; `embedding_dimension` may be null before successful check, but must be positive before enable/readiness/upload use | `chat_model` must be null/blank | Not eligible for app default model binding |
| `CHAT_EMBEDDING` | `chat_model`, `embedding_model`; `embedding_dimension` may be null before successful check, but must be positive before embedding use | n/a | Eligible for app default model binding and embedding lookup |

Backend invariant:

- App default model resolution and binding must require `status=ENABLED` and chat capability.
- Embedding lookup must require `status=ENABLED`, embedding capability, matching `embedding_model`, matching positive `embedding_dimension`, and usable upstream key.
- Enabling a config should validate that required fields for its declared capability are present. For embedding-capable configs, enabling should fail if `embedding_dimension` is missing or non-positive unless product explicitly supports a disabled "draft" state.
- Existing rows must be migrated safely. Backfill suggested:
  - `chat_model` present and no embedding model -> `CHAT`.
  - `chat_model` present and embedding model present -> `CHAT_EMBEDDING`.
  - Future embedding-only rows -> `EMBEDDING`.

## API / Payload Contracts

### Existing Model Config Create/Update

Endpoints:

```http
POST /api/admin/model-configs
PUT  /api/admin/model-configs/{id}
GET  /api/admin/model-configs
GET  /api/admin/model-configs/{id}
POST /api/admin/model-configs/{id}/enable
POST /api/admin/model-configs/{id}/disable
```

Create DTO should add:

```json
{
  "capability": "CHAT | EMBEDDING | CHAT_EMBEDDING",
  "name": "DashScope Embedding",
  "provider_name": "dashscope",
  "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "api_key": "<upstream-key>",
  "chat_model": null,
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Update DTO should add the same `capability` field. Existing preserve-key semantics remain:

- Omitted `api_key` preserves existing encrypted/masked key.
- Non-blank `api_key` rotates encrypted/masked key.
- Blank `api_key` fails with `400 INVALID_REQUEST`.

VO should add:

```json
{
  "capability": "EMBEDDING"
}
```

Secret rule: VO still returns `api_key_masked` only; never returns plaintext `api_key` or `api_key_encrypted`.

### Listing / Filtering

Keep existing status filter:

```http
GET /api/admin/model-configs?status=ENABLED
```

Add optional capability filter:

```http
GET /api/admin/model-configs?status=ENABLED&capability=CHAT
GET /api/admin/model-configs?status=ENABLED&capability=EMBEDDING
```

Filtering semantics:

- `capability=CHAT` returns configs that can serve chat: `CHAT`, `CHAT_EMBEDDING`.
- `capability=EMBEDDING` returns configs that can serve embeddings: `EMBEDDING`, `CHAT_EMBEDDING`.
- Invalid capability returns `400 INVALID_REQUEST`.

### App Default Model Binding

Endpoint stays:

```http
PUT /api/admin/apps/{appId}/default-model-config
```

Payload unchanged:

```json
{
  "model_config_id": 10
}
```

New validation:

- Same user: required.
- `status=ENABLED`: required.
- Chat capability: required.
- `chat_model` non-blank: required.
- Embedding-only configs must be rejected with `400 MODEL_CONFIG_NOT_READY` or `400 INVALID_REQUEST` according to existing admin binding style; do not bind them silently.

Frontend App binding modal must load only enabled chat-capable configs.

### Model Config Check API

Add safe admin check endpoints:

```http
POST /api/admin/model-configs/check
POST /api/admin/model-configs/{id}/check
X-Admin-User-Id: <userId>
```

Unsaved check request:

```json
{
  "capability": "CHAT | EMBEDDING | CHAT_EMBEDDING",
  "provider_name": "openai-compatible",
  "base_url": "https://api.example.com/v1",
  "api_key": "<upstream-key>",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Saved check request may omit unchanged fields and may omit `api_key`; backend uses the stored encrypted upstream key for same-user configs:

```json
{
  "embedding_model": "text-embedding-v4"
}
```

Response:

```json
{
  "capability": "EMBEDDING",
  "overall_status": "SUCCESS | FAILED | PARTIAL",
  "base_url_checked": true,
  "chat": null,
  "embedding": {
    "status": "SUCCESS",
    "model": "text-embedding-v4",
    "actual_dimension": 1024,
    "configured_dimension": null,
    "message": "Embedding check succeeded."
  }
}
```

Chat check behavior:

- For chat-capable configs, perform a bounded upstream chat check using configured `base_url`, `api_key`, and `chat_model`.
- Use a minimal safe payload; do not return or log the assistant answer.
- Verify base URL/key/model reachability through provider response success/failure.

Embedding check behavior:

- For embedding-capable configs, perform a bounded `/v1/embeddings` request with a short probe input.
- Return `actual_dimension` from vector length.
- If `configured_dimension` is present and differs from `actual_dimension`, return `FAILED` with a safe mismatch reason.
- If provider succeeds, frontend should offer to fill `embedding_dimension` from `actual_dimension`.

Check error safety:

- No raw provider response body.
- No stack trace.
- No plaintext or encrypted upstream key.
- No embedding vector values.
- No full prompt/messages.
- Message should classify the boundary: validation, base_url, auth, model, embedding_dimension, upstream_timeout, upstream_error.

## Validation / Error Matrix

| Scenario | HTTP / status | Expected result | Assertion point |
|---|---:|---|---|
| Missing admin user header | 400 | `INVALID_REQUEST` | Controller test |
| Cross-user saved config check | 403 | `FORBIDDEN`, no provider call | Controller/service test |
| Missing config id | 404 | `NOT_FOUND` | Controller test |
| Invalid capability literal | 400 | `INVALID_REQUEST` | DTO/service validation test |
| Create `CHAT` without `chat_model` | 400 | `INVALID_REQUEST` | `ModelConfigServiceTest` |
| Create `EMBEDDING` without `embedding_model` | 400 | `INVALID_REQUEST` | `ModelConfigServiceTest` |
| Create `EMBEDDING` without `chat_model` | 200 | Config saved, `chat_model=null`, not bindable as default model | Service/controller/frontend test |
| Enable embedding-capable config without positive dimension | 400 | `INVALID_REQUEST`; do not mark enabled | Service test |
| Bind embedding-only config as app default model | 400 | `MODEL_CONFIG_NOT_READY` or `INVALID_REQUEST`; app unchanged | `AppServiceTest`, `AppAdminControllerTest` |
| List with `capability=CHAT` | 200 | Only chat-capable rows returned | Service/controller/frontend test |
| Readiness default model bound to embedding-only config | 200 | `default_model_config` `NOT_READY` or `MISSING`, safe metadata only | `AppServiceTest` |
| Readiness embedding config exists but disabled | 200 | `embedding_config` `DISABLED` | Existing readiness regression |
| Embedding check success | 200 | `actual_dimension` returned, no vector values | Check service/controller test |
| Embedding check dimension mismatch | 200 or 400 | Safe failure with configured vs actual dimension only | Check service/controller test |
| Provider 401/403 or invalid key | 200 check result `FAILED` or 502 mapped safely | Safe message, no provider body/key | Check service test |
| Provider timeout | 200 check result `FAILED` or 504 mapped safely | Boundary marked `upstream_timeout` | Check service test |
| Check log/output contains forbidden fields | n/a | Forbidden strings absent | Test response JSON and logs where feasible |

## Good / Base / Bad Cases

Good:

- User creates a `CHAT` config with only `chat_model`.
- User creates an `EMBEDDING` config with only `embedding_model`; uses check to obtain `actual_dimension`; saves positive `embedding_dimension`.
- App binding modal shows only enabled chat-capable configs.
- Knowledge Base creation can reuse an enabled embedding-capable config's model/dimension or otherwise requires explicit positive dimension.
- Readiness reports chat and embedding prerequisites separately and safely.

Base:

- Provider supports embeddings but not a reliable model catalog. Embedding check still verifies the embedding model by calling `/v1/embeddings`; chat check may verify through a minimal chat call.
- Provider check fails because of auth/base URL/model mismatch. UI shows actionable safe error and does not save fake success.
- Existing combined rows migrate to `CHAT` or `CHAT_EMBEDDING` based on existing fields.

Bad:

- Embedding-only config is bindable as app default chat model.
- Backend keeps requiring placeholder `chat_model` for embedding configs.
- Check endpoint returns provider raw body, vectors, prompts, stack traces, or keys.
- Dimension mismatch is silently ignored and later fails during upload.
- Frontend types model `capability` or check status as arbitrary strings without fallback.
- Upload/retrieval silently falls back to a chat config when no matching embedding-capable config exists.

## Expected Files To Modify

Backend:

- `backend/src/main/resources/db/migration/V8__model_config_capability_split.sql` or next available migration.
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/CreateModelConfigDTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/UpdateModelConfigDTO.java`
- New model check DTO/VO/service classes under `backend/src/main/java/com/sangui/raggateway/model/`.
- `backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
- Existing tests in `backend/src/test/java/com/sangui/raggateway/model/`, `app/`, `embedding/`, `document/`, `retrieval/`.

Frontend:

- `frontend/src/types/model-config.ts`
- `frontend/src/api/model-configs.ts`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- Likely `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
- `frontend/src/app/i18n/dict.ts`
- Any shared status/check display component if extracted.

Specs/docs:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/gateway/resilience.md` only if check error classification adds a durable upstream-check contract.
- README or runbook only if user-facing setup instructions change.

## Required Tests

Run from `backend/` with a hard 60s timeout for unit tests where possible:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=DocumentServiceTest,RetrievalServiceTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
```

Frontend:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

Optional smoke after implementation, only when runtime secrets and configured provider state are available:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 -ApiKey "<fresh-key>" -AppId <app-id> -AdminUserId <admin-user-id> -VerifyRevokedKey -RevokedApiKey "<revoked-key>"
```

## Acceptance Criteria

- [ ] `rag_model_config` has explicit capability semantics and supports embedding-only configs without a placeholder chat model.
- [ ] Create/update/enable validation follows capability-specific field requirements.
- [ ] App default-model binding rejects embedding-only configs and frontend only lists chat-capable configs.
- [ ] Readiness keeps separate `default_model_config` and `embedding_config` checks with safe metadata only.
- [ ] Model Config page exposes check actions for unsaved and saved configs.
- [ ] Embedding check returns actual dimension and the UI can fill/confirm `embedding_dimension`.
- [ ] Knowledge Base creation no longer requires operators to manually know dimension when a checked enabled embedding config is available.
- [ ] Backend and frontend specs are updated with API fields, error matrix, Good/Base/Bad cases, and tests.
- [ ] Targeted backend tests, frontend typecheck, and frontend build pass.

## Boundaries For DeepSeek

- Do not alter public `/v1/chat/completions` compatibility beyond using chat-capable config resolution.
- Do not add `/v1/embeddings` public gateway support.
- Do not implement provider catalogs or fallback routing.
- Do not log or return provider raw bodies, vectors, keys, prompts, request messages, assistant answers, stack traces, storage paths, or chunk content.
- Do not keep duplicate capability truth in multiple places. The backend capability enum/check helper should be the single behavior source.
- Do not silently coerce bad configs into success. Invalid capability/field combinations must fail visibly.
- Do not commit or push; human controls git closeout.
