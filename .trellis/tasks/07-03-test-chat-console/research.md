# 测试对话 - Focused Code Research

## Current Project Status

- Branch: `feature/test-chat-console`.
- Working directory was clean before task setup.
- No active Trellis task existed before this task.
- Current task directory: `.trellis/tasks/07-03-test-chat-console`.
- Recent journal shows the previous `normalizeText reuse governance` work was completed, manually tested, committed, recorded, and archived.
- Active journal `.trellis/workspace/sangui/journal-3.md` is near the 2000-line warning threshold; record-session later should watch journal rollover behavior.

## Relevant Specs

- `.trellis/spec/frontend/index.md`: frontend feature entry point; requires project spec, directory, type safety, state management, quality guidelines, and cross-layer guide when API contracts are touched.
- `.trellis/spec/frontend/directory-structure.md`: pages live under `frontend/src/pages/<domain>`, API clients under `frontend/src/api`, shared DTOs under `frontend/src/types`, domain components under `frontend/src/components/domain`.
- `.trellis/spec/frontend/type-safety.md`: OpenAI/request-log/API key types must be explicit; normal API key list/detail types must not include plaintext or hash.
- `.trellis/spec/frontend/state-management.md`: full app API keys must remain page/modal memory only; no localStorage/sessionStorage/global store.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend validation baseline is lint/test/typecheck/build; pages must cover loading/empty/error/success and secret-safe rendering.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: maps gateway chat flow and requires defining request fields, validation owner, error shape, sensitive fields, and tests before implementation.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search/reuse first; this task has an existing smoke implementation that should be reused or generalized rather than duplicated.
- `.trellis/spec/gateway/resilience.md`: `/v1/chat/completions` errors must stay OpenAI-compatible, visible, bounded, and safe.
- `.trellis/spec/backend/error-handling.md`: gateway response/error matrix for invalid key, model_config_not_ready, knowledge_base_not_ready, embedding_failed, upstream_error, upstream_timeout, rate_limit_exceeded.
- `.trellis/spec/backend/logging-guidelines.md`: request-log and gateway observability allow safe IDs, latency, token usage, question summary, hit chunk IDs, retrieval evidence; forbid keys/prompts/chunk content/provider bodies.
- `.trellis/spec/rag/retrieval-quality.md`: retrieval must be tenant/KB scoped; RAG feedback may use safe hit IDs and retrieval evidence only.
- `.trellis/spec/rag/prompt-context-policy.md`: original messages are preserved, RAG context is bounded, no-hit under `STRICT_RAG` says insufficient evidence.
- `.trellis/spec/security/rag-security.md`: admin `/api/admin/**` uses admin JWT; public `/v1/*` uses app API key; request-log evidence is metadata-only; output preview is explicit access only.
- `.trellis/spec/backend/database-guidelines.md`: API key plaintext is never stored; list/detail may expose `key_prefix`, not `key_hash` or plaintext.

## Code Patterns Found

- Navigation/page registry:
  - `frontend/src/components/layout/AdminShell.tsx:15` defines `PageKey`.
  - `frontend/src/components/layout/AdminShell.tsx:17` maps `PageKey` to i18n keys.
  - `frontend/src/components/layout/AdminShell.tsx:104` builds menu items from that map.
  - `frontend/src/App.tsx:27` renders `SmokeTestPage` by page key; new page follows this switch pattern.
- Gateway `/v1` frontend client:
  - `frontend/src/api/openai.ts:8` uses `V1_BASE = '/v1'`.
  - `frontend/src/api/openai.ts:10` defines `SmokeApiError`.
  - `frontend/src/api/openai.ts:52` posts non-streaming chat completions with caller-supplied app key.
  - `frontend/src/api/openai.ts:72` posts streaming chat completions and parses SSE evidence.
  - This should be generalized/reused for test-chat rather than copied into a second raw fetch implementation.
- Existing OpenAI frontend types:
  - `frontend/src/types/openai.ts:6` defines `SmokeChatCompletionRequest` with `model`, `messages`, and `stream: false`.
  - `frontend/src/types/openai.ts:35` defines `SmokeChatCompletionResponse`.
  - Consider renaming or adding generic aliases while preserving existing Smoke imports.
- Backend chat contract:
  - `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionRequest.java:1` DTO includes `model`, `messages`, `temperature`, `max_tokens`, `top_p`, `stream`.
  - `ChatCompletionGatewayService` validates messages/roles/content, but runtime upstream model is resolved from App default model config, not request `model`.
  - `OpenAiChatCompletionsController.java:80` supports `X-Sangui-Return-Citations`; response includes `sangui_citations` only when header is true.
