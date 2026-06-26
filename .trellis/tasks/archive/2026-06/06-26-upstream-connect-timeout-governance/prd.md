# Upstream Connect Timeout Governance

## Task Classification

Complex Task.

Reason: this task affects upstream chat and embedding HTTP clients, timeout configuration contracts, OpenAI-compatible gateway error mapping, safe logs, tests, and documentation/spec synchronization. It is a backend/gateway resilience task only. Codex planning mode must not edit business implementation files.

## Current Project State

- Branch: `feature/gateway-connect-timeout-governance`.
- Working tree was clean before task creation.
- No active Trellis task existed before this task.
- Latest journaled work: health service contract sync was completed and archived after commit `e88a0e47`.

## Goal

Make upstream connection timeout behavior bounded, configurable, testable, and documented without adding silent fallback, retry, provider failover, or broad platform behavior.

The immediate problem is that current chat, embedding, and model-config probe RestClient setup uses one timeout value for both connect and read/response behavior. The default is 30 seconds, so failed or unreachable upstream endpoints can spend too long at the connection boundary and degrade real gateway recovery.

## Scope

In scope:

- Upstream chat client timeout configuration in `OpenAiCompatibleUpstreamClient`.
- Embedding client timeout configuration in `OpenAiCompatibleEmbeddingClient`.
- Admin model-config chat probe timeout configuration in `ModelConfigCheckService` if it shares the same upstream/probe RestClient boundary.
- Spring configuration defaults in backend resources.
- Environment variable examples and README configuration docs if new keys are introduced.
- Gateway resilience spec updates if the executable timeout contract changes.
- Focused unit tests for timeout configuration and existing error mapping.
- Existing gateway error semantics: chat connect/read timeout must map to `upstream_timeout`; embedding timeout must remain `embedding_failed`.

Out of scope:

- No public API shape changes.
- No database schema, migration, entity column, mapper, or frontend type changes.
- No new `/v1/*` endpoint support.
- No retry, fallback, circuit breaker, provider routing, health probe scheduler, or silent pass-through.
- No changes to RAG no-hit policy, retrieval SQL, prompt construction, request-log output preview, Docker image security, nginx config, frontend i18n/a11y, or health endpoint contract.
- No direct business-code edits by Codex during this planning handoff.

## Contract Definition

### Configuration fields

Expected implementation should define distinct timeout semantics while preserving compatibility where practical:

| Field | Purpose | Suggested default | Notes |
|---|---:|---:|---|
| `rag.gateway.upstream.connect-timeout-seconds` | TCP/connect establishment timeout for chat upstream calls | `5` | Must be shorter than response timeout. |
| `rag.gateway.upstream.response-timeout-seconds` or `read-timeout-seconds` | Read/response timeout for non-streaming and stream setup/read boundary | keep current `30` unless code proves a better existing naming pattern | Do not silently remove current behavior. |
| `rag.gateway.upstream.timeout-seconds` | Existing legacy combined timeout | `30` | Keep as backward-compatible fallback only if new read/response field is absent. |
| `rag.gateway.embedding.connect-timeout-seconds` | TCP/connect timeout for embedding calls and probes | `5` | Applies to query embedding and ingestion embedding. |
| `rag.gateway.embedding.response-timeout-seconds` or `read-timeout-seconds` | Read/response timeout for embedding calls and probes | keep current `30` | Existing `rag.gateway.embedding.timeout-seconds` may remain legacy fallback. |
| `rag.gateway.streaming.emitter-timeout-seconds` | Gateway-owned SSE emitter timeout | existing `300` | Must not be conflated with upstream connect timeout. |

If the implementer chooses property classes, keep them in a package consistent with existing config conventions and validate invalid values visibly. If the implementer keeps constructor `@Value`, add explicit tests that verify generated request factories receive the configured values.

### Environment variables

If new Spring properties are introduced, document corresponding env keys:

| Env var | Spring property | Default |
|---|---|---:|
| `RAG_GATEWAY_UPSTREAM_CONNECT_TIMEOUT_SECONDS` | `rag.gateway.upstream.connect-timeout-seconds` | `5` |
| `RAG_GATEWAY_UPSTREAM_RESPONSE_TIMEOUT_SECONDS` or `RAG_GATEWAY_UPSTREAM_READ_TIMEOUT_SECONDS` | matching response/read property | `30` |
| `RAG_GATEWAY_EMBEDDING_CONNECT_TIMEOUT_SECONDS` | `rag.gateway.embedding.connect-timeout-seconds` | `5` |
| `RAG_GATEWAY_EMBEDDING_RESPONSE_TIMEOUT_SECONDS` or `RAG_GATEWAY_EMBEDDING_READ_TIMEOUT_SECONDS` | matching response/read property | `30` |

