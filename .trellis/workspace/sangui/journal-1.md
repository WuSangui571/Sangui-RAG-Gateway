# Journal - sangui (Part 1)

> AI development session journal
> Started: 2026-05-25

---



## Session 1: 初始化项目工程基线

**Date**: 2026-05-27
**Task**: 初始化项目工程基线
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Item | Details |
|---|---|
| Commit | `dfc043b chore:?????????` |
| Main modules | Backend Spring Boot baseline, deployment Docker Compose, local env example, README, frontend/docs placeholders, Trellis spec baseline contracts |
| Backend changes | Java 21 + Spring Boot 3.4.5 Maven project; `ApiResponse`; `BusinessException`; `GlobalExceptionHandler`; `GET /api/health`; dev/test Spring config; Flyway migration `V1__init_pgvector.sql` |
| Infra changes | `deploy/docker-compose.yml` defines pgvector PostgreSQL and Redis with ports, volumes, health checks; `.env.example` documents safe local defaults; `.gitignore` excludes `.env` and build outputs |
| Documentation/spec | README documents local setup, health checks, current non-goals, and OpenAI-compatible subset statement. Project spec now records executable baseline contracts for commands, env keys, health API, migration, and verification matrix |
| Verification by Codex | `mvn -q -DskipTests compile` passed; `mvn test` passed with 4 tests, 0 failures; `mvn verify` passed; `docker compose --env-file .env.example -f deploy\docker-compose.yml config` passed; static searches found no accidental `/v1/*` route implementation and no console/debug/TODO/any/non-null assertion issues in touched project files |
| Manual verification by sangui | `docker ps` showed `sangui-postgres` and `sangui-redis` healthy; `curl http://localhost:8080/api/health` returned HTTP 200 with `code=OK` and `data.status=UP`; `curl http://localhost:8080/actuator/health` returned HTTP 200 with `status=UP` and DB details |
| Boundary | Frontend remains placeholder-only; no login, knowledge base, document ingestion, RAG retrieval, OpenAI-compatible endpoint, or admin UI was implemented |
| Follow-up risk | Manual probing of `/v1/models`, `/v1/chat/completions`, and browser favicon showed current global exception handling maps Spring `NoResourceFoundException` to `INTERNAL_ERROR` and logs stack traces. This does not expose a fake `/v1/*` success, but should be fixed next so unknown routes return safe 404 behavior without error-level stack traces |

**Updated Files**:
- `.env.example`
- `.gitignore`
- `README.md`
- `backend/pom.xml`
- `backend/src/main/java/com/sangui/raggateway/SanguiRagGatewayApplication.java`
- `backend/src/main/java/com/sangui/raggateway/common/response/ApiResponse.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/BusinessException.java`
- `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/sangui/raggateway/health/HealthController.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/db/migration/V1__init_pgvector.sql`
- `backend/src/test/java/com/sangui/raggateway/SanguiRagGatewayApplicationTests.java`
- `backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`
- `backend/src/test/resources/application-test.yml`
- `deploy/docker-compose.yml`
- `frontend/.gitkeep`
- `docs/.gitkeep`
- `.trellis/spec/sangui-rag-gateway.md`


### Git Commits

| Hash | Message |
|------|---------|
| `dfc043b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Safe 404 unmatched routes

**Date**: 2026-05-27
**Task**: Safe 404 unmatched routes
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Item | Details |
|------|---------|
| Commit | `3e85e6e fix:??404?????` |
| Main modules | Backend global exception handling, backend MockMvc tests, README baseline notes, Trellis project spec baseline matrix |
| Updated files | `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`; `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`; `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java`; `README.md`; `.trellis/spec/sangui-rag-gateway.md` |
| Verification | `mvn -q "-Dtest=GlobalExceptionHandlerIntegrationTest" test` passed; `mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest,HealthControllerTest" test` passed; `mvn test` passed with 12 tests, 0 failures, 0 errors; human curl/manual verification passed after commit |
| Result | Unknown routes, `/favicon.ico`, and currently unimplemented `/v1/models` and `/v1/chat/completions` now return safe 404 admin envelope responses instead of falling through to generic 500. Expected route misses are logged as WARN without passing exception objects to the logger. |
| Boundary | Did not implement OpenAI-compatible `/v1/*` gateway APIs; did not add authentication, RAG retrieval, provider forwarding, streaming, DB schema, Redis, Docker, or frontend behavior. Future real `/v1/*` endpoints should replace this baseline 404 behavior with gateway controllers and OpenAI-compatible error shapes. |

**Manual verification by sangui**:
- `http://localhost:8080/v1/models` returned `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.
- `http://localhost:8080/v1/chat/completions` returned `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.
- User confirmed all manual tests passed and committed the code.


### Git Commits

| Hash | Message |
|------|---------|
| `3e85e6e` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: OpenAI Gateway Error Baseline

