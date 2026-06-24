# Database Guidelines

> PostgreSQL and pgvector rules for Sangui-RAG-Gateway. The highest-risk area is tenant-safe vector retrieval; every query must preserve user/app/knowledge-base boundaries.

## Database Stack

Use PostgreSQL as the main database and pgvector for embeddings.

Recommended persistence stack:

```text
PostgreSQL + pgvector
MyBatis-Plus
Migration files under src/main/resources/db/migration
```

Redis is used for rate limits, quota counters, cache, and task state support. Redis is not the source of truth for business records.

## Core Tables

Recommended tables:

```text
sys_user
rag_app
rag_api_key
rag_model_config
rag_knowledge_base
rag_document
rag_document_chunk
rag_request_log
```

## Table Rules

- Every business table must have primary key `id`.
- Core tables must include `created_at` and `updated_at`.
- Soft deletion may use `deleted` or `deleted_at`; choose one and keep it consistent once implemented.
- Tenant-related tables must include `user_id` or have a mandatory relation that resolves to a user.
- Status fields must use explicit enum values.
- Large text may use `text`.
- Flexible metadata should use `jsonb`.
- Embeddings should use fixed-dimension pgvector columns when the deployment has one global dimension. If the product allows per-knowledge-base dimensions, store vectors in a separate table with a variable `VECTOR` column and enforce dimensions in service/provider validation before insert.

## Tenant Isolation

Core data must not be queryable across user boundaries.

Tenant-sensitive objects:

```text
App
ApiKey
KnowledgeBase
Document
DocumentChunk
ModelConfig
ApiRequestLog
```

If a query is used by a user-facing admin API, it must be scoped by the current user unless the endpoint is explicitly system-admin only.

If a query is used by `/v1/chat/completions`, it must be scoped through the API key's app and knowledge base.

## Vector Retrieval Rules

Never run global vector search.

Forbidden:

```sql
SELECT *
FROM rag_document_chunk
ORDER BY embedding <=> ?
LIMIT 5;
```

Minimum acceptable scope:

```sql
SELECT *
FROM rag_document_chunk
WHERE knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

Preferred scope when `user_id` exists:

```sql
SELECT *
FROM rag_document_chunk
WHERE user_id = ?
  AND knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

When an app can bind multiple knowledge bases, use an app-authorized list:

```sql
SELECT *
FROM rag_document_chunk
WHERE user_id = ?
  AND knowledge_base_id = ANY(?)
ORDER BY embedding <=> ?
LIMIT ?;
```

Do not filter tenant boundaries in Java after vector search. The SQL query itself must enforce the boundary.

## Embedding Dimension Rules

- A knowledge base has exactly one embedding model and vector dimension.
- The embedding dimension is fixed at knowledge-base creation time.
- Reject attempts to insert chunks with a mismatched vector dimension.
- Changing a knowledge base's embedding model requires re-embedding all documents or creating a new knowledge base.

## Status Enums

Document status:

```text
UPLOADED
PARSING
PARSED
EMBEDDING
READY
FAILED
```

App status:

```text
ENABLED
DISABLED
```

API key status:

```text
ACTIVE
DISABLED
EXPIRED
REVOKED
```

Knowledge base status should distinguish "created but empty", "processing", "ready", and "failed" when implemented.

## API Key Storage

Never store plaintext app API keys.

Store:

```text
key_hash
key_prefix
status
expires_at
last_used_at
rate_limit_config
quota_config
```

The full key value is only returned once on creation.

### Implemented App/API Key Baseline

The baseline App/API key schema is introduced by:

```text
backend/src/main/resources/db/migration/V2__create_app_api_key_tables.sql
```

`rag_app` columns:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Owner boundary for future tenant-scoped admin queries. |
| `name` | `VARCHAR(255)` | yes | App display name. |
| `status` | `VARCHAR(32)` | yes | Application enum values: `ENABLED`, `DISABLED`. |
| `created_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`; services must update it when mutating the row. |

`rag_api_key` columns:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `app_id` | `BIGINT` | yes | Foreign key to `rag_app(id)`. |
| `user_id` | `BIGINT` | yes | Denormalized owner boundary for future tenant-scoped admin queries. |
| `name` | `VARCHAR(255)` | yes | Human-readable key label. |
| `key_hash` | `VARCHAR(128)` | yes | Unique deterministic hash of the full plaintext key. Never store plaintext. |
| `key_prefix` | `VARCHAR(32)` | yes | Safe display prefix only; not sufficient to authenticate. |
| `status` | `VARCHAR(32)` | yes | Application enum values: `ACTIVE`, `DISABLED`, `EXPIRED`, `REVOKED`. |
| `expires_at` | `TIMESTAMP` | no | `NULL` means no expiry. |
| `last_used_at` | `TIMESTAMP` | no | Updated after successful `/v1/*` authentication. |
| `revoked_at` | `TIMESTAMP` | no | Set when revoke behavior is implemented. |
| `requests_per_minute` | `INTEGER` | no | Added by `V13__add_api_key_rate_limit_quota.sql`. Null means use `rag.gateway.api-key-limits.default-requests-per-minute`; positive values override the default. |
| `tokens_per_minute` | `INTEGER` | no | Added by `V13__add_api_key_rate_limit_quota.sql`. Null means use `rag.gateway.api-key-limits.default-tokens-per-minute`; positive values override the default. |
| `daily_request_quota` | `INTEGER` | no | Added by `V13__add_api_key_rate_limit_quota.sql`. Null means use `rag.gateway.api-key-limits.default-daily-request-quota`; positive values override the default. |
| `daily_token_quota` | `INTEGER` | no | Added by `V13__add_api_key_rate_limit_quota.sql`. Null means use `rag.gateway.api-key-limits.default-daily-token-quota`; positive values override the default. |
| `created_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`; services must update it when mutating the row. |

