# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project-level boundary; request logs are safe operational metadata and must not store full prompts or document content by default.
- `.trellis/spec/backend/logging-guidelines.md`: request context, safe log fields, gateway structured log contract, request-log persistence contract, forbidden sensitive data.
- `.trellis/spec/gateway/resilience.md`: request-log insert failure must leave gateway response unchanged, but must be safe and observable.
- `.trellis/spec/security/rag-security.md`: request logs, evidence, errors, and observability surfaces must not expose keys, prompts, chunk content, provider bodies, raw SSE, storage paths, stack traces, or environment values.
- `.trellis/spec/backend/error-handling.md`: public `/v1/*` responses must remain OpenAI-compatible; request-log failures must not replace the original gateway error response.
- `.trellis/spec/backend/quality-guidelines.md`: request-log observability tests and streaming runtime smoke checks are required around gateway/request-log changes.
- `.trellis/spec/backend/database-guidelines.md`: `rag_request_log` is tenant-scoped safe metadata; no schema change is expected for this task.
- `.trellis/spec/rag/retrieval-quality.md`: `question_summary`, `hit_chunk_ids`, and `retrieval_evidence` are safe request-log data for persistence/API boundaries, but raw evidence JSON should not be dumped into error logs.
- `.trellis/spec/rag/prompt-context-policy.md`: full prompts/messages and complete context are sensitive and must not be logged.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: request logging/observability is a cross-layer trigger; define response shape, sensitive fields, and test cases before implementation.

## Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - `record(CreateRequestLogCommand)` currently catches `Exception` and logs `Failed to persist request log for request_id={}, errorType={}`.
  - It does not rethrow, so the gateway response is already protected.
  - Current observable signal is minimal and not asserted by tests.

- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
  - Calls `apiRequestLogService.record(...)` in all authenticated controller/logging-boundary outcomes:
    - non-streaming rate-limit rejection;
    - non-streaming success;
    - non-streaming `GatewayException`;
    - streaming rate-limit rejection;
    - streaming pre-start preparation/upstream failure;
    - streaming timeout;
    - streaming client cancellation;
    - streaming success;
    - streaming post-start gateway/general failure;
    - `recordGatewayFailure(...)` for validation/rate-limit service failures.
  - Because record is called inline on non-streaming paths, a throwing mock `ApiRequestLogService.record()` would currently expose whether controller assumes the service catches internally. The production contract is service-owned catching; controller-level tests should use a mock that throws only if the task intentionally wants to harden controller as a second boundary.

- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
  - Existing `shouldHandleInsertFailureSafely()` asserts insert is attempted and no exception escapes.
  - It does not use `OutputCaptureExtension`, does not assert ERROR-level output, and does not assert forbidden strings are absent.

- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java`
  - Existing pattern for safe log assertions uses `@ExtendWith(OutputCaptureExtension.class)` and `CapturedOutput`.
  - Assertions combine `output.getOut() + output.getErr()` and check event tokens plus negative secret/body/message fixtures.

- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
  - Same safe-log pattern is used for validation and parse failures.
  - Useful model for adding log safety tests without custom logback appenders.

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
  - Existing tests assert request-log command fields for success/failure/stream terminal outcomes.
  - Several streaming assertions use Mockito `timeout(...)` because record happens on a virtual thread.
  - There is no explicit test that request-log persistence failure leaves the response unchanged.

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`
  - Real embedded servlet-container streaming smoke covers normal `[DONE]`, disconnect, emitter timeout, and post-start upstream failure.
  - It verifies `ApiRequestLogService.record(...)` on async streaming paths with timeouts. Keep this passing; add only narrow coverage if necessary.

## Files Likely To Modify

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - Tighten the failure log into a stable, safe event with request_id, safe IDs/status/error_code, and exception class.
  - Do not include exception message or stack trace.

- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
  - Add `OutputCaptureExtension`.
  - Strengthen insert-failure test to assert observable signal and forbidden-string absence.

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
  - Add or adjust tests proving gateway success/failure response behavior remains unchanged when request-log persistence fails.
  - Prefer exercising real `ApiRequestLogService` with a throwing mapper where possible; if using mocked service, be explicit that this adds a controller-level defense beyond current service-owned catching.

- `.trellis/spec/backend/logging-guidelines.md`
  - Update request-log persistence contract to require an observable safe failure signal, not just an internal catch.

- `.trellis/spec/gateway/resilience.md`
  - Clarify request-log insert failure row: response unchanged plus safe observable signal.

- `.trellis/spec/security/rag-security.md`
  - Optional: clarify forbidden fields in request-log persistence failure logs if backend/gateway specs are not enough.

## Risk / Boundary Notes

- This task should not add a fallback write path, retry queue, dead-letter table, new metric backend, or DB schema field unless the user explicitly expands scope.
- Avoid logging `CreateRequestLogCommand` wholesale. Today it can carry `questionSummary`, `retrievalEvidence`, and `outputPreview`.
- Avoid logging exception messages. Mapper/provider exceptions can include SQL fragments, body fragments, or injected sensitive-looking strings in tests.
- Do not log stack traces for expected insert failure observability unless the user accepts the added leakage/noise risk. Exception class is enough for the planned contract.
- Public OpenAI-compatible response shape must remain authoritative. Request-log failure must not convert success to `500`, alter upstream errors, or fabricate success.
- Existing spec already says `record()` catches exceptions; this task should refine that into a testable observability contract rather than reverse the non-throwing behavior.
- No frontend/API/DTO/DB field alignment is required because there are no planned payload changes.

## Required Tests

Run from `backend/` unless noted:

```bash
mvn -q "-Dtest=ApiRequestLogServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,OpenAiChatCompletionsControllerTest,OpenAiChatCompletionsRuntimeSmokeTest" test
mvn -q -DskipTests compile
```

Then from repo root:

```bash
git diff --check
```

If any backend unit-test command approaches the 60 second limit, split commands and report the incomplete command explicitly.

