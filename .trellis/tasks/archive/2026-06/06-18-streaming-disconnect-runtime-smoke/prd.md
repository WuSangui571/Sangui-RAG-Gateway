# Streaming Disconnect Runtime Smoke

## Classification

Complex Task.

This is a backend/gateway runtime verification task, not a small hotfix. It touches the public `/v1/chat/completions` streaming boundary, servlet container SSE behavior, request-log persistence, API-key rate-limit reservation lifecycle, upstream post-start failures, timeout handling, and executable spec documentation.

## Goal

Add a real servlet-container streaming smoke test path for Sangui-RAG-Gateway so the project no longer relies only on MockMvc and upstream-client unit tests for critical SSE lifecycle behavior.

The smoke must prove the implemented streaming terminal states are observable and stable under a real HTTP client:

- Normal streaming completion with `[DONE]`.
- Client-side disconnect after stream start.
- Gateway-owned emitter timeout.
- Upstream failure after the stream has already started.

## Non-Goals

- Do not redesign streaming behavior unless the smoke exposes a real bug.
- Do not add a metrics dashboard, tracing backend, setup wizard, frontend workflow, provider fallback, retry, circuit breaker, or routing feature.
- Do not add DB migrations, public API payload fields, frontend DTO/type changes, or admin UI changes unless a bug makes them strictly necessary.
- Do not persist raw SSE payloads, prompts, messages, provider bodies, answer text, chunk content, keys, hashes, stack traces, storage paths, or environment values.
- Do not fake a passing smoke with silent fallbacks or mocked success paths at the HTTP boundary. Failures must surface visibly.

## Current Context

Recent completed work already added MockMvc/unit coverage and implementation support for:

- `StreamCompletionOutcome.CANCELLED`.
- `status=cancelled` with `error_code=client_cancelled`.
- `status=cancelled` with `error_code=stream_timeout`.
- Streaming `status=success` on `[DONE]`.
- Streaming `status=failure` on post-start upstream failure or missing `[DONE]`.
- `output_capture_status=STREAMING_UNSUPPORTED` for all streaming request-log rows.
- One-shot terminal handling guarded by `AtomicBoolean`.
- Reservation release on streaming cancellation, timeout, post-start upstream failure, and pre-start stream preparation failure.

The remaining risk is that MockMvc does not fully reproduce embedded servlet container behavior for `SseEmitter` writes, client disconnect timing, async callback ordering, or real HTTP response commit.

## API / Command / Payload Contract

### Public Gateway Endpoint Under Test

```http
POST /v1/chat/completions
Authorization: Bearer <app-api-key>
Content-Type: application/json
Accept: text/event-stream

{
  "model": "gpt-4o-mini",
  "messages": [
    { "role": "user", "content": "runtime streaming smoke" }
  ],
  "stream": true,
  "max_tokens": 16
}
```

### Runtime Smoke Test Command

Required targeted backend command:

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
```

If DeepSeek chooses a different class name, update this PRD and all task context/spec references before handoff completion.

### Regression Test Commands

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=ApiKeyRateLimitServiceTest,ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```

Backend unit tests must be run with a hard timeout of 60 seconds per command when feasible.

### Minimal Runtime Smoke Shape

Use a real embedded servlet container and a real HTTP client. Preferred shape:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- Do not activate the `test` profile because gateway controller/filter beans are `@Profile("!test")`.
- Use a dedicated smoke/test application configuration that imports only the gateway controller, auth filter registration, exception handler, JSON support, and mock/stub collaborators needed by this endpoint.
- Use Java 21 `java.net.http.HttpClient` or another existing test dependency to open streaming responses and explicitly close/cancel the client side.
- Use Mockito mocks or deterministic in-memory stubs for `ApiKeyService`, `AppService`, `ChatCompletionGatewayService`, `OpenAiCompatibleUpstreamClient`, `ApiRequestLogService`, `ApiKeyRateLimitService`, and `OutputCapturePolicy`.
- Avoid real PostgreSQL, Redis, Flyway, MyBatis, Docker, provider keys, and external upstream providers.

## Validation / Error Matrix

