# Test Profile Coverage Governance

## Goal

Close the structural testing blind spot where standard `test` profile integration tests exclude runtime-only Spring beans annotated with `@Profile("!test")`.

The task must establish lightweight, repeatable coverage that proves key runtime beans can be created under a non-`test` profile without connecting to PostgreSQL, Redis, Flyway, MyBatis, object storage, or external model providers.

## Task Classification

Complex Task.

Reason: this crosses Spring profile conditions, runtime bean wiring, configuration binding, timeout construction, scheduler/worker gates, auth filters, and backend quality-spec governance. A local unit-test-only patch would keep the same structural blind spot.

## Current State

- Current branch: `feature/test-profile-coverage-governance`.
- Working tree was clean before task setup.
- No active Trellis task existed before this task was created.
- Previous recorded task `gateway-connect-timeout-governance` changed timeout wiring around:
  - `OpenAiCompatibleUpstreamClient`
  - `OpenAiCompatibleEmbeddingClient`
  - `ModelConfigCheckService`
  - `RestClientTimeoutFactory`
  - timeout properties in `application.yml`
- Existing standard application smoke:
  - `SanguiRagGatewayApplicationTests` uses `@SpringBootTest` with `@ActiveProfiles("test")`.
  - `application-test.yml` excludes DataSource, Flyway, Redis, Hibernate JPA, and MyBatis-Plus auto-configuration.
  - Many runtime beans are excluded by `@Profile("!test")`, so this smoke cannot catch production-like bean wiring failures for those beans.
- Existing `ProductionContextSmokeTest` already demonstrates an `ApplicationContextRunner` pattern for small non-test-profile bean smoke, but currently covers only selected auth/encryption/api-key-limit configuration, not embedding/model-config/check/worker runtime beans.

## Requirements

1. Inventory all production code `@Profile("!test")` beans and classify them by domain:
   - gateway
   - embedding / retrieval
   - model-config / app / knowledge
   - worker / document / storage
   - auth / security / api-key
   - logs / observability

2. Define the coverage rule:
   - Pure unit tests remain responsible for business logic, parsing, error mapping, and local behavior branches.
   - Lightweight runtime-like Spring context smoke tests are required for runtime-only beans whose real constructor/configuration is hidden by the standard `test` profile.

3. Add or extend tests so that a non-`test` profile can instantiate the high-risk runtime-only beans without external services:
   - `OpenAiCompatibleEmbeddingClient`
   - `ModelConfigCheckService`
   - `GatewayAuthConfig` and `AdminAuthConfig` filter/bean registration
   - `EncryptionConfig`
   - document runtime config and worker/scheduler boundaries where feasible
   - timeout-bound clients using their production constructors

4. The runtime-like smoke must not connect to PostgreSQL, Redis, Flyway, MyBatis, object storage, or external providers:
   - Use mock/stub dependencies for mappers, services, Redis template, upstream collaborators, and other infrastructure dependencies.
   - Disable or avoid scheduled execution unless the assertion explicitly covers scheduler bean registration.
   - Do not send real HTTP requests to upstream chat or embedding providers.

5. Cover timeout configuration binding and construction:
   - Defaults remain connect `5s`, response `30s`.
   - Legacy `timeout-seconds` remains response-timeout fallback only.
   - Explicit `response-timeout-seconds` wins over legacy fallback.
   - Invalid zero/negative connect or response timeout values fail visibly through real runtime constructors or shared timeout factory.

6. Cover profile conditions:
   - Runtime-like non-`test` profile includes the targeted runtime-only beans when dependencies/properties are supplied.
   - Standard `test` profile excludes `@Profile("!test")` beans as expected.
   - The smoke profile must not use `prod`/`production`; use a test-local non-test profile such as `runtime-smoke`.

7. Update project guidance:
   - Update `.trellis/spec/backend/quality-guidelines.md` or `.trellis/spec/guides/cross-layer-thinking-guide.md`.
   - Record the rule that runtime-only beans must not rely only on standard `test` profile integration tests.
   - Include a short Good/Base/Bad matrix and targeted test command guidance.

## Non-Goals

- Do not change public `/v1/*` API behavior.
- Do not change Admin API DTO/VO fields, frontend types, or frontend pages.
- Do not change database schema, migrations, mapper SQL, or tenant query semantics.
- Do not change Docker Compose, production deployment, Maven mirror policy, or CI workflow unless a test-only requirement proves it unavoidable.
- Do not add provider fallback, retry, circuit breaker, alternate routing, or hidden success paths.
- Do not remove `@Profile("!test")` from runtime beans as a shortcut.
- Do not replace existing focused unit tests with a broad full-context test.
- Do not make runtime smoke depend on local PostgreSQL, Redis, Flyway, MyBatis, S3/MinIO, Docker, or live upstream provider credentials.

## API / Command / Payload Contract

No public API, database payload, frontend DTO, or migration contract should change.

Test/runtime command contract:

```bash
cd backend
mvn -q "-Dtest=<runtime-smoke-test-class>,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
mvn -q -DskipTests compile
```

Likely concrete class name:

```text
RuntimeProfileBeanSmokeTest
```

Runtime-like profile payload/properties for smoke tests:

```text
spring.profiles.active=runtime-smoke
rag.admin-auth.jwt-secret=<strong test-only secret at least 32 chars>
rag.gateway.encryption.secret-key=<strong test-only secret at least 32 chars>
rag.gateway.upstream.connect-timeout-seconds=5
rag.gateway.upstream.response-timeout-seconds=30
rag.gateway.embedding.connect-timeout-seconds=5
rag.gateway.embedding.response-timeout-seconds=30
rag.document-processing.worker.enabled=false
```

Auto-configuration exclusions or narrow `ApplicationContextRunner` user configuration must prevent external connections:

```text
DataSourceAutoConfiguration
HibernateJpaAutoConfiguration
FlywayAutoConfiguration
RedisAutoConfiguration
MybatisPlusAutoConfiguration
```

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| Non-`test` runtime-smoke profile with strong JWT/AES test secrets | Targeted runtime-only beans are created | `ApplicationContextRunner` or narrow `@SpringBootTest` assertions |
| Standard `test` profile | `@Profile("!test")` beans under test are absent | Context assertion |
| Missing or short JWT/AES secret under non-test profile when guard is imported | Context fails visibly | Existing or new guard/context assertion |
| Embedding client runtime constructor with valid timeout values | Bean is created using real timeout factory path | Context assertion for `OpenAiCompatibleEmbeddingClient` |
| Model-config check service runtime constructor with valid timeout values and mocked dependencies | Bean is created and receives required collaborators | Context assertion for `ModelConfigCheckService` |
| Zero/negative connect timeout | Context or constructor fails visibly; no silent clamp/infinite timeout | Startup failure root-cause assertion |
| Zero/negative response timeout | Context or constructor fails visibly; no silent clamp/infinite timeout | Startup failure root-cause assertion |
| Legacy timeout property only | Response timeout fallback behavior is preserved | `GatewayTimeoutConfigurationTest` or equivalent |
| Explicit response timeout plus legacy timeout | Explicit response timeout wins | `GatewayTimeoutConfigurationTest` or equivalent |
| Worker scheduler disabled in smoke | No polling loop or external work is triggered | Bean absence or conditional assertion |
| Worker/scheduler registration intentionally tested | Registration occurs only with mocked worker/task dependencies and no external DB calls | Context assertion |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | A runtime-like non-test profile smoke starts a narrow Spring context with real runtime constructors/config classes and mocked infrastructure; it proves key runtime-only beans wire correctly, validates timeout defaults/fallbacks/fail-fast behavior, and the spec records the governance rule. |
| Base | Existing pure unit tests continue to cover local behavior; the runtime smoke does not exercise external HTTP/provider calls, database access, Redis scripts, Flyway migrations, or scheduler loops. |
| Bad | Only adding more unit tests around package-private constructors; using `@ActiveProfiles("test")` and claiming production-like coverage; connecting to local PostgreSQL/Redis/provider; removing `@Profile("!test")`; hiding failed wiring with fallback mocks or broad try/catch. |

## Files Likely To Modify

Expected test files:

- `backend/src/test/java/com/sangui/raggateway/RuntimeProfileBeanSmokeTest.java` - likely new narrow runtime-like context smoke.
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java` - possible alternative place to extend existing non-test profile smoke.
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/GatewayTimeoutConfigurationTest.java` - possible extension if timeout binding assertions need to include real constructor failure cases.
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java` - keep or extend local client behavior tests if needed.
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigCheckServiceTest.java` - keep or extend local check-service behavior tests if needed.

Expected spec file:

- `.trellis/spec/backend/quality-guidelines.md` - preferred location for the runtime-only bean coverage rule.

Production files should not change unless the new smoke exposes an actual runtime wiring bug. If that happens, keep the fix narrow and directly tied to the failing wiring/configuration invariant.

## Required Tests

Run from `backend/` with a hard timeout of 60 seconds per backend unit-test command when feasible.

Primary:

```bash
mvn -q "-Dtest=RuntimeProfileBeanSmokeTest,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
```

Fallback if the smoke is implemented inside `ProductionContextSmokeTest`:

```bash
mvn -q "-Dtest=ProductionContextSmokeTest,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
```

Related regression if gateway/auth wiring is touched:

```bash
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest,GatewayAuthFilterTest,AdminAuthFilterTest" test
```

Compile:

```bash
mvn -q -DskipTests compile
```

Diff hygiene:

```bash
git diff --check
```

## Acceptance Criteria

- [ ] The `@Profile("!test")` inventory is documented and categorized.
- [ ] The implementation distinguishes pure unit coverage from runtime-like Spring context smoke coverage.
- [ ] A runtime-like non-test smoke covers the targeted high-risk runtime-only beans without external DB/Redis/Flyway/MyBatis/provider connections.
- [ ] The smoke asserts both non-test inclusion and test-profile exclusion for representative runtime-only beans.
- [ ] Timeout binding and invalid timeout fail-fast behavior remain covered.
- [ ] Spec guidance records that runtime-only beans cannot be validated only by standard `test` profile integration tests.
- [ ] Required targeted tests and compile pass.
- [ ] No public API, DB schema, frontend type, Docker/CI, retry/fallback/routing, or provider-call behavior changes are introduced.

