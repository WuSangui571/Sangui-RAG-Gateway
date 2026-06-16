# Production Context Smoke Test

## Goal

Add the smallest backend smoke coverage that proves the non-`test` Spring production wiring can start with the beans used by real gateway traffic, while still failing clearly when required production configuration is missing.

This is a test-scope task. It must not change runtime business behavior, controller logic, limiter behavior, Redis behavior, gateway auth behavior, or introduce fallback paths.

## Scope Classification

Complex Task.

Reason: the smoke spans production Spring profiles, configuration binding/validation, Redis auto-configuration, gateway authentication filter registration, API-key limiter wiring, OpenAI controller/service dependencies, encryption/admin auth secrets, datasource/Flyway/MyBatis startup, and RAG/gateway bean chains. The implementation should still be a narrow backend test-only change.

## Requirements

- Add a non-`test` profile Spring context smoke that exercises production-like bean registration instead of the current `@ActiveProfiles("test")` bypass.
- Cover these critical production beans or registrations:
  - `GatewayAuthFilter`
  - `FilterRegistrationBean<GatewayAuthFilter>` for `/v1/*`
  - `ApiKeyLimitProperties`
  - `ApiKeyRateLimitService`
  - `StringRedisTemplate`
  - `OpenAiChatCompletionsController`
  - `ChatCompletionGatewayService`
  - `OpenAiCompatibleUpstreamClient`
  - `RetrievalService`
  - `OpenAiCompatibleEmbeddingClient`
  - `UpstreamApiKeyEncryptor`
  - `AdminJwtService` or the config path that proves `rag.gateway.secret-key` is required by production auth/encryption wiring
- Keep the smoke minimal: context startup and bean presence/registration assertions only. Do not perform full `/v1/chat/completions` E2E, real upstream calls, real API-key auth calls, or request-log persistence scenarios.
- Use a non-`test` profile for the positive smoke. A dedicated profile name such as `prod-smoke` is acceptable if it does not activate `test` and does not rely on `application-test.yml`.
- Provide production-like required properties inside the test invocation, not in runtime defaults:
  - `rag.gateway.secret-key` with a safe test-only value long enough for JWT signing.
  - datasource URL/user/password suitable for the chosen smoke strategy.
  - Redis host/port suitable for the chosen smoke strategy.
  - API-key limiter defaults remain positive.
- Prefer a config/profile-level smoke first if local Docker/Redis is unstable. If Testcontainers or Docker is introduced, keep it isolated to test scope and document the assumption.
- Add a negative startup smoke proving missing `rag.gateway.secret-key` fails visibly, because `application.yml` intentionally defaults it to blank and production must not silently start without it.
- Do not hide missing Redis/DataSource/secret failures with mocks, fallback beans, `@MockBean`, broad `spring.autoconfigure.exclude`, disabled limiter defaults, or relaxed runtime defaults.

## Non-Goals

- No business implementation changes under `backend/src/main/java`.
- No database migration or schema change.
- No admin API/frontend change.
- No Docker Compose health smoke in this task unless explicitly requested later.
- No full gateway E2E with real app key, database seed, knowledge base, embedding, retrieval, or upstream provider.
- No new runtime fallback when Redis, datasource, Flyway, MyBatis, or secret config is missing.
- No changes to `/v1/chat/completions` request/response behavior.
- No changes to API-key limiter algorithms, Redis key format, token reservation, or error mapping.

## Command / Config Contract

There is no new public API, command, DTO, or payload.

The implementation is expected to add backend test code only. Required command contracts:

```bash
cd backend
mvn -q "-Dtest=ProductionContextSmokeTest" test
mvn -q "-Dtest=ProductionContextSmokeTest,SanguiRagGatewayApplicationTests,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

If the selected implementation uses a different test class name, update the command with that exact class.

Configuration fields that must be explicitly aligned in the smoke:

| Property | Positive Smoke | Missing/Invalid Smoke |
|---|---|---|
| `spring.profiles.active` | non-`test`, e.g. `prod-smoke` | non-`test`, e.g. `prod-smoke` |
| `rag.gateway.secret-key` | safe test-only non-blank value long enough for JWT signing | absent or blank; startup must fail visibly |
| `rag.gateway.api-key-limits.enabled` | default `true` unless there is a documented test-only reason to set it explicitly true | not the target |
| `rag.gateway.api-key-limits.default-*` | positive values; binding succeeds | one invalid non-positive value may be an optional additional negative case |
| `spring.datasource.*` | configured for the smoke strategy | missing datasource is not the target unless separately asserted |
| `spring.data.redis.*` | configured for the smoke strategy | missing/unreachable Redis should not be silently hidden |

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Positive non-`test` context starts with required safe test properties | Spring context loads successfully | Test completes without startup exception |
| Gateway auth config is active outside `test` | `GatewayAuthFilter` bean exists and registration maps `/v1/*` | Assert bean presence and URL pattern/order where practical |
| API-key limiter production wiring is active | `ApiKeyRateLimitService` has a real `StringRedisTemplate` dependency | Assert both beans exist; do not replace with a mock |
| Redis auto-config is not excluded in smoke | `StringRedisTemplate` bean exists from Spring Redis auto-config | Assert bean presence; avoid `application-test.yml` excludes |
| OpenAI chat production endpoint is active | `OpenAiChatCompletionsController` bean exists | Assert bean presence |
| Chat service dependency chain is active | `ChatCompletionGatewayService`, `RetrievalService`, upstream chat and embedding clients exist | Assert bean presence |
| Production secret supplied | `UpstreamApiKeyEncryptor` and `AdminJwtService` initialize | Assert bean presence or context success |
| Secret missing/blank under non-`test` profile | Context startup fails visibly | Assert failure root/message includes `rag.gateway.secret-key must not be blank` or `JWT secret must not be blank` |
| Invalid API-key limiter defaults, if included | Context startup fails via configuration validation | Assert failure mentions binding/validation for the invalid `rag.gateway.api-key-limits.*` property |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | A non-`test` smoke with explicit safe properties starts the backend context and asserts gateway auth, Redis template, API-key limiter, OpenAI controller/service, retrieval, embedding/upstream clients, and secret-backed beans are present. |
| Base | Existing `test` profile context tests still pass and may continue to verify lightweight controller/error-handler behavior, but they are not counted as production wiring evidence. |
| Bad | The smoke passes while using `@ActiveProfiles("test")`, `application-test.yml`, Redis/DataSource auto-config excludes, mocked production beans, disabled limiter defaults, or newly added runtime fallbacks. |

## Expected Code Research Findings

- Current `backend/src/test/resources/application-test.yml` excludes JDBC, Flyway, Redis, and MyBatis-Plus auto-configuration.
- Current `SanguiRagGatewayApplicationTests` uses `@SpringBootTest` with `@ActiveProfiles("test")`, so it does not cover `@Profile("!test")` production beans.
- Many real runtime beans are annotated `@Profile("!test")`, including gateway auth config, admin auth config, encryption config, API key services, model/app/document/log services, OpenAI controllers, retrieval, and embedding.
- `ApiKeyRateLimitService` requires `StringRedisTemplate`, `ApiKeyLimitProperties`, and `ApiKeyService`.
- `ApiKeyLimitProperties` uses `@Validated` and `@Min(1)` on defaults, so invalid configured defaults should fail startup.
- `application.yml` intentionally leaves `rag.gateway.secret-key` blank by default; `application-dev.yml` supplies only a local dev placeholder. Production-like smoke must supply a test-only secret explicitly.
- `UpstreamApiKeyEncryptor` fails fast on blank `rag.gateway.secret-key`; `AdminJwtService` also rejects blank secrets.

## Files Likely To Modify

Expected:

- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java` or similarly named backend test class.

Possible only if the chosen smoke strategy needs a test-only non-runtime resource:

- `backend/src/test/resources/application-prod-smoke.yml`

Avoid unless there is a clear test-scope reason:

- `backend/pom.xml` only if adding a test-scope dependency such as Testcontainers or an embedded service. Prefer existing dependencies if sufficient.

Do not modify:

- `backend/src/main/java/**`
- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application-dev.yml`
- `deploy/docker-compose.yml`
- frontend files
- migrations
- runtime scripts

## Required Tests

At minimum:

```bash
cd backend
mvn -q "-Dtest=ProductionContextSmokeTest" test
mvn -q "-Dtest=ProductionContextSmokeTest,SanguiRagGatewayApplicationTests,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

Recommended follow-up regression when implementation is done:

```bash
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,GatewayAuthFilterTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
```

If Testcontainers/Docker is used and unavailable locally, report the Docker-specific failure separately and still keep the config-level smoke passing if implemented without Docker.

## Acceptance Criteria

- [ ] New production-context smoke is backend test-only.
- [ ] Positive smoke runs under a non-`test` profile and does not use `application-test.yml` auto-config excludes.
- [ ] Positive smoke asserts critical gateway auth, Redis, limiter, OpenAI controller/service, retrieval, upstream/embedding, and secret-backed beans.
- [ ] Negative smoke proves missing/blank `rag.gateway.secret-key` fails clearly.
- [ ] No runtime fallback, business behavior, API contract, schema, frontend, or Docker behavior is changed.
- [ ] Targeted smoke command passes.
- [ ] Focused backend regression commands pass or failures are explained as environment-only.

## Implementation Notes For DeepSeek

- Start from a test class rather than changing runtime config.
- Do not use `@ActiveProfiles("test")`.
- Do not add `spring.autoconfigure.exclude` for Redis/JDBC/Flyway/MyBatis in the positive smoke.
- Prefer asserting bean presence/registration over exercising request handling.
- If using `ApplicationContextRunner`, ensure it loads the same production auto-config and component scan needed to prove the real `!test` beans are active. A runner that hand-registers only a subset of beans is not enough unless it is explicitly used only for the negative secret/config test.
- If using `@SpringBootTest`, provide required properties through `@TestPropertySource` or `properties = ...` on the test annotation with safe test-only values.
- Keep failures visible. Do not catch startup failures and convert them into skipped tests.
