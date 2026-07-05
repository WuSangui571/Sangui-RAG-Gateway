# 测试对话

## Goal

在 Admin Console 中新增一个接近 ChatGPT 的“测试对话”页面，让管理员能用某个 App 的 app API key 直接验证：

- 应用是否可用。
- app key 是否有效。
- RAG 是否生效或至少能看到安全的 RAG/request-log 证据。
- non-streaming `/v1/chat/completions` 是否正常。
- streaming 是否作为第二阶段明确接入，或留作后续任务。

本任务的第一阶段以 frontend-first 为主，优先完成 non-streaming 对话测试页和安全元数据展示。除非代码研究证明现有前端代理或 API contract 无法支持，否则不新增后端接口、不改数据库、不改 gateway/RAG 行为。

## Task Classification

Complex Task。

原因：页面本身是前端体验任务，但会跨到 app/API key 安全边界、OpenAI-compatible gateway 调用、RAG/request-log 安全可观测字段、错误展示、多轮消息状态和 streaming 行为切分，不能按普通 CRUD 页面处理。

## Scope

### In Scope

- 新增左侧菜单“测试对话”和对应前端路由。
- 新增测试对话页面，加载可用 App 列表并选择目标 App。
- 加载所选 App 下可用 API key 元数据列表，用于选择/确认测试目标 key。
- 提供安全的 plaintext key 输入/粘贴模式：
  - Admin API 正常 key 列表只展示 `key_prefix` / masked metadata，不能用于调用 gateway。
  - 页面提供 password/secret 输入框让用户粘贴完整 `sk-sangui-*` app key。
  - 明文 key 只能存在于当前页面内存状态，不能写入 localStorage、sessionStorage、URL、全局 store、日志或测试快照。
  - 选择 key 元数据仅用于提示目标 key，不得假设 masked/prefix key 可认证。
- 对话 UI：
  - ChatGPT-like 消息流。
  - 支持多轮 message history。
  - 支持发送中状态、禁用重复发送、错误展示、清空会话。
  - 显示请求耗时、HTTP/status/error code、基础 token usage（如响应提供）。
- Gateway 调用：
  - 使用用户粘贴的 plaintext app key 调用 `POST /v1/chat/completions`。
  - 第一阶段只做 `stream=false` non-streaming。
  - 请求从前端 typed API client 发出，页面/组件不得直接调用 raw fetch/axios。
  - 响应保持 OpenAI-compatible shape，不包 admin `ApiResponse`。
- RAG / observability feedback：
  - 默认展示 answer 和安全运行元数据。
  - 如果响应包含 opt-in safe citations（例如 `sangui_citations`），可以展示 citation metadata。
  - 如果需要 request-log evidence，优先通过已有 request-log admin API 读取安全字段；不直接展示 chunk content、full prompt、messages、provider body、key/hash。
  - request-log evidence 可以作为“匹配最近请求”的后续增强；第一阶段不可为了展示 RAG 反馈而新增高敏接口。
- i18n：
  - 新页面文本使用现有 typed dictionary / `useI18n()` 模式。
  - 中文和英文 key 必须保持 parity。
- 测试：
  - 前端类型检查、组件/页面测试、必要 API mock。
  - 若最终新增后端 proxy/API，再补 backend tests；默认不新增。

### Out of Scope / Forbidden

- 不新增数据库表或迁移。
- 不改变 app API key 的创建、hash、prefix、状态、限流、配额逻辑。
- 不让后端或前端从 masked/prefix key 反推出明文 key。
- 不返回、展示、记录或持久化 `key_hash`、完整 app key、admin JWT、upstream key、`api_key_encrypted`。
- 不改变 `/v1/chat/completions` 的 gateway runtime 行为、RAG retrieval SQL、prompt builder、no-hit policy 或 request-log 持久化规则。
- 不把 Admin Console 改造成通用聊天产品或 agent/workflow 平台；该页是诊断/验证工具。
- 不默认展示 chunk content、full messages、full augmented prompt、raw SSE、provider raw body、output preview。
- 不在第一阶段强行实现 streaming；如实现，必须独立列出 SSE 状态、取消、错误和测试边界。
- 不新增 silent fallback、mock success path、隐藏降级或“key 不可用时假装成功”的逻辑。

## User Workflow