API key limit configuration:

```yaml
rag:
  gateway:
    api-key-limits:
      enabled: true
      default-requests-per-minute: 60
      default-tokens-per-minute: 60000
      default-daily-request-quota: 1000
      default-daily-token-quota: 1000000
      default-completion-token-reservation: 1024
```

Validation contract:

- All configured default limits and the default completion reservation must be `>= 1`; invalid values must fail startup through configuration validation.
- Existing keys with null limit columns are still protected by configured defaults.
- `ApiKeyRateLimitService` must use `api_key_id` only in Redis keys. It must never use plaintext keys, key hashes, key prefixes, prompts, messages, provider keys, or request bodies.
- The Admin API/frontend productized edit path for these four fields is deferred; until that path exists, values may be managed by SQL/manual seed while runtime enforcement still applies.

Redis counter keys:

```text
rag:api-key-limit:{apiKeyId}:rpm:{yyyyMMddHHmm}
rag:api-key-limit:{apiKeyId}:tpm:{yyyyMMddHHmm}
rag:api-key-limit:{apiKeyId}:daily-requests:{yyyyMMdd}
rag:api-key-limit:{apiKeyId}:daily-tokens:{yyyyMMdd}
```

### Implemented Admin User Baseline

The Admin user schema is introduced by:

```text
backend/src/main/resources/db/migration/V12__create_admin_user_table.sql
```

`sys_user` columns:

| Column | Type | Required | Notes |
|---|---:|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `username` | `VARCHAR(255)` | yes | Unique login name. |
| `password_hash` | `VARCHAR(255)` | yes | BCrypt hash of the password. Never store plaintext passwords. |
| `status` | `VARCHAR(32)` | yes | Application enum values: `ACTIVE`, `DISABLED`. |
| `created_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |

Required indexes and constraints:

```text
PRIMARY KEY sys_user(id)
unique idx_sys_user_username on sys_user(username)
```

Bootstrap boundary:

- `V12__create_admin_user_table.sql` creates only the `sys_user` schema and unique username index.
- Production migrations must not hardcode a default admin password or reusable secret.
- Local development may create the first admin explicitly with a BCrypt hash inserted into `sys_user(username, password_hash, status)`.
- Required validation: `UserServiceTest` covers username/id lookup and active/disabled status; `PasswordHasherTest` covers BCrypt hash/verify behavior.

Required indexes and constraints:

```text
PRIMARY KEY rag_app(id)
idx_rag_app_user_status on rag_app(user_id, status)
PRIMARY KEY rag_api_key(id)
fk_rag_api_key_app rag_api_key(app_id) -> rag_app(id)
unique idx_rag_api_key_hash on rag_api_key(key_hash)
idx_rag_api_key_app on rag_api_key(app_id)
idx_rag_api_key_app_status on rag_api_key(app_id, status)
```

Matching Java contracts:

```text
backend/src/main/java/com/sangui/raggateway/app/AppEntity.java
backend/src/main/java/com/sangui/raggateway/app/AppStatus.java
backend/src/main/java/com/sangui/raggateway/app/AppMapper.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyEntity.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyStatus.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyMapper.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java
```

Validation cases:

| Case | Expected result | Required assertion |
|---|---|---|
| Create enabled app | `status=ENABLED`, owner `user_id` persisted | Service/entity test or mapper integration test. |
| Create API key | plaintext returned only by create result; row stores `key_hash` and `key_prefix` | `ApiKeyServiceTest` must assert no plaintext is persisted. |
| Lookup by key hash | Unique lookup returns at most one row | Unique index exists or mapper/service behavior is covered. |
| Successful auth metadata update | `last_used_at` and `updated_at` move together | `ApiKeyServiceTest` or persistence test. |
| Invalid status literal | Must fail service validation unless application enum explicitly accepts it | `isValid` test for non-`ACTIVE` status. |
| Null API key limit fields | Runtime limiter uses explicit configured defaults | `ApiKeyRateLimitServiceTest`. |
| Non-positive configured defaults | Startup/configuration validation fails visibly | `ApiKeyRateLimitServiceTest` or Spring binding validation test. |
| Redis limiter rejection | Lua check rejects without incrementing counters for the rejected attempt | `ApiKeyRateLimitServiceTest` or Redis integration test. |
| Token reservation reconciliation | Reconciliation/release adjusts the same minute/day windows that received the preflight reservation | `ApiKeyRateLimitServiceTest` and controller tests. |

Run these checks after changing this schema or the matching services:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=ApiKeyRateLimitServiceTest,OpenAiChatCompletionsControllerTest" test
mvn test
```

