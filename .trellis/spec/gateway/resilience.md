# Gateway Resilience

> All upstream model calls must be bounded, observable, and normalized into safe OpenAI-compatible gateway behavior.

## 1. Scope / Trigger

Use this spec before changing:

- upstream chat client behavior
- embedding client behavior
- timeout configuration
- gateway error mapping
- streaming setup or post-start SSE errors
- request-log failure persistence
- retry, fallback, circuit breaker, rate limit, or provider routing behavior

This task only records the spec. It does not implement retry, fallback, circuit breaker, or provider routing.

## 2. Current Hard Specification

- Sangui-RAG-Gateway depends on external chat and embedding providers. Providers can return rate limits, timeouts, auth failures, malformed responses, service errors, or network failures.
- Every upstream call must have an explicit timeout.
- Upstream errors must not become uncontrolled generic `500` responses.
- Public gateway errors must use the OpenAI-compatible error shape.
- Request logs must record upstream error or timeout status when the request reaches the logging boundary.
- API-key rate limiting is a pre-upstream guardrail for `POST /v1/chat/completions`; rejected requests must not call retrieval, embedding, or upstream chat.
- Redis rate-limit outages must fail visibly with an OpenAI-compatible gateway error. They must not silently bypass enforcement.
- Logs must never print complete upstream API keys, app API keys, authorization headers, full prompts, full documents, vectors, provider raw bodies, or stack traces in client responses.
- Embedding failure during ingestion must put the document into `FAILED` or an explicit retryable state, not `READY`.
- Chat upstream failure must persist a safe failure summary in request logs where feasible.
- Current V0.2 beta does not require intelligent provider fallback or routing. Timeout, error handling, and safe logging are hard requirements.

## 3. Signatures

OpenAI-compatible gateway error shape:

```json
{
  "error": {
    "message": "Specific safe message",
    "type": "invalid_request_error",
    "code": "upstream_error"
  }
}
```

Common gateway error codes:

```text
upstream_error
upstream_timeout
embedding_failed
knowledge_base_not_ready
model_config_not_ready
rate_limit_exceeded
internal_error
```

Request-log failure/cancellation fields:

```text
request_id
user_id
app_id
api_key_id
model
provider_name
status = failure or cancelled
error_code
latency_ms
upstream_latency_ms
messages_count
question_summary
hit_chunk_ids
```

## 4. Contracts

| Contract | Required behavior |
|----------|-------------------|
| Timeout | Chat and embedding calls have configured timeout values. |
| Error normalization | Provider/network/malformed response errors map to safe gateway exceptions. |
| Public shape | `/v1/*` errors use OpenAI-compatible response shape, not admin `ApiResponse`. |
| Streaming pre-commit | Validation and upstream setup failures detected before SSE commit return JSON error. |
| Streaming post-commit | Failures after SSE starts emit safe SSE error event when possible and then close. |
| Request log | Persist safe failure status and error code when the controller/logging boundary is reached. |
| Secret safety | Logs and responses omit keys, raw provider bodies, full prompts, documents, vectors, and stack traces. |
| Ingestion status | Embedding failures cannot be silently swallowed or marked ready. |
| Rate-limit preflight | Valid chat payloads are checked against API-key limits before embedding/retrieval/upstream calls. |
| Rate-limit reservation | Request counters count accepted attempts; token counters reserve estimated tokens before upstream work and reconcile or release explicitly. |
| Embedding batch size | Document ingestion uses `rag.gateway.embedding.batch-size` / `RAG_GATEWAY_EMBEDDING_BATCH_SIZE` with default `64`, minimum `1`, maximum `2048`; invalid values fail configuration binding at startup. |

## 5. Validation & Error Matrix

| Scenario | HTTP / status | Error code | Required behavior |
|----------|---------------|------------|-------------------|
| Upstream chat timeout | 504 | `upstream_timeout` | OpenAI-compatible JSON if pre-stream; safe SSE error if post-start |
| Upstream chat non-2xx | 502 | `upstream_error` | Do not expose provider body |
| Upstream network failure | 502 | `upstream_error` | Safe message and log exception class only |
| Upstream malformed success body | 502 | `upstream_error` | No provider body in response/log |
| Embedding timeout or provider failure | 502 or document `FAILED` | `embedding_failed` | No upstream chat call for query embedding failure |
| Invalid embedding batch size | startup failure | n/a | `EmbeddingProperties` validation rejects values `< 1` or `> 2048`; no silent clamping |
| API key request/token limit exceeded | 429 | `rate_limit_exceeded` | OpenAI-compatible JSON, safe request log, no upstream/retrieval call |
| Redis limiter unavailable | 500 | `internal_error` | OpenAI-compatible JSON, safe request log where possible, no silent pass-through |
| Invalid chat payload | 400 | `invalid_request` | Validate before limiter; no quota consumed |
| Request log insert failure | Gateway response unchanged | n/a | Log safe request ID and exception class only |
| Client disconnect during stream | stream closes | `client_cancelled` | Cancel upstream, persist `status=cancelled`, release token reservation, and log as cancellation, not internal error |

## 6. Good/Base/Bad Cases

| Case | Expected result |
|------|-----------------|
| Good | Provider succeeds within timeout; gateway logs safe timing and returns OpenAI-compatible success. |
| Base | Provider times out or returns error; gateway returns normalized compatible error and records safe failure metadata. |
| Bad | Provider raw body is passed to users, timeout is missing, keys appear in logs, or broad catch-all logic hides failures while returning success. |

