# Chat Completions Streaming Baseline

## Classification

Complex Task.

This task changes the public OpenAI-compatible gateway path, upstream HTTP streaming behavior, servlet response lifecycle, request logging, error boundaries, and cancellation handling. Codex planning for this session must not modify business implementation files.

## Goal

Implement the baseline `stream=true` path for authenticated `POST /v1/chat/completions` requests.

The existing `stream=false` behavior must remain unchanged: authentication, model config resolution, non-streaming upstream forwarding, normalized errors, structured logs, and request-log persistence must continue to work as currently implemented.

## Product Rationale

Streaming is a core V0.2 gateway capability and is required for normal OpenAI-compatible client integration. It belongs on the gateway main path before future RAG retrieval/prompt augmentation work, so future RAG logic can support both non-streaming and streaming response paths without reworking the public contract twice.

## In Scope

- Accept `stream=true` in `POST /v1/chat/completions`.
- Validate request and model configuration before the first response byte is committed whenever possible.
- Forward an OpenAI-compatible upstream SSE stream to the client.
- Preserve upstream `data: ...` chunks, including `data: [DONE]`.
- Cancel or close upstream streaming work when the client disconnects.
- Normalize pre-stream errors as standard OpenAI-compatible JSON errors.
- Handle post-start upstream errors through a compatible SSE error event where possible, then close safely.
- Persist one safe `rag_request_log` row for authenticated streaming requests, covering success and failure.
- Keep token usage nullable for streaming in this baseline.
- Add focused tests for success, upstream error, timeout, cancellation/disconnect, non-streaming regression, and sensitive-data safety.

## Out of Scope

- Do not implement RAG retrieval, embeddings, prompt augmentation, source citations, or knowledge-base status checks.
- Do not add frontend pages, frontend types, or admin request log UI.
- Do not change database schema unless implementation proves the current `rag_request_log` nullable token fields are insufficient. The expected plan is no migration.
- Do not change app API key authentication semantics or admin model config APIs.
- Do not add support for unsupported OpenAI APIs such as `/v1/responses`, `/v1/embeddings`, tools, vision, audio, or `response_format`.
- Do not pass through upstream provider error bodies to clients or logs.
- Do not introduce provider-specific streaming logic unless hidden behind the existing OpenAI-compatible upstream client boundary.

## Public API Contract

Endpoint:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Supported request fields remain:

| Field | Required | Streaming behavior |
|---|---:|---|
| `model` | no | Accepted for client compatibility but not trusted for upstream selection. Upstream model remains `ModelConfigEntity.chatModel`. |
| `messages` | yes | Non-empty array. Roles remain `system`, `user`, and `assistant`; each message requires string `content`. |
| `temperature` | no | Forwarded to upstream when present. |
| `max_tokens` | no | Forwarded to upstream when present. |
| `top_p` | no | Forwarded to upstream when present. |
| `stream` | no | `true` activates SSE forwarding; absent or `false` uses existing non-streaming path. |

Streaming response:

```http
HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
```

Chunk behavior:

```text
data: {upstream chat.completion.chunk JSON}

data: {next upstream chat.completion.chunk JSON}

data: [DONE]

```

The gateway should forward OpenAI-compatible upstream SSE payloads as faithfully as possible. It must not wrap chunks in the admin `ApiResponse` envelope.

## Upstream Contract

- Construct upstream URL exactly as the existing non-streaming client does:
  - Trim trailing slash characters.
  - If base URL ends with `/v1`, append `/chat/completions`.
  - Otherwise, append `/v1/chat/completions`.
- Use `Authorization: Bearer <decrypted-upstream-api-key>` only for the outbound call.
- Send upstream request with resolved `model`, original allowed chat parameters, and `stream=true`.
- Treat upstream pre-response non-2xx, connection failure, invalid setup, and timeout as normalized gateway failures.
- For chunks:
  - Forward `data:` lines containing JSON chunks.
  - Forward `data: [DONE]` and close cleanly.
  - Ignore or safely pass through harmless SSE framing lines only if compatible with OpenAI clients.
- On client disconnect, stop writing and cancel/close the upstream request without logging it as an internal server error.

## Validation And Error Matrix

