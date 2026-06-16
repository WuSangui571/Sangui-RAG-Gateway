# Production Config Guardrails

## Classification

Complex Task.

This is a backend / deployment / security contract task. It touches startup configuration validation, Spring profiles, Docker/env contracts, secret safety, Redis/PostgreSQL defaults, local file storage, and request-log output capture. It must be handled as a structural guardrail, not as isolated assertions in tests.

## Current Project State

- Current branch: `feature/production-config-guardrails`.
- Working tree was clean when the task was created.
- No active Trellis task existed before this task.
- Recent recorded work:
  - Production context smoke test added `ProductionContextSmokeTest` for gateway auth filter registration, API key limit property binding, encryption/admin secret startup checks, and blank secret negative cases.
  - Output capture scheduled cleanup added `OutputCaptureProperties.cleanupFixedDelayMs` validation and `RequestLogOutputCleanupScheduler` under `!test`.
  - API key rate limit/quota uses Redis and intentionally fails visibly on limiter outages instead of silently bypassing enforcement.

## Goal

Add explicit startup-time production configuration guardrails so a production or production-like Spring profile cannot start with dangerous local defaults or high-risk diagnostic settings accidentally enabled.

The guard must fail visibly at startup by throwing an explicit exception with a safe, actionable message. It must not introduce silent fallbacks, fake success paths, or runtime bypass behavior.

## Scope

In scope:

- Backend startup configuration guard, active only for production-like profiles.
- Production-like profile detection based on active Spring profiles.
- Rejection of weak or placeholder `rag.gateway.secret-key`.
- Rejection of default PostgreSQL datasource URL/user/password in production-like profiles.
- Rejection of default local Redis host/port combination in production-like profiles.
- Rejection of local file storage in production-like profiles unless explicitly confirmed.
- Rejection of global request-log output capture in production-like profiles unless explicitly confirmed.
- Detection of dangerous profile combinations such as `prod` with `dev` or `test`.
- Focused unit tests around guard behavior using `ApplicationContextRunner` or equivalent lightweight Spring context tests.
- Spec/documentation update for the new configuration contract, limited to backend/guides and README or `.env.example` only if the implementation introduces new env keys.

Out of scope:

- No database schema or migration.
- No frontend UI or TypeScript changes.
- No Admin API, public `/v1/*` API, DTO, VO, or payload changes.
- No Docker infrastructure redesign.
- No MinIO/object-storage implementation.
- No new secret manager integration.
- No changes to API-key rate-limit runtime logic.
- No changes to request-log output preview access behavior beyond startup guard checks.
- No commits, pushes, or release tagging by Codex.

## Production-Like Profile Contract

For this task, production-like means any active Spring profile whose normalized lowercase value is:

```text
prod
production
```

The guard must not apply to `dev` or `test` alone.

Dangerous combinations must fail when any production-like profile is active together with:

```text
dev
test
```

Do not add broader staging/UAT semantics unless the user explicitly extends the requirement.

## Configuration / Command Contract

Existing config fields to inspect:

| Environment variable | Spring property | Production-like requirement |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `spring.profiles.active` | Must include `prod` or `production` for the guard to activate. Must not combine production-like with `dev` or `test`. |
| `RAG_GATEWAY_SECRET_KEY` | `rag.gateway.secret-key` | Required, non-blank, strong enough for JWT/encryption, and not a known local placeholder. |
| `SPRING_DATASOURCE_URL` | `spring.datasource.url` | Must not be the local default URL. |
| `SPRING_DATASOURCE_USERNAME` | `spring.datasource.username` | Must not be the local default username. |
| `SPRING_DATASOURCE_PASSWORD` | `spring.datasource.password` | Must not be blank or the local default password. |
| `SPRING_DATA_REDIS_HOST` | `spring.data.redis.host` | Must not be `localhost` or `127.0.0.1` when port is the default. |
| `SPRING_DATA_REDIS_PORT` | `spring.data.redis.port` | Default `6379` is allowed only when the host is not local-default. |
| `FILE_STORAGE_TYPE` | `rag.gateway.storage.type` | `local` in production-like profiles requires an explicit production acknowledgement. |
| `FILE_STORAGE_LOCAL_PATH` | `rag.gateway.storage.local-path` | Must not silently use the local dev default in production-like profiles. |
| n/a | `rag.request-log.output-capture.enabled` | `true` in production-like profiles requires an explicit production acknowledgement. |

