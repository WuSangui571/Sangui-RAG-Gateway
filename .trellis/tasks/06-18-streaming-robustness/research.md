# Streaming Robustness Research

## Relevant Specs

Read during planning:

```text
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/backend/index.md
.trellis/spec/backend/directory-structure.md
.trellis/spec/backend/database-guidelines.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
.trellis/spec/gateway/index.md
.trellis/spec/gateway/resilience.md
.trellis/spec/rag/index.md
.trellis/spec/rag/retrieval-quality.md
.trellis/spec/rag/prompt-context-policy.md
.trellis/spec/security/index.md
.trellis/spec/security/rag-security.md
.trellis/spec/frontend/index.md
.trellis/spec/frontend/type-safety.md
.trellis/spec/frontend/quality-guidelines.md
.trellis/spec/guides/index.md
.trellis/spec/guides/cross-layer-thinking-guide.md
.trellis/spec/guides/code-reuse-thinking-guide.md
```

Key spec contracts:

- `stream=true` must use SSE, forward upstream tokens as they arrive, cancel upstream when clients disconnect, emit the most compatible safe SSE error event for post-start errors, and document usage limitations.
- Pre-stream errors must stay OpenAI-compatible JSON; post-commit failures cannot rely on `GlobalExceptionHandler`.
- Request logs must persist safe metadata only: IDs, status, error_code, latencies, model/provider, messages_count, question_summary, hit_chunk_ids, retrieval_evidence.
- `retrieval_evidence` is metadata-only and must never contain prompt, messages, chunk content, raw SSE, provider bodies, keys, stack traces, storage paths, vectors, or environment values.
- API-key rate-limit reservation must happen after payload validation and before retrieval/upstream work. Reservations must be reconciled or released on the correct terminal paths.
- Frontend request-log type safety must stay aligned if backend status values change.

## Code Patterns Found

### Controller orchestration

File:

```text
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
```

Observed flow:

- `chatCompletions(...)` generates `requestId`, stores it in `GatewayRequestContext`, validates the request, branches to `handleStreamCompletion(...)` when `request.stream == true`.
- Non-streaming path handles limiter reservation, upstream service call, token reconcile on success, token release on `GatewayException`, safe request-log persistence, optional non-streaming `sangui_citations`.
- Streaming path also checks limiter and prepares RAG/upstream request before starting a virtual thread.
- `streamReady` and `responseCommitted` distinguish pre-SSE failures from post-commit failures.
- Streaming success currently records `status=success`, `outputCaptureStatus=STREAMING_UNSUPPORTED`, `questionSummary`, `hitChunkIds`, and `retrievalEvidence`.
- Post-start `GatewayException` currently records `status=failure`, emits safe SSE error, and completes the emitter.
- `waitForStreamReady(...)` releases reservation for pre-ready failures and records failure.

Planning concern:

- Current streaming terminal paths need an explicit single terminal-state owner. Success, post-start failure, client cancellation, emitter timeout, and unexpected exception must not double-record request logs or leak token reservations.
- `OpenAiCompatibleUpstreamClient` catches `IOException` from `emitter.send()` and returns normally; without a cancellation signal back to the controller, a client disconnect can be misclassified as success.
- Streaming post-start success/failure/cancel do not currently have obvious reservation release/reconcile coverage equivalent to non-streaming.

### Upstream streaming client

File:

```text
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
```

Observed flow:

- `streamChatCompletion(...)` posts to normalized `/v1/chat/completions`.
- Non-2xx response before reading body maps to `GatewayException(upstream_error, 502)`.
- `onStreamReady.run()` fires only after 2xx status is confirmed.
- Reads upstream body line-by-line and forwards lines starting `data: ` to `SseEmitter`.
- Detects `data: [DONE]` and treats it as success.
- If EOF occurs without `[DONE]`, throws `GatewayException(upstream_error, 502)`.
- `ResourceAccessException` maps timeout-ish causes to `upstream_timeout`, others to `upstream_error`.
- Safe logs avoid API key, message content, and provider body.

Planning concern:

- Cancellation currently returns `null` from the exchange callback after logging `gateway.chat.stream_cancelled`, making it indistinguishable from success to the caller.
- A better contract is needed: return a terminal enum/result or throw/use callback for cancellation, so controller can record `cancelled/client_cancelled` and release token reservation.
- If emitter timeout callbacks are added in controller, upstream client must still close the body stream and avoid sending after terminal state.

### Stream preparation

File:

```text
backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java
```

Observed flow:

- Carries base URL, decrypted upstream API key, upstream request, model, provider, question summary, hit chunk IDs, and retrieval evidence.
- It does not carry terminal/cancellation callbacks or reservation state.

Planning concern:

- Keep this as data-only if possible. If lifecycle helpers are added, prefer a narrow gateway-stream helper class rather than making this class own concurrency.

### Gateway service

File:

```text
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
```

Observed flow:

- `processChatCompletion(...)` and `prepareStreamCompletion(...)` share validation, app/model config resolution, upstream key decryption, retrieval, prompt augmentation, and upstream request construction.
- `prepareStreamCompletion(...)` sets `upstreamRequest.stream=true`, computes `questionSummary`, `hitChunkIds`, and `retrievalEvidence`.
- Retrieval failures before upstream setup throw normal `GatewayException` values, so streaming setup can still return JSON errors.

Planning concern:

- Do not duplicate retrieval/prompt logic for streaming. Preserve the existing shared preparation flow.
- Retrieval evidence order and content must remain aligned with non-streaming behavior.

### Request-log service and VOs

Files:

```text
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogQuery.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java
```

Observed flow:

- `CreateRequestLogCommand` already carries status/error_code/latencies/token metadata/question_summary/hit_chunk_ids/retrieval_evidence/output capture metadata.
- `ApiRequestLogVO.from(...)` parses `hit_chunk_ids` JSONB and fails visibly on malformed values.
- `ApiRequestLogDetailVO.from(...)` parses `retrieval_evidence` and fails visibly on malformed values.
- Request-log list filters currently validate status values in service/controller code; if `cancelled` is added, update that validation and tests.

Planning concern:

- Prefer using existing `status` + `error_code` columns for `cancelled/client_cancelled`. Add migration only if a DB constraint blocks it.
- If frontend status unions exist, keep them aligned.

### Rate limit reservation

Files:

```text
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitService.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitResult.java
backend/src/main/java/com/sangui/raggateway/common/config/ApiKeyLimitProperties.java
```

Observed flow:

- `checkAndReserve(...)` estimates tokens from message char count plus `max_tokens` or default completion reservation.
- `reconcileTokens(...)` and `releaseReservation(...)` use the minute/day windows stored in `ApiKeyRateLimitResult`.
- Existing controller tests cover non-streaming reconcile and release on upstream failure.

Planning concern:

- Streaming currently has no actual upstream usage data. For success, keeping the conservative reservation is acceptable under the PRD. For cancellation/failure, release it.
- Do not introduce Redis keys using plaintext key, hash, prefix, prompt, messages, provider keys, or raw SSE.

## Existing Tests Found

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyRateLimitServiceTest.java
```

Existing useful coverage:

- Stream returns `text/event-stream`.
- Stream opt-in citation header does not emit `sangui_citations`.
- Pre-ready upstream failure returns JSON `upstream_error`.
- Stream pre-validation failure returns JSON `invalid_request`.
- Upstream client forwards chunks and handles `[DONE]`.
- Upstream client throws on non-2xx and on stream close without `[DONE]`.
- Safe stream logs do not include API key, message content, or provider raw body.
- Non-streaming limiter validates before reservation and releases reservation on upstream failure.

Missing / weak coverage to add:

- Client disconnect/cancel terminal state and request-log classification.
- Exactly one request-log record for streaming terminal paths.
- Token reservation release on streaming cancel and post-start failure.
- Token reservation behavior on streaming success.
- Post-start upstream exception/error after stream ready emits safe SSE error and records failure.
- Emitter timeout lifecycle if implemented.
- Request-log `cancelled` status filter/VO/frontend type alignment if introduced.

## Files Likely To Modify

Backend:

```text
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogQuery.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java
```

Tests:

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyRateLimitServiceTest.java
```

Frontend only if request-log status/type changes:

```text
frontend/src/types/request-log.ts
frontend/src/pages/request-logs/RequestLogDetailDrawer.tsx
frontend/src/pages/request-logs/RequestLogPage.tsx
```

Specs:

```text
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/gateway/resilience.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
.trellis/spec/security/rag-security.md
.trellis/spec/frontend/type-safety.md (only if frontend-facing status/type changes)
```

## Risk / Boundary Notes

- Do not change the public OpenAI-compatible payload shape.
- Do not add streaming citation events.
- Do not persist raw SSE chunks.
- Do not move retrieval or prompt construction into controller or upstream client.
- Do not make post-start errors rely on `GlobalExceptionHandler`; response is already committed.
- Do not classify cancellation as success.
- Do not double-write request logs from emitter callbacks plus worker thread.
- Do not double-release reservations or release a null/unaccepted reservation.
- Do not log expected client disconnect as `ERROR`.
- Do not update frontend unless backend status semantics require it.

## Required Tests

Backend:

```bash
cd backend
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=ApiKeyRateLimitServiceTest" test
mvn -q -DskipTests compile
```

Frontend, only if touched:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Repo gate:

```bash
git diff --check
```