| Scenario | HTTP / stream behavior | Request-log assertion | Reservation assertion | Output-capture assertion |
|---|---|---|---|---|
| Normal `[DONE]` completion | HTTP 200 `text/event-stream`; client observes at least one `data:` line and final `[DONE]` | one row only: `status=success`, `error_code=null`, `model`, `provider_name`, `messages_count`, `question_summary`, optional `hit_chunk_ids`/`retrieval_evidence` | no `releaseReservation`; no `reconcileTokens` for streaming unless the implementation explicitly adds safe streaming usage reconciliation | `STREAMING_UNSUPPORTED` |
| Client disconnect after stream start | Client closes/cancels response after first data line; server finishes without unhandled error | one row only: `status=cancelled`, `error_code=client_cancelled` | `releaseReservation` exactly once; `reconcileTokens` never | `STREAMING_UNSUPPORTED` |
| Gateway-owned emitter timeout | Server-side `SseEmitter` times out under a short test timeout; client does not receive `[DONE]` | one row only: `status=cancelled`, `error_code=stream_timeout` | `releaseReservation` exactly once; `reconcileTokens` never | `STREAMING_UNSUPPORTED` |
| Upstream ready then fails | Stream starts/response commits, then upstream throws `GatewayException` or runtime error | one row only: `status=failure`, `error_code=upstream_error` or `upstream_timeout` as appropriate; safe SSE error event attempted when possible | `releaseReservation` exactly once; `reconcileTokens` never | `STREAMING_UNSUPPORTED` |
| Pre-start upstream/setup failure | Upstream setup fails before response commit | OpenAI-compatible JSON error, not SSE | one row only: `status=failure`, stable gateway `error_code` | `releaseReservation` exactly once if reservation was acquired | disabled or `STREAMING_UNSUPPORTED` according to current controller boundary |
| Rate limit rejection | HTTP 429 OpenAI-compatible JSON | one row only: `status=failure`, `error_code=rate_limit_exceeded` | no release/reconcile because reservation was not acquired | disabled status from `OutputCapturePolicy` |
| Invalid payload | HTTP 400 OpenAI-compatible JSON | one row only if controller logging boundary is reached; no limiter call | no check/reserve/release/reconcile | disabled status |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | The runtime smoke uses embedded servlet container + real HTTP client; all four target streaming terminal cases pass; request-log commands are captured exactly once per request; rate-limit reservation release happens exactly once on cancellation/timeout/post-start failure; normal `[DONE]` succeeds; all streaming rows use `output_capture_status=STREAMING_UNSUPPORTED`; no sensitive fields are asserted, printed, or persisted. |
| Base | Environment cannot run a full Docker/provider smoke, but the local `RANDOM_PORT` backend smoke still runs without PostgreSQL, Redis, external provider keys, frontend, or Docker. The task records these preconditions and separates local runtime-smoke evidence from external/manual smoke evidence. |
| Bad | The test only uses MockMvc, only tests `OpenAiCompatibleUpstreamClient` with `MockRestServiceServer`, fakes disconnect by directly throwing `IOException` without an HTTP client, accepts duplicate request-log writes, releases reservations more than once, records raw SSE/provider/prompt content, or hides failures behind fallback success behavior. |

## Required Code Research Findings

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary, OpenAI-compatible streaming requirement, request-log and secret safety contract.
- `.trellis/spec/backend/error-handling.md`: OpenAI-compatible gateway error shape and streaming terminal status matrix.
- `.trellis/spec/backend/logging-guidelines.md`: safe structured gateway log/request-log fields and streaming `STREAMING_UNSUPPORTED` output capture rule.
- `.trellis/spec/backend/database-guidelines.md`: `rag_request_log` fields, status values, output observability schema, and rate-limit reservation contract.
- `.trellis/spec/backend/quality-guidelines.md`: backend test requirements, streaming cancellation requirement, and rate-limit reconciliation/release checks.
- `.trellis/spec/gateway/resilience.md`: upstream timeout/error normalization, streaming pre-commit/post-commit behavior, request-log failure persistence, reservation contract.
- `.trellis/spec/security/rag-security.md`: safe observability, forbidden fields, API-key cost boundary.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: streaming and request-log cross-layer checklist.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search first and avoid duplicating existing lifecycle logic.

### Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`: central streaming lifecycle, `SseEmitter`, `streamReady`, `terminalHandled`, request-log write points, reservation release points.
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`: SSE upstream read loop, `[DONE]` detection, `StreamCompletionOutcome.CANCELLED`, upstream timeout/error normalization.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitService.java`: `checkAndReserve`, `releaseReservation`, `reconcileTokens`; Redis keys are API-key-ID scoped only.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`: request-log command-to-entity mapping and safe persistence behavior.
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`: current MockMvc controller assertions for streaming success/cancel/failure/rate-limit reservation behavior.
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`: current upstream-client unit tests for SSE `[DONE]`, missing `[DONE]`, send `IOException`, and safe logging.
- `scripts/demo-smoke.ps1` and `docs/runtime-evidence-checklist.md`: existing safe runtime-evidence style; do not print raw answer/SSE/key/prompt/chunk content.

## Files Likely To Modify

Expected:

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java` - new real servlet-container streaming smoke test.
- `.trellis/spec/gateway/resilience.md` - add executable runtime-smoke contract, command, Good/Base/Bad, and failure boundaries.
- `.trellis/spec/backend/quality-guidelines.md` - add the runtime smoke to backend streaming/regression validation.