### Implemented Model Config Baseline

The model config schema and app association is introduced by:

```text
backend/src/main/resources/db/migration/V3__create_model_config_and_app_default.sql
```

`rag_model_config` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Owner boundary. |
| `name` | `VARCHAR(255)` | yes | Admin-facing model config name. |
| `provider_name` | `VARCHAR(128)` | yes | Provider label, e.g. `openai-compatible`, `openai`, `deepseek`. |
| `base_url` | `VARCHAR(1024)` | yes | Upstream OpenAI-compatible base URL. |
| `api_key_encrypted` | `TEXT` | no | Encrypted upstream key placeholder. Never plaintext. |
| `api_key_masked` | `VARCHAR(128)` | no | Safe display value. |
| `capability` | `VARCHAR(32)` | no | Model config capability: `CHAT`, `EMBEDDING`. `CHAT_EMBEDDING` legacy-only after V10 normalization. Added in V9 migration. |
| `chat_model` | `VARCHAR(255)` | no | Model id; required for CHAT, must be null for EMBEDDING. Nullable since V9. |
| `embedding_model` | `VARCHAR(255)` | no | Embedding model id for later RAG tasks. Required for EMBEDDING. |
| `embedding_dimension` | `INTEGER` | no | Required only with embedding model when enabling embedding-capable configs. |
| `status` | `VARCHAR(32)` | yes | `ENABLED` or `DISABLED`. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |

App association (`rag_app`):

| Column | Type | Required | Notes |
|---|---|---|---|
| `default_model_config_id` | `BIGINT` | no | FK to `rag_model_config(id)`. |

Tenant rule: when resolving or assigning `default_model_config_id`, `app.user_id` must equal `model_config.user_id`. Enforced in service logic.

Required indexes:

```text
idx_rag_model_config_user_status on rag_model_config(user_id, status)
idx_rag_model_config_provider_model on rag_model_config(provider_name, chat_model)
idx_rag_app_default_model_config on rag_app(default_model_config_id)
```

Matching Java contracts:

```text
backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigStatus.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigMapper.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
```

Validation cases:

| Case | Expected result | Required assertion |
|---|---|---|
| Create enabled model config | `status=ENABLED`, owner `user_id` persisted | `ModelConfigServiceTest` |
| Plaintext upstream key never persisted | `api_key_encrypted` and `api_key_masked` are null | `ModelConfigServiceTest.shouldNeverPersistPlaintextUpstreamKey` |
| Embedding model with valid dimension | `embedding_model` and positive `embedding_dimension` persisted | `ModelConfigServiceTest.shouldPersistEmbeddingFieldsWhenProvided` |
| Embedding model without dimension or non-positive dimension | Service rejects with `IllegalArgumentException` before insert | `ModelConfigServiceTest` validation tests |
| `findEnabledByIdAndUserId` returns same-user enabled config | Returns entity for matching id+user+ENABLED | Mapper/service integration test. |
| Disabled config not returned by enabled lookup | Returns null | Service or mapper test. |
| Different-user config not returned by enabled lookup | Returns null | Service or mapper test. |
| App resolves default model config | Uses `app.default_model_config_id` plus `app.user_id` | `AppServiceTest` |

