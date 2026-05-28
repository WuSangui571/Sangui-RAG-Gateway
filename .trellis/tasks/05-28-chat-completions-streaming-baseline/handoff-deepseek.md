# DeepSeek Execution Handoff

## Task

Implement Chat Completions streaming baseline.

Task path:

```text
.trellis/tasks/05-28-chat-completions-streaming-baseline
```

PRD:

```text
.trellis/tasks/05-28-chat-completions-streaming-baseline/prd.md
```

Research:

```text
.trellis/tasks/05-28-chat-completions-streaming-baseline/research.md
```

## Must-Read Context

Trellis context has already been initialized. Read these before coding:

```text
.trellis/tasks/05-28-chat-completions-streaming-baseline/prd.md
.trellis/tasks/05-28-chat-completions-streaming-baseline/research.md
.trellis/tasks/05-28-chat-completions-streaming-baseline/implement.jsonl
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/backend/directory-structure.md
.trellis/spec/backend/database-guidelines.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
.trellis/spec/guides/cross-layer-thinking-guide.md
.trellis/spec/guides/code-reuse-thinking-guide.md
```

Current task has been activated with:

```text
python ./.trellis/scripts/task.py start .trellis/tasks/05-28-chat-completions-streaming-baseline
```

## Expected Scope

Implement only the baseline `stream=true` path for authenticated `POST /v1/chat/completions`.

Keep existing `stream=false` behavior unchanged.

## Expected API Behavior

- `stream=true` returns OpenAI-compatible SSE (`text/event-stream`).
- Forward upstream chunks and `data: [DONE]`.
- Pre-stream errors return OpenAI-compatible JSON:
  - validation: `400 invalid_request`
  - model config not ready: `409 model_config_not_ready`
  - upstream error: `502 upstream_error`
  - upstream timeout: `504 upstream_timeout`
- Post-start upstream errors use safe SSE error/close behavior.
- Client disconnect cancels upstream forwarding and is not treated as internal error.
- Streaming request logs are persisted with safe metadata and nullable usage.

## Expected Files To Modify

Likely implementation files:

```text
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamChatCompletionRequest.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
```

Potential new files:

```text
backend/src/main/java/com/sangui/raggateway/gateway/stream/*
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionStreamResult.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamStreamHandler.java
```

Tests to update/add:

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
```

Specs to update after implementation:

```text
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
```

## Do Not Modify / Do Not Expand

- Do not implement RAG retrieval, embeddings, prompt augmentation, source citations, or knowledge-base checks.
- Do not add frontend/admin UI.
- Do not alter app API key auth semantics.
- Do not change admin model config APIs.
- Do not add unrelated OpenAI API endpoints.
- Do not expose upstream provider error bodies.
- Do not log or persist request messages, Authorization headers, app key hashes, upstream key plaintext/encrypted values, raw provider bodies, or stack traces.
- Avoid database migration unless absolutely necessary; current `rag_request_log` nullable fields should support the baseline.

## Implementation Notes

- The project currently uses Spring MVC and `RestClient`; no WebFlux/WebClient dependency exists.
- Existing non-streaming upstream client reads the whole response body. Add a separate streaming method rather than weakening the non-streaming path.
- Capture request ID, app ID, user ID, API key ID, model/provider, and message count before async streaming work. `GatewayRequestContextHolder` is ThreadLocal and may be cleared by the filter before async callbacks finish.
- Centralize streaming request-log finalization to guarantee exactly one row.
- Treat downstream `IOException` while writing as client cancellation.
- Do not log chunk payloads.

## Must-Run Tests

Run from repository root or `backend/` as appropriate:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Acceptance Checklist

- [ ] `stream=true` succeeds for valid upstream SSE.
- [ ] `stream=false` regression remains unchanged.
- [ ] Pre-stream errors are JSON OpenAI-compatible errors.
- [ ] Post-start errors close safely and do not leak provider details.
- [ ] Client disconnect cancels upstream and is not logged as internal failure.
- [ ] Streaming request log row is written exactly once.
- [ ] Streaming usage fields are null unless safely available.
- [ ] No sensitive data appears in logs, request logs, or responses.
- [ ] Specs are updated after implementation.