## 7. Wrong vs Correct

### Wrong

```text
Catch any upstream exception, log the full exception message and provider body, then return a generic 500 or fake success.
```

This hides the actual boundary and risks leaking secrets or private prompt data.

### Correct

```text
Classify timeout, upstream error, malformed response, and network failure; return a safe OpenAI-compatible error; persist safe request-log metadata; let failures remain visible.
```

## 8. Runtime Streaming Smoke Test

### 8.1 Command

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
```

Test class: `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`

### 8.2 Contract

The runtime smoke uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a dedicated embedded servlet container configuration that imports `OpenAiChatCompletionsController`, `GatewayAuthConfig`, `GlobalExceptionHandler`, and mock/stub collaborators. No PostgreSQL, Redis, Flyway, MyBatis, Docker, provider keys, or external upstream providers are required. The test uses Java 21 `java.net.http.HttpClient` as a real HTTP client against `localhost:{port}`.

### 8.3 Covered Scenarios

| Scenario | Stream behavior | Request-log assertion | Reservation assertion |
|---|---|---|---|
| Normal `[DONE]` | HTTP 200, `text/event-stream`, `data:` lines, final `[DONE]` | `status=success`, `error_code=null`, `output_capture_status=STREAMING_UNSUPPORTED` | No `releaseReservation`, no `reconcileTokens` |
| Client disconnect | Client reads first line, closes stream; server finishes without error | `status=cancelled`, `error_code=client_cancelled` | `releaseReservation` exactly once |
| Emitter timeout | Short `emitter-timeout-seconds=3`; upstream hangs after `onStreamReady` | `status=cancelled`, `error_code=stream_timeout` | `releaseReservation` exactly once |
| Post-start upstream failure | `onStreamReady` called, then upstream throws `GatewayException` | `status=failure`, `error_code=upstream_error` | `releaseReservation` exactly once |

### 8.4 Good / Base / Bad

| Case | Expected result |
|---|---|
| Good | All four streaming terminal cases pass; request-log commands captured exactly once per request; `releaseReservation` called exactly once on cancel/timeout/failure; normal success does not release; all rows use `STREAMING_UNSUPPORTED`; no forbidden fields asserted or logged. |
| Base | Environment cannot run Docker but the `RANDOM_PORT` smoke still passes without PostgreSQL, Redis, or external providers. Local smoke evidence is recorded separately from external/manual smoke. |
| Bad | Test uses MockMvc only; disconnect is faked by throwing `IOException` without HTTP client; duplicate request-log writes; reservation release called more than once; raw SSE, prompt, key, or provider body content persisted or asserted. |

### 8.5 Failure Boundaries

- Client-disconnect smoke must close the `InputStream` from `HttpResponse.BodyHandlers.ofInputStream()`, not just throw `IOException` inside the mock.
- After an SSE send `IOException` returns `CANCELLED`, the controller must record `cancelled/client_cancelled` and release the reservation without calling `SseEmitter.complete()` on the already-unusable response.
- A response-write `IOException` for `/v1/chat/completions` with `Accept: text/event-stream` is a client disconnect signal; `GlobalExceptionHandler` must log it at INFO with safe metadata and must not convert it into a generic unexpected-error stack trace.
- Emitter-timeout smoke must let the real `SseEmitter` timeout fire; do not invoke the timeout callback directly.
- The timeout callback must explicitly complete the `SseEmitter` after recording `cancelled/stream_timeout`, otherwise the servlet async timeout can be dispatched as a generic 500 even though the request-log row is correct.
- Post-start upstream failure must throw after `onStreamReady`; pre-start failure before `onStreamReady` is covered by existing `OpenAiChatCompletionsControllerTest`.

## 9. Future Enhancement Roadmap

The following are valid later enhancements, not V0.2 beta requirements:

- provider fallback
- model fallback
- retry with backoff
- circuit breaker
- per-provider rate limit
- provider health checks
- failover routing

Future fallback or retry must be explicit, configurable, observable, and must not silently convert a strict RAG failure into a normal pass-through answer.

## 10. Embedding Ingestion Batch Configuration

Document ingestion batches provider embedding calls before vector persistence:

```text
Spring property: rag.gateway.embedding.batch-size
Environment variable: RAG_GATEWAY_EMBEDDING_BATCH_SIZE
Default: 64
Minimum: 1
Maximum: 2048
Binding class: backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingProperties.java
Consumer: backend/src/main/java/com/sangui/raggateway/document/DocumentService.java
```

Validation cases:

| Case | Expected result | Required assertion |
|---|---|---|
| Missing property | `EmbeddingProperties.batchSize == 64` | `EmbeddingPropertiesTest` |
| Valid custom value | Bound value is used by `DocumentService.embedAndFinalize(...)` | `EmbeddingPropertiesTest`, `DocumentServiceTest` |
| `0` or negative value | Spring context fails during configuration binding | `EmbeddingPropertiesTest` |
| Value greater than `2048` | Spring context fails during configuration binding | `EmbeddingPropertiesTest` |

Run after changing this contract:

```bash
cd backend
mvn -q "-Dtest=EmbeddingPropertiesTest,DocumentServiceTest,OpenAiCompatibleEmbeddingClientTest" test
mvn -q -DskipTests compile
```
