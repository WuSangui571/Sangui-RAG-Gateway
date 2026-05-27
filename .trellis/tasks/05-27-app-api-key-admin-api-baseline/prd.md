# 应用 API Key 管理 Admin API 基线

## Task Classification

Complex Task.

This task changes Admin API contracts, database-backed app/API-key management behavior, secret handling, tenant isolation, and gateway-auth regression coverage. It must be implemented through the Trellis task workflow after focused code research and context injection.

## Current Project State

- The project is on `main` with a clean working directory at task creation time.
- Previous completed work established:
  - app/API key database and `/v1/*` API-key authentication baseline.
  - authenticated `GET /v1/models` using the app default model config.
  - upstream model config Admin API and encrypted upstream API-key storage.
- Current operational gap: app and app API keys still lack a complete Admin API management entry point, so manual database insertion remains part of the workflow.

## Goal

Provide the backend Admin API baseline for managing apps and app API keys so the minimum management loop becomes:

```text
Create app
  -> Create app API key with one-time plaintext return
  -> Bind default model config
  -> Call GET /v1/models with the app API key
```

## Scope

Implement backend-only Admin APIs for:

- Creating apps.
- Listing and reading same-user apps.
- Creating app API keys under same-user apps.
- Listing app API keys without returning plaintext or hash.
- Disabling or revoking app API keys.
- Preserving existing `/v1/*` gateway authentication behavior.

## Explicit Non-Goals

- Do not implement frontend UI.
- Do not implement real admin login or Spring Security admin authentication.
- Do not replace the temporary `X-Admin-User-Id` mechanism.
- Do not implement `/v1/chat/completions`.
- Do not implement RAG retrieval, document upload, knowledge base binding, rate limiting, quotas, or request logs.
- Do not change upstream model config encryption behavior except where tests need to compose the manual flow.
- Do not return plaintext API keys outside the create-key response.
- Do not return `key_hash`, encrypted secrets, raw authorization headers, or stack traces in any Admin API response.

## Actors and Identity

Temporary admin identity remains:

```http
X-Admin-User-Id: <positive long>
```

Rules:

- Required for all new `/api/admin/**` endpoints.
- Missing, non-numeric, or non-positive values return `400 INVALID_REQUEST`.
- All app/API-key admin operations are scoped to this user ID.

## API Contracts

All Admin APIs use the existing `ApiResponse<T>` envelope:

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

Errors use the existing Admin envelope, not OpenAI-compatible gateway errors.

### Create App

```http
POST /api/admin/apps
X-Admin-User-Id: 100
Content-Type: application/json
```

Request:

```json
{
  "name": "Demo App"
}
```

Validation:

- `name` is required and must not be blank.
- Optional future fields must not be added unless required by existing service contracts.

Response `200 OK`:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "user_id": 100,
    "name": "Demo App",
    "status": "ENABLED",
    "default_model_config_id": null,
    "created_at": "2026-05-27T10:00:00",
    "updated_at": "2026-05-27T10:00:00"
  }
}
```

Field naming should follow existing JSON naming strategy in the backend. If current responses use camelCase, use camelCase consistently instead of adding per-field snake_case overrides.

### List Apps

```http
GET /api/admin/apps
X-Admin-User-Id: 100
```

Query:

- Optional `status=ENABLED|DISABLED` if existing service patterns support it.
- Pagination is recommended if there is already a pagination pattern; otherwise keep list baseline narrow.

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 1,
      "user_id": 100,
      "name": "Demo App",
      "status": "ENABLED",
      "default_model_config_id": 10,
      "created_at": "2026-05-27T10:00:00",
      "updated_at": "2026-05-27T10:00:00"
    }
  ]
}
```

Tenant rule:

- Return only apps owned by `X-Admin-User-Id`.

### Get App Detail

```http
GET /api/admin/apps/{id}
X-Admin-User-Id: 100
```

Response:

- Same app VO shape as create/list item.

Tenant and existence behavior:

- If no app exists with `id`, return `404 NOT_FOUND`.
- If app exists but belongs to another user, return `403 FORBIDDEN` with a generic `Access denied` message.

### Create App API Key

```http
POST /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: 100
Content-Type: application/json
```

Request:

```json
{
  "name": "Production Key",
  "expires_at": "2026-12-31T23:59:59"
}
```

Validation:

