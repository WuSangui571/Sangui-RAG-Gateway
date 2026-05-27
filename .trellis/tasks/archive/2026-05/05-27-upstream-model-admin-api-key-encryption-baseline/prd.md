# 上游模型配置 Admin API 与密钥加密存储基线

## Classification

Complex Task.

This task crosses backend admin APIs, database-backed model configuration, secret encryption, tenant boundaries, app-model association, OpenAI-compatible `/v1/models` behavior, tests, and specs. It must be planned before coding.

## Current Project State

- App/API key authentication baseline is implemented for `/v1/*`.
- `GET /v1/models` is implemented and resolves the authenticated app's enabled default model config.
- `rag_model_config` already has `api_key_encrypted` and `api_key_masked`, but current service creation leaves them empty.
- Model config rows still rely on manual SQL for realistic setup.
- No admin login or admin user context exists yet.
- No `POST /v1/chat/completions`, upstream HTTP forwarding, RAG retrieval, frontend admin UI, or real upstream key usage exists yet.

## Goal

Add the backend baseline that lets admin-side code create, update, list, detail, disable, and bind upstream model configs while storing upstream API keys encrypted at rest and only returning masked keys.

This task should make future `/v1/chat/completions` work possible without mixing configuration management, key safety, upstream forwarding, RAG retrieval, and OpenAI error mapping in one later change.

## Scope

- Add upstream key encryption/decryption utility and configuration.
- Extend model config service behavior for create/update/detail/list/disable with tenant scope.
- Add minimal admin API endpoints for model config CRUD-like operations.
- Add admin API endpoint or service method for binding an app to a same-user default model config.
- Ensure disabled configs are not returned by `/v1/models`.
- Ensure plaintext upstream API keys are never persisted, logged, or returned.
- Update relevant backend specs and project spec contracts.
- Add focused unit/controller tests.

## Non-Goals

- Do not implement `POST /v1/chat/completions`.
- Do not implement real upstream HTTP calls or WebClient clients.
- Do not implement streaming.
- Do not implement RAG retrieval, prompt construction, embeddings, knowledge bases, documents, request logs, rate limits, or quotas.
- Do not build frontend UI or TypeScript types in this task.
- Do not introduce admin login/session/JWT/Spring Security.
- Do not change app API key hashing behavior.
- Do not return plaintext upstream API keys from any API.
- Do not rewrite existing `/v1/models` behavior beyond ensuring it respects enabled same-user default configs.

## Admin Identity Assumption

Admin authentication is not implemented. For this baseline, admin endpoints must use an explicit temporary user boundary so service and controller tests can enforce tenant isolation.

Use this temporary contract:

```http
X-Admin-User-Id: <long>
```

Rules:

- The header is required on all new `/api/admin/**` endpoints in this task.
- The header must parse to a positive long.
- It is only a baseline user context, not real authentication.
- Do not add real admin auth in this task.
- Specs must document that this header is temporary until admin login exists.

If the human chooses a different admin identity contract before coding, update this PRD first.

## API Contract

All admin APIs return the existing admin envelope:

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

Error responses must also use `ApiResponse`, not OpenAI-compatible `error` objects. Do not include plaintext or encrypted keys in any response body.

### Create Model Config

```http
POST /api/admin/model-configs
X-Admin-User-Id: 100
Content-Type: application/json
```

Request:

```json
{
  "name": "Default OpenAI",
  "provider_name": "openai",
  "base_url": "https://api.openai.com/v1",
  "api_key": "sk-upstream-secret",
  "chat_model": "gpt-4o-mini",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536
}
```

Response `data`:

```json
{
  "id": 10,
  "user_id": 100,
  "name": "Default OpenAI",
  "provider_name": "openai",
  "base_url": "https://api.openai.com/v1",
  "api_key_masked": "sk-...cret",
  "chat_model": "gpt-4o-mini",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536,
  "status": "ENABLED",
  "created_at": "2026-05-27T12:00:00",
  "updated_at": "2026-05-27T12:00:00"
}
```

Notes:

- `api_key` is required on create.
- Persist `api_key_encrypted` and `api_key_masked`.
- Response must not include `api_key` or `api_key_encrypted`.

### Update Model Config

