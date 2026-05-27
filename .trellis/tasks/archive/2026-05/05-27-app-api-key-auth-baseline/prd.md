# App 与 API Key 认证基线

## Task Classification

Complex Task.

Reason: this task crosses database schema, backend domain services, gateway authentication, OpenAI-compatible error contract, secret handling, and test coverage. It must be planned before implementation and must not expand into chat completions, RAG, model config, frontend UI, or full admin CRUD.

## Current Project State

- The Spring Boot backend baseline exists and tests pass in prior sessions.
- Global admin error handling returns `ApiResponse` for admin/common paths.
- OpenAI-compatible gateway error baseline exists through `GatewayException`, `OpenAiError`, and `OpenAiErrorResponse`.
- `/v1/models` and `/v1/chat/completions` are intentionally unimplemented and currently return safe 404 admin envelopes.
- No App, API key, model config, knowledge base, request log, RAG retrieval, upstream forwarding, or frontend admin workflow has been implemented yet.

## Goal

Establish the first secure gateway authentication boundary:

- Persist `App` and `ApiKey` baseline records.
- Generate gateway API keys in the `sk-sangui-...` format.
- Store only API key hash and prefix metadata.
- Authenticate `/v1/*` requests using `Authorization: Bearer <key>`.
- Resolve authenticated App/API key context for future gateway controllers.
- Return OpenAI-compatible `invalid_api_key` errors for missing, malformed, unknown, revoked, disabled, expired, or app-disabled gateway credentials.

## Scope

### In Scope

- Backend-only changes.
- New Flyway migration for baseline app and API key tables.
- `app` domain entity/mapper/service for minimal App lifecycle needed by tests and future gateway use.
- `apikey` domain entity/mapper/service for key creation, hash lookup, status, expiry, revocation/disable behavior, and last-used metadata.
- API key generation helper and hashing helper.
- Gateway authentication mechanism for `/v1/*`.
- Request-scoped authenticated gateway context containing at least `appId`, `apiKeyId`, and safe `apiKeyPrefix`.
- OpenAI-compatible error responses for gateway auth failures.
- Focused tests for hashing, one-time plaintext behavior, auth failure shapes, success context resolution, and secret non-leakage.
- Spec/README update only if implementation establishes a new durable contract not already documented.

### Out of Scope

- Do not implement `GET /v1/models` response data.
- Do not implement `POST /v1/chat/completions`.
- Do not implement RAG retrieval, embeddings, document upload, chunking, prompt building, upstream forwarding, streaming, or request logs.
- Do not implement frontend UI.
- Do not implement full admin CRUD unless a minimal backend command/service is needed to create App/API key records for tests.
- Do not introduce rate limiting or quota enforcement beyond reserving nullable/config columns if appropriate.
- Do not store or log full API keys.
- Do not return full API keys after creation.
- Do not add broad platform/user/team abstractions beyond fields required for tenant-ready App ownership.

## Domain Contracts

### App

Minimum persisted fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | bigint/long | yes | Primary key. |
| `user_id` | bigint/long | yes | Tenant owner boundary placeholder. |
| `name` | varchar | yes | App display name. |
| `status` | varchar enum | yes | `ENABLED`, `DISABLED`. |
| `created_at` | timestamp | yes | DB/application timestamp. |
| `updated_at` | timestamp | yes | DB/application timestamp. |

Optional future-ready fields may be added only if directly needed and documented:

- `default_knowledge_base_id`
- `default_model_config_id`
- `system_prompt`
- `retrieval_config`

Do not implement behavior around optional fields in this task.

### API Key

