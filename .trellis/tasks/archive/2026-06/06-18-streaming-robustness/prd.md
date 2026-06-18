# P1 Streaming Robustness PRD

## Scope Classification

Complex Task.

This task crosses the public OpenAI-compatible `/v1/chat/completions` API, SSE lifecycle, upstream HTTP client behavior, request-log semantics, API-key rate-limit reservation handling, gateway error mapping, backend tests, frontend request-log type alignment if status values change, and executable specs.

## Goal

Harden `stream=true` chat completions so SSE calls have deterministic lifecycle semantics for success, client cancellation, upstream failures, upstream half-open/early close, timeout, safe request logging, and token reservation cleanup.

The implementation must preserve the lightweight OpenAI-compatible RAG gateway boundary. This is not a metrics dashboard, provider fallback, frontend redesign, or live provider smoke task.

## Current State Summary

Recent completed work strengthened non-streaming source citations, retrieval evidence, request-log safe observability, and retrieval evaluation. The current branch is `feature/streaming-robustness`, the working tree was clean at task start, and no Trellis task was active.

Relevant current behavior found during planning:

- `OpenAiChatCompletionsController` validates before limiter reservation and branches to `handleStreamCompletion` for `stream=true`.
- Streaming setup waits for upstream 2xx before returning `SseEmitter`, preserving JSON errors for pre-stream failures.
- Streaming request logs currently persist success/failure rows with `STREAMING_UNSUPPORTED` output capture and retrieval evidence when available.
- `OpenAiCompatibleUpstreamClient` forwards upstream `data:` lines, detects `[DONE]`, maps no-`[DONE]` early close to `upstream_error`, and treats `IOException` from `emitter.send()` as cancellation.
- Gaps to close: post-start cancellation is not clearly propagated to controller request-log semantics, streaming token reservations are not clearly released/reconciled on terminal background paths, emitter/upstream terminal state is spread across controller/client callbacks, and tests do not cover the full lifecycle matrix.

## API Contract

Endpoint remains:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json

{
  "model": "ignored-by-gateway-or-compatible-client-value",
  "messages": [
    {"role": "user", "content": "question"}
  ],
  "temperature": 0.7,
  "max_tokens": 1024,
  "top_p": 1,
  "stream": true
}
```

Successful streaming response:

```http
HTTP/1.1 200
Content-Type: text/event-stream

data: {upstream OpenAI-compatible chat.completion.chunk JSON}