Run these checks after changing this schema or the matching services:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,AppServiceTest,OpenAiModelsControllerTest" test
mvn test
```

## Upstream API Key Storage

Never store plaintext upstream API keys.

Store encrypted values:

```text
api_key_encrypted: "v1:<base64url-iv>:<base64url-ciphertext>"
api_key_masked or prefix for display
encryption_version if key rotation is introduced
```

The encryption master key must come from environment variables (`RAG_GATEWAY_ENCRYPTION_SECRET_KEY`), not source code. The `dev` profile provides the local non-production placeholder `local-dev-aes-key-secret-change-me-32chars` (at least 32 UTF-8 characters, AES-256-GCM compatible) as the default. The weak placeholder `local-dev-change-me` (19 characters) is always rejected at startup because it does not satisfy the minimum strength requirement. The documented replacement placeholder `<set-a-strong-32-char-secret>` must be replaced before startup. The `test` profile skips guard checks. Production-like runs must override with a strong `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` of at least 32 characters that is not any known placeholder. The old `RAG_GATEWAY_SECRET_KEY` is deprecated and no longer used for encryption.

### Implemented Encryption Baseline

The encryption infrastructure is implemented via:

```text
backend/src/main/java/com/sangui/raggateway/common/config/EncryptionProperties.java
backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java
backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java
backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyMasker.java
```

Encryption contract:

- Algorithm: AES-256-GCM with random 12-byte IV per encryption.
- Key derivation: SHA-256 of `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` produces a 256-bit AES key.
- Stored format: `v1:<base64url-iv>:<base64url-ciphertext>`.
- The encryptor fails fast if `rag.gateway.encryption.secret-key` is blank at startup.
- Decrypt is available for future upstream forwarding, but decrypted values are never returned from admin APIs.

Masking contract:

- Keys >= 8 characters: keep first 3 and last 4 characters, mask middle with `*`.
- Keys < 8 characters: fully masked.
- Mask is never equal to plaintext.
- Null input returns null.

Validation cases (admin create/update/rotation):

| Case | Expected result | Required assertion |
|---|---|---|
| Create enabled model config | `api_key_encrypted` and `api_key_masked` persisted with non-null values | `ModelConfigServiceTest` |
| Plaintext upstream key never persisted | Only `api_key_encrypted` and `api_key_masked` are stored; plaintext field is input-only | `ModelConfigServiceTest` |
| Update without `api_key` | Existing encrypted/masked fields are preserved unchanged | `ModelConfigServiceTest` |
| Update with non-blank `api_key` | Encrypted value changes (new ciphertext), mask updates to new value | `ModelConfigServiceTest` |
| Update with blank `api_key` | Rejected with `IllegalArgumentException("apiKey must not be blank")` | `ModelConfigServiceTest` |
| Encrypt/decrypt round-trip | `decrypt(encrypt(plaintext)) == plaintext` | `UpstreamApiKeyEncryptorTest` |
| Repeated encryption produces different ciphertext | Random IV ensures uniqueness | `UpstreamApiKeyEncryptorTest` |
| Blank/null secret rejected | `IllegalStateException` at construction | `UpstreamApiKeyEncryptorTest` |
| Malformed payload decrypt fails safely | `IllegalArgumentException` without leaking secrets | `UpstreamApiKeyEncryptorTest` |
| Normal key masking | Masked value != plaintext, preserved prefix | `UpstreamApiKeyMaskerTest` |
| Short key (< 8 chars) fully masked | All characters replaced with `*` | `UpstreamApiKeyMaskerTest` |

Run after changes:

```bash
cd backend
mvn -q "-Dtest=UpstreamApiKeyEncryptorTest,UpstreamApiKeyMaskerTest,ModelConfigServiceTest" test
```

### Implemented Request Log Baseline

The request log schema is introduced by:

```text
backend/src/main/resources/db/migration/V4__create_request_log_table.sql
```

`rag_request_log` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `request_id` | `VARCHAR(64)` | yes | Unique per request |
| `user_id` | `BIGINT` | yes | Tenant boundary |
| `app_id` | `BIGINT` | yes | App boundary |
| `api_key_id` | `BIGINT` | yes | Safe key metadata ID only |
| `model` | `VARCHAR(255)` | no | Resolved model from config |
| `provider_name` | `VARCHAR(128)` | no | Resolved provider from config |
| `status` | `VARCHAR(32)` | yes | `success`, `failure`, or `cancelled` |
| `error_code` | `VARCHAR(64)` | no | Stable gateway error code |
| `latency_ms` | `BIGINT` | no | Total controller elapsed time |
| `upstream_latency_ms` | `BIGINT` | no | Upstream latency when available |
| `prompt_tokens` | `INTEGER` | no | From upstream usage |
| `completion_tokens` | `INTEGER` | no | From upstream usage |
| `total_tokens` | `INTEGER` | no | From upstream usage |
| `messages_count` | `INTEGER` | no | Count only, no content |
| `question_summary` | `VARCHAR(512)` | no | Bounded prefix only |
| `hit_chunk_ids` | `JSONB` | no | Future RAG retrieval IDs |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |

Required indexes:

```text
idx_rag_request_log_app_created_at on rag_request_log(app_id, created_at DESC)
idx_rag_request_log_user_created_at on rag_request_log(user_id, created_at DESC)
idx_rag_request_log_api_key_created_at on rag_request_log(api_key_id, created_at DESC)
unique idx_rag_request_log_request_id on rag_request_log(request_id)
```

Matching Java contracts:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
```

Tenant rule: `rag_request_log` carries `user_id` and `app_id` directly. Future admin log query APIs must scope by user/app. Gateway inserts use resolved context IDs from `GatewayRequestContext`.

Secret safety: persisted rows must never contain app API key plaintext/hash, upstream key plaintext/encrypted, Authorization header, full messages, or provider raw body. Only safe operational fields and IDs are stored.

Validation cases:

| Case | Expected result | Required assertion |
|---|---|---|
| Success request | One `success` row with model, provider, usage, latency | `ApiRequestLogServiceTest` |
| Validation failure | One `failure` row with `invalid_request` error_code | `ApiRequestLogServiceTest` |
| Model config not ready | One `failure` row with `model_config_not_ready` | `ApiRequestLogServiceTest` |
| Upstream 502 | One `failure` row with `upstream_error` | `ApiRequestLogServiceTest` |
| Upstream 504 | One `failure` row with `upstream_timeout` | `ApiRequestLogServiceTest` |
| No sensitive data persisted | Entity toString() does not contain secrets | `ApiRequestLogServiceTest` |
| Insert failure | Exception caught, gateway response unchanged | `ApiRequestLogServiceTest` |

