# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: Defines `/v1/chat/completions`, OpenAI-compatible subset, streaming requirements, request-log model, and V0.2 roadmap priority.
- `.trellis/spec/backend/directory-structure.md`: Places OpenAI-compatible HTTP behavior in `gateway`, upstream calls in `gateway/upstream`, orchestration in `gateway/completion`, and persistence in `log`.
- `.trellis/spec/backend/database-guidelines.md`: Documents `rag_request_log` schema and safe nullable token fields. Current schema appears sufficient for baseline streaming; no migration is expected.
- `.trellis/spec/backend/error-handling.md`: Defines gateway JSON error shape, `upstream_error`/`upstream_timeout`, streaming pre-start vs post-start error boundary, and client disconnect expectations.
- `.trellis/spec/backend/logging-guidelines.md`: Defines existing safe structured log events and forbidden sensitive data.
- `.trellis/spec/backend/quality-guidelines.md`: Requires streaming forwarding tests, client disconnect cancellation, OpenAI-compatible public responses, and secret-safe behavior.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Required because the task touches public API, upstream integration, streaming, observability, and request logs.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: Relevant because URL construction, validation, logging, and request-log finalization should reuse existing patterns.

## Current Project State From Journal

- Non-streaming `POST /v1/chat/completions` is implemented and committed.
- Existing path includes gateway auth, model config resolution, upstream forwarding, normalized errors, safe structured logs, and request-log persistence.
- `stream=true` currently returns `400 invalid_request`.
- Request-log persistence was added in the last completed task. It records safe operational metadata and intentionally excludes malformed JSON/auth-filter failures.
- Working tree was clean before this planning task; current untracked files are only the new Trellis task directory.

## Code Patterns Found

- HTTP boundary and request-log finalization:
  - `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java:37`
  - Generates `request_id`, stores it in `GatewayRequestContext`, computes `messages_count`, records success/failure rows, and rethrows `GatewayException`.
- Service orchestration:
  - `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java:68`
  - Validates request, resolves app/model config, decrypts upstream key, logs `config_resolved`, builds upstream request, calls upstream client, parses response.
- Current streaming rejection:
  - `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java:144`
  - `validateRequest` rejects `stream=true` with `invalid_request`. This must change without relaxing message validation.
- Upstream URL/error/logging pattern:
  - `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java:53`
  - Uses `normalizeBaseUrl`, appends `/v1/chat/completions`, logs sanitized URL, maps non-2xx/network/timeout to `GatewayException`, and avoids provider body leakage.
- Upstream body buffering:
  - `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java:72`
  - Current non-streaming path reads the whole body with `readAllBytes()`. Streaming needs a separate path that reads/writes incrementally.
- Request-log service failure isolation:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java:20`
  - `record()` catches insert failures and logs request ID plus exception class only.
- Existing tests:
  - Controller success/request-log assertions at `OpenAiChatCompletionsControllerTest.java:90`.
  - Current `stream=true` rejection tests at `OpenAiChatCompletionsControllerTest.java:137` and `ChatCompletionGatewayServiceTest.java:282`; these must be replaced/updated.
  - Non-streaming `stream=false` regression at `ChatCompletionGatewayServiceTest.java:207`.
  - Safe upstream log assertions at `OpenAiCompatibleUpstreamClientTest.java:146`.
  - Upstream timeout service behavior at `ChatCompletionGatewayServiceTest.java:469`.

## Technical Constraints Observed

- `backend/pom.xml` uses `spring-boot-starter-web`; no WebFlux/WebClient dependency is present.
- No existing `SseEmitter`, `StreamingResponseBody`, `ResponseBodyEmitter`, WebClient, or MockWebServer usage was found.
- `RestClient` is the existing upstream HTTP abstraction. Implementer can either:
  - use `RestClient.exchange(...)` with response `InputStream` streaming, or
  - introduce a focused alternative based on JDK HTTP client or Spring MVC primitives, if tests remain straightforward and no broad dependency is added.
- If adding `spring-boot-starter-webflux` just for `WebClient`, document why. The conservative baseline is to stay within MVC/blocking IO unless cancellation semantics require otherwise.

## Files Likely To Modify

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
  - Branch on `stream=true`; return an SSE-capable response type; preserve current JSON path for non-streaming.
  - Ensure one request-log row per authenticated streaming lifecycle.
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
  - Stop rejecting `stream=true`.
  - Reuse validation and config resolution for both paths.
  - Add streaming orchestration or extract shared config resolution to avoid duplication.
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
  - Add streaming upstream call method.
  - Reuse URL construction, sanitized logging, timeout/error mapping.
  - Provide cancellation/close hook when client disconnects.
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamChatCompletionRequest.java`
  - Likely unchanged because it already has `stream`.
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java`
  - Possibly leave unchanged for non-streaming and add a separate streaming result/context.
- `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java`
  - Likely unchanged; nullable fields already support streaming usage gaps.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - Likely unchanged unless helper methods are added.
- Tests:
  - `OpenAiChatCompletionsControllerTest.java`
  - `ChatCompletionGatewayServiceTest.java`
  - `OpenAiCompatibleUpstreamClientTest.java`
  - `ApiRequestLogServiceTest.java`
  - `GatewayAuthFilterTest.java`, `GlobalExceptionHandlerTest`, `GlobalExceptionHandlerIntegrationTest` as regression if touched.

## Risk / Boundary Notes

- Pre-stream errors can still use `GatewayException` and `GlobalExceptionHandler`; post-start errors cannot change HTTP status once bytes are committed.
- A streaming implementation must avoid duplicate request-log rows. Centralize finalization around stream close/failure/cancel.
- Thread-local `GatewayRequestContextHolder` may not be available inside async streaming callbacks if the implementation uses another thread. Capture safe context values and request ID before leaving the controller thread.
- `GatewayAuthFilter` clears `GatewayRequestContextHolder` in `finally` after `filterChain.doFilter`. Async streaming may outlive that scope. Do not depend on ThreadLocal during async chunk writes unless explicitly propagated.
- `RestClient` streaming with `exchange` must not close the response before the downstream writer finishes. Keep streaming read/write inside the exchange callback or another controlled lifecycle.
- Client disconnect often appears as `IOException` while writing. Treat that as cancellation, close upstream, and avoid `ERROR`/internal failure logs.
- Do not log SSE payloads. Chunk JSON may include user/output content.
- Usage fields should remain null for baseline streaming unless final usage is safely available.
- No DB migration is expected because request-log token and upstream latency fields are nullable.
- Existing non-streaming behavior and tests are high-value regression coverage. Preserve them.

## Required Tests

Targeted commands:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

Required assertion points:

- `stream=true` accepts valid request and returns `text/event-stream`.
- Upstream request body contains `stream=true` and configured chat model, not caller model.
- SSE chunks and `data: [DONE]` are forwarded in order.
- Existing non-streaming response shape and request-log assertions still pass.
- Pre-stream validation/model config/upstream errors return JSON OpenAI errors.
- Post-start upstream errors produce safe SSE error/close behavior and persist a failure row.
- Timeout maps to `upstream_timeout`; distinguish pre-start JSON vs post-start SSE close behavior where practical.
- Client disconnect/write failure closes upstream and does not log internal error.
- Auth failure remains filter-owned 401 with no request-log row.
- Logs/request logs/responses do not contain app key, app key hash, upstream key plaintext/encrypted, Authorization header, message content, raw provider body, or stack traces.