Keep naming consistent across chat and embedding. Do not introduce two naming schemes unless a legacy key requires it.

### API / command / payload fields

No HTTP API, request payload, response payload, DTO, VO, database, or frontend type fields are expected to change.

The only external contract change should be configuration:

- `application.yml` default values.
- optional `.env.example` variables.
- README deployment/config table.
- gateway resilience spec if default and fallback rules are made durable.

### Error semantics

| Scenario | HTTP/status | Public code | Required behavior |
|---|---:|---|---|
| Chat connect timeout before non-streaming response | 504 | `upstream_timeout` | OpenAI-compatible JSON; safe message; no provider body/key/raw URL query. |
| Chat read/response timeout before non-streaming response | 504 | `upstream_timeout` | Same as above. |
| Chat connect timeout before stream is committed | 504 | `upstream_timeout` | OpenAI-compatible JSON when possible; safe logs. |
| Chat timeout after stream starts | SSE failure path | `upstream_timeout` or existing compatible stream failure mapping | Safe SSE error or close behavior per existing streaming contract. Do not regress cancellation/release. |
| Embedding connect or read timeout for query embedding | 502 | `embedding_failed` | No upstream chat call after query embedding failure; safe message. |
| Embedding connect or read timeout during ingestion | document/task failure path | `embedding_failed` equivalent internally | Document/task must fail visibly; no READY state from failed embedding. |
| Model-config chat probe connect/read timeout | Admin check result `FAILED` | message `Upstream timeout` or equivalent safe summary | No provider body/key/raw URL. |
| Invalid timeout value | startup/config failure | n/a | Fail visibly; no silent clamp to success. |

## Validation / Error Matrix

| Case | Expected assertion point |
|---|---|
| Defaults are absent | chat connect timeout is short default; chat response/read timeout remains 30s; embedding connect short default; embedding response/read 30s. |
| Custom connect timeout set | chat and embedding RestClient factories use the custom connect timeout without changing read timeout. |
| Custom response/read timeout set | chat and embedding RestClient factories use the custom read timeout without changing connect timeout. |
| Legacy `timeout-seconds` only | read/response timeout remains compatible with existing 30s behavior; connect timeout still bounded by the new default unless the chosen compatibility strategy explicitly documents otherwise. |
| Invalid zero/negative timeout | configuration binding or construction fails visibly; no silent fallback to 30s success. |
| Simulated chat timeout | `GatewayException` code `upstream_timeout`, HTTP 504. |
| Simulated embedding timeout | `EmbeddingException` retryable/timeout flag remains true and service maps to `embedding_failed` where applicable. |
| Safe logging | logs include safe IDs/model/provider/latency/error_class only; no API keys, Authorization, provider raw body, prompt/messages, or URL query/userinfo. |

## Good / Base / Bad Cases

Good:

- Provider is reachable and responds within read timeout. Gateway returns normal OpenAI-compatible success, logs safe timing, and request-log success behavior remains unchanged.
- Provider host is unreachable or connection stalls. The connect boundary fails quickly using the new configured connect timeout and maps to safe timeout semantics.
- User overrides connect timeout in config/env and tests prove the client uses that value.

Base:

- Provider accepts connection but response takes too long. The read/response timeout remains the existing longer boundary and maps to `upstream_timeout` for chat or `embedding_failed` for embedding.
- Existing deployments that only set `rag.gateway.upstream.timeout-seconds` or `rag.gateway.embedding.timeout-seconds` keep equivalent read/response behavior.

Bad:

- Connect timeout remains tied to the 30-second response timeout.
- Implementation catches all upstream exceptions and returns fake success or pass-through.
- Provider raw response body, full URL query, API key, Authorization header, prompt/messages, or stack trace appears in public errors or logs.
- Timeout config is silently clamped or ignored.
- A retry/fallback/provider-routing feature is added without a separate PRD.

## Focused Code Research

### Current timeout sources