Run these checks after changing this schema or the matching services:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest" test
mvn test
```

### Implemented Request Log Output Observability Schema

The request-log output observability schema is introduced by:

```text
backend/src/main/resources/db/migration/V11__add_request_log_output_observability.sql
```

`rag_request_log` additional columns:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `completion_length` | `INTEGER` | no | Character count of assistant output when available. Safe numeric metadata. |
| `output_capture_status` | `VARCHAR(32)` | yes | Defaults to `DISABLED`. Explicit status, not inferred from nulls. |
| `output_preview` | `TEXT` | no | Bounded redacted preview only. Not returned by list or normal detail APIs. |
| `output_preview_truncated` | `BOOLEAN` | yes | Defaults to `FALSE`; true when original output exceeded preview limit. |
| `output_redacted` | `BOOLEAN` | yes | Defaults to `FALSE`; true when deterministic redaction changed the preview. |
| `output_retention_expires_at` | `TIMESTAMP` | no | Used by cleanup to expire preview content. |

`rag_app` additional column:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `request_log_output_capture_enabled` | `BOOLEAN` | yes | Defaults to `FALSE`; app-level opt-in switch. Effective capture requires this and the global switch. |

Admin mutation contract for this column:

```java
AppService.updateOutputCapture(Long appId, boolean enabled, Long userId)
```

The service must scope the update by `id` and `user_id`, set `request_log_output_capture_enabled` to the requested boolean, update `updated_at`, and return `null` when the app is missing or not owned by the caller. No migration is needed when only exposing this existing column through Admin APIs.

Output access audit table:

```text
rag_request_log_output_access_audit
```

| Column | Type | Required | Notes |
|---|---|---:|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Admin caller from validated admin JWT context. |
| `app_id` | `BIGINT` | yes | App boundary. |
| `request_log_id` | `BIGINT` | no | Null only when the request log was missing. |
| `request_id` | `VARCHAR(64)` | yes | Attempted request id. |
| `access_result` | `VARCHAR(32)` | yes | `GRANTED`, `DENIED`, `NOT_FOUND`, etc. |
| `reason` | `VARCHAR(256)` | no | Bounded optional reason. Never stores preview content. |
| `created_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |

Required indexes:

```text
idx_rag_request_log_output_expiry on rag_request_log(output_retention_expires_at)
idx_rag_request_log_output_audit_user_created_at on rag_request_log_output_access_audit(user_id, created_at DESC)
idx_rag_request_log_output_audit_app_created_at on rag_request_log_output_access_audit(app_id, created_at DESC)
idx_rag_request_log_output_audit_request_id on rag_request_log_output_access_audit(request_id)
```

Retention cleanup contract:

```java
ApiRequestLogService.cleanupExpiredOutputPreviews()
```

The cleanup selects rows where `output_retention_expires_at < now` and `output_preview IS NOT NULL`, then sets `output_preview = NULL`, `output_capture_status = 'EXPIRED'`, and updates `updated_at`. It must not delete request-log rows and must preserve numeric metadata such as `completion_length`.

Validation cases:

| Case | Expected result | Required assertion |
|---|---|---|
| Both global and app switches disabled by default | New rows do not persist output preview by default | `OutputCapturePolicyTest`, gateway controller test. |
| Owner toggles app switch | `rag_app.request_log_output_capture_enabled` is persisted and `updated_at` changes | `AppServiceTest`. |
| Cross-user app switch update | Service returns `null` and does not update the row | `AppServiceTest`, `AppAdminControllerTest`. |
| Captured non-streaming output | Bounded preview metadata maps through `CreateRequestLogCommand` to `ApiRequestLogEntity` | `ApiRequestLogOutputServiceTest`. |
| Audit write | Audit row stores caller/app/request/result/reason only, not preview content | `ApiRequestLogOutputServiceTest`. |
| Expired preview cleanup | Preview is nulled and status becomes `EXPIRED`; row remains | `ApiRequestLogOutputServiceTest`. |

Run after changing this schema or matching services:

```bash
cd backend
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogOutputServiceTest,OutputCapturePolicyTest" test
mvn -q "-Dtest=ApiRequestLogAdminControllerTest,OpenAiChatCompletionsControllerTest" test
```

## Migrations

- Every schema change must be represented by a migration file.
- Migration file names should be ordered and descriptive.
- Migration SQL must create required indexes with tables.
- Include pgvector extension setup in initial migrations.
- Do not rely on ORM auto-DDL outside local experiments.

Recommended indexes:

```text
rag_api_key(key_hash)
rag_app(user_id, status)
rag_model_config(user_id, status)
rag_model_config(provider_name, chat_model)
rag_knowledge_base(user_id, status)
rag_document(knowledge_base_id, status)
rag_document_chunk(knowledge_base_id)
rag_request_log(app_id, created_at)
```

Add vector indexes only after confirming pgvector operator class and distance metric choices.