| Scenario | Before first byte? | HTTP / SSE | Error code | Required behavior |
|---|---:|---|---|---|
| Missing/invalid/disabled app API key | yes | 401 JSON | `invalid_api_key` | Existing `GatewayAuthFilter` behavior unchanged; no request log row. |
| Malformed JSON | yes | 400 JSON | `invalid_request` | Existing `GlobalExceptionHandler` gateway JSON behavior; request log remains unpersisted unless implementation already has safe context. |
| Null body, empty `messages`, missing role/content, unsupported role | yes | 400 JSON | `invalid_request` | No upstream call; persist failure row for authenticated requests. |
| `stream=false` or absent | yes | Existing JSON | Existing | Must remain current non-streaming behavior. |
| `stream=true`, model config missing/disabled/key missing/key decrypt failure | yes | 409 JSON | `model_config_not_ready` | Do not start stream; persist failure row. |
| Upstream non-2xx before response stream starts | yes | 502 JSON | `upstream_error` | Do not expose provider body; persist failure row. |
| Upstream connection/network failure before stream starts | yes | 502 JSON | `upstream_error` | Safe generic public error; persist failure row. |
| Upstream timeout before stream starts | yes | 504 JSON | `upstream_timeout` | Safe generic public error; persist failure row. |
| Upstream timeout after response stream starts | no | SSE then close | `upstream_timeout` | Emit compatible SSE error event if response is still writable; close safely; persist failure row. |
| Upstream malformed/invalid chunk after stream starts | no | SSE then close | `upstream_error` | Do not leak raw provider body; emit compatible error event if possible; persist failure row. |
| Upstream closes after `[DONE]` | no | 200 SSE | none | Persist success row. Usage fields may be null. |
| Upstream closes without `[DONE]` after chunks | no | 200 SSE closed | `upstream_error` if detectable | Persist failure if detectable as abnormal; otherwise document behavior in tests/spec. |
| Client disconnects | no | Connection closed | none or client_cancelled internal marker only if needed | Cancel upstream; do not log as internal error; request log should not mark as upstream/internal failure. |
| Request-log insert failure | any | Response unchanged | none | `ApiRequestLogService.record()` already catches exceptions; no client impact. |

## Request Log Contract

Streaming requests should persist one safe row in `rag_request_log` after the stream lifecycle ends or fails.

Fields:

| Field | Streaming value |
|---|---|
| `request_id` | Controller-generated request ID. |
| `user_id` | From `GatewayRequestContext`. |
| `app_id` | From `GatewayRequestContext`. |
| `api_key_id` | From `GatewayRequestContext`. |
| `model` | Resolved model config chat model when available. |
| `provider_name` | Resolved provider name when available. |
| `status` | `success` or `failure`. Client disconnect may be treated as safe cancellation; do not mark as internal error. |
| `error_code` | `invalid_request`, `model_config_not_ready`, `upstream_error`, or `upstream_timeout` for failures. |
| `latency_ms` | Total elapsed time from controller entry until stream close/failure/cancellation. |
| `upstream_latency_ms` | Time to upstream lifecycle completion when measurable. |
| `prompt_tokens` | Null in baseline streaming unless upstream provides final usage and parsing is implemented safely. |
| `completion_tokens` | Null in baseline streaming unless upstream provides final usage and parsing is implemented safely. |
| `total_tokens` | Null in baseline streaming unless upstream provides final usage and parsing is implemented safely. |
| `messages_count` | Count only; no message content. |
| `question_summary` | Null for this baseline. |
| `hit_chunk_ids` | Null for this baseline. |

Never persist:

- App API key plaintext.
- App API key hash.
- Upstream API key plaintext.
- Encrypted upstream API key.
- Authorization header.
- Full request body.
- Message content.
- Provider raw error body.
- Full prompt.
- Stack trace.

## Observability Contract

Reuse the existing safe structured gateway logging style. New streaming events may be introduced, but must remain safe:

- `gateway.chat.stream_started`
- `gateway.chat.stream_chunk_forwarded` only if not too noisy, and without chunk body.
- `gateway.chat.stream_completed`
- `gateway.chat.stream_failed`
- `gateway.chat.stream_cancelled`

Safe fields:

```text
request_id
user_id
app_id
api_key_id
provider_name
model
status
error_code
latency_ms
upstream_latency_ms
messages_count
error_class
```

Do not log chunk payloads, request messages, provider raw bodies, Authorization headers, or secrets.

## Good / Base / Bad Cases

Good cases:

- Authenticated `stream=true` request with enabled model config forwards upstream SSE chunks and `[DONE]`.
- Streaming request persists one success request-log row with nullable usage and correct latency/messages count.
- Existing `stream=false` request still returns normal JSON and existing request-log semantics.

Base cases:

- `stream` absent behaves exactly as `stream=false`.
- Valid auth plus validation failure returns JSON error before stream starts.
- Usage is null for streaming when upstream does not provide final usage.
- Existing `/v1/models` behavior remains unchanged.

Bad cases:

- Invalid API key still returns 401 from the filter and does not reach controller persistence.
- Missing/disabled model config returns 409 before any SSE bytes.
- Upstream pre-stream non-2xx/network failure maps to 502 JSON.
- Upstream pre-stream timeout maps to 504 JSON.
- Upstream failure after streaming begins emits safe SSE error/close behavior and persists failure.
- Client disconnect cancels upstream and is not logged as internal failure.
- Logs and request logs contain no app key, upstream key, Authorization header, message content, raw provider body, or stack trace.

## Expected Implementation Shape

Likely patterns:

- Keep `OpenAiChatCompletionsController` as HTTP boundary and branch by `request.stream`.
- Keep current non-streaming call path untouched except for shared helper extraction if necessary.
- Add or extend a service method for streaming orchestration.
- Add upstream client streaming support near `OpenAiCompatibleUpstreamClient`, reusing URL building, safe logging, timeout, and error classification.
- Prefer Spring MVC streaming primitives that support cancellation detection, such as `SseEmitter`, `StreamingResponseBody`, or servlet async handling. Pick the option that integrates cleanly with tests and upstream cancellation.
- Keep request-log persistence in a single finalization path to avoid duplicate rows.

## Files Likely To Modify

Implementation files:

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamChatCompletionRequest.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java` only if current command/result shape cannot express streaming safely.
- `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java` only if needed for streaming metadata, but avoid schema changes.
- `backend/src/main/java/com/sangui/raggateway/common/exception/GatewayException.java` only if a cancellation marker is absolutely needed.

Potential new files:

- `backend/src/main/java/com/sangui/raggateway/gateway/stream/*`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionStreamResult.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamStreamHandler.java`

Tests:

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java` only for regression if auth behavior is touched.

Spec/docs likely to update after implementation:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md` only if adding a concrete streaming test contract.

## Required Tests And Assertion Points

Targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

Assertions:

- `stream=true` returns `text/event-stream` and forwards chunk payloads in order.
- `[DONE]` is forwarded exactly once and terminates normally.
- Upstream request body contains `stream=true` and resolved model config chat model.
- Existing non-streaming tests still pass and `stream=false` still sends `stream=false` upstream.
- Pre-stream validation/model-config/upstream errors return OpenAI-compatible JSON, not SSE and not `ApiResponse`.
- Post-start upstream failure does not expose raw upstream body and closes safely.
- Upstream timeout maps to `upstream_timeout`.
- Client disconnect or writer failure triggers upstream cancellation and is not treated as internal error.
- Request-log success/failure rows are written exactly once for authenticated streaming requests.
- Streaming request-log usage token fields are null unless safely available.
- Logs and persisted rows do not contain app API key plaintext/hash, upstream key plaintext/encrypted, Authorization header, request message content, provider raw body, or stack trace.

## Acceptance Criteria

- [ ] `POST /v1/chat/completions` accepts `stream=true`.
- [ ] `stream=false` and absent `stream` behavior is unchanged.
- [ ] Streaming response is OpenAI-compatible SSE and forwards upstream chunks plus `[DONE]`.
- [ ] Pre-stream errors return OpenAI-compatible JSON with existing status/code semantics.
- [ ] Post-start upstream errors are handled through safe SSE error/close behavior.
- [ ] Client disconnect cancels upstream work and is not logged as internal failure.
- [ ] Streaming request logs are persisted with safe operational fields and nullable usage.
- [ ] No secrets or full message content appear in logs, request logs, or error responses.
- [ ] Focused tests and full Maven test suite pass.
- [ ] Relevant specs are updated after implementation.

## Planning Notes For Implementer

- Before editing constants or request/response fields, search references with `rg`.
- Keep the first implementation narrow: one OpenAI-compatible streaming path only.
- Avoid broad abstractions until concrete duplication appears.
- Avoid holding any database transaction open across upstream streaming.
- Be explicit in code/tests about the limitation that baseline streaming usage may be null.