1. 管理员登录 Admin Console。
2. 左侧菜单进入“测试对话”。
3. 选择 App。
4. 页面加载该 App 下 API key metadata，用户可选择一个 key 行确认目标。
5. 用户粘贴该 key 的完整 plaintext `sk-sangui-*` 值。
6. 用户输入问题并发送。
7. 页面以 `Authorization: Bearer <plaintext key>` 调用 `POST /v1/chat/completions`，请求体包含当前消息历史与 `stream: false`。
8. 页面展示 assistant 回复、耗时、错误码/状态、usage 和安全 RAG/request-log 元数据。
9. 用户可继续追问或清空会话。

## API / Command / Payload Contracts

### Admin App List

Expected existing client:

```http
GET /api/admin/apps
Authorization: Bearer <admin-jwt>
```

Use only safe App metadata: `id`, `name`, `status`, default binding/readiness display fields if already available. Do not add app API key data to this response.

### Admin App API Key List

Expected existing client:

```http
GET /api/admin/apps/{appId}/api-keys
Authorization: Bearer <admin-jwt>
```

Allowed key metadata:

```text
id
name
key_prefix
status
expires_at
last_used_at
created_at
updated_at
```

Forbidden fields:

```text
key
plaintextKey
key_hash
api_key
authorization
```

Normal list/detail APIs must never return plaintext. The page must explain through UI state that the pasted plaintext key is required for gateway testing.

### Gateway Chat Completion Test

Request:

```http
POST /v1/chat/completions
Authorization: Bearer <plaintext-app-api-key>
Content-Type: application/json
```

Initial request body:

```json
{
  "model": "ignored-by-gateway",
  "messages": [
    { "role": "user", "content": "..." }
  ],
  "stream": false
}
```

Existing backend DTO accepts `model`, but runtime gateway uses the App default chat model resolved from the authenticated app. Existing frontend smoke types require `model: string` and the smoke page sends `ignored-by-gateway`. The test-chat page should follow that current contract unless a separate backend/API task changes the DTO/type relationship.

Expected success response:

```json
{
  "id": "...",
  "object": "chat.completion",
  "created": 0,
  "model": "...",
  "choices": [
    {
      "index": 0,
      "message": { "role": "assistant", "content": "..." },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "completion_tokens": 0,
    "total_tokens": 0
  }
}
```

Optional safe citation extension, if already present:

```text
sangui_citations
```

Only render bounded metadata, not chunk content.

Expected error response:

```json
{
  "error": {
    "message": "Specific safe message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

The UI should display HTTP status, `error.code`, and safe `error.message`. It must not expose request body internals or pasted key.

## Validation / Error Matrix

| Scenario | Expected UI behavior | Assertion point |
|---|---|---|
| User not logged in | Existing AdminShell unauthenticated guard applies | AdminShell/page test |
| App list loading | Show loading state, disable send | Page test |
| App list empty | Show explicit empty state and do not show chat as ready | Page test |
| App list load fails | Show retryable error state, no gateway call | Page test |
| App selected | Load key metadata for selected App and reset selected key/plaintext/chat status as appropriate | Page test |
| Key list empty | Show no-key state; user may still paste a known plaintext key only if product decision allows, otherwise disable send | Page test |
| Key metadata selected but plaintext missing | Send disabled with visible validation message | Page test |
| Plaintext key blank or not `sk-sangui-*` | Client-side validation fails before network call | Page test |
| Message blank | Send disabled or inline validation; no network call | Page test |
| Valid plaintext key, ready app/KB/model | Non-streaming chat succeeds, assistant message appended, latency/usage shown | API mock test |
| Invalid/revoked/expired key | Show HTTP 401 and `invalid_api_key`; do not clear conversation unless user clears | API mock test |
| App missing model config | Show 409 `model_config_not_ready` safely | API mock test |
| KB not ready/no bound KB | Show 409 `knowledge_base_not_ready` safely | API mock test |
| Embedding failure | Show 502 `embedding_failed` safely | API mock test |
| Upstream timeout | Show 504 `upstream_timeout` safely | API mock test |
| Upstream error | Show 502 `upstream_error` safely | API mock test |
| Rate limit exceeded | Show 429 `rate_limit_exceeded` safely | API mock test |
| Network/proxy failure | Show visible transport error; no fake assistant response | API mock test |
| Safe citations present | Render citation IDs/source metadata only | Page test |
| Forbidden fields returned by a mock by mistake | UI must not render `key_hash`, full keys, prompts, chunk content, provider body, stack trace | Safe rendering test |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Admin selects enabled App, selects/identifies an active key, pastes full plaintext key, sends a multi-turn question, receives OpenAI-compatible non-streaming response, sees assistant reply, latency, usage, error/status area, and safe RAG/citation metadata if available. No secrets are persisted or rendered. |
| Base | Admin can load Apps and key metadata but does not have plaintext key available. The page clearly shows that masked/prefix keys cannot be used for gateway auth and keeps send disabled until a plaintext key is pasted. |
| Bad | The page tries to call gateway with `key_prefix`/masked key, stores plaintext key in localStorage/sessionStorage/global store, logs key to console, renders `key_hash`, exposes chunk content/full prompt/provider body, fabricates RAG success without evidence, or silently treats gateway errors as successful answers. |

## Acceptance Criteria

- [ ] New left navigation item and route for “测试对话” exists and is protected by existing admin auth shell.
- [ ] Page loads App list through existing typed API client pattern.
- [ ] Page loads selected App key metadata through existing typed API client pattern.
- [ ] Page provides session-memory plaintext key input and never stores it outside component/page state.
- [ ] Page sends non-streaming `/v1/chat/completions` through a typed gateway API client with `Authorization: Bearer <plaintext key>`.
- [ ] Page supports multi-turn messages, sending state, clear conversation, retry/error visibility, and empty/loading states.
- [ ] Page displays safe status metadata: HTTP status, gateway error code, latency, usage, and safe citation/request-log metadata if available.
- [ ] UI does not render forbidden fields: complete app key, key hash, admin JWT, upstream key, encrypted upstream key, full prompt/messages, chunk content, provider raw body, raw SSE, stack trace, storage path.
- [ ] Frontend tests cover happy path, missing plaintext key, invalid key error, upstream/gateway error, loading/empty states, safe rendering, and message history.
- [ ] `cmd /c npm run lint`, `cmd /c npm run test`, `cmd /c npm run typecheck`, and `cmd /c npm run build` pass from `frontend/`.
- [ ] If backend code is changed despite the frontend-first plan, targeted backend tests are added and run for the affected API/auth/gateway/request-log seam.

## Implementation Plan

1. Inspect existing AdminShell/router/menu/page patterns and i18n dictionary.
2. Inspect existing typed clients for apps, API keys, request logs, and gateway/smoke helpers.
3. Reuse or safely generalize existing `frontend/src/types/openai.ts` and `frontend/src/api/openai.ts`; avoid duplicating `SmokeApiError` / chat response parsing under a second implementation.
4. Add a typed gateway test/chat client for `POST /v1/chat/completions` using relative `/v1` path and caller-supplied app key.
5. Add `TestChatPage` under the existing page organization.
6. Wire route/menu/i18n.
7. Add component/page tests with mocked API clients; assert secret-safe rendering.
8. Run frontend validation commands.

## Technical Notes

- Hotfix vs structural: structural frontend feature. The invariant is that app/key/gateway testing must reuse existing typed API/client patterns and keep secret handling in one explicit UI boundary.
- The plaintext app key is not recoverable from the backend after creation. This is expected; do not add plaintext retrieval.
- If the current frontend dev server lacks a `/v1` proxy but production Nginx already has one, prefer adding or reusing a frontend dev proxy/client path rather than a backend admin proxy. Do not add a backend proxy unless needed and explicitly tested.
- If request-log evidence is added, use existing admin request-log APIs and safe fields only. Do not fetch output preview by default.
- Streaming can be a second phase after non-streaming lands. It must cover SSE `[DONE]`, post-start error, cancellation/clear behavior, and `STREAMING_UNSUPPORTED` output-capture metadata if request logs are used.

## Required Tests

Frontend default required:

```powershell
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Targeted frontend tests should include:

```text
AdminShell navigation exposes Test Chat page
TestChatPage loads apps and key metadata
TestChatPage blocks send without app/plaintext key/message
TestChatPage appends user and assistant messages on non-streaming success
TestChatPage displays OpenAI-compatible errors
TestChatPage clears conversation without persisting key
TestChatPage does not render forbidden secret/content fields
```

Backend tests only if backend changes occur:

```powershell
cd backend
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyRateLimitServiceTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q -DskipTests compile
```

## Open Questions

- No blocking product question for first implementation phase. The safe assumption is: key metadata selection is for operator context only; actual gateway auth uses a pasted plaintext key.
- Streaming remains intentionally out of first-phase acceptance unless the implementer explicitly completes the extra SSE UI/test boundary.