- Existing smoke page reuse seams:
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:192` loads apps with `listApps(undefined)`.
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:220` loads app API key metadata and filters active keys.
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:296` performs non-streaming smoke.
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:301` sends `model: 'ignored-by-gateway'`.
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:374` validates request-log evidence after a successful non-streaming call.
  - `frontend/src/pages/smoke/SmokeTestPage.tsx:711` uses `Input.Password` for pasted plaintext key.
  - The new page should borrow the loading/key/readiness/safe evidence behavior but replace the step-runner UI with multi-turn chat.
- Request-log evidence:
  - `frontend/src/api/request-logs.ts:13` lists request logs.
  - `frontend/src/api/request-logs.ts:31` loads request-log detail.
  - `frontend/src/api/request-logs.ts:40` loads hit chunks.
  - `frontend/src/types/request-log.ts:34` has `retrieval_evidence`.
  - `frontend/src/components/domain/SourceCitationList.tsx:16` renders safe citation metadata.
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx:256` already shows retrieval evidence without chunk content.
- API key model:
  - `frontend/src/types/api-key.ts:3` normal `ApiKeyVO` includes `key_prefix`, status, and timestamps only.
  - `frontend/src/types/api-key.ts:17` create-only `ApiKeyCreateVO` contains `key`.
  - Do not add plaintext fields to `ApiKeyVO`.
- Proxy path:
  - `frontend/vite.config.ts` proxies both `/api` and `/v1` to localhost backend.
  - `frontend/nginx.conf` proxies `/v1/` with buffering off and long read timeout.
  - No backend admin proxy is needed for the first-phase plan.
- Test patterns:
  - `frontend/src/__tests__/pages/SmokeTestPage.test.tsx` mocks apps/api-keys/openai/request-log clients.
  - `frontend/src/__tests__/pages/SmokeTestPage.test.tsx:171` defines forbidden strings such as `key_hash`.
  - `frontend/src/__tests__/pages/SmokeTestPage.test.tsx:437` asserts forbidden strings are not rendered after smoke execution.
  - `frontend/src/__tests__/AdminShell.test.tsx:153` covers navigation menu items after login.

## Files Likely To Modify

- `frontend/src/components/layout/AdminShell.tsx`: add `test-chat` page key and `nav.test-chat` mapping.
- `frontend/src/App.tsx`: import and render `TestChatPage`.
- `frontend/src/app/i18n/dict.ts`: add Chinese/English nav and page strings; maintain dictionary parity.
- `frontend/src/api/openai.ts`: either add generic `chatCompletions` / `OpenAiApiError` exports or keep smoke exports and add non-duplicative wrappers. Add optional citation header support if the page wants `sangui_citations`.
- `frontend/src/types/openai.ts`: add generic chat message/request/response/citation/error types or aliases. Preserve existing Smoke imports.
- `frontend/src/pages/test-chat/TestChatPage.tsx`: new ChatGPT-like page.
- `frontend/src/__tests__/pages/TestChatPage.test.tsx`: new page tests.
- `frontend/src/__tests__/AdminShell.test.tsx`: update navigation expectations for the new menu item.
- Possibly `frontend/src/components/domain/SourceCitationList.tsx`: only if existing citation type can be reused for direct `sangui_citations`; otherwise avoid changes.
- Avoid backend files by default. Only modify backend if frontend cannot safely call existing `/v1` or existing response types are wrong, which current research does not indicate.

## Risk / Boundary Notes

- Plaintext app key source is the central risk. Use pasted plaintext in page-local state only. Do not persist it and do not derive it from `key_prefix`.
- Existing Smoke page already has a lot of the underlying diagnostics. The maintainable path is reuse/extract/generic wrappers, not copying the whole smoke implementation.
- `model` in current frontend type is required, but backend runtime uses App default model config. The page can send `ignored-by-gateway`; do not create a UI illusion that changing this field changes the upstream model.
- Multi-turn chat must send the full visible message history as OpenAI messages. Keep roles limited to `system | user | assistant` to match backend validation.
- If the page fetches request-log evidence after a send, match by latest success row and question prefix as Smoke currently does, but this is a best-effort diagnostic. Do not block assistant response rendering on request-log lookup.
- Request-log hit chunks expose `summary`; this is a bounded preview but still higher sensitivity than citation metadata. For first phase prefer retrieval evidence/citations metadata over hit chunk summaries.
- `X-Sangui-Return-Citations: true` is available for non-streaming response. Use only if UI renders safe citation metadata and tests forbid raw content/secrets.
- Streaming should stay out of first-phase implementation unless the implementer adds explicit SSE state and tests.

## Required Tests

Frontend targeted:

```powershell
cd frontend
cmd /c npm run test -- TestChatPage
cmd /c npm run test -- AdminShell
```

Frontend required before handoff back to Codex:

```powershell
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Backend only if backend code changes:

```powershell
cd backend
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q -DskipTests compile
```
