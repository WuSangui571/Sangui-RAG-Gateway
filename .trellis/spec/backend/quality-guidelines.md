# Backend Quality Guidelines

> Quality is judged by API compatibility, tenant isolation, RAG correctness, security, streaming behavior, and operational clarity.

## Testing Requirements

MVP should include focused tests for:

```text
API key authentication
document chunking
knowledge retrieval
prompt construction
OpenAI-compatible request parsing
non-streaming upstream forwarding
streaming forwarding
multi-tenant isolation
```

## Backend Docker Maven Build Contract

`backend/settings.xml` is part of the backend Docker build contract because
`backend/Dockerfile` copies it to `/root/.m2/settings.xml` before running:

```bash
mvn -B -ntp -DskipTests package
```

Rules:

- `backend/settings.xml` may contain public Maven mirror metadata only.
- It must not contain credentials, tokens, usernames, passwords, private repository URLs, provider keys, or environment-expanded secrets.
- The Docker build must not leave Maven Central uncovered. `spring-boot-starter-parent` and other parent POMs resolve from repository id `central`, so `backend/settings.xml` must include an explicit public mirror for `central` in network environments where direct `repo.maven.apache.org` TLS is unreliable.
- Do not use `mirrorOf=*`. Keep mirror selectors specific enough that the active mirror policy is reviewable, for example one public mirror for `central` and one public mirror for `external:*,!central`.
- Keep the Dockerfile Maven command visible. Do not reintroduce quiet `dependency:go-offline -q` or other hidden dependency-resolution steps that mask the failing artifact.
- The runtime stage must run the Java process as a non-root user. The user is created with `adduser -D -h /app sangui` and owns `/app` recursively, including `/app/data/uploads` for local file storage.
- Do not change the Maven build stage user contract: `settings.xml` is still copied to `/root/.m2/settings.xml` and `mvn -B -ntp -DskipTests package` runs as root in the build stage.
- The runtime healthcheck (`curl -sf http://localhost:8080/api/health`) must execute successfully under the non-root runtime user.

Required checks after changing `backend/settings.xml`, `backend/Dockerfile`,
or the backend Docker/Compose build path:

```bash
cd backend
mvn -q -DskipTests compile
cd ..
docker build --progress=plain -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
docker compose --progress=plain --env-file .env -f deploy/docker-compose.yml build backend --no-cache
```

Good/base/bad cases:

| Case | Expected result |
|---|---|
| Good | `backend/settings.xml` explicitly mirrors `central` through a public repository, contains only public repository metadata, the Docker build succeeds through `mvn -B -ntp -DskipTests package`, and the runtime Java process runs as non-root user `sangui` with writable `/app/data/uploads`. |
| Base | Docker is not available locally; XML syntax, secret scan, Dockerfile settings path, Dockerfile non-root user directives, and `mvn -q -DskipTests compile` are verified, and the missing Docker evidence is stated explicitly. |
| Bad | `central` is excluded from all mirrors and Docker falls back to direct `repo.maven.apache.org` access in an unreliable network; a public mirror uses `mirrorOf=*`; dependency resolution is hidden behind quiet prefetch steps; settings contain credentials or private repository URLs; runtime Java process runs as root; `/app/data/uploads` is not writable by the runtime user. |

High-priority unit tests:

```text
RagPromptBuilder
TextChunker
ApiKeyHasher
RetrievalService
OpenAiResponseAdapter
DocumentParser implementations
```

High-priority integration test:

```text
upload document -> parse/chunk/embed -> call chat completions -> return enhanced answer
```

## Review Checklist

Before completing backend work, verify:

- [ ] Public gateway responses remain OpenAI-compatible for the supported subset.
- [ ] API keys and upstream keys are never stored or logged in plaintext.
- [ ] Upstream API keys are encrypted at rest with AES-256-GCM using `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`; `RAG_GATEWAY_SECRET_KEY` is deprecated compatibility input only.
- [ ] Admin API responses only return `api_key_masked`, never `api_key_encrypted` or plaintext upstream keys.
- [ ] Admin endpoints require `Authorization: Bearer <admin-jwt>` and derive tenant identity from `AdminAuthContextHolder`, not request headers.
- [ ] Admin model config CRUD endpoints enforce same-user ownership with 404/403 distinction.
- [ ] App-model config binding validates same-user ownership of both app and model config.
- [ ] Disabled model configs are excluded from `/v1/models` resolution.
- [ ] Queries touching tenant data include user/app/knowledge-base boundaries.
- [ ] Vector retrieval is scoped in SQL, not filtered after retrieval in Java.
- [ ] Document status transitions are explicit and failure states are persisted.
- [ ] Upstream HTTP calls have timeouts and normalized error handling.
- [ ] Streaming requests cancel upstream calls when clients disconnect (unit-tested via `SseEmitter` send `IOException` handling in `OpenAiCompatibleUpstreamClient`; runtime smoke verified via `OpenAiChatCompletionsRuntimeSmokeTest` with real embedded servlet container and HTTP client).
- [ ] Request logs avoid full prompts and document content.
- [ ] New database fields have migration notes and indexes where needed.
- [ ] Tests cover both success and relevant failure paths.
- [ ] API-key rate limits validate request payloads before quota reservation, so malformed or invalid requests do not consume quota.
- [ ] Redis limiter failures are visible OpenAI-compatible failures, not silent bypasses or admin-envelope responses.
- [ ] Token reservation reconciliation and release use the same minute/day Redis windows as the preflight reservation.