### Implemented Knowledge Base and Document Upload Schema

The knowledge base/document/chunk schema is introduced by:

```text
backend/src/main/resources/db/migration/V5__create_knowledge_document_tables.sql
```

`rag_knowledge_base` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary. |
| `name` | `VARCHAR(255)` | yes | KB display name. Unique per user. |
| `embedding_model` | `VARCHAR(255)` | yes | Future embedding contract. |
| `embedding_dimension` | `INTEGER` | yes | Must be positive, fixed at creation. |
| `status` | `VARCHAR(32)` | yes | `EMPTY`, `PROCESSING`, `READY`, `FAILED`. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |

Required indexes:

```text
idx_rag_knowledge_base_user_status on (user_id, status)
idx_rag_knowledge_base_user_created_at on (user_id, created_at DESC)
unique idx_rag_knowledge_base_user_name on (user_id, name)
```

`rag_document` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary. |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)`. |
| `original_filename` | `VARCHAR(512)` | yes | Safe filename only. |
| `content_type` | `VARCHAR(255)` | no | Client-provided content type. |
| `file_size` | `BIGINT` | yes | Uploaded size. |
| `storage_path` | `VARCHAR(1024)` | yes | Internal storage key, never exposed by VO. |
| `status` | `VARCHAR(32)` | yes | `UPLOADED`, `PARSING`, `PARSED`, `EMBEDDING`, `READY`, `FAILED`. |
| `chunk_count` | `INTEGER` | yes | Default `0`. |
| `error_message` | `VARCHAR(512)` | no | Bounded admin-safe message. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |

Required indexes:

```text
idx_rag_document_user_status on (user_id, status)
idx_rag_document_kb_status on (knowledge_base_id, status)
idx_rag_document_user_kb_created_at on (user_id, knowledge_base_id, created_at DESC)
```

`rag_document_chunk` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary, denormalized for SQL-level future retrieval. |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)`. |
| `document_id` | `BIGINT` | yes | FK to `rag_document(id)`. |
| `chunk_index` | `INTEGER` | yes | 0-based index within document. |
| `content` | `TEXT` | yes | Chunk text. |
| `token_count` | `INTEGER` | no | Placeholder (character count in baseline). |
| `metadata` | `JSONB` | no | Source filename, parser info. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |

Required indexes:

```text
idx_rag_document_chunk_user_kb on (user_id, knowledge_base_id)
idx_rag_document_chunk_document on (document_id)
unique idx_rag_document_chunk_document_index on (document_id, chunk_index)
```

Tenant rule: every admin query must include `user_id` or explicitly verify ownership before mutation/listing. `rag_document_chunk` carries `user_id` even before retrieval so future vector retrieval can enforce tenant boundaries in SQL.

### Implemented Document Processing Task Schema

The async document-processing task schema is introduced by:

```text
backend/src/main/resources/db/migration/V14__create_document_processing_task_table.sql
```

`rag_document_processing_task` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary duplicated from the document owner. |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)`. |
| `document_id` | `BIGINT` | yes | FK to `rag_document(id)`; unique in the current one-active-task-per-document model. |
| `status` | `VARCHAR(32)` | yes | `PENDING`, `PROCESSING`, `SUCCEEDED`, `RETRYABLE`, `FAILED`, `CANCELED`. |
| `attempt_count` | `INTEGER` | yes | Number of processing attempts started by worker claim. Starts at `0`; explicit retry resets it to `0`. |
| `max_attempts` | `INTEGER` | yes | Configured attempt ceiling copied onto the task at creation. |
| `last_error_message` | `VARCHAR(512)` | no | Bounded admin-safe message only. |
| `locked_by` | `VARCHAR(128)` | no | Worker identifier while `PROCESSING`. |
| `locked_at` | `TIMESTAMP` | no | Worker lock time for stale recovery. |
| `next_attempt_at` | `TIMESTAMP` | no | Retry eligibility time for `RETRYABLE` tasks. |
| `started_at` | `TIMESTAMP` | no | Current/last processing attempt start time. |
| `finished_at` | `TIMESTAMP` | no | Terminal completion time. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`; services update it on transitions. |

Required indexes:

```text
unique idx_rag_doc_proc_task_document on (document_id)
idx_rag_doc_proc_task_status_next on (status, next_attempt_at, created_at)
idx_rag_doc_proc_task_user_kb_status on (user_id, knowledge_base_id, status)
```

Transaction boundary: task claim/state updates are short database transactions. Parser reads and embedding provider calls must not run inside a long-held task-claim transaction.

### Implemented Document Chunk Embedding Schema

The chunk embedding vector table is introduced by:

```text
backend/src/main/resources/db/migration/V6__create_document_chunk_embedding_table.sql
```

