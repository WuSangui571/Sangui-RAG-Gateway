# Research: Test Profile Coverage Governance

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, non-goals, timeout env/property contracts, production guard profile behavior, and deployment secret rules.
- `.trellis/spec/backend/directory-structure.md`: backend package ownership; runtime-only tests should mirror backend package structure and keep common config under `common`.
- `.trellis/spec/backend/database-guidelines.md`: confirms DB, mapper, tenant, and migration boundaries are out of scope for this task; smoke must not require database connections.
- `.trellis/spec/backend/error-handling.md`: failures should remain visible and safe; no hidden fallbacks or broad success masking.
- `.trellis/spec/backend/logging-guidelines.md`: runtime smoke must not log keys, provider bodies, prompts, chunk content, vectors, or stack traces in client-facing paths.
- `.trellis/spec/backend/quality-guidelines.md`: testing and runtime smoke guidance; this is the preferred spec to update with the new runtime-only bean coverage rule.
- `.trellis/spec/gateway/resilience.md`: timeout contract for upstream chat, embedding, and model-config chat probe; invalid timeout values must fail visibly.
- `.trellis/spec/security/rag-security.md`: auth/secret boundary; runtime-like tests must keep admin JWT and app API key auth separate and safe.
- `.trellis/spec/rag/document-ingestion.md`: document worker/embedding boundaries; worker smoke must not run parser/embedding/provider work or persist fake success.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: profile/config/env behavior is a cross-layer trigger; map validation owner and tests before implementation.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: reuse existing `ApplicationContextRunner`/runtime smoke patterns rather than inventing a broad full-stack harness.

## Current Project State From Trellis Journal

- Session 91 recorded `gateway-connect-timeout-governance` as complete on `feature/gateway-connect-timeout-governance`.
- That work split connect/response timeouts for chat and embedding, introduced/used `RestClientTimeoutFactory`, aligned `ModelConfigCheckService` chat probe timeouts, and updated specs/docs.
- Validated commands included targeted timeout/client/check-service tests and `mvn -q -DskipTests compile`.
- The current branch is `feature/test-profile-coverage-governance`, clean before task setup, with no active task.

## Code Patterns Found

### Existing non-test profile smoke

File: `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`

Pattern:

- Uses `ApplicationContextRunner`.
- Imports narrow runtime config classes.
- Supplies mock dependencies through `@TestConfiguration`.
- Asserts bean creation and startup failures without external services.

Useful examples:

- `GatewayAuthConfig` smoke creates `GatewayAuthFilter` and `FilterRegistrationBean`.
- `AdminAuthConfig` smoke creates `AdminJwtService` when strong test secret is provided.
- Guard tests assert non-test secret failures while `test` profile skips the guard.

### Existing runtime HTTP smoke without external DB/Redis/provider

File: `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`

Pattern:

- Uses `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Defines a small `SmokeApplication`.
- Excludes `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration`, `RedisAutoConfiguration`, and `MybatisPlusAutoConfiguration`.
- Imports only needed gateway/controller/config classes.
- Supplies Mockito bean stubs through `@TestConfiguration`.
- Uses a real Java `HttpClient` against embedded servlet container.

This is useful for runtime servlet behavior, but this task should prefer narrower `ApplicationContextRunner` where no HTTP behavior is required.

### Existing timeout binding test

File: `backend/src/test/java/com/sangui/raggateway/gateway/upstream/GatewayTimeoutConfigurationTest.java`

Pattern:

- Uses `ApplicationContextRunner` with `ConfigDataApplicationContextInitializer`.
- Binds `@Value` timeout expressions through small test beans.
- Covers default, legacy fallback, and explicit response-timeout precedence.

Blind spot:

- It proves property-expression binding, not real `@Profile("!test")` bean creation for embedding/model-config clients.

### Existing local client/service unit tests

Files:

- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigCheckServiceTest.java`

Pattern:

- Direct construction with package-private constructors or direct static factory calls.
- Mock HTTP via `MockRestServiceServer` or `RestClient` collaborators.
- Good for behavior/error mapping/log safety.

Blind spot:

- Direct construction does not prove Spring can create the runtime bean with production constructor, `@Value` properties, profile conditions, and actual collaborator wiring.

## `@Profile("!test")` Inventory

Command:

```bash
rg -n '@Profile' backend/src/main/java
```

### Auth / security / API key