API key rate-limit regression checks:

```bash
cd backend
mvn -q "-Dtest=ApiKeyServiceTest,GatewayAuthFilterTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

Streaming runtime smoke verification:

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=ApiKeyRateLimitServiceTest,ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```

The runtime smoke uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real embedded servlet container and Java 21 `java.net.http.HttpClient`. It does not require PostgreSQL, Redis, Docker, or external providers. It covers normal streaming `[DONE]`, client disconnect, emitter timeout, and post-start upstream failure. Every streaming row asserts `output_capture_status=STREAMING_UNSUPPORTED`. Reservation release is asserted exactly once for disconnect, timeout, and failure; normal success does not release. Backend unit tests must run with a hard timeout of 60 seconds per command when feasible.

## RAG Pipeline Quality

For ingestion changes, verify:

- Parser selection is deterministic.
- Unsupported file types fail clearly.
- Chunk size and overlap are configurable.
- Embedding dimension is validated against the knowledge base.
- Large documents can be handled asynchronously when needed.

For retrieval changes, verify:

- `top_k`, similarity threshold, max context tokens, and max single chunk tokens are respected.
- Duplicate chunks or repeated source content are controlled.
- Retrieval never crosses tenant or knowledge-base boundaries.

For prompt changes, verify:

- Original user messages are preserved.
- Original user system prompt is preserved.
- RAG context is clearly separated from user input.
- No-hit behavior follows the configured policy, defaulting to `STRICT_RAG`.

## API Compatibility

MVP supports only:

```text
GET /v1/models
POST /v1/chat/completions
```

Do not accidentally imply support for unsupported OpenAI APIs in docs or responses.

Unsupported MVP fields/features:

```text
/v1/responses
/v1/embeddings
/v1/images
tools
function_call
vision
audio
response_format
parallel_tool_calls
```

If unsupported fields are received, either ignore them only when safe and documented, or return a compatible error.

## Performance Rules

- Do not hold database transactions open across upstream HTTP calls.
- Use bounded context size before calling upstream chat models.
- Avoid loading full documents into gateway request logs.
- Batch embedding calls where provider and limits allow.
- Add pagination to admin list APIs.
- Use indexes for common admin and gateway lookup paths.

## Forbidden Patterns

- Plaintext API keys in database, logs, responses, or exceptions.
- Controller methods containing RAG orchestration details.
- Global vector search without tenant and knowledge-base filters.
- Hard-coded provider-specific logic inside generic gateway code.
- Prompt construction mixed into retrieval SQL or upstream client code.
- Silent fallback to pass-through when the app is configured for strict RAG.
- Adding broad platform features that do not serve the lightweight RAG gateway goal.

## Knowledge Base and Document Ingestion Baseline Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
```

Tested areas:
- Knowledge base create/list/detail with tenant isolation and status validation.
- Document upload (txt/md/markdown) with sync processing (UPLOADED->PARSING->PARSED/FAILED).
- Parser selection by filename extension.
- UTF-8 text parsing and text normalization.
- Deterministic character-based chunking with overlap.
- Local file storage with path traversal prevention and UUID-based keys.
- Document VO excludes `storage_path`.
- Controller 403/404/400 error matrix for admin endpoints.
- Empty file, unsupported extension, and parse failure handling.

Regression tests must still pass:
- All existing admin tests (app, API key, model config).
- All existing gateway tests (chat completions, upstream forwarding).
- All existing auth and error handler tests.

## Embedding and Vector Storage Baseline Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=ModelConfigServiceTest" test
```

RAG retrieval and prompt augmentation baseline tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest,DocumentServiceTest,DocumentAdminControllerTest,ModelConfigServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest,ApiRequestLogServiceTest" test
mvn test
```

Tested areas:
- Embedding client URL construction for base URL variants.
- Safe logging (no vectors, chunk content, upstream keys, provider bodies).
- Response count/index/dimension validation.
- Non-2xx, timeout, and malformed response handling.
- Document happy path through EMBEDDING to READY.
- Vector persistence per chunk with tenant-safe fields.
- Missing/disabled/mismatched embedding config safe failure.
- Embedding failure safe error_message and KB status preservation.
- Admin status filter accepts EMBEDDING and READY.

## Request Log Observability Admin API Tests

Required targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
```