Optional if implementation needs them:

- `backend/src/test/resources/application-streaming-smoke.yml` or equivalent test property source - only if a dedicated runtime smoke profile/properties file is cleaner than inline test properties.
- `backend/pom.xml` - only if there is a strong reason to add a test dependency; prefer Java 21 `HttpClient` and existing Spring Boot test dependencies first.
- `README.md` or `docs/runtime-evidence-checklist.md` - only if the task expands external/manual smoke instructions. Keep evidence metadata-only.

Forbidden unless a failing smoke proves a real implementation bug:

- `backend/src/main/java/**` business implementation files.
- `frontend/**`.
- DB migration files.
- Public DTO/payload fields.
- Deployment/runtime env contracts.

## Implementation Approach

1. Create a focused runtime smoke test class under the gateway/openai test package.
2. Build a minimal `RANDOM_PORT` Spring test context that includes:
   - `OpenAiChatCompletionsController`.
   - `GatewayAuthConfig` / `GatewayAuthFilter` so auth and `GatewayRequestContext` are real.
   - `GlobalExceptionHandler` for pre-start failures.
   - `ObjectMapper`.
   - mock/stub collaborators for app/API-key lookup, stream preparation, upstream streaming, request-log recording, rate-limit service, output capture policy.
3. Use a generated/known `sk-sangui-*` key, hash it with the real `ApiKeyHasher`, and configure `ApiKeyService.findByHash(...)` plus `ApiKeyService.isValid(...)` and `AppService.findById(...)` so the real filter authenticates.
4. For each scenario, drive the endpoint with a real HTTP client against `localhost:{port}`:
   - normal: consume full SSE and assert `[DONE]`.
   - disconnect: read first data line, then close/cancel the response body/client side and wait for the server-side terminal log command.
   - timeout: set `rag.gateway.streaming.emitter-timeout-seconds` very low and have fake upstream stay open after `onStreamReady`.
   - post-start failure: fake upstream calls `onStreamReady`, optionally sends one event, then throws a `GatewayException`.
5. Capture `CreateRequestLogCommand` arguments and verify exactly one terminal command per request.
6. Verify `ApiKeyRateLimitService.releaseReservation(...)` call count exactly once for cancellation/timeout/post-start failure and zero for normal success.
7. Verify `reconcileTokens(...)` is not called for streaming unless implementation is intentionally changed and documented.
8. Add/adjust specs with executable command, preconditions, Good/Base/Bad, and boundary assertions.

## Acceptance Criteria

- [ ] Task stays scoped to tests/specs unless the runtime smoke exposes a real implementation bug.
- [ ] Runtime smoke uses a real embedded servlet container and a real HTTP client; it is not a MockMvc-only test.
- [ ] Normal streaming completion asserts HTTP 200, SSE content type, `data:` lines, and final `[DONE]`.
- [ ] Client disconnect smoke closes/cancels the client side after stream start and asserts one `cancelled/client_cancelled` request-log command.
- [ ] Gateway timeout smoke asserts one `cancelled/stream_timeout` request-log command.
- [ ] Post-start upstream failure smoke asserts one `failure/upstream_error` or `failure/upstream_timeout` request-log command after response commit.
- [ ] Every streaming request-log command asserts `output_capture_status=STREAMING_UNSUPPORTED`.
- [ ] Reservation release is asserted exactly once for disconnect, timeout, and post-start failure.
- [ ] Normal streaming success does not release reservation and does not reconcile tokens unless a new explicit streaming usage contract is introduced.
- [ ] Test output and committed docs contain only safe metadata, never raw SSE payloads, prompts, messages, answers, keys, hashes, provider bodies, chunk content, stack traces, storage paths, or environment values.
- [ ] Relevant gateway/backend spec is updated with command, Good/Base/Bad, and failure boundary matrix.
- [ ] Required targeted Maven commands and `git diff --check` pass or failures are recorded with exact environment cause.

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: business implementation, frontend, DB migrations, public DTO/payload, metrics/dashboard/setup wizard unless a real bug requires a narrow fix.
- Expected modification files: listed above.
- Required tests: listed above.
- Specific guidelines read: project spec, backend directory/database/error/logging/quality, gateway resilience, security RAG security, RAG retrieval/prompt/document specs, cross-layer and code-reuse guides.
- Open questions: none currently requiring user confirmation.
- API/DB/frontend DTO alignment: no API/DB/frontend DTO changes expected.