`rag_document_chunk_embedding` columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key. |
| `user_id` | `BIGINT` | yes | Tenant boundary. |
| `knowledge_base_id` | `BIGINT` | yes | FK to `rag_knowledge_base(id)`. |
| `document_id` | `BIGINT` | yes | FK to `rag_document(id)`. |
| `chunk_id` | `BIGINT` | yes | FK to `rag_document_chunk(id)`, unique. |
| `embedding_model` | `VARCHAR(255)` | yes | Must match KB embedding model used at ingestion time. |
| `embedding_dimension` | `INTEGER` | yes | Must match KB dimension and actual vector length. |
| `embedding` | `VECTOR` | yes | pgvector vector value, variable dimension. |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP`. |

Required indexes:

```text
unique idx_rag_doc_chunk_emb_chunk_id on rag_document_chunk_embedding(chunk_id)
idx_rag_doc_chunk_emb_user_kb on rag_document_chunk_embedding(user_id, knowledge_base_id)
idx_rag_doc_chunk_emb_document on rag_document_chunk_embedding(document_id)
```

Tenant rule: every vector row duplicates `user_id` and `knowledge_base_id`. Future vector queries must include both `user_id` and `knowledge_base_id` in SQL before ordering by vector distance. Java-only tenant filtering after vector operations is forbidden. Runtime retrieval must also join the source `rag_document` row and require `status = 'READY'` before vector ordering; chunks from `UPLOADED`, `PARSING`, `PARSED`, `EMBEDDING`, or `FAILED` documents are not valid retrieval hits.

Dimension safety: the number of vectors returned by the embedding provider must equal the number of input chunks. Every vector length must equal `rag_knowledge_base.embedding_dimension`. The model config used for embedding must have same `user_id`, `status=ENABLED`, non-blank `embedding_model`, `embedding_dimension` equal to KB dimension, and a usable encrypted upstream API key. If multiple enabled model configs match the same embedding model and dimension for one user, the latest updated config is the operational default.

### pgvector Literal Serialization Contract

All Java code must use the shared `PgVectorFormatter` (`com.sangui.raggateway.common.util.PgVectorFormatter`) for every `float[]` → pgvector `VECTOR` literal conversion. Services must not duplicate formatter logic.

Contract:

```java
public final class PgVectorFormatter {
    public static String format(float[] vector)
}
```

Expected payload shape: `[<c0>,<c1>,...,<cn>]` with no spaces.

Component formatting: `String.format(Locale.ROOT, "%.8f", component)` — fixed eight decimal places, Locale.ROOT decimal separator.

| Contract | Required behavior |
|---|---|
| Normal vector | Bracketed comma-separated literal with fixed 8 decimal places and Locale.ROOT. |
| Null input | `IllegalArgumentException` with clear message. |
| Empty array | `IllegalArgumentException` with clear message. |
| Non-finite component (`NaN` / `Infinity`) | `IllegalArgumentException` with the offending component index; do not pass invalid vector literals to mapper SQL. |
| Call sites | `DocumentService.persistEmbeddings` writes formatted string into `DocumentChunkEmbeddingEntity.embedding`; mapper uses `#{embedding}::vector`. `RetrievalService.retrieve` passes formatted string to `RetrievalMapper.retrieveChunks`; mapper uses `#{queryVector}::vector`. |
| No alternate formatters | There is exactly one production implementation. Duplicate formatters in services are forbidden. |

Mapper SQL `::vector` casts remain unchanged: `#{embedding}::vector` in `DocumentChunkEmbeddingMapper.insertEmbedding` and `#{queryVector}::vector` in `RetrievalMapper.retrieveChunks`.

ANN index (HNSW/IVFFlat) is deferred until the retrieval distance metric is chosen.

### Implemented App/KB Binding and Retrieval Schema

The app-to-knowledge-base binding and retrieval configuration schema is introduced by:

```text
backend/src/main/resources/db/migration/V7__add_app_default_knowledge_base.sql
```

`rag_app` new columns:

| Column | Type | Required | Notes |
|---|---|---|---|
| `default_knowledge_base_id` | `BIGINT` | no | FK to `rag_knowledge_base(id)`. |
| `retrieval_top_k` | `INTEGER` | yes | Default `5`. |
| `retrieval_similarity_threshold` | `NUMERIC(4,3)` | yes | Default `0.300`; chosen for baseline recall with OpenAI-compatible embedding providers and short Chinese queries. |
| `retrieval_max_context_chunks` | `INTEGER` | yes | Default `5`. |
| `retrieval_max_context_chars` | `INTEGER` | yes | Default `12000`. |
| `retrieval_max_single_chunk_chars` | `INTEGER` | yes | Default `3000`. |
| `no_hit_policy` | `VARCHAR(32)` | yes | Default `STRICT_RAG`. |

Required indexes:

```text
idx_rag_app_default_knowledge_base on rag_app(default_knowledge_base_id)
```

FK constraint:

```text
fk_rag_app_default_knowledge_base: rag_app(default_knowledge_base_id) -> rag_knowledge_base(id)
```

Tenant rule: when resolving or assigning `default_knowledge_base_id`, `app.user_id` must equal `knowledge_base.user_id`. Enforced in service logic.

#### Retrieval SQL Contract