Tested areas:
- List endpoint with default pagination, status filter (success/failure/cancelled case-insensitive), error_code, start_time/end_time range.
- Invalid page/page_size/status/time format/time range returns `400 INVALID_REQUEST`.
- Missing app returns `404 NOT_FOUND`; cross-user app returns `403 FORBIDDEN` (no log query executed).
- Detail endpoint returns safe fields only (no prompt, messages, api_key, key_hash, upstream_api_key, chunk_content, embedding, provider_response_body, stack_trace).
- Hit chunk endpoint returns tenant-scoped summaries with bounded content (200 chars).
- App with no default KB returns `400 INVALID_REQUEST` for hit-chunks.
- Empty/null hit_chunk_ids returns empty summary list.
- JSONB `hit_chunk_ids` parsed to numeric array; sensitive fields absent from responses.
- Admin auth validation (missing/non-Bearer/invalid/expired JWT returns `401 UNAUTHORIZED`; controller fallback without context returns `401 UNAUTHORIZED`).

Regression tests must still pass:
```bash
mvn -q "-Dtest=AppAdminControllerTest,DocumentAdminControllerTest,RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn test
```

## Source Citation and Retrieval Evaluation Tests

Required targeted tests after changing citation/evidence/evaluation behavior:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```

Tested areas:

- Default `/v1/chat/completions` response omits `sangui_citations` and otherwise preserves OpenAI-compatible response serialization.
- Opt-in non-streaming responses include bounded `sangui_citations` only.
- `stream=true` does not emit P1 citation SSE events, but request logs still persist retrieval evidence.
- Citation IDs and order match final injected chunks and `hit_chunk_ids`.
- Request-log `retrieval_evidence` stores safe metadata only and malformed JSON fails visibly.
- Retrieval SQL keeps user and knowledge-base scope when loading citation source filenames.
- Retrieval evaluation reports safe precision/recall/MRR metadata without chunk content, prompts, embeddings, keys, storage paths, or provider raw bodies.

## Object Storage and Delete Lifecycle Tests

Required targeted tests after changing storage backend selection or delete lifecycle:

```bash
cd backend
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest,DocumentConfigTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest" test
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
```

Tested areas:

- Local storage keeps UUID-based non-guessable keys, path traversal protection, and idempotent delete.
- Object storage uses the same opaque logical key shape, calls S3 put/delete APIs, and treats `NoSuchKey` or S3 `404` from `headObject` as cleanup-complete.
- Object storage non-404 S3 failures remain visible exceptions.
- `DocumentConfig` selects only `local` or `object`, rejects unknown types, and names missing object config properties without echoing secret values.
- Document delete returns `404` for missing documents, `403` for cross-user documents, and never calls storage cleanup before ownership is verified.
- Successful document delete attempts storage cleanup before deleting embeddings, chunks, and document rows, then updates KB status.
- Knowledge-base delete rejects same-user app references with `409 KNOWLEDGE_BASE_IN_USE`.
- Delete API responses do not expose `storage_path`, object endpoint, bucket, access key, secret key, or absolute local paths.

## Runtime-Only Bean Coverage Governance

### Rule

Runtime-only beans annotated with `@Profile("!test")` must not be validated only by standard `test` profile integration tests. A dedicated non-`test` profile smoke test must prove that Spring can create these beans with their real constructors, `@Value` configuration binding, and profile conditions.

Pure unit tests remain responsible for business logic, parsing, error mapping, and local behavior branches. Runtime-like Spring context smoke tests are required for beans whose real constructor/configuration is hidden by the standard `test` profile.

### Required Profile and Properties

The runtime smoke must:
- Use a test-local non-test profile such as `runtime-smoke` — never `prod`, `production`, or `test`.
- Exclude auto-configuration: `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `FlywayAutoConfiguration`, `RedisAutoConfiguration`, `MybatisPlusAutoConfiguration`.
- Use mock/stub dependencies for mappers, services, Redis template, and upstream collaborators.
- Avoid external connections: no PostgreSQL, Redis, Flyway, MyBatis, object storage, or live upstream provider calls.

### Targeted Test Command

```bash
cd backend
mvn -q "-Dtest=RuntimeProfileBeanSmokeTest,GatewayTimeoutConfigurationTest,OpenAiCompatibleEmbeddingClientTest,ModelConfigCheckServiceTest" test
mvn -q -DskipTests compile
```

### Required Coverage Areas

