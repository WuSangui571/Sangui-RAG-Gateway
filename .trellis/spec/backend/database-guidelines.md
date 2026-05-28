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
- Embeddings should use pgvector column types with fixed dimensions.

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
| `created_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`. |
| `updated_at` | `TIMESTAMP` | yes | Defaults to `CURRENT_TIMESTAMP`; services must update it when mutating the row. |

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

Run these checks after changing this schema or the matching services:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
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
| `chat_model` | `VARCHAR(255)` | yes | Model id returned by `/v1/models`. |
| `embedding_model` | `VARCHAR(255)` | no | Embedding model id for later RAG tasks. |
| `embedding_dimension` | `INTEGER` | no | Required only with embedding model. |
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

The encryption master key must come from environment variables (`RAG_GATEWAY_SECRET_KEY`), not source code, outside local development. The `dev` profile may provide a safe placeholder default matching `.env.example` so `mvn spring-boot:run` starts locally; production-like runs must override it with `RAG_GATEWAY_SECRET_KEY`.

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
- Key derivation: SHA-256 of `RAG_GATEWAY_SECRET_KEY` produces a 256-bit AES key.
- Stored format: `v1:<base64url-iv>:<base64url-ciphertext>`.
- The encryptor fails fast if `rag.gateway.secret-key` is blank at startup.
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
| `status` | `VARCHAR(32)` | yes | `success` or `failure` |
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

## Transaction Boundaries

Use service methods as transaction boundaries.

Typical transactions:

- Create app and default config records.
- Create API key hash/prefix metadata.
- Create document metadata and mark upload state.
- Update document state during parsing/embedding transitions.
- Persist chunks and update document/knowledge-base readiness.

Do not keep a database transaction open while calling upstream model or embedding APIs. Persist an intermediate state, call the external service, then persist the result or failure.