New env/property keys are allowed only if needed for explicit acknowledgement. If introduced, use a narrow backend namespace and document it in PRD/spec/README:

| Suggested environment variable | Suggested Spring property | Purpose |
|---|---|---|
| `RAG_PRODUCTION_ALLOW_LOCAL_FILE_STORAGE` | `rag.production-guard.allow-local-file-storage` | Explicitly allow local filesystem uploads in production-like profiles. Default `false`. |
| `RAG_PRODUCTION_ALLOW_OUTPUT_CAPTURE` | `rag.production-guard.allow-output-capture` | Explicitly allow global output capture in production-like profiles. Default `false`. |

Do not add any new public HTTP API or frontend payload field.

## Validation / Error Matrix

All failures below are startup failures. Prefer `IllegalStateException` or a similarly explicit startup exception with safe messages. Messages must name the unsafe property and the reason, but must not print secret values, passwords, URLs with credentials, or raw environment values containing secrets.

| Scenario | Expected result | Assertion point |
|---|---|---|
| Active profile `dev` with `.env.example` defaults | Context starts; production guard is inactive. | Guard test asserts no failure. |
| Active profile `test` with test exclusions | Context starts; production guard is inactive. | Guard test asserts no failure. |
| Active profile `prod` with blank `rag.gateway.secret-key` | Startup fails. | Error mentions `rag.gateway.secret-key` and blank/required. |
| Active profile `prod` with `local-dev-change-me` | Startup fails. | Error mentions weak/placeholder secret without echoing the full value. |
| Active profile `prod` with secret shorter than 32 chars | Startup fails. | Error mentions minimum strength/length. |
| Active profile `prod` with default datasource URL | Startup fails. | Error mentions `spring.datasource.url` default/local value. |
| Active profile `prod` with default datasource username `sangui` | Startup fails. | Error mentions `spring.datasource.username`. |
| Active profile `prod` with default datasource password `sangui_password` | Startup fails. | Error mentions `spring.datasource.password` without printing the password. |
| Active profile `prod` with Redis `localhost:6379` or `127.0.0.1:6379` | Startup fails. | Error mentions Redis local default host/port. |
| Active profile `prod` with Redis `redis:6379` | Allowed. | Good prod context starts. |
| Active profile `prod` with `FILE_STORAGE_TYPE=local` and no explicit acknowledgement | Startup fails. | Error mentions local file storage and acknowledgement property. |
| Active profile `prod` with `FILE_STORAGE_TYPE=local` and explicit acknowledgement | Allowed. | Good prod context starts. |
| Active profile `prod` with `rag.request-log.output-capture.enabled=true` and no explicit acknowledgement | Startup fails. | Error mentions output capture and acknowledgement property. |
| Active profile `prod` with output capture enabled and explicit acknowledgement | Allowed. | Good prod context starts. |
| Active profiles `prod,dev` | Startup fails. | Error mentions incompatible profiles. |
| Active profiles `production,test` | Startup fails. | Error mentions incompatible profiles. |
| Active profile `prod` with all non-default safe values | Context starts. | Good prod test asserts guard passes. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | `prod` profile starts with strong secret, non-default DB credentials, non-local Redis host, local file storage explicitly acknowledged if used, and output capture either disabled or explicitly acknowledged. |
| Base | `dev` and `test` profiles keep current local developer ergonomics; `.env.example` defaults continue to work outside production-like profiles. |
| Bad | Production-like startup succeeds with `local-dev-change-me`, default DB credentials, `localhost:6379`, unacknowledged local storage, unacknowledged global output capture, or mixed `prod,dev` / `production,test` profiles. |

## Implementation Direction

Recommended backend shape:

- Add a small guard component under `common.config`, for example:
  - `ProductionConfigGuard`
  - optional `ProductionGuardProperties`
- Activate it outside the `test` profile, but make validation logic conditional on active production-like profiles.
- Use Spring `Environment` or bound configuration properties to inspect active profiles and required values.
- Keep validation centralized in one guard rather than scattering production checks across encryption, admin auth, storage, Redis, or request-log services.
- Prefer named constants for known defaults:
  - `local-dev-change-me`
  - `jdbc:postgresql://localhost:5432/sangui_rag_gateway`
  - `sangui`
  - `sangui_password`
  - `localhost`
  - `127.0.0.1`
  - `6379`
- Keep error messages safe and actionable.
- Reuse existing `ApplicationContextRunner` test style from `ProductionContextSmokeTest` and `RequestLogOutputCleanupSchedulerTest`.