| Area | Required test | Pattern |
|---|---|---|
| Runtime-only bean creation under non-test profile | `RuntimeProfileBeanSmokeTest` positive smoke | `ApplicationContextRunner` with narrow user configuration and mock collaborators |
| Standard test profile exclusion | `RuntimeProfileBeanSmokeTest` test-profile exclusion | `ApplicationContextRunner` + `spring.profiles.active=test` → `doesNotHaveBean` |
| Timeout binding with real constructors | `RuntimeProfileBeanSmokeTest` positive/default timeouts | `ApplicationContextRunner` importing the production bean class directly |
| Zero/negative timeout fail-fast | `RuntimeProfileBeanSmokeTest` negative timeout | `ApplicationContextRunner` → `hasFailed` + root cause message assertion |
| Worker scheduler disabled in smoke | `RuntimeProfileBeanSmokeTest` worker scheduler boundary | `rag.document-processing.worker.enabled=false` → `doesNotHaveBean(DocumentProcessingScheduler.class)` |
| Worker scheduler registration when intentionally covered | `RuntimeProfileBeanSmokeTest` worker scheduler boundary | Mock `DocumentProcessingWorker` and assert registration without invoking scheduled methods |
| Legacy timeout fallback | `GatewayTimeoutConfigurationTest` | `ApplicationContextRunner` with `ConfigDataApplicationContextInitializer` |
| Explicit response timeout precedence | `GatewayTimeoutConfigurationTest` | Same pattern, explicit `response-timeout-seconds` |

### Current `@Profile("!test")` Inventory

Keep this inventory categorized when runtime-only beans are added, removed, or moved. High-risk beans with constructor configuration, filter registration, scheduler behavior, or external-service collaborators need runtime-like smoke coverage; lower-risk service/controller beans may stay covered by focused unit tests plus existing runtime servlet smoke when that avoids fake broad integration tests.

| Category | Current beans |
|---|---|
| Auth / security / api-key | `AdminAuthConfig`, `GatewayAuthConfig`, `EncryptionConfig`, `AdminAuthController`, `AdminAuthService`, `DefaultAdminBootstrapService`, `UserService`, `ApiKeyService`, `ApiKeyAdminController`, `ApiKeyRateLimitService` |
| Gateway | `OpenAiChatCompletionsController`, `OpenAiModelsController`, `ChatCompletionGatewayService` |
| Embedding / retrieval | `OpenAiCompatibleEmbeddingClient`, `RetrievalService`, `RetrievalEvaluationService`, `RetrievalEvaluationAdminController` |
| Model-config / app / knowledge | `ModelConfigService`, `ModelConfigCheckService`, `ModelConfigAdminController`, `AppService`, `AppAdminController`, `KnowledgeBaseService`, `KnowledgeBaseAdminController` |
| Worker / document / storage | `SchedulingConfig`, `DocumentConfig`, `DocumentService`, `DocumentAdminController`, `DocumentProcessingTaskService`, `DocumentProcessingWorker`, `DocumentProcessingScheduler` |
| Logs / observability | `ApiRequestLogService`, `ApiRequestLogAdminController`, `RequestLogOutputCleanupScheduler` |

### Targeted High-Risk Smoke Beans

| Bean | Package | Required smoke boundary |
|---|---|---|
| `OpenAiCompatibleEmbeddingClient` | `com.sangui.raggateway.embedding` | Real Spring constructor, default/explicit timeout binding, invalid timeout fail-fast |
| `ModelConfigCheckService` | `com.sangui.raggateway.model` | Real Spring constructor with mocked collaborators, upstream timeout binding, invalid timeout fail-fast |
| `GatewayAuthConfig` | `com.sangui.raggateway.common.config` | `GatewayAuthFilter` and `/v1/*` `FilterRegistrationBean` creation |
| `AdminAuthConfig` | `com.sangui.raggateway.common.config` | `AdminJwtService` creation with strong test-only JWT secret |
| `EncryptionConfig` | `com.sangui.raggateway.common.config` | `UpstreamApiKeyEncryptor` creation with strong test-only AES secret |
| `DocumentConfig` | `com.sangui.raggateway.document.config` | Local `FileStorageService`, `TextChunker`, and text parser beans without object-storage client creation |
| `DocumentProcessingScheduler` | `com.sangui.raggateway.document` | Absent when `rag.document-processing.worker.enabled=false`; present only in an intentional mocked-worker registration smoke |

### Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | A runtime-like non-test profile smoke starts a narrow Spring context with real runtime constructors/config classes and mocked infrastructure; it proves key runtime-only beans wire correctly, validates timeout defaults/fallbacks/fail-fast behavior, and the spec records the governance rule. |
| Base | Existing pure unit tests continue to cover local behavior; the runtime smoke does not exercise external HTTP/provider calls, database access, Redis scripts, Flyway migrations, or scheduler loops. |
| Bad | Only adding more unit tests around package-private constructors; using `@ActiveProfiles("test")` and claiming production-like coverage; connecting to local PostgreSQL/Redis/provider; removing `@Profile("!test")`; hiding failed wiring with fallback mocks or broad try/catch. |
