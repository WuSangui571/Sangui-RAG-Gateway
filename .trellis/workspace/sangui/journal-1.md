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