```http
PUT /api/admin/model-configs/{id}
X-Admin-User-Id: 100
Content-Type: application/json
```

Request fields are optional except validation dependencies:

```json
{
  "name": "Default OpenAI Updated",
  "provider_name": "openai",
  "base_url": "https://api.openai.com/v1",
  "api_key": "sk-new-upstream-secret",
  "chat_model": "gpt-4o-mini",
  "embedding_model": "text-embedding-3-small",
  "embedding_dimension": 1536
}
```

Rules:

- Omitted `api_key` preserves the existing encrypted key and masked key.
- Non-blank `api_key` replaces both encrypted and masked values.
- Blank `api_key` is invalid.
- `status` is not updated through this endpoint; use disable for disabling.
- `updated_at` must change on successful mutation.
- Response shape is the same masked VO as create.

### Detail Model Config

```http
GET /api/admin/model-configs/{id}
X-Admin-User-Id: 100
```

Rules:

- Must scope lookup by `id` and `X-Admin-User-Id`.
- Return the same masked VO as create.
- Do not expose disabled configs across users.

### List Model Configs

```http
GET /api/admin/model-configs?status=ENABLED
X-Admin-User-Id: 100
```

Response `data` can be a simple list for this baseline:

```json
[
  {
    "id": 10,
    "user_id": 100,
    "name": "Default OpenAI",
    "provider_name": "openai",
    "base_url": "https://api.openai.com/v1",
    "api_key_masked": "sk-...cret",
    "chat_model": "gpt-4o-mini",
    "embedding_model": "text-embedding-3-small",
    "embedding_dimension": 1536,
    "status": "ENABLED",
    "created_at": "2026-05-27T12:00:00",
    "updated_at": "2026-05-27T12:00:00"
  }
]
```

Rules:

- Must scope by `user_id`.
- Optional `status` accepts only `ENABLED` or `DISABLED`.
- Pagination may be deferred for this baseline, but specs should note future pagination requirement.

### Disable Model Config

```http
POST /api/admin/model-configs/{id}/disable
X-Admin-User-Id: 100
```

Rules:

- Must scope by `id` and `user_id`.
- Set status to `DISABLED`.
- Update `updated_at`.
- Return masked VO or a success boolean; prefer masked VO for consistency.
- Disabled configs must not be returned by app default config resolution and must produce `/v1/models` 409 `model_config_not_ready` when bound to an app.

### Bind App Default Model Config

```http
PUT /api/admin/apps/{appId}/default-model-config
X-Admin-User-Id: 100
Content-Type: application/json
```

Request:

```json
{
  "model_config_id": 10
}
```

Response `data`:

```json
{
  "app_id": 1,
  "user_id": 100,
  "default_model_config_id": 10
}
```

Rules:

- The app must belong to `X-Admin-User-Id`.
- The model config must belong to the same user and be `ENABLED`.
- Cross-user binding must be rejected.
- Missing or disabled model config must be rejected.
- Update `rag_app.default_model_config_id` and `rag_app.updated_at`.

## Encryption Contract

Use the existing deployment env key for this baseline:

```text
RAG_GATEWAY_SECRET_KEY
```

Implementation guidance:

- Add a Spring configuration/properties binding that reads `RAG_GATEWAY_SECRET_KEY`.
- Fail fast or reject encryption if the configured secret is blank.
- Use standard JDK crypto; do not add a dependency unless there is a clear reason.
- Recommended algorithm: AES-GCM with a random IV per encryption.
- Recommended stored format: `v1:<base64url-iv>:<base64url-ciphertext>`.
- Derive an AES key from `RAG_GATEWAY_SECRET_KEY` with SHA-256 or another deterministic JDK-supported derivation so local placeholder length does not break startup.
- Provide decrypt support for future upstream forwarding tests, but do not expose decrypted values from admin APIs.
- Add a masker that returns a safe display string and never the full key. Example: keep a small prefix and suffix for ordinary keys, but fully mask very short values.

## Validation Matrix

