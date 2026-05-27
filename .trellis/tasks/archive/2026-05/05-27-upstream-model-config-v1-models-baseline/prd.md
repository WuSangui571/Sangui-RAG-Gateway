# 上游模型配置与 /v1/models 基线

## Task Classification

Complex Task.

This task touches backend database schema, domain services, app-model configuration association, public `/v1/*` gateway API behavior, API key request context reuse, secret masking/storage rules, and test/spec synchronization.

## Goal

Implement the first real authenticated OpenAI-compatible gateway endpoint after API key authentication:

```http
GET /v1/models
Authorization: Bearer sk-sangui-...
```

The endpoint should resolve the authenticated app from `GatewayRequestContextHolder`, load the app's enabled default upstream model config, and return an OpenAI-compatible model list response. It must not implement chat completions, upstream forwarding, RAG retrieval, document processing, frontend UI, or admin model configuration APIs.

## Product Boundary

This task serves the lightweight OpenAI-compatible RAG gateway by connecting:

```text
valid app API key -> enabled app -> default enabled model config -> /v1/models response
```

It reduces uncertainty for the later `/v1/chat/completions` task by establishing the model configuration table, Java domain, app association, secret storage rule, and OpenAI-compatible response shape.

## Requirements

- Add `rag_model_config` database schema through a Flyway migration.
- Store upstream API keys only as encrypted/masked placeholders; never store plaintext.
- Add backend `model` package with minimal entity/status/mapper/service contracts.
- Add a default model config association to `rag_app` if code research confirms this is the simplest app-to-model contract.
- Enforce `app.user_id == model_config.user_id` when associating or resolving app default model config.
- Implement `GET /v1/models` only.
- Reuse `GatewayRequestContextHolder`; do not re-authenticate inside the controller.
- Return OpenAI-compatible success shape for configured enabled model.
- Return OpenAI-compatible safe error when an authenticated enabled app has no enabled default model config.
- Preserve API key filter behavior: invalid/missing key must still return `401 invalid_api_key` from `GatewayAuthFilter`.
- Add focused backend tests for service behavior, endpoint success/error behavior, invalid auth regression, secret non-leakage, and global exception behavior.
- Update relevant spec docs after behavior/schema contracts are implemented.

## Non-Goals / Forbidden Scope

- Do not implement `POST /v1/chat/completions`.
- Do not implement RAG retrieval, prompt building, document ingestion, embeddings, request logs, streaming, or upstream HTTP calls.
- Do not add frontend UI or frontend API clients.
- Do not add admin CRUD endpoints for model config unless strictly required by tests; service-level create/query methods are enough.
- Do not introduce Redis, MinIO, encryption KMS, or external provider clients in this task.
- Do not store or return plaintext upstream API keys.
- Do not broaden OpenAI API support beyond `GET /v1/models`.
- Do not refactor unrelated app/API key/auth code except where needed for the model association.

## Proposed Database Contract

### New Table: `rag_model_config`

Fields:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Owner boundary. |
| `name` | `VARCHAR(255)` | yes | Admin-facing model config name. |
| `provider_name` | `VARCHAR(128)` | yes | Provider label, e.g. `openai-compatible`, `openai`, `deepseek`. |
| `base_url` | `VARCHAR(1024)` | yes | Upstream OpenAI-compatible base URL. |
| `api_key_encrypted` | `TEXT` | no | Encrypted upstream key placeholder. Never plaintext. |
| `api_key_masked` | `VARCHAR(128)` | no | Safe display value such as `sk-...abcd`; never authenticate with it. |
| `chat_model` | `VARCHAR(255)` | yes | Model id returned by `/v1/models`, e.g. `gpt-4o-mini`. |
| `embedding_model` | `VARCHAR(255)` | no | Embedding model id for later RAG tasks. |
| `embedding_dimension` | `INTEGER` | no | Required only when `embedding_model` is set. |
| `status` | `VARCHAR(32)` | yes | `ENABLED` or `DISABLED`. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`; service updates on mutation. |

Required indexes:

```text
PRIMARY KEY rag_model_config(id)
idx_rag_model_config_user_status on rag_model_config(user_id, status)
idx_rag_model_config_provider_model on rag_model_config(provider_name, chat_model)
```

Optional uniqueness:

```text
No uniqueness requirement for chat_model. Multiple users may configure the same upstream model.
```

### App Association

Preferred minimal schema:

```text
ALTER TABLE rag_app ADD COLUMN default_model_config_id BIGINT NULL;
ALTER TABLE rag_app ADD CONSTRAINT fk_rag_app_default_model_config
  FOREIGN KEY (default_model_config_id) REFERENCES rag_model_config(id);