data: [DONE]
```

Post-start upstream failure event:

```text
data: {"error":{"message":"Upstream service is unavailable","type":"server_error","code":"upstream_error"}}
```

Pre-stream failures must remain normal OpenAI-compatible JSON errors and must not commit SSE:

```json
{
  "error": {
    "message": "safe message",
    "type": "server_error",
    "code": "upstream_error"
  }
}
```

No new public request payload fields are required. Do not add frontend-visible configuration UI unless a directly required status/type field changes.

## Request-Log Contract

Every authenticated streaming request that reaches the controller logging boundary must persist exactly one safe request-log row.

Required safe fields:

```text
request_id
user_id
app_id
api_key_id
model
provider_name
status
error_code
latency_ms
upstream_latency_ms where available
messages_count
question_summary
hit_chunk_ids
retrieval_evidence
output_capture_status=STREAMING_UNSUPPORTED
```

Required status semantics:

| Streaming outcome | Persisted status | error_code | Notes |
|---|---|---|---|
| Upstream 2xx, `[DONE]` forwarded | `success` | null | `upstream_latency_ms` populated when measurable. |
| Validation/model/KB/embedding failure before SSE commit | `failure` | existing gateway code | JSON response, no SSE committed. |
| Upstream non-2xx/timeout/network failure before SSE commit | `failure` | `upstream_error` or `upstream_timeout` | JSON response, no SSE committed. |
| Upstream failure after SSE commit | `failure` | `upstream_error` or `upstream_timeout` | Safe SSE error event then close. |
| Upstream closes without `[DONE]` after SSE commit | `failure` | `upstream_error` | Safe SSE error event then close. |
| Client disconnect / `SseEmitter.send` IOException | `cancelled` | `client_cancelled` | No error event required; log at INFO, not ERROR. |
| Server-side emitter timeout | `cancelled` or `failure` depending on owner | `stream_timeout` if gateway-owned timeout; `client_cancelled` if disconnect-like | Must be deterministic and tested. |

If `status=cancelled` is introduced, update all matching request-log status validation/type surfaces:

- backend request-log list filter validation
- backend VO/service tests
- frontend request-log status TypeScript union and display fallback, if present
- specs listing allowed request-log statuses

Request-log rows must never include raw SSE payloads, prompts, full messages, chunk content, output preview, provider raw bodies, API keys, key hashes, encrypted upstream keys, authorization headers, stack traces, storage paths, embeddings, or environment values.

## Rate-Limit / Reservation Contract

Existing rule remains: invalid chat payloads fail before limiter reservation, and authenticated rate-limit rejections do not call retrieval, embedding, or upstream chat.

Streaming-specific terminal behavior:

| Outcome | Request counter | Token reservation |
|---|---|---|
| Pre-stream validation failure | no reservation | no token charge |
| Rate-limit rejected | rejected without upstream call | no accepted reservation |
| Pre-stream model/KB/embedding/upstream setup failure after reservation | accepted attempt may remain; release token reservation |
| Stream success with `[DONE]` and no usage support | keep the conservative token reservation as the current streaming charge |
| Stream success with future upstream usage support | reconcile to actual total tokens |
| Client cancellation before `[DONE]` | release token reservation |
| Post-start upstream failure / missing `[DONE]` | release token reservation |
| Background unexpected exception | release token reservation |

Do not silently leak token reservations on any terminal failure/cancel path. Reuse `ApiKeyRateLimitService.releaseReservation(...)` and `reconcileTokens(...)` semantics; do not introduce Redis keys containing prompts, keys, provider names, request bodies, or raw SSE.

## SSE Lifecycle Requirements

Required behavior:

- Validation, API-key auth, limiter check, model config resolution, KB readiness, retrieval/embedding, upstream API-key decryption, and upstream 2xx setup must finish before SSE response is committed where possible.
- Once committed, forward upstream `data:` payloads as they arrive without buffering the whole stream.
- `[DONE]` must be detected and treated as successful terminal completion.
- Missing `[DONE]` must be a detectable failure, not success.
- `IOException` while sending to the client must stop upstream reading, close resources, record `cancelled`, and release token reservation.
- Upstream response body streams must be closed on success, failure, timeout, early close, and cancellation.
- `SseEmitter` completion/error/timeout callbacks should be registered before launching the virtual thread so terminal state is idempotent.
- Terminal state must be guarded with a single completion flag so success/failure/cancel cannot double-record request logs or double-release reservations.
- Do not log expected client disconnects as internal errors.

Timeouts:

- Upstream connect/read timeout remains mandatory.
- Add or document a bounded gateway-owned SSE/emitter timeout if the current infinite emitter timeout remains unsafe. If a new property is added, use a conservative default and update specs/tests.

## Validation / Error Matrix

| Case | Expected response | Request log | Reservation assertion |
|---|---|---|---|
| Valid stream, upstream sends chunk then `[DONE]` | 200 SSE, chunks forwarded, `[DONE]` forwarded | `success`, safe fields, retrieval evidence retained | reservation retained or reconciled if usage exists |
| Valid stream, upstream non-2xx before ready | 502 JSON `upstream_error` | `failure/upstream_error`, model/provider and retrieval evidence if prep reached that point | token reservation released |
| Valid stream, upstream timeout before ready | 504 JSON `upstream_timeout` | `failure/upstream_timeout` | token reservation released |
| Valid stream, upstream sends one chunk then closes without `[DONE]` | 200 SSE then safe error event and close | `failure/upstream_error` | token reservation released |
| Valid stream, upstream throws after ready | 200 SSE then safe error event and close | `failure/upstream_error` or `upstream_timeout` | token reservation released |
| Client disconnect during send | stream closes quietly | `cancelled/client_cancelled`, not success | token reservation released |
| Emitter timeout before done | deterministic close | `cancelled/client_cancelled` or `failure/stream_timeout` per implementation rule | token reservation released |
| Invalid request body/messages | 400 JSON `invalid_request` | `failure/invalid_request` when controller has request ID | no limiter reservation |
| API-key rate limit exceeded | 429 JSON `rate_limit_exceeded` | `failure/rate_limit_exceeded` | no upstream call |
| Redis limiter unavailable | 500 JSON `internal_error` | `failure/internal_error` where feasible | no upstream call |
| Missing/disabled model config | 409 JSON `model_config_not_ready` | `failure/model_config_not_ready` | token reservation released if reservation already happened |
| KB not ready / missing user message / embedding failed | existing JSON error mapping | `failure` with existing code | token reservation released if reservation already happened |

## Good / Base / Bad Cases

Good:

- A valid `stream=true` request with ready KB and healthy upstream returns SSE chunks and `[DONE]`, records one safe `success` request-log row with `question_summary`, `hit_chunk_ids`, and `retrieval_evidence`, and does not expose citations as SSE-specific events.
- A client disconnect is recorded as `cancelled/client_cancelled`, releases token reservation, closes upstream resources, and is not logged as an internal error.
- A post-start upstream failure emits one safe SSE error event, closes the emitter, records one `failure` row, and releases token reservation.

Base:

- Streaming usage remains unsupported; `output_capture_status=STREAMING_UNSUPPORTED`.
- Successful streaming may keep the conservative token reservation until upstream usage accounting exists.
- No provider fallback/retry/circuit-breaker is introduced.

Bad:

- Client disconnect records `success`.
- Missing `[DONE]` records `success`.
- Post-start failure leaks raw provider body or stack trace through SSE/logs.
- Any terminal streaming path records multiple request-log rows.
- Streaming reservation is never released on cancel/failure.
- Invalid requests consume quota.
- Frontend/backend request-log status unions drift if `cancelled` is introduced.

## Expected Files To Modify

Backend implementation likely:

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

Backend tests likely:

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyRateLimitServiceTest.java
```

