# Redis Script Reuse Governance

## Goal

Remove avoidable per-request Redis Lua script object construction from the chat completion rate-limit path while preserving the current Redis key contract, Lua semantics, public error behavior, and visible failure behavior.

## Scope Classification

Complex Task.

The task is localized to backend Redis rate-limit implementation and tests, but it touches a gateway protection boundary and Redis/cache infrastructure behavior. It requires explicit contract preservation rather than a blind refactor.

## Background

`/v1/chat/completions` is protected by API-key rate limiting before upstream forwarding. Current Redis Lua usage must be checked for repeated construction of `DefaultRedisScript`, script text, and key/value collections on hot request paths. If script definitions are repeatedly allocated inline, they should be moved to reusable static singleton script definitions or an injected reusable script owner.

## Requirements

- Locate `ApiKeyRateLimitService` or the equivalent Redis Lua caller for API-key rate limiting.
- Identify all Redis Lua contracts used by rate limiting:
  - request limit check/increment
  - token reservation
  - daily token counters
  - reservation release
  - reservation reconcile
- Refactor only script ownership/allocation structure so script definitions are reusable.
- Preserve existing Redis keys, ARGV order, return values, TTL behavior, counter semantics, and exception mapping.
- Preserve visible Redis failure behavior. Redis execution errors must not become silent allow, silent reject, or hidden fallback behavior.
- Add or adjust focused backend tests covering allow, reject, Redis failure, reservation release, and reservation reconcile.
- Update Trellis spec only if research confirms no existing backend/gateway guidance records the Redis Lua script owner/reuse contract.

## Non-Goals

- Do not change public API endpoints, request/response DTOs, OpenAI-compatible payload fields, frontend types, or admin UI behavior.
- Do not change database schema, migrations, API-key persistence, Redis key names, TTL durations, quota math, token counting, tenant scoping, or upstream provider routing.
- Do not add distributed locks, new Redis dependencies, retries, fallbacks, circuit breakers, or asynchronous quota reconciliation.
- Do not broaden rate-limit policy beyond removing repeated script construction.
- Do not modify frontend, Docker, nginx, image build, CI, or deployment configuration.

## API / Command / Payload Contract

- Public API command remains `POST /v1/chat/completions`.
- Public payload fields remain unchanged.
- API-key authentication and authorization headers remain unchanged.
- Public rejection and failure response shape must remain compatible with the current implementation.
- No new environment variables or configuration keys are expected.

## Validation / Error Matrix

| Case | Expected behavior |
|---|---|
| Request count within limit | Request continues through the existing gateway path. Redis script returns the same allow result as before. |
| Request count over limit | Request is rejected with the existing rate-limit exception/status/error mapping. |
| Token reservation within available budget | Reservation succeeds and returns the same reservation data/allowed state as before. |
| Token reservation over available budget | Reservation is rejected with the existing quota/rate-limit mapping. |
| Reservation release | Reserved tokens are released according to the existing Lua/key semantics. |
| Reservation reconcile | Reserved token counters are reconciled to actual token usage according to the existing Lua/key semantics. |
| Redis script execution failure | Failure remains visible through the existing exception path; no silent pass-through, hidden fallback, or mock success path. |
| Redis missing/unavailable in tests | Tests should mock the Redis boundary or run targeted slices already used by existing tests; no new live Redis requirement unless already present. |

## Good / Base / Bad Cases

- Good: A request under request and token limits is allowed, reservation/reconcile leaves counters consistent, and reusable script instances are used.
- Base: A request over configured limits is rejected with the same public behavior as before.
- Bad: Redis execution throws; the service surfaces the same visible failure behavior instead of allowing traffic or masking quota state.

## Expected Implementation Shape

- Prefer the smallest maintainable structural change:
  - static final `DefaultRedisScript<T>` constants near the owner service, or
  - a small injected script owner component if multiple scripts make the service noisy.
- Keep Lua script text in one owner location. Do not duplicate script text across tests and implementation unless a test intentionally asserts a contract through the public method behavior.
- Use existing Spring Data Redis APIs and local test patterns.
- Keep script result types explicit and aligned with existing return parsing.

## Files Likely To Modify

Confirmed by focused code research:

- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitService.java`
  - `executeCheck(...)` currently creates the main Lua script text and `DefaultRedisScript<List>` inside the method.
  - `reconcileTokens(...)` currently creates the reconciliation Lua script text and `DefaultRedisScript<Long>` inside the method.
  - `releaseReservation(...)` currently creates the release Lua script text and `DefaultRedisScript<Long>` inside the method.
- Optional `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyRateLimitScripts.java` or equivalent same-package owner if that keeps `ApiKeyRateLimitService` clearer than static fields.
- `backend/src/test/java/com/sangui/raggateway/apikey/ApiKeyRateLimitServiceTest.java`
  - Existing tests cover token estimation, effective limit selection, reset seconds, script result parsing, and invalid default validation.
  - Missing coverage: actual `StringRedisTemplate.execute(...)` contract for allow/reject/failure/release/reconcile and stable script ownership.
- Potential controller regression tests if needed:
  - `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
  - `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`
- Spec update candidate:
  - `.trellis/spec/backend/database-guidelines.md`
  - Existing spec documents Redis key names and rate-limit/quota counter behavior, but does not explicitly document reusable Lua script ownership. Update this file only if the implementation establishes that contract.

## Required Tests And Assertion Points

- Targeted backend unit tests for the rate-limit service:
  - allow path still invokes Redis with expected key/argument contract.
  - reject path still maps to the same exception/result.
  - Redis failure remains visible.
  - release path uses the same key/argument contract and returns the same behavior.
  - reconcile path uses the same key/argument contract and returns the same behavior.
- Add assertions that script ownership is stable enough to prevent per-request `DefaultRedisScript` construction where feasible without overfitting to private implementation.
- Run targeted Maven tests for the modified rate-limit service tests.
- Run backend compile after targeted tests.
- Recommended regression tests after implementation:
  - `mvn -q "-Dtest=ApiKeyRateLimitServiceTest,OpenAiChatCompletionsControllerTest" test`
  - `mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test` if any streaming reservation contract is touched.
  - `mvn -q -DskipTests compile`

## Relevant Existing Contracts Found

- Redis keys must remain:
  - `rag:api-key-limit:{apiKeyId}:rpm:{yyyyMMddHHmm}`
  - `rag:api-key-limit:{apiKeyId}:tpm:{yyyyMMddHHmm}`
  - `rag:api-key-limit:{apiKeyId}:daily-requests:{yyyyMMdd}`
  - `rag:api-key-limit:{apiKeyId}:daily-tokens:{yyyyMMdd}`
- Runtime limiter must use `api_key_id` only in Redis keys; never plaintext keys, key hashes, key prefixes, prompts, messages, provider keys, or request bodies.
- Valid chat payloads are checked against API-key limits before embedding, retrieval, or upstream calls.
- Redis limiter unavailable maps to OpenAI-compatible `500 internal_error`; no silent pass-through.
- Request/token limit exceeded maps to OpenAI-compatible `429 rate_limit_exceeded`; no retrieval, embedding, or upstream call.
- Reconciliation/release must adjust the same minute/day token windows that received the preflight reservation.
- Streaming success does not call release/reconcile; streaming cancellation, timeout, and post-start failure call `releaseReservation` exactly once.

## Focused Code Research Summary

### Code Patterns Found

- `ApiKeyRateLimitService` owns Redis key construction, Lua execution, result parsing, and visible Redis failure mapping.
- `OpenAiChatCompletionsController` validates request shape before quota reservation and records safe request-log failures for rate-limit rejection or Redis limiter errors.
- Controller tests mock `ApiKeyRateLimitService` for gateway behavior; runtime smoke tests assert release behavior for streaming terminal states without requiring live Redis.

### Boundary Notes

- This task should avoid changing controller call order unless tests prove an implementation issue; the root issue is script object ownership in `ApiKeyRateLimitService`.
- Test improvements should prefer mocking `StringRedisTemplate.execute(...)` and capturing `RedisScript`/keys/ARGV rather than starting Redis.
- If a reusable script owner is extracted, keep it in the `apikey` module, not `common`, because the scripts encode API-key quota business semantics.

## Acceptance Criteria

- [ ] No request-path creation of Redis Lua `DefaultRedisScript` or equivalent script definitions remains in rate-limit hot methods.
- [ ] Existing Redis Lua semantics, key names, ARGV order, return parsing, TTL behavior, and failure visibility are preserved.
- [ ] Focused tests cover allow, reject, Redis failure, release, and reconcile.
- [ ] Backend compile passes.
- [ ] Spec update is made only if the reusable script owner contract is missing from current guidelines.
- [ ] No business behavior outside backend API-key rate-limit Redis script ownership changes.

## Handoff Constraint

Codex prepares this PRD, task context, spec reading, code research, risk notes, and test plan only. DeepSeek will perform implementation. Codex must not modify business implementation files in this round.