- `appId` must identify a same-user app.
- `name` is required and must not be blank.
- `expires_at` is optional.
- If `expires_at` is provided, it must be in the future.

Response `200 OK`:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "app_id": 10,
    "user_id": 100,
    "name": "Production Key",
    "key": "sk-sangui-plaintext-returned-once",
    "key_prefix": "sk-sangui-abc123",
    "status": "ACTIVE",
    "expires_at": "2026-12-31T23:59:59",
    "last_used_at": null,
    "revoked_at": null,
    "created_at": "2026-05-27T10:00:00",
    "updated_at": "2026-05-27T10:00:00"
  }
}
```

Secret rule:

- `key` is returned only in this create response.
- `key_hash` is never returned.
- The plaintext key must not be persisted.
- Logs and errors must not include `key`.

### List App API Keys

```http
GET /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: 100
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "id": 1,
      "app_id": 10,
      "user_id": 100,
      "name": "Production Key",
      "key_prefix": "sk-sangui-abc123",
      "status": "ACTIVE",
      "expires_at": "2026-12-31T23:59:59",
      "last_used_at": null,
      "revoked_at": null,
      "created_at": "2026-05-27T10:00:00",
      "updated_at": "2026-05-27T10:00:00"
    }
  ]
}
```

Secret rule:

- Do not return `key`.
- Do not return `key_hash`.
- Do not return authorization header values.

Tenant rule:

- `appId` must be a same-user app before listing keys.

### Disable App API Key

```http
POST /api/admin/api-keys/{id}/disable
X-Admin-User-Id: 100
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "app_id": 10,
    "user_id": 100,
    "name": "Production Key",
    "key_prefix": "sk-sangui-abc123",
    "status": "DISABLED",
    "expires_at": "2026-12-31T23:59:59",
    "last_used_at": null,
    "revoked_at": null,
    "created_at": "2026-05-27T10:00:00",
    "updated_at": "2026-05-27T10:05:00"
  }
}
```

Status behavior:

- `ACTIVE -> DISABLED` is allowed.
- `DISABLED -> DISABLED` may be idempotent.
- `REVOKED -> DISABLED` should be rejected unless existing service conventions prefer idempotent terminal-state handling.

Gateway behavior:

- A disabled key must fail `/v1/*` authentication with `401 invalid_api_key`.

### Revoke App API Key

```http
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: 100
```

Response:

- Same safe key VO shape as disable, with `status=REVOKED` and `revoked_at` populated.

Status behavior:

- `ACTIVE -> REVOKED` is allowed.
- `DISABLED -> REVOKED` is allowed unless existing domain rules reject it.
- `REVOKED -> REVOKED` may be idempotent.

Gateway behavior:

- A revoked key must fail `/v1/*` authentication with `401 invalid_api_key`.

## Validation and Error Matrix

| Area | Scenario | HTTP | Code | Assertion |
|---|---|---:|---|---|
| Admin identity | Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Admin envelope, no stack trace. |
| Admin identity | Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Admin envelope. |
| Admin identity | `X-Admin-User-Id <= 0` | 400 | `INVALID_REQUEST` | Controller/service validation. |
| JSON | Malformed body | 400 | `INVALID_REQUEST` | Message must not echo body content. |
| Create app | Blank name | 400 | `INVALID_REQUEST` | No row inserted. |
| Get app | Missing app ID | 404 | `NOT_FOUND` | Safe message. |
| Get app | Existing app owned by different user | 403 | `FORBIDDEN` | Generic `Access denied`. |
| List apps | Same-user apps exist | 200 | `OK` | Excludes other-user rows. |
| Create key | Missing app | 404 | `NOT_FOUND` | No key inserted. |
| Create key | Cross-user app | 403 | `FORBIDDEN` | No key inserted; generic message. |
| Create key | Blank name | 400 | `INVALID_REQUEST` | No plaintext or hash in response. |
| Create key | Past `expires_at` | 400 | `INVALID_REQUEST` | No key inserted. |
| Create key | Valid request | 200 | `OK` | Returns one-time `key`; persists only hash/prefix. |
| List keys | Same-user app | 200 | `OK` | Returns prefix/status only; no `key` or `key_hash`. |
| List keys | Cross-user app | 403 | `FORBIDDEN` | Does not enumerate keys. |
| Disable key | Missing key | 404 | `NOT_FOUND` | Safe message. |
| Disable key | Cross-user key | 403 | `FORBIDDEN` | Generic `Access denied`. |
| Disable key | Active key | 200 | `OK` | Status becomes `DISABLED`; `updated_at` changes. |
| Revoke key | Active/disabled key | 200 | `OK` | Status becomes `REVOKED`; `revoked_at` set. |
| Gateway auth | Disabled/revoked key calls `/v1/models` | 401 | `invalid_api_key` | OpenAI-compatible error shape. |

## Good / Base / Bad Cases

Good cases:

- Same-user admin creates an app and receives enabled app metadata.
- Same-user admin creates an API key and receives plaintext exactly once.
- Same-user admin lists API keys and sees only `key_prefix`, status, timestamps, and metadata.
- Same-user admin disables or revokes a key; subsequent `/v1/models` with that key returns `401 invalid_api_key`.
- Same-user admin creates app, creates key, binds default model config, then calls `/v1/models` successfully.

Base cases:

- Existing `/api/admin/model-configs/**` and app default model config binding behavior remains unchanged.
- Existing `/v1/models` success and `model_config_not_ready` behavior remains unchanged for active keys.
- Existing `ApiKeyGenerator`, `ApiKeyHasher`, and `GatewayAuthFilter` contracts remain the source of truth for key format and validation.
- Admin API errors continue using `ApiResponse`; public gateway errors continue using OpenAI-compatible shape.

Bad cases:

- Missing or malformed admin identity fails before business mutation.
- Cross-user app/key access fails with `403 FORBIDDEN`.
- Missing resource fails with `404 NOT_FOUND`.
- Blank names, past expiry, and invalid status transitions fail with `400 INVALID_REQUEST`.
- Lists/details never include plaintext key or hash.
- Logs and exception messages never include plaintext keys.

## Likely Data and Domain Contracts

Existing `rag_app` and `rag_api_key` tables from `V2__create_app_api_key_tables.sql` should be reused if they already contain required columns.

Expected status values:

```text
App: ENABLED, DISABLED
ApiKey: ACTIVE, DISABLED, EXPIRED, REVOKED
```

Implementation should avoid schema changes unless code research proves a missing column or index is required. If a migration is needed, add a new ordered migration rather than editing existing migrations.

## Required Tests and Assertion Points

Targeted tests:

```text
AppAdminControllerTest
ApiKeyAdminControllerTest
AppServiceTest
ApiKeyServiceTest
GatewayAuthFilterTest
OpenAiModelsControllerTest
GlobalExceptionHandlerTest
GlobalExceptionHandlerIntegrationTest
```

Required assertions:

- Create app persists `user_id`, `name`, `status=ENABLED`, timestamps.
- List/detail app is same-user scoped.
- Cross-user app detail returns 403; missing app returns 404.
- Create key returns plaintext in create VO only.
- Create key persists `key_hash` and `key_prefix`; plaintext is not persisted.
- List key/detail-like VO never has `key` or `key_hash`.
- Disable and revoke enforce same-user ownership.
- Disable and revoke update status and relevant timestamps.
- Disabled/revoked keys fail gateway authentication.
- Malformed JSON maps to 400 `INVALID_REQUEST` without echoing request body.
- Admin responses do not contain OpenAI `error` object.
- Gateway invalid key responses do not contain admin `code/message/data` envelope.

Recommended manual closure test:

```text
1. POST /api/admin/apps
2. POST /api/admin/apps/{appId}/api-keys
3. POST /api/admin/model-configs
4. PUT /api/admin/apps/{appId}/default-model-config
5. GET /v1/models with Authorization: Bearer <created app key>
6. POST /api/admin/api-keys/{id}/disable or /revoke
7. GET /v1/models again and verify 401 invalid_api_key
```

## Must-Run Test Commands

From `backend/`:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,AppServiceTest,ApiKeyServiceTest" test
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,GatewayAuthFilterTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Implementation Notes for DeepSeek

- Prefer extending existing `app` and `apikey` module patterns over introducing new generic admin abstractions.
- Keep controllers thin. Put validation, tenant checks, key generation/hash persistence, and status transitions in services.
- Reuse `ApiKeyGenerator` and `ApiKeyHasher`.
- Reuse existing `BusinessException` HTTP status support for 400/403/404.
- Do not add dependencies unless existing code cannot meet the contract.
- Add or update specs only if implementation discovers an executable contract not already recorded here or in `.trellis/spec/`.