| Area | Case | HTTP | Code | Required assertion |
|---|---|---:|---|---|
| Admin identity | Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Response does not include stack trace or secrets. |
| Admin identity | Non-numeric or non-positive user id | 400 | `INVALID_REQUEST` | Service/controller does not run tenant query. |
| Create | Valid payload with upstream key | 200 | `OK` | DB entity has encrypted key and masked key; plaintext absent. |
| Create | Blank name/provider/base_url/chat_model/api_key | 400 | `INVALID_REQUEST` | No insert; response does not echo key. |
| Create | Embedding model without dimension | 400 | `INVALID_REQUEST` | Matches existing embedding validation behavior. |
| Create | Non-positive embedding dimension | 400 | `INVALID_REQUEST` | No insert. |
| Update | Omit `api_key` | 200 | `OK` | Existing encrypted/masked fields are preserved. |
| Update | Non-blank `api_key` | 200 | `OK` | Encrypted value changes, mask updates, plaintext absent. |
| Update | Blank `api_key` | 400 | `INVALID_REQUEST` | No plaintext in response/log assertion where practical. |
| Detail | Same-user config | 200 | `OK` | Masked VO only. |
| Detail | Missing config | 404 | `NOT_FOUND` | No encrypted key returned. |
| Detail | Different-user config | 403 | `FORBIDDEN` | Cross-user object is not returned. |
| List | User has configs | 200 | `OK` | Only same-user rows; only masked keys. |
| List | Invalid status filter | 400 | `INVALID_REQUEST` | No query with unchecked enum. |
| Disable | Same-user enabled config | 200 | `OK` | Status becomes `DISABLED`; `updated_at` changes. |
| Disable | Different-user config | 403 | `FORBIDDEN` | No update. |
| Bind app | Same-user app and enabled config | 200 | `OK` | App default config id updates. |
| Bind app | Same-user app, disabled config | 400 or 409 | `MODEL_CONFIG_NOT_READY` | No binding. |
| Bind app | Different-user config | 403 | `FORBIDDEN` | No binding. |
| `/v1/models` | Bound enabled config | 200 | OpenAI list | Still returns chat model/provider only. |
| `/v1/models` | Bound disabled config | 409 | `model_config_not_ready` | Disabled config not exposed. |

If existing `BusinessException` cannot return 403/404 today, add a minimal status-aware admin exception path or extend the exception handling contract. Do not collapse tenant failures into generic 500.

## Good / Base / Bad Cases

Good cases:

- Admin creates a model config with a valid upstream key and receives only `api_key_masked`.
- Admin updates non-secret fields without sending `api_key`; stored encrypted key remains unchanged.
- Admin rotates `api_key`; encrypted value changes and the new masked value appears.
- Admin binds an app to an enabled model config owned by the same user.
- `/v1/models` continues to return an OpenAI-compatible model list for an authenticated app bound to an enabled config.

Base cases:

- Existing manual rows with `api_key_encrypted = null` can still be read/listed safely as masked null, but new Admin API creates must store encrypted/masked key values.
- Existing `/api/health`, `/actuator/health`, gateway auth tests, app API key hashing, and unmatched route behavior remain unchanged.
- Admin responses use `ApiResponse`; `/v1/*` responses retain OpenAI-compatible shapes where already implemented.

Bad cases:

- Plaintext upstream API key appears in DB entity fields, response body, exception message, or logs.
- Cross-user model config detail, update, disable, or app binding succeeds.
- Disabled model config is returned by `/v1/models`.
- Blank encryption secret silently stores plaintext or a reversible placeholder.
- Admin API leaks `api_key_encrypted`.
- Implementation adds chat completions, upstream forwarding, RAG retrieval, or frontend UI.

## Required Tests

Add or update focused tests. Required assertion points:

- `UpstreamApiKeyEncryptorTest` or equivalent:
  - encrypt output differs from plaintext.
  - decrypt(encrypt(value)) equals original.
  - repeated encryption of same value produces different ciphertext because IV is random.
  - blank secret is rejected.
  - malformed encrypted payload fails safely without leaking secrets.
- `UpstreamApiKeyMaskerTest` or equivalent:
  - normal keys are masked.
  - short keys are fully or mostly masked.
  - returned mask is never equal to plaintext.
- `ModelConfigServiceTest`:
  - create persists encrypted and masked key, not plaintext.
  - update without key preserves encrypted/masked key.
  - update with key replaces encrypted/masked key.
  - disable scopes by user and status.
  - list/detail scope by user.
  - invalid embedding config remains covered.