- `backend/src/main/resources/application.yml`
  - `rag.gateway.upstream.timeout-seconds: 30`
  - `rag.gateway.embedding.timeout-seconds: 30`
  - `rag.gateway.streaming.emitter-timeout-seconds: 300`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
  - Constructor reads `rag.gateway.upstream.timeout-seconds`.
  - `createRestClient(int timeoutSeconds)` sets both connect and read timeout to the same value.
  - Non-streaming and streaming timeout classification currently map timeout-ish `ResourceAccessException` to `GatewayException` code `upstream_timeout`.
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
  - Constructor reads `rag.gateway.embedding.timeout-seconds`.
  - `createRestClient(int timeoutSeconds)` sets both connect and read timeout to the same value.
  - `ResourceAccessException` timeout maps to `EmbeddingException(..., true)`.
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckService.java`
  - Constructor reads `rag.gateway.embedding.timeout-seconds`.
  - Uses that same value for chat probe connect and read timeouts.
  - Chat probe timeout reports safe failed check message.

### Code patterns to preserve

- `OpenAiCompatibleUpstreamClientTest` uses `MockRestServiceServer` and `OutputCaptureExtension` for safe logging assertions.
- `OpenAiCompatibleEmbeddingClientTest` uses `MockRestServiceServer` for URL construction, non-2xx, malformed body, response count, and dimension validation.
- `OpenAiChatCompletionsControllerTest` already asserts `upstream_timeout` returns HTTP 504 OpenAI-compatible shape.
- `ChatCompletionGatewayServiceTest` already asserts upstream timeout passes through as `GatewayException` code `upstream_timeout`.
- Safe URL logging uses `ChatCompletionLogHelper.sanitizeUpstreamUrl(...)`; preserve it.

## Files Likely To Modify

Expected implementation files:

- `backend/src/main/resources/application.yml`
  - Add distinct connect/response timeout defaults and env placeholders.
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
  - Split connect timeout from read/response timeout.
  - Keep safe error mapping and logging unchanged.
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
  - Split connect timeout from read/response timeout.
  - Preserve `EmbeddingException` timeout semantics.
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckService.java`
  - Align probe timeout configuration with the new boundary.
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
  - Add tests for configured connect/read timeout values and existing timeout error mapping if currently weak.
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`
  - Add tests for configured connect/read timeout values and timeout classification.
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigCheckServiceTest.java`
  - Add or update timeout-probe coverage if constructor/config behavior changes.
- `README.md`
  - Document new env vars/config defaults if introduced.
- `.env.example`
  - Add safe local placeholder/default env keys if README documents them.
- `.trellis/spec/gateway/resilience.md`
  - Update timeout contract/defaults if new durable behavior is introduced.
- `.trellis/spec/sangui-rag-gateway.md`
  - Update environment variable list only if project-level config contract requires it.

Expected files not to modify:

- No migration files under `backend/src/main/resources/db/migration`.
- No frontend files.
- No Dockerfile/nginx changes.
- No retrieval/prompt/no-hit policy files except specs if needed.

## Required Tests

Run from `backend/`, with each targeted unit-test command capped at 60 seconds when feasible:

```bash
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=ModelConfigCheckServiceTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn -q -DskipTests compile
```

If README or `.env.example` changes:

```bash
git diff --check
```

Optional if streaming code is touched beyond constructor/config wiring:

```bash
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
```

## Acceptance Criteria

- [ ] Connect timeout for upstream chat is independently configurable and defaults to a bounded short value.
- [ ] Connect timeout for embedding calls/probes is independently configurable and defaults to a bounded short value.
- [ ] Read/response timeout remains independently configurable and does not silently shrink streaming/long response behavior unless explicitly documented.
- [ ] Existing legacy `timeout-seconds` behavior is either preserved as a fallback or removed only with README/spec migration notes; no hidden behavior drift.
- [ ] Chat timeout still maps to OpenAI-compatible `504 upstream_timeout`.
- [ ] Embedding timeout still maps to `embedding_failed` and does not call upstream chat after query embedding failure.
- [ ] Logs and errors remain secret-safe and provider-body-safe.
- [ ] Targeted tests cover config binding/defaults plus error semantics.
- [ ] README/spec/env examples are synchronized if new config keys are added.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: no API/DB/frontend/retry/fallback/no-hit/health/nginx/Docker image security changes.
- Expected modify files: listed above.
- Required tests: listed above.
- Concrete guidelines read: project spec, backend index, gateway index, security index, guides index, gateway resilience, backend error handling, logging, quality, directory structure, RAG security, RAG document ingestion, cross-layer guide.
- Open requirement questions: none blocking. The only implementation choice is naming `response-timeout-seconds` vs `read-timeout-seconds`; choose one consistent naming scheme and document it.
- API/DB/frontend DTO alignment: no changes expected.

