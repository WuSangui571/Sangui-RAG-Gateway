# DeepSeek 执行交接说明

## Task

- Task path: `.trellis/tasks/07-03-test-chat-console`
- PRD: `.trellis/tasks/07-03-test-chat-console/prd.md`
- Research: `.trellis/tasks/07-03-test-chat-console/research.md`
- Branch: `feature/test-chat-console`
- Task type: Complex / frontend-first with gateway, API-key, RAG observability boundaries

## Must Read First

Trellis context files:

- `.trellis/tasks/07-03-test-chat-console/implement.jsonl`
- `.trellis/tasks/07-03-test-chat-console/check.jsonl`

Direct files:

- `.trellis/tasks/07-03-test-chat-console/prd.md`
- `.trellis/tasks/07-03-test-chat-console/research.md`

Specs injected into context:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/rag/retrieval-quality.md`
- `.trellis/spec/rag/prompt-context-policy.md`
- `.trellis/spec/security/rag-security.md`

## Implementation Summary

Build a new Admin Console page named “测试对话” / “Test Chat” that behaves like a multi-turn ChatGPT-style diagnostic chat for a selected App. First phase is non-streaming only.

Required workflow:

1. Add navigation and page key.
2. Load Apps.
3. Load selected App active API key metadata.
4. Let the user paste a full plaintext `sk-sangui-*` app key in a password input.
5. Maintain multi-turn messages in page-local state.
6. Send non-streaming `POST /v1/chat/completions` with the pasted app key.
7. Render assistant response, latency, usage, HTTP/status/error code, and safe RAG/citation/request-log metadata where available.
8. Support sending state, clear conversation, visible errors, loading/empty states, and tests.

## Existing Code To Reuse

- `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - App loading, key metadata loading, plaintext key input, readiness, gateway smoke call, request-log validation, safe evidence display.
- `frontend/src/api/openai.ts`
  - `/v1/chat/completions` client and OpenAI-compatible error parser.
  - Prefer generalizing/adding generic exports instead of copying another raw `fetch`.
- `frontend/src/types/openai.ts`
  - Existing request/response types. Preserve Smoke imports while adding generic aliases/types if needed.
- `frontend/src/api/apps.ts`
  - `listApps`, `getAppReadiness`.
- `frontend/src/api/api-keys.ts`
  - `listApiKeys`.
- `frontend/src/api/request-logs.ts`
  - `listRequestLogs`, `getRequestLogDetail`, `getHitChunks`.
- `frontend/src/components/domain/SourceCitationList.tsx`
  - Safe citation metadata renderer.
- `frontend/src/__tests__/pages/SmokeTestPage.test.tsx`
  - API mock and forbidden-field test patterns.
- `frontend/src/__tests__/AdminShell.test.tsx`
  - Navigation test patterns.

## Expected Files To Modify

- `frontend/src/components/layout/AdminShell.tsx`
- `frontend/src/App.tsx`
- `frontend/src/app/i18n/dict.ts`
- `frontend/src/api/openai.ts`
- `frontend/src/types/openai.ts`
- `frontend/src/pages/test-chat/TestChatPage.tsx`
- `frontend/src/__tests__/pages/TestChatPage.test.tsx`
- `frontend/src/__tests__/AdminShell.test.tsx`

Only if needed:

- `frontend/src/components/domain/SourceCitationList.tsx`
- `frontend/src/types/request-log.ts`

Do not modify backend by default.

## Hard Boundaries

- Do not store pasted plaintext app key in localStorage, sessionStorage, URL state, global store, logs, or test snapshots.
- Do not call gateway with `key_prefix` or masked key.
- Do not add plaintext key recovery API.
- Do not add `key_hash` or plaintext fields to normal `ApiKeyVO`.
- Do not change API key hashing, status, rate limits, gateway auth, retrieval SQL, prompt builder, no-hit policy, request-log persistence, database migrations, Docker, or deployment config.
- Do not render complete app key, key hash, admin JWT, upstream key, encrypted upstream key, full prompt/messages, chunk content, provider raw body, raw SSE, stack trace, storage path, or output preview.
- Do not turn this into a general chat product, agent UI, workflow platform, or marketing page.
- Streaming is not required in phase 1. If implemented, it must have explicit SSE state, `[DONE]`, errors, cancellation/clear behavior, and tests.

## API Notes

- `/v1` proxy already exists in both `frontend/vite.config.ts` and `frontend/nginx.conf`; no backend proxy is needed.
- Existing Smoke client sends:

```ts
{
  model: 'ignored-by-gateway',
  messages: [{ role: 'user', content }],
  stream: false,
}
```

- Backend DTO accepts `model`, but `ChatCompletionGatewayService` uses the App default model config for upstream calls. Do not create a model selector unless a separate task defines that behavior.
- Non-streaming citations can be requested through `X-Sangui-Return-Citations: true`; render metadata only if used.

## Required Tests

Targeted while coding:

```powershell
cd frontend
cmd /c npm run test -- TestChatPage
cmd /c npm run test -- AdminShell
```

Required before returning to Codex:

```powershell
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Backend only if backend files are changed:

```powershell
cd backend
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q -DskipTests compile
```

## Acceptance Checklist

- [ ] Nav/menu shows “测试对话” and “Test Chat”.
- [ ] Test chat page loads apps and app key metadata through typed API clients.
- [ ] Plaintext app key input is password-style and page-local only.
- [ ] Send is blocked when app, plaintext key, or user message is missing.
- [ ] Multi-turn visible history is sent as `messages`.
- [ ] Non-streaming response appends assistant message and displays safe metadata.
- [ ] Gateway/OpenAI errors display HTTP status and `error.code`.
- [ ] Clear conversation resets messages and result state without persisting key.
- [ ] Forbidden strings/fields are not rendered.
- [ ] Full required frontend validation passes.