CREATE INDEX idx_rag_app_default_model_config ON rag_app(default_model_config_id);
```

Tenant rule:

```text
When resolving or assigning default_model_config_id, app.user_id must equal model_config.user_id.
```

Because PostgreSQL cannot enforce this cross-table owner equality with a simple FK, enforce it in service logic and tests.

## Domain Contract

Add package:

```text
backend/src/main/java/com/sangui/raggateway/model
```

Expected classes:

```text
ModelConfigEntity
ModelConfigStatus
ModelConfigMapper
ModelConfigService
```

Minimum service capabilities:

```text
create(...)
findById(Long id)
findEnabledByIdAndUserId(Long id, Long userId)
isEnabled(ModelConfigEntity entity)
```

If the existing code style favors simple parameterized service methods over command objects, keep that style for this task. Avoid adding admin DTO/VO classes unless needed.

## Gateway API Contract

### Endpoint

```http
GET /v1/models
Authorization: Bearer sk-sangui-...
```

No request body. Query parameters are not required and may be ignored.

### Success Response

OpenAI-compatible model list shape:

```json
{
  "object": "list",
  "data": [
    {
      "id": "configured-chat-model",
      "object": "model",
      "created": 0,
      "owned_by": "configured-provider-name"
    }
  ]
}
```

Field contract:

| Field | Source | Notes |
|---|---|---|
| `object` | literal | Must be `list`. |
| `data[].id` | `rag_model_config.chat_model` | Do not expose embedding model in `/v1/models` for this baseline. |
| `data[].object` | literal | Must be `model`. |
| `data[].created` | literal or derived | Prefer `0` for baseline compatibility/stability unless project style chooses epoch seconds. |
| `data[].owned_by` | `rag_model_config.provider_name` | Safe provider label only. |

### Error Response For Missing Config

Decision: Return an OpenAI-compatible safe error, not an empty list.

Rationale:

- Authenticated app exists, but the gateway cannot serve requests without a default upstream model.
- Returning an empty list would make clients think the gateway is healthy but has no models; later chat completions would fail anyway.
- This is an actionable server-side configuration problem.

Recommended response:

```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "error": {
    "message": "Default model config is not configured for this app.",
    "type": "invalid_request_error",
    "code": "model_config_not_ready"
  }
}
```

If the implementation reuses existing `GatewayException`, add/normalize the code in spec docs.

## Validation / Error Matrix

| Case | HTTP | Response Shape | Code | Required Assertions |
|---|---:|---|---|---|
| Missing `Authorization` | 401 | OpenAI-compatible error | `invalid_api_key` | Produced by `GatewayAuthFilter`; no controller execution. |
| Non-Bearer auth | 401 | OpenAI-compatible error | `invalid_api_key` | No raw header/token in response. |
| Unknown/disabled/expired key | 401 | OpenAI-compatible error | `invalid_api_key` | Existing auth tests remain green. |
| Valid key, enabled app, enabled default model config | 200 | OpenAI-compatible list | n/a | `object=list`, one model with configured `chat_model`, no admin envelope. |
| Valid key, enabled app, no default model config | 409 | OpenAI-compatible error | `model_config_not_ready` | No `code/message/data` admin envelope; no stack trace. |
| Valid key, default model config is disabled | 409 | OpenAI-compatible error | `model_config_not_ready` | Disabled config must not be returned. |
| App and model config have different `user_id` | 409 or service validation failure | OpenAI-compatible error at gateway, domain failure in service | `model_config_not_ready` or internal domain error | Cross-user config must not be exposed. |
| Upstream API key fields present | 200 or service result | No plaintext leakage | n/a | Response/log/test fixtures must not include plaintext upstream key. |
| `/v1/chat/completions` after this task | 404 | Existing safe admin 404 or auth-filtered 404 for valid key | `NOT_FOUND` admin envelope after auth | Must not become implemented accidentally. |

## Good / Base / Bad Cases

Good:

- Active API key belongs to enabled app.
- App has `default_model_config_id`.
- Referenced model config has same `user_id`, status `ENABLED`, and nonblank `chat_model`.
- `GET /v1/models` returns one OpenAI-compatible model entry.

Base:

- `/api/health` and `/actuator/**` are not affected by gateway API key auth or model config.
- Existing API key generation, hashing, lookup, and last-used update behavior stays unchanged.
- `POST /v1/chat/completions` remains unimplemented.

Bad:

- Invalid app API key receives `401 invalid_api_key`.
- Missing/disabled/default-cross-user model config does not return model data.
- Plaintext upstream API key does not appear in database entity string output, response JSON, logs, or test assertions.

## Acceptance Criteria

- [ ] Flyway migration creates `rag_model_config` and app default model association with required indexes.
- [ ] Java entity/enum/mapper/service exist under `model` and follow existing MyBatis-Plus patterns.
- [ ] App entity/service support default model config lookup/association without breaking existing app/API key tests.
- [ ] `GET /v1/models` uses `GatewayRequestContextHolder` and returns OpenAI-compatible model list for valid authenticated app.
- [ ] Missing/disabled/default-cross-user model config returns OpenAI-compatible safe error.
- [ ] Invalid API key behavior remains `401 invalid_api_key` from the filter.
- [ ] No plaintext upstream API key is stored, logged, or returned.
- [ ] Relevant backend specs are updated with concrete schema/API/error/test contracts.
- [ ] Required targeted tests pass.

## Required Tests And Assertion Points

### Model Config Service Tests

Suggested file:

```text
backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java
```

Assertions:

- Creating a model config persists `user_id`, `provider_name`, `base_url`, `chat_model`, status, timestamps.
- Plaintext upstream key is never persisted in `api_key_encrypted` or `api_key_masked`.
- `findEnabledByIdAndUserId` returns enabled same-user config.
- Disabled config is not returned by enabled lookup.
- Different-user config is not returned by enabled lookup.
- `embedding_dimension` validation rejects non-positive values when present.

### App Association Tests

Assertions:

- App can be linked to a same-user default model config.
- App cannot resolve or link to another user's model config.
- Existing app enabled/disabled behavior is unchanged.

### `/v1/models` Endpoint Tests

Suggested file:

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiModelsControllerTest.java
```

Assertions:

- Valid key + enabled app + enabled default model config returns HTTP 200.
- Response has `$.object == "list"`.
- Response has `$.data[0].id == chat_model`.
- Response has `$.data[0].object == "model"`.
- Response has `$.data[0].owned_by == provider_name`.
- Response does not have admin envelope fields `$.code`, `$.message`, `$.data.code`.
- Response does not contain upstream API key plaintext, encrypted value, masked value, or authorization token.
- Missing default model config returns HTTP 409 with `$.error.code == "model_config_not_ready"`.
- Disabled model config returns HTTP 409 with `$.error.code == "model_config_not_ready"`.
- Invalid/missing key returns HTTP 401 with `$.error.code == "invalid_api_key"`.

### Regression Tests

Existing tests that must remain green:

```text
GlobalExceptionHandlerTest
GlobalExceptionHandlerIntegrationTest
ApiKeyGeneratorTest
ApiKeyHasherTest
ApiKeyServiceTest
GatewayAuthFilterTest
```

Because `/v1/models` changes from "unimplemented 404" to "implemented authenticated endpoint", update tests/specs that currently assert valid-key `/v1/models` 404. Keep `/v1/chat/completions` unimplemented.

## Required Commands

Run from `backend/`:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Files Likely To Modify

Expected implementation files:

```text
backend/src/main/resources/db/migration/V3__create_model_config_and_app_default.sql
backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigStatus.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigMapper.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
backend/src/main/java/com/sangui/raggateway/app/AppEntity.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModelsController.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModel.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModelsResponse.java
backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiModelsControllerTest.java
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/backend/database-guidelines.md
.trellis/spec/backend/error-handling.md
```

Only modify files outside this list when code research shows an existing pattern requires it.

## Open Questions

No blocking user clarification is required before implementation.

Assumption fixed by this PRD: missing or disabled default model config returns `409 model_config_not_ready` OpenAI-compatible error rather than an empty model list.