Frontend only if request-log status/type/API display changes:

```text
frontend/src/types/request-log.ts
frontend/src/pages/request-logs/RequestLogDetailDrawer.tsx
frontend/src/pages/request-logs/RequestLogPage.tsx
frontend/src/i18n/* or local status label files if present
```

Specs/docs:

```text
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/gateway/resilience.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
.trellis/spec/frontend/type-safety.md (only if frontend status/type changes)
.trellis/spec/security/rag-security.md
README.md only if public streaming limitations or smoke contract wording changes
```

## Explicit Non-Goals / Forbidden Scope

- Do not implement metrics dashboard, Prometheus, Grafana, tracing, or a new admin analytics UI.
- Do not implement provider fallback, retry, circuit breaker, provider routing, or live model health checks.
- Do not implement streaming source-citation SSE events.
- Do not persist raw SSE payloads or streaming answer preview content.
- Do not introduce a new database table unless proven necessary; existing `rag_request_log.status/error_code` can carry lifecycle semantics if no constraint blocks it.
- Do not change retrieval ranking, prompt policy, citation format, or no-hit behavior except where tests need existing retrieval evidence to pass through streaming logs.
- Do not change Admin auth or public API-key auth boundaries.
- Do not add frontend UX beyond type/status alignment required by backend status semantics.
- Do not make invalid requests consume rate-limit quota.

## Required Tests And Assertion Points

Run from `backend/`:

```bash
mvn -q "-Dtest=OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=ApiKeyRateLimitServiceTest" test
mvn -q -DskipTests compile
```

If frontend types or UI labels change, run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

Repo-level formatting gate:

```bash
git diff --check
```

Required assertions:

- `OpenAiCompatibleUpstreamClientTest` covers successful `[DONE]`, non-2xx, timeout/network classification, missing `[DONE]`, client send `IOException` cancellation, safe logs without provider body/messages/keys, and upstream body close on terminal paths where testable.
- `OpenAiChatCompletionsControllerTest` covers pre-stream JSON failures, post-start SSE error path, cancellation request-log status, success request-log status, exactly-one-log-row behavior, and reservation release/reconcile behavior for success/failure/cancel.
- `ApiRequestLogServiceTest` and admin controller tests cover `cancelled` status/filter/VO behavior if introduced.
- `ApiKeyRateLimitServiceTest` continues to prove release/reconcile use the reservation windows and Redis keys remain api-key-id scoped.
- Tests assert no forbidden fields in request-log commands/responses where applicable: prompt, messages, raw_sse, api_key, key_hash, upstream_api_key, api_key_encrypted, provider_response_body, stack_trace, storage_path, embedding, chunk_content.

## Planning Self-Check

- Acceptance criteria are explicit in API, request-log, reservation, lifecycle, and test contracts.
- Forbidden scope is explicit.
- Expected implementation and test files are listed.
- Required tests are listed.
- Concrete guideline/spec files were read during planning, not only index files.
- No database migration is required unless the implementation discovers a status constraint or index requirement.
- If introducing `status=cancelled`, align backend filters, frontend types, and specs in the same change.
- No unresolved user confirmation is required before implementation as long as the task stays within this PRD.