- `backend/src/main/java/com/sangui/raggateway/common/config/AdminAuthConfig.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/GatewayAuthConfig.java`
- `backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java`
- `backend/src/main/java/com/sangui/raggateway/auth/AdminAuthController.java`
- `backend/src/main/java/com/sangui/raggateway/auth/AdminAuthService.java`
- `backend/src/main/java/com/sangui/raggateway/auth/DefaultAdminBootstrapService.java`
- `backend/src/main/java/com/sangui/raggateway/user/UserService.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitService.java`

Coverage recommendation:

- Keep unit tests for filter/service behavior.
- Keep/extend runtime context smoke for config beans and filter registrations.
- Do not hit Redis; mock `StringRedisTemplate` if `ApiKeyRateLimitService` is included.

### Gateway

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModelsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`

Note:

- `OpenAiCompatibleUpstreamClient` is a runtime component but is not annotated with `@Profile("!test")`; it is still relevant to timeout construction and may already be present in standard test profile context.

Coverage recommendation:

- Runtime servlet lifecycle already covered by `OpenAiChatCompletionsRuntimeSmokeTest`.
- Pure unit tests remain responsible for request validation, error mapping, rate-limit ordering, request-log fields, and streaming terminal states.

### Embedding / retrieval

- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/evaluation/RetrievalEvaluationService.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/evaluation/RetrievalEvaluationAdminController.java`

Coverage recommendation:

- Add runtime-like context smoke for `OpenAiCompatibleEmbeddingClient` real constructor and timeout values.
- Keep unit tests for provider response parsing, count/dimension validation, timeout/error classification, and safe logging.
- Retrieval services require mapper/model/app dependencies; include in smoke only with mocks if the assertion is valuable and does not produce a broad fake integration test.

### Model-config / app / knowledge

- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`

Coverage recommendation:

- Add runtime-like context smoke for `ModelConfigCheckService` real constructor with mocked `ModelConfigService`, `UpstreamApiKeyEncryptor`, and `EmbeddingClient`.
- Keep unit tests for saved/unsaved check semantics, decrypt failure messaging, capability validation, and embedding probe results.

### Worker / document / storage

- `backend/src/main/java/com/sangui/raggateway/common/config/SchedulingConfig.java`
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentConfig.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingWorker.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingScheduler.java`

Coverage recommendation:

- Add runtime-like context smoke for `DocumentConfig` local storage/parser/chunker beans with safe local path and no object storage client.
- If scheduler is included, set `rag.document-processing.worker.enabled=false` for base smoke and optionally add a separate mocked-dependency assertion for enabled scheduler registration.
- Do not run scheduled methods or worker loops in smoke tests.

### Logs / observability

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/log/RequestLogOutputCleanupScheduler.java`

Coverage recommendation:

- Keep service/controller unit tests for safe fields and error matrices.
- Include cleanup scheduler in runtime smoke only with mocked `ApiRequestLogService` and explicit properties if doing so does not trigger scheduled execution.

## Files Likely To Modify

Preferred:

- `backend/src/test/java/com/sangui/raggateway/RuntimeProfileBeanSmokeTest.java`
- `.trellis/spec/backend/quality-guidelines.md`

Possible alternative or supporting edits:

- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/GatewayTimeoutConfigurationTest.java`
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigCheckServiceTest.java`

Production code should stay unchanged unless the new smoke exposes a concrete runtime wiring failure.

## Risk / Boundary Notes

- The main risk is replacing a real wiring check with another direct-constructor unit test. The runtime-like smoke must let Spring create the target beans.
- Do not activate `test` profile in the runtime-like inclusion smoke, or `@Profile("!test")` remains excluded.
- Do not activate `prod`/`production`; production guard will add unrelated datasource/Redis/storage constraints.
- Use a profile such as `runtime-smoke` plus strong test-only JWT/AES secrets to satisfy `ProductionConfigGuard` when imported.
- Avoid component-scanning the whole app unless all mapper/Redis/storage dependencies are intentionally mocked. A narrow import/context runner is safer and clearer.
- Scheduler beans can start background behavior if scheduling is enabled; keep the base smoke scheduler-free or disabled.
- `DocumentConfig` with `type=object` would build an S3 client; use `local` storage for smoke unless specifically testing object config failure.
- Do not add hidden fallbacks, broad try/catch, or mock-success paths to production code to make context load.

## Required Tests

Run from `backend/`:

```bash
mvn -q "-Dtest=RuntimeProfileBeanSmokeTest,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
mvn -q -DskipTests compile
git diff --check
```

If the runtime smoke is implemented inside the existing production smoke class:

```bash
mvn -q "-Dtest=ProductionContextSmokeTest,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
```

If gateway/auth runtime imports are touched:

```bash
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest,GatewayAuthFilterTest,AdminAuthFilterTest" test
```