Minimum persisted fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | bigint/long | yes | Primary key. |
| `app_id` | bigint/long | yes | Required App relation. |
| `user_id` | bigint/long | yes | Denormalized owner boundary for future tenant-safe admin queries. |
| `name` | varchar | yes | Human label. |
| `key_hash` | varchar | yes | Unique hash of the full plaintext key. Never plaintext. |
| `key_prefix` | varchar | yes | Safe display prefix, not enough to authenticate. |
| `status` | varchar enum | yes | `ACTIVE`, `DISABLED`, `EXPIRED`, `REVOKED`. |
| `expires_at` | timestamp/null | no | Null means no expiry. |
| `last_used_at` | timestamp/null | no | Set/update on successful auth if practical. |
| `revoked_at` | timestamp/null | no | Set when revoked. |
| `created_at` | timestamp | yes | DB/application timestamp. |
| `updated_at` | timestamp | yes | DB/application timestamp. |

Optional future-ready fields may be added as JSON/text only if low-risk:

- `rate_limit_config`
- `quota_config`

### API Key Generation

- Plaintext format: `sk-sangui-<high-entropy-token>`.
- Full plaintext key is returned only from the create command/service result.
- Persisted response/list/detail shapes must include only `id`, `appId`, `name`, `keyPrefix`, `status`, `expiresAt`, and timestamps.
- Hashing must be deterministic for lookup and must not be reversible.
- Prefer SHA-256 or HMAC-SHA-256 with project secret if a suitable secret property already exists. If using an app secret, document env/property requirements and tests must not depend on production secrets.

## Database Contract

Create a new migration after `V1__init_pgvector.sql`, likely:

```text
backend/src/main/resources/db/migration/V2__create_app_api_key_tables.sql
```

Required tables:

```text
rag_app
rag_api_key
```

Required indexes/constraints:

- `rag_app(id)` primary key.
- `rag_app(user_id, status)` index.
- `rag_api_key(id)` primary key.
- `rag_api_key(app_id)` index.
- `rag_api_key(key_hash)` unique index.
- `rag_api_key(app_id, status)` index.
- Foreign key from `rag_api_key.app_id` to `rag_app.id`.

Status values are enforced either by application enums or DB check constraints. If DB check constraints are added, tests/migrations must use exactly the same enum literals.

## Gateway Auth Contract

### Request Header

```http
Authorization: Bearer sk-sangui-...
```

Parsing rules:

- Missing `Authorization`: invalid key.
- Non-Bearer scheme: invalid key.
- Empty Bearer token: invalid key.
- Token not starting with `sk-sangui-`: invalid key.
- Unknown hash: invalid key.
- Key `DISABLED`, `REVOKED`, or expired: invalid key.
- App missing or `DISABLED`: invalid key unless implementation chooses `app_not_found` for safe internal boundaries. For this baseline, prefer `invalid_api_key` to avoid credential/resource enumeration.

### Gateway Context

On successful auth, expose a request-scoped context for later gateway controllers:

| Field | Required | Notes |
|---|---:|---|
| `appId` | yes | Authenticated app id. |
| `userId` | yes | Owner boundary for later app/knowledge queries. |
| `apiKeyId` | yes | Authenticated API key id. |
| `apiKeyPrefix` | yes | Safe prefix only. |

The context must be cleared after the request if a thread-local/context holder is used.

### Affected Paths

Auth should apply to public gateway paths:

```text
/v1/**
```

It must not force gateway API key auth on:

```text
/api/health
/actuator/**
admin/common endpoints
static resource 404 handling outside /v1/**
```

If no concrete `/v1/*` controller exists yet, valid credentials may still receive the existing safe 404 behavior for unimplemented routes. Missing/invalid credentials on `/v1/*` must return OpenAI-compatible auth errors before falling through to a generic 404.

## Error Matrix

All gateway auth failures must return the OpenAI-compatible shape:

```json
{
  "error": {
    "message": "Invalid API key.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

| Case | HTTP | Shape | Code | Notes |
|---|---:|---|---|---|
| Missing Authorization | 401 | OpenAI-compatible | `invalid_api_key` | No admin envelope. |
| Non-Bearer Authorization | 401 | OpenAI-compatible | `invalid_api_key` | Do not echo header. |
| Empty Bearer token | 401 | OpenAI-compatible | `invalid_api_key` | Do not echo header. |
| Malformed key prefix | 401 | OpenAI-compatible | `invalid_api_key` | Do not echo token. |
| Unknown key hash | 401 | OpenAI-compatible | `invalid_api_key` | Do not reveal lookup reason. |
| Disabled key | 401 or 403 | OpenAI-compatible | `invalid_api_key` | Prefer 401 for uniform invalid credential behavior. |
| Revoked key | 401 or 403 | OpenAI-compatible | `invalid_api_key` | Prefer 401 for uniform invalid credential behavior. |
| Expired key | 401 | OpenAI-compatible | `invalid_api_key` | No expiry timestamp in public error. |
| App disabled/missing | 401 or 403 | OpenAI-compatible | `invalid_api_key` | Avoid app enumeration. |
| Valid key for unimplemented `/v1/models` | 404 | Existing safe 404 unless a minimal test endpoint/controller is introduced | `NOT_FOUND` admin envelope currently acceptable for unimplemented route | Do not fake `/v1/models`. |
| Admin/common business errors | existing mapping | `ApiResponse` | existing codes | Must not regress. |

## Good / Base / Bad Cases

### Good Case

- Create an enabled App.
- Create an active API key for that App.
- Service result returns plaintext once and a safe prefix.
- DB record stores `key_hash` and `key_prefix`; it does not store the plaintext key.
- A `/v1/*` request with `Authorization: Bearer <plaintext>` authenticates and resolves `appId`, `userId`, `apiKeyId`, and `apiKeyPrefix`.

### Base Case

- `/api/health` remains unauthenticated and returns the existing admin envelope.
- Unimplemented `/v1/*` with a valid key still does not imply support for `/v1/models` or `/v1/chat/completions`.
- Existing `GatewayException` and `OpenAiErrorResponse` tests continue to pass.

### Bad Cases

- Missing/invalid/malformed Authorization on `/v1/*`.
- Unknown key.
- Revoked key.
- Disabled key.
- Expired key.
- Disabled app.
- Response bodies and logs do not contain full API keys, authorization headers, stack traces, or plaintext secrets.

## Required Tests and Assertion Points

### Unit Tests

- `ApiKeyGenerator` or equivalent:
  - Generated keys start with `sk-sangui-`.
  - Generated token has sufficient non-empty random suffix.
  - Prefix extraction returns only safe prefix.

- `ApiKeyHasher` or equivalent:
  - Hash is deterministic for the same input.
  - Hash differs from plaintext.
  - Hash output never includes the original key.

- `ApiKeyService` or equivalent:
  - Create result includes one-time plaintext key.
  - Persisted/fetched API key metadata excludes plaintext.
  - Revoked/disabled/expired states fail auth.

### Web/Auth Tests

- Missing Authorization on `/v1/*` returns HTTP 401 and OpenAI-compatible `error.code=invalid_api_key`.
- Invalid Bearer token returns HTTP 401 and OpenAI-compatible shape.
- Revoked key returns OpenAI-compatible `invalid_api_key`.
- Expired key returns OpenAI-compatible `invalid_api_key`.
- Disabled app returns OpenAI-compatible `invalid_api_key`.
- Successful auth exposes the expected request context.
- Auth failure response has no `code`, `message`, or `data` admin envelope fields.
- Auth failure response has no full key, no `Authorization` header echo, no `Exception`, and no `java.` stack trace.
- `/api/health` remains unaffected.

### Migration/Persistence Tests

- Migration/schema can load in backend tests.
- Unique lookup by `key_hash` is enforced or covered by mapper/service behavior.
- App/API key status enum literals match DB constraints if constraints are used.

### Regression Tests

Run existing gateway error tests:

```bash
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
```

Run full backend tests:

```bash
mvn test
```

Compile:

```bash
mvn -q -DskipTests compile
```

## Implementation Plan

1. Inspect current backend dependencies, existing migration, exception response classes, and tests.
2. Add DB migration for `rag_app` and `rag_api_key` with indexes and status columns.
3. Add app domain enum/entity/mapper/service with minimal create/find/status behavior.
4. Add apikey domain enum/entity/mapper/service plus key generator/hasher.
5. Add gateway auth component for `/v1/**` that parses Bearer token, hashes token, resolves API key/app, validates status/expiry, and sets request context.
6. Route gateway auth failures through `GatewayException` or direct `OpenAiErrorResponse` using `invalid_api_key`.
7. Add focused tests for key generation/hash/service/auth success/failure and non-leakage.
8. Re-run existing exception/health tests and full backend tests.
9. Update spec/README only for durable contract changes discovered during implementation.

## Files Likely To Modify

- `backend/src/main/resources/db/migration/V2__create_app_api_key_tables.sql`
- `backend/src/main/java/com/sangui/raggateway/app/**`
- `backend/src/main/java/com/sangui/raggateway/apikey/**`
- `backend/src/main/java/com/sangui/raggateway/common/security/**`
- `backend/src/main/java/com/sangui/raggateway/gateway/**`
- `backend/src/main/java/com/sangui/raggateway/common/exception/**`
- `backend/src/main/resources/mapper/**` if XML mappers are used.
- `backend/src/test/java/com/sangui/raggateway/app/**`
- `backend/src/test/java/com/sangui/raggateway/apikey/**`
- `backend/src/test/java/com/sangui/raggateway/gateway/**`
- `backend/src/test/java/com/sangui/raggateway/common/exception/**` for regression or helper tests if needed.
- `.trellis/spec/backend/error-handling.md` only if the gateway auth contract adds new durable rules.
- `.trellis/spec/backend/database-guidelines.md` only if schema conventions are clarified.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, App/API key domain, gateway auth flow, key format, security rules, MVP API scope.
- `.trellis/spec/backend/directory-structure.md`: target packages include `app`, `apikey`, `common.security`, and `gateway`; services own business logic and transaction boundaries.
- `.trellis/spec/backend/database-guidelines.md`: table names, status values, tenant fields, migration/index requirements, and plaintext-key prohibition.
- `.trellis/spec/backend/error-handling.md`: current `GatewayException` and `OpenAiErrorResponse` contract for `invalid_api_key`.
- `.trellis/spec/backend/logging-guidelines.md`: never log full API keys, authorization headers, private documents, or prompt content.
- `.trellis/spec/backend/quality-guidelines.md`: API key auth, secret handling, tenant isolation, and failure path tests are high priority.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required for `/v1/*`, DB schema, API key, tenant, and secret boundary changes.
- `.trellis/spec/frontend/index.md`: read only to confirm frontend is out of scope; no frontend implementation is planned.

### Code Patterns Found

- `backend/pom.xml`: Spring Boot 3.4.5, Java 21, `spring-boot-starter-web`, validation, actuator, Redis, PostgreSQL runtime, Flyway, and MyBatis-Plus starter are already present. Spring Security is not present.
- `backend/src/main/resources/db/migration/V1__init_pgvector.sql`: migration baseline currently only creates pgvector extension. Business schema starts at V2.
- `backend/src/main/resources/application-dev.yml`: dev profile enables datasource, Flyway, Redis, and MyBatis-Plus XML mapper locations.
- `backend/src/test/resources/application-test.yml`: test profile excludes datasource, Flyway, Redis, and MyBatis-Plus auto-config. Persistence/migration tests may need a dedicated test slice/profile or remain mapper/service unit tests with mocks unless the implementer adds test DB support.
- `backend/src/main/java/com/sangui/raggateway/common/exception/GatewayException.java`: required non-null fields `message`, `type`, `code`, `httpStatus`; should be reused for gateway auth failures if the failure happens inside MVC exception handling.
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`: maps `GatewayException` to `OpenAiErrorResponse`; logs safe code/type/message at WARN.
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`: standalone MockMvc pattern with local test controller and assertions for no admin envelope/no stack traces.
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java`: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")` pattern for route behavior.
- `backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java`: small controller unit tests use `MockMvcBuilders.standaloneSetup`.

### Files Likely To Modify

- `backend/src/main/resources/db/migration/V2__create_app_api_key_tables.sql`: create `rag_app` and `rag_api_key`.
- `backend/src/main/java/com/sangui/raggateway/app/AppStatus.java`, `AppEntity.java`, `AppMapper.java`, `AppService.java`: minimal App domain.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyStatus.java`, `ApiKeyEntity.java`, `ApiKeyMapper.java`, `ApiKeyService.java`, create/auth result records: API key lifecycle and lookup.
- `backend/src/main/java/com/sangui/raggateway/common/security/ApiKeyGenerator.java`, `ApiKeyHasher.java`, `GatewayRequestContext.java`, `GatewayRequestContextHolder.java`: security helpers and request context.
- `backend/src/main/java/com/sangui/raggateway/gateway/**` or `common/security/**`: `/v1/**` auth filter/interceptor registration.
- `backend/src/test/java/com/sangui/raggateway/apikey/**`: generator/hasher/service tests.
- `backend/src/test/java/com/sangui/raggateway/gateway/**` or `common/security/**`: auth filter/interceptor tests.
- `backend/src/test/java/com/sangui/raggateway/common/exception/**`: update existing integration expectations if `/v1/*` missing auth now returns OpenAI-compatible auth errors before route 404.
- `backend/src/test/resources/application-test.yml`: may need careful adjustment if integration tests require components excluded today.

### Risk / Boundary Notes

- Existing `GlobalExceptionHandlerIntegrationTest` asserts unauthenticated `/v1/models` and `/v1/chat/completions` return 404 admin envelopes. This task intentionally changes unauthenticated `/v1/**` behavior to 401 OpenAI-compatible `invalid_api_key`; tests must be updated with that contract while preserving valid-key unimplemented route behavior if tested.
- Filter-level exceptions may bypass `@RestControllerAdvice` depending on implementation. If using a servlet filter, either write the OpenAI error response directly through a small helper or ensure exceptions reach MVC handling. Tests must prove the exact shape.
- Because Spring Security is not currently a dependency, adding it may create broad default behavior changes. Prefer a scoped MVC interceptor/filter unless there is a clear reason to introduce Spring Security now.
- The test profile excludes datasource/Flyway/MyBatis-Plus. If implementing real mapper integration tests, the implementer must explicitly plan test infrastructure. For this baseline, focused unit tests plus compile may be acceptable unless migration validation is added with a controlled DB.
- HMAC with `RAG_GATEWAY_SECRET_KEY` is preferable if implemented cleanly. Plain SHA-256 is deterministic and non-reversible but provides weaker protection if DB and key format are both exposed. The implementer should choose deliberately and document the choice.
- No frontend types should be added in this task.

## Risk / Boundary Notes

- Secret handling is the main risk: never persist, log, or return full plaintext keys except one-time create result.
- Auth should be scoped to `/v1/**` only; admin/common endpoints must not break.
- Avoid implementing fake `/v1/models` or chat behavior just to test auth. Use focused auth tests or a narrow test-only controller if needed.
- Avoid introducing frontend work. Frontend API key one-time display behavior is documented but not implemented in this task.
- Avoid broad Spring Security setup unless the implementation can keep it small and clearly scoped. A servlet filter/interceptor may be enough for the baseline if existing dependencies do not include Spring Security.
- If MyBatis-Plus is not yet configured, the implementer must decide whether to add it now or use the repository pattern already present. Follow existing dependencies and keep the persistence layer minimal.
- If using HMAC with `RAG_GATEWAY_SECRET_KEY`, ensure test configuration provides a deterministic safe test secret and `.env.example` remains placeholder-only.

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden/out-of-scope areas are explicit.
- [x] Expected modified files are listed.
- [x] Required tests and assertion points are listed.
- [x] Backend concrete guidelines and cross-layer guide were read, not just indexes.
- [x] No user clarification is required before implementation.
- [x] API, DB, context, DTO/service fields, validation, and error matrix are aligned for this baseline.