```sql
SELECT c.id AS chunk_id, c.document_id, c.content, c.metadata::text,
       1 - (e.embedding <=> ?::vector) AS similarity
FROM rag_document_chunk_embedding e
JOIN rag_document_chunk c
  ON c.id = e.chunk_id
 AND c.user_id = e.user_id
 AND c.knowledge_base_id = e.knowledge_base_id
 AND c.document_id = e.document_id
JOIN rag_document d
  ON d.id = e.document_id
 AND d.user_id = e.user_id
 AND d.knowledge_base_id = e.knowledge_base_id
 AND d.status = 'READY'
WHERE e.user_id = ?
  AND e.knowledge_base_id = ?
ORDER BY e.embedding <=> ?::vector
LIMIT ?
```

Vector similarity: `1 - cosine_distance` (pgvector `<=>` returns cosine distance).

The query must keep embedding, chunk, and document duplicate boundary columns consistent. Do not rely on Java-side filtering to remove chunks from non-READY documents or mismatched duplicated row metadata after vector ordering.

## Transaction Boundaries

Use service methods as transaction boundaries.

Typical transactions:

- Create app and default config records.
- Create API key hash/prefix metadata.
- Create document metadata and mark upload state.
- Update document state during parsing/embedding transitions.
- Persist chunks and update document/knowledge-base readiness.

Do not keep a database transaction open while calling upstream model or embedding APIs. Persist an intermediate state, call the external service, then persist the result or failure.

### Implemented Request Log Admin Queries

Admin query methods added to `ApiRequestLogMapper`:

```java
@Select("SELECT * FROM rag_request_log WHERE user_id = #{userId} AND app_id = #{appId} AND request_id = #{requestId}")
ApiRequestLogEntity selectByRequestIdAndUserAndApp(@Param("userId") Long userId,
                                                    @Param("appId") Long appId,
                                                    @Param("requestId") String requestId);
```

List and count queries use `LambdaQueryWrapper` with dynamic filters for `status`, `error_code`, `start_time`, `end_time`, scoped by `user_id` and `app_id`. Pagination uses manual `LIMIT`/`OFFSET` without MyBatis-Plus page interceptor.

`hit_chunk_ids` JSONB read contract:
- Persisted as `String` in entity (raw JSON like `"[8,9]"`).
- Parsed to `List<Long>` in VO layer using Jackson `ObjectMapper`.
- Malformed JSONB fails visibly via `IllegalArgumentException` (no silent fallback).
- Null/empty maps to empty list in VO.

Hit chunk tenant-scoped query in `DocumentChunkMapper`:

```java
@Select("<script>" +
    "SELECT * FROM rag_document_chunk " +
    "WHERE user_id = #{userId} " +
    "AND knowledge_base_id = #{knowledgeBaseId} " +
    "<choose>" +
    "<when test='ids != null and ids.size() > 0'>" +
    "AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
    "</when>" +
    "<otherwise>AND 1 = 0</otherwise>" +
    "</choose>" +
    "</script>")
List<DocumentChunkEntity> selectByIdsAndUserAndKb(...)
```

The service checks null/empty hit IDs before mapper calls; the mapper also returns no rows for null/empty `ids` instead of generating invalid `IN ()` SQL.

No new table required; existing `rag_request_log` and `rag_document_chunk` schemas are sufficient.

### Implemented Source Citation Retrieval Evidence Schema

The bounded retrieval evidence column is introduced by:

```text
backend/src/main/resources/db/migration/V15__add_request_log_retrieval_evidence.sql
```

`rag_request_log` additional column:

| Column | Type | Required | Notes |
|---|---|---:|---|
| `retrieval_evidence` | `JSONB` | no | Safe retrieval evidence metadata for final injected chunks. Null for old rows and pre-retrieval failures. |

`retrieval_evidence` stores metadata only:

```text
version
no_hits
retrieval_latency_ms
top_k
similarity_threshold
max_context_chunks
citations[]
```

Each citation may include only safe metadata:

```text
citation_id
chunk_id
document_id
knowledge_base_id
source_filename
chunk_index
similarity
metadata.source
metadata.parser
content_chars
injected_chars
```

Forbidden in `retrieval_evidence`:

```text
content
chunk_content
summary
embedding
prompt
messages
full_messages
augmented_prompt
api_key
key_hash
authorization
upstream_api_key
api_key_encrypted
provider_response_body
stack_trace
storage_path
raw_sse
environment
```

Retrieval SQL that loads source filenames must keep tenant and knowledge-base scope at the SQL boundary. The filename join must not depend only on `document_id`; it must also keep the joined document in the same `user_id` and `knowledge_base_id` as the already-scoped embedding row.

`hit_chunk_ids` remains the compatibility field. For successful RAG retrieval, its ID order must match `retrieval_evidence.citations[].chunk_id`.

VO parsing contract:

- Missing/null/blank `retrieval_evidence` maps to `null` for old rows.
- Malformed `retrieval_evidence` fails visibly; it must not be silently treated as no-hit or success evidence.
- Normal list/detail APIs may expose only the bounded metadata above.

Required checks after changing this contract:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```