## Files Likely To Modify

Expected implementation files:

- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/ProductionGuardProperties.java` if acknowledgement properties are introduced
- `backend/src/main/resources/application.yml` if new acknowledgement properties need defaults
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java` or extend `ProductionContextSmokeTest` if the guard remains small
- `.trellis/spec/backend/quality-guidelines.md` or a focused new backend config guardrail spec, then link from `.trellis/spec/backend/index.md` if a new spec file is created
- `.trellis/spec/guides/cross-layer-thinking-guide.md` only if the env/deployment contract needs a cross-layer rule
- `.env.example` and `README.md` only if new environment variables are introduced

Files expected not to change:

- No `backend/src/main/resources/db/migration/*`
- No `frontend/*`
- No Admin controller/service/DTO/VO files
- No gateway request/response model files
- No API-key limiter logic unless a test reveals direct coupling that must be adjusted for guard wiring

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: deployment env keys, Docker Compose service contract, secret rules, local defaults.
- `.trellis/spec/backend/index.md`: backend guideline index and pre-development checklist.
- `.trellis/spec/backend/directory-structure.md`: `common.config` ownership for Spring configuration/properties.
- `.trellis/spec/backend/database-guidelines.md`: `RAG_GATEWAY_SECRET_KEY` must come from env outside local development; Redis is used for rate limits and is not a silent fallback source.
- `.trellis/spec/backend/error-handling.md`: errors must be safe and must not leak secrets or internals.
- `.trellis/spec/backend/logging-guidelines.md`: output capture is sensitive and default-off; logs must omit secrets and environment values.
- `.trellis/spec/backend/quality-guidelines.md`: startup/config/security changes require focused tests and no plaintext secret leakage.
- `.trellis/spec/gateway/resilience.md`: rate-limit/Redis failures must be visible, not silently bypassed.
- `.trellis/spec/security/rag-security.md`: responses/logs must not expose keys, env vars, filesystem paths, or sensitive observability data.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: environment variables and Docker Compose contracts require cross-layer thinking.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search existing guard/property/test patterns before adding new code.

## Code Patterns Found

- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`: current production-context smoke style using `ApplicationContextRunner`, `@TestConfiguration`, and explicit positive/negative startup assertions.
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java` + `EncryptionProperties.java`: existing `rag.gateway.secret-key` binding and encryptor construction.
- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`: current JWT secret usage through `@Value("${rag.gateway.secret-key}")`.
- `backend/src/main/java/com/sangui/raggateway/common/config/ApiKeyLimitProperties.java`: `@ConfigurationProperties` + `@Validated` pattern for visible config binding failures.
- `backend/src/main/java/com/sangui/raggateway/log/OutputCaptureProperties.java`: existing high-sensitivity output capture config defaults and validation pattern.
- `backend/src/test/java/com/sangui/raggateway/log/RequestLogOutputCleanupSchedulerTest.java`: property binding failure assertion pattern for configuration validation.
- `backend/src/main/resources/application.yml` and `application-dev.yml`: default/env override split to preserve local dev behavior.
- `deploy/docker-compose.yml` and `.env.example`: current safe local defaults and Compose service-name bindings.

## Required Tests

Run from `backend/` with the project 60-second backend unit-test timeout policy:

```bash
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
mvn -q "-Dtest=ProductionConfigGuardTest,RequestLogOutputCleanupSchedulerTest" test
mvn -q -DskipTests compile
```

If the guard touches property binding shared by existing config classes, also run:

```bash
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest,GlobalExceptionHandlerIntegrationTest" test
```

If docs or env files change, run:

```bash
git diff --check
```

Assertion points:

- Dev/test defaults are not broken.
- Prod weak/default values fail startup visibly.
- Valid prod values pass startup.
- Failure messages identify the unsafe property without echoing secrets or passwords.
- Output capture and local file storage require explicit acknowledgement in prod.
- Mixed prod/dev or prod/test profiles fail.

## Planning Self-Check

- Acceptance criteria are explicit in the validation matrix.
- Forbidden scope is explicit: no API, DB migration, frontend, limiter runtime rewrite, object storage, or business feature expansion.
- Expected files are listed.
- Required test commands are listed.
- Concrete guideline files were read, not only indexes.
- No unresolved API, DB, frontend type, or DTO contract exists because this task adds no public API or schema.
- Ambiguity resolved for this PRD: production-like means `prod` or `production` only.