- `ModelConfigAdminControllerTest` or equivalent:
  - create/update/detail/list/disable return `ApiResponse` masked VO only.
  - missing/invalid `X-Admin-User-Id` fails safely.
  - response bodies never contain plaintext or `api_key_encrypted`.
  - cross-user requests are rejected.
- `AppServiceTest`:
  - bind default model config succeeds for same-user enabled config.
  - bind fails for cross-user or disabled config.
  - `resolveDefaultModelConfig` still uses enabled same-user lookup.
- `OpenAiModelsControllerTest`:
  - disabled config remains 409 `model_config_not_ready`.
  - response does not contain upstream key fields.
- Existing regression tests:
  - API key auth filter tests.
  - global exception handler tests.

Required commands:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,AppServiceTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Specs To Update

- `.trellis/spec/backend/database-guidelines.md`
  - Replace placeholder upstream key wording with encrypted/masked Admin API baseline.
  - Add validation cases for encrypted key persistence and update/rotation behavior.
- `.trellis/spec/backend/error-handling.md`
  - Document admin model config error codes/statuses and secret-safe responses.
  - Keep `/v1/models` OpenAI-compatible error behavior distinct from admin errors.
- `.trellis/spec/backend/logging-guidelines.md`
  - Add explicit upstream model admin API key logging prohibition if missing.
- `.trellis/spec/backend/quality-guidelines.md`
  - Add completion checks for upstream key encryption/masking and tenant-scoped admin APIs if missing.
- `.trellis/spec/sangui-rag-gateway.md`
  - Add Admin API baseline, temporary admin user header, upstream key storage contract, and test matrix.

## Files Likely To Modify

Expected implementation files:

- `backend/src/main/java/com/sangui/raggateway/common/config/*`
- `backend/src/main/java/com/sangui/raggateway/common/security/*`
- `backend/src/main/java/com/sangui/raggateway/common/exception/*`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/*DTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/*VO.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/*DTO.java`
- `backend/src/main/java/com/sangui/raggateway/app/*VO.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java` or a narrowly named binding controller.
- `backend/src/main/resources/application.yml`
- `.env.example`

Expected test files:

- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyMaskerTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiModelsControllerTest.java`

Spec/docs files:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/sangui-rag-gateway.md`

Migration expectation:

- Prefer no schema migration if existing `rag_model_config.api_key_encrypted` and `api_key_masked` are sufficient.
- Add a new migration only if implementation introduces a necessary column such as encryption metadata. Do not modify already committed migrations unless explicitly requested.

## Implementation Plan

1. Add encryption properties and upstream API key encrypt/decrypt/mask utilities under `common.config` and `common.security`.
2. Extend model config service with explicit command-style methods for create/update/list/detail/disable, validating text fields, embedding field dependency, tenant scope, and key handling.
3. Add DTO/VO conversion that never exposes plaintext or encrypted upstream keys.
4. Add admin model config controller using `X-Admin-User-Id` as a temporary user boundary.
5. Extend app service with same-user default model config binding behavior.
6. Add admin app binding endpoint or controller method.
7. Ensure `/v1/models` still resolves only enabled same-user default configs and returns 409 for disabled/missing config.
8. Add focused tests for encryption, masking, model service, admin controllers, app binding, and `/v1/models` regression.
9. Update specs listed above to document the new executable contracts.
10. Run targeted Maven tests and full `mvn test`.

## Acceptance Criteria

- [ ] Admin can create a model config with upstream API key.
- [ ] Admin can update non-secret fields without re-entering the key.
- [ ] Admin can rotate the upstream API key by sending a new key.
- [ ] Admin can detail/list/disable configs with same-user scoping.
- [ ] Admin can bind an app to a same-user enabled model config.
- [ ] Cross-user model config access and binding are rejected.
- [ ] Disabled configs are not usable by `/v1/models`.
- [ ] `api_key_encrypted` is persisted as ciphertext, never plaintext.
- [ ] `api_key_masked` is returned instead of plaintext or ciphertext.
- [ ] No response body includes plaintext upstream key or `api_key_encrypted`.
- [ ] Tests cover Good/Base/Bad cases above.
- [ ] Required backend specs and project spec are updated.
- [ ] No business implementation outside the stated scope is added.