**Date**: 2026-05-27
**Task**: OpenAI Gateway Error Baseline
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Item | Details |
|------|---------|
| Commit | `f0ab2fe feat:??OpenAI????????` |
| Main modules | Backend common exception handling; OpenAI-compatible error response models; backend MockMvc and SpringBootTest exception tests; backend error-handling spec |
| Updated files | `backend/src/main/java/com/sangui/raggateway/common/exception/GatewayException.java`; `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`; `backend/src/main/java/com/sangui/raggateway/common/response/OpenAiError.java`; `backend/src/main/java/com/sangui/raggateway/common/response/OpenAiErrorResponse.java`; `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java`; `backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java`; `.trellis/spec/backend/error-handling.md` |
| Verification | `mvn -q -DskipTests compile` passed; `mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test` passed; `mvn test` passed with 14 tests, 0 failures, 0 errors; `git diff --check` passed; human curl/manual verification passed after commit |
| Result | Established the baseline OpenAI-compatible gateway error contract through `GatewayException` and `OpenAiErrorResponse`, while preserving admin/common `ApiResponse` behavior for `BusinessException`, generic 500 responses, and unmatched routes. Current unimplemented `/v1/models` and `/v1/chat/completions` still return safe 404 admin envelopes. |
| Codex check fixes | Made `OpenAiError` and `OpenAiErrorResponse` fields final; added non-null constructor validation for `GatewayException` required fields; documented the non-null contract in backend error-handling spec. |
| Boundary | Did not implement `GET /v1/models` or `POST /v1/chat/completions`; did not add API key authentication, app/model lookup, RAG retrieval, upstream forwarding, streaming, DB schema, Redis, Docker, or frontend behavior. Future real gateway controllers should throw/translate domain failures through this gateway error baseline. |

**Manual verification by sangui**:
- `GET /api/health` returned `{"code":"OK","message":"success","data":{"status":"UP","service":"sangui-rag-gateway"}}`.
- `GET /v1/models` returned `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.
- `POST /v1/chat/completions` returned HTTP 404 with `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.
- `GET /this-route-does-not-exist-anywhere` returned HTTP 404 with `{"code":"NOT_FOUND","message":"Resource not found","data":null}`.

**Status**: Completed and committed by sangui.


### Git Commits

| Hash | Message |
|------|---------|
| `f0ab2fe` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: App 与 API Key 认证基线

**Date**: 2026-05-27
**Task**: App 与 API Key 认证基线
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Item | Details |
|------|---------|
| Commit | `dd31ea4 feat:??API Key????` |
| Task | `05-27-app-api-key-auth-baseline` / App ? API Key ???? |
| Result | ??? App/API Key ??????????????`/v1/*` Bearer ???OpenAI-compatible `invalid_api_key` ?????????????????? |
| Main Modules | `app`, `apikey`, `common.security`, `common.config`, Flyway migration, backend spec docs? |

**????**:
- ?? `rag_app` ? `rag_api_key` ???????`user_id` ???????????????`key_hash` ?????`app_id` FK ????????
- ?? App ??????`AppEntity`, `AppStatus`, `AppMapper`, `AppService`?
- ?? API Key ????`ApiKeyEntity`, `ApiKeyStatus`, `ApiKeyMapper`, `ApiKeyService`, `CreateApiKeyResult`?
- ?? `ApiKeyGenerator`??? `sk-sangui-<base64url-token>` ??????????? prefix?
- ?? `ApiKeyHasher`??? SHA-256 ????? hash??????? plaintext key?
- ?? `GatewayAuthFilter` ? `GatewayAuthConfig`???? `/v1/*`??? `Authorization: Bearer <key>`???????? `GatewayRequestContextHolder`????????? OpenAI-compatible `401 invalid_api_key`?
- ?? `GatewayRequestContext` ? `GatewayRequestContextHolder`???? gateway controller ?? `appId`, `userId`, `apiKeyId`, `apiKeyPrefix`?
- ?? backend spec?`database-guidelines.md` ?? V2 ???????Java ???????`error-handling.md` ?? `/v1/*` API Key filter ?????????????

**Codex ????**:
- `ApiKeyService.updateLastUsed()` ???? `lastUsedAt` ? `updatedAt`?
- ?? `GatewayAuthFilter` ??? `beforeAuth()` ???
- ?? `ApiKeyServiceTest.shouldUpdateLastUsedAndUpdatedAtTogether()` ??????????????

**????**:
- `cd backend && mvn -q -DskipTests compile` ???
- `cd backend && mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test` ???
- `cd backend && mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test` ???
- `cd backend && mvn -q test` ????? Maven ?? 54/54 ???

**????**:
- Docker Compose ?? PostgreSQL/Redis ???
- Flyway ??? `flyway_schema_history`, `rag_app`, `rag_api_key`?
- `rag_app` ??????`idx_rag_app_user_status` ???? FK ?????
- `rag_api_key` ??????`idx_rag_api_key_hash` ?????`idx_rag_api_key_app`, `idx_rag_api_key_app_status`, `fk_rag_api_key_app` ?????
- ???? enabled app ? active API key ??`GET /api/health` ?? Authorization ?? 200 admin envelope?
- `GET /v1/models` ?? Authorization?Basic scheme?? Bearer??? prefix??? key ??? 401 OpenAI-compatible `invalid_api_key`?
- ?? key ?? `GET /v1/models` ??????????? 404 admin envelope???? models ???
- ?? key ??? `rag_api_key.last_used_at` ? `updated_at` ?????
- `/actuator/health` ?? gateway API key filter ????? 200?
- ????????? key?Authorization?Exception ? `java.`?

**???????**:
- ?? baseline ??? `/v1/models` ? `/v1/chat/completions` ?????
- ?? `test` profile ????? datasource/Flyway/MyBatis-Plus??? migration ????? Docker/PostgreSQL ???
- `updateLastUsed` ??????????? DB??????????????????
- ?? key hash ?? SHA-256???????? HMAC-SHA-256 + `RAG_GATEWAY_SECRET_KEY`?


### Git Commits

| Hash | Message |
|------|---------|
| `dd31ea4` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
