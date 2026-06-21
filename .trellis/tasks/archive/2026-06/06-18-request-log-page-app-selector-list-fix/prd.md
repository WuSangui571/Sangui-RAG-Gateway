# 请求日志页面应用选择与日志不显示问题

## Goal

修复 Admin 请求日志页面的可用性问题：未选择应用时提供与 API 密钥页一致的应用下拉选择；确保基础 request-log metadata 列表不受 output capture 开关影响；定位并修复“无论输出捕获是否开启都不显示日志”的真实原因。

本轮 Codex 只负责 PRD、计划、Trellis context、spec/code research 和测试计划，不修改业务实现文件。DeepSeek 端负责后续编码。

## Scope Classification

类型：Simple Task，中等复杂度，按 fullstack task workflow 准备。

理由：
- 目标明确，核心页面和 API seam 集中在 request-log list page、typed API helper、app selector/app list pattern、request-log admin API。
- 但它触及前端状态、Admin API 查询、request-log 安全暴露语义和 output preview 边界，必须按跨层契约验证。
- 当前不应扩大为 output capture 策略重做、DB schema/migration、完整 observability redesign 或 smoke page IA 简化。

## User-Facing Requirements

- 请求日志页面未选应用时，展示与 API 密钥页一致的应用下拉选择模式，避免手动输入应用 ID/名称。
- 用户选择应用后，页面加载该应用的 request-log metadata 列表。
- 如果没有日志，显示明确空状态，说明是当前应用暂无请求日志，而不是被 output capture 开关隐藏。
- 加载失败时展示可读错误态和重试入口。
- 基础日志列表必须始终展示安全 metadata：request_id、status、model、provider_name、latency_ms、usage、messages_count、question_summary、hit_chunk_ids、created_at 等现有安全字段。
- output capture 开关只影响 output preview 的捕获、可用状态和显式访问；不得影响基础 request-log metadata 列表展示。
- 列表、详情和空状态不得渲染 prompt/messages/full answer/output_preview/chunk_content/API keys/provider bodies/storage_path/stack traces 等敏感字段。

## API / Command / Payload Contract

### Existing Admin App List

用于应用下拉选择，复用现有 typed API client。

```http
GET /api/admin/apps
Authorization: Bearer <admin-jwt>
```

前端路径通常为：

```text
frontend/src/api/apps.ts
frontend/src/types/app.ts
```

要求：
- 不新增 app 选择专用 API。
- 不信任或合成 `X-Admin-User-Id`。
- Admin identity 继续由 `frontend/src/api/http.ts` 统一注入 `Authorization: Bearer <admin-jwt>`。

### Existing Request Log List

```http
GET /api/admin/apps/{appId}/request-logs?page=<page>&page_size=<pageSize>&status=<status?>&error_code=<errorCode?>&start_time=<start?>&end_time=<end?>
Authorization: Bearer <admin-jwt>
```

Response:

```text
ApiResponse<ApiRequestLogPageVO<ApiRequestLogVO>>
```

列表 VO 只允许 safe metadata，不允许 `output_preview`。

### Existing Request Log Detail

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
Authorization: Bearer <admin-jwt>
```

Response:

```text
ApiResponse<ApiRequestLogDetailVO>
```

正常 detail 只允许 output metadata：

```text
output_capture_status
completion_length
output_preview_available
output_preview_truncated
output_redacted
output_retention_expires_at
```

不得返回或渲染 `output_preview`。

### Existing Output Preview Explicit Access

```http
POST /api/admin/apps/{appId}/request-logs/{requestId}/output-preview/access
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "confirm_access": true,
  "reason": "optional bounded reason"
}
```

本任务不要求重做该端点。只有当现有 UI 已经有 preview access 流程且本次修复影响 detail drawer 时，才允许做最小适配。

### Commands For Validation

Frontend:

```powershell
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Backend, only if backend request-log/controller/service code changes:

```powershell
cd backend
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest,OutputCapturePolicyTest" test
mvn -q -DskipTests compile
```

Repository:

```powershell
git diff --check
```

Browser smoke, if frontend behavior changes:

```text
Login -> Request Logs -> no app selected state -> select app -> list/empty/error state.
```

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Admin user opens request-log page with no selected app | Page shows app dropdown/select pattern consistent with API key page; no manual app ID input is required | Frontend component test / browser smoke |
| App list loading | Select disabled/loading state is visible; no request-log list fetch runs with invalid app ID | Frontend test with mocked `listApps` and `listRequestLogs` |
| App list error | Visible error/retry state; no silent empty table masking the error | Frontend test |
| No apps exist | Clear empty state explaining no application is available | Frontend test |
| App selected and request logs exist | Request-log metadata rows render | Frontend test; optional backend mocked API fixture |
| App selected and no logs exist | Empty state says current app has no request logs | Frontend test |
| Output capture disabled globally/app-level | Basic request-log metadata list still loads and renders | Frontend test; backend test only if query was gated |
| Output capture enabled but no preview captured | Basic metadata list still loads; detail shows output metadata status only | Frontend/backend test depending on changed code |
| Invalid `page`/`page_size`/`status`/time range | Existing backend returns `400 INVALID_REQUEST`; frontend displays safe error | Backend existing tests; frontend error test if touched |
| Cross-user app ID | Backend returns `403 FORBIDDEN`; frontend does not fabricate empty success | Backend existing tests; frontend error handling |
| Missing app ID in request-log API call | Frontend should not call list API; if backend touched, preserve safe validation | Frontend test |
| Forbidden fields present in fixture | UI must not render prompt/messages/output_preview/chunk_content/API keys/provider bodies/storage paths/stack traces | Frontend safe-rendering test |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Logged-in admin opens Request Logs, sees an app dropdown, selects an app, and sees safe request-log metadata rows even when output capture is disabled. Detail drawer may show output capture metadata but not preview text unless explicit access is confirmed through the existing preview endpoint. |
| Base | App has no logs or output capture is disabled: page still behaves normally with a truthful empty state or metadata list; output preview action/status reflects disabled/unavailable preview only. |
| Bad | Page requires manual app ID, sends invalid appId, treats output capture disabled as "no logs", hides all logs because `output_capture_status=DISABLED`, silently swallows API errors, or renders raw output/prompt/messages/chunk content by default. |

## Expected Investigation Path

1. Compare app selection pattern in API key page with current request-log page.
2. Trace request-log page selected app state and list fetch trigger.
3. Trace typed API helper path and query parameter mapping for request logs.
4. Trace frontend request-log types against backend VO snake_case fields.
5. Inspect filters/default status values to ensure list is not unintentionally filtering all rows.
6. Inspect backend request-log admin controller/service only if frontend query and mapping are correct.
7. Verify output capture status is used only for preview UI state, not for list inclusion.

## Likely Files To Modify

Frontend likely:

```text
frontend/src/pages/request-logs/RequestLogListPage.tsx
frontend/src/api/request-logs.ts
frontend/src/types/request-log.ts
frontend/src/__tests__/pages/RequestLogListPage.test.tsx
frontend/src/app/i18n/dict.ts
```

Pattern/reference files likely:

```text
frontend/src/pages/api-keys/ApiKeyManagementPage.tsx
frontend/src/api/apps.ts
frontend/src/types/app.ts
```

Backend only if investigation proves a real backend bug:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
```

## Explicit Non-Goals / Forbidden Scope

- Do not add DB migrations or change request-log schema unless current code proves schema mismatch is the root cause and user approves.
- Do not change gateway `/v1/chat/completions` request-log persistence semantics unless backend investigation proves logs are never persisted.
- Do not change output capture policy: effective preview capture remains `global enabled AND app request_log_output_capture_enabled`.
- Do not make output preview globally visible in list/detail.
- Do not expose raw prompts, messages, full answers, output_preview, chunk content, embeddings, API keys, provider raw bodies, stack traces, storage_path, raw SSE, or env values.
- Do not add a separate app selector global store or persist selected app in localStorage unless existing API key page already has that exact pattern and it is safe.
- Do not broaden into smoke page simplification, diagnostics redesign, request-log analytics, or provider/runtime fallback behavior.

## Acceptance Criteria

- [ ] Request Logs page has app dropdown/select behavior aligned with API key page.
- [ ] No request-log list fetch is made until a valid app is selected.
- [ ] Selecting an app fetches and renders safe request-log metadata rows when rows exist.
- [ ] Empty logs render a truthful empty state and are not conflated with output capture disabled.
- [ ] Output capture disabled/enabled status only affects output preview metadata/action, not basic list visibility.
- [ ] Frontend request-log types align with backend snake_case VO fields.
- [ ] Safe-rendering assertions prove forbidden fields are absent from the DOM.
- [ ] Existing Admin JWT path remains the only admin identity source.
- [ ] No business code outside the listed likely files is changed without root-cause justification.
- [ ] Required frontend validation commands pass.
- [ ] Backend targeted tests pass if backend request-log/admin code is changed.

## Required Tests And Assertion Points

Frontend component/unit tests:
- RequestLogListPage renders no-app app selector.
- Selecting app triggers `listRequestLogs(appId, query)` with numeric app ID.
- Disabled output capture row/status still appears in the list.
- Empty list state differs from API error state.
- Safe-rendering test asserts forbidden sensitive strings are not present.
- Existing unauthenticated guard remains intact.

Backend tests, only if backend changed:
- `ApiRequestLogAdminControllerTest`: list endpoint returns rows independent of output capture status.
- `ApiRequestLogServiceTest`: dynamic query filters do not include `output_capture_status` unless explicitly added for a future approved filter.
- Existing output preview access tests still pass.

Smoke:
- Browser smoke after implementation should keep the page open long enough to visually confirm selector, loading, empty/list, and detail metadata behavior.

## Planning Self-Check

- 验收标准：已明确。
- 禁止修改范围：已明确。
- 预计修改文件：已列出，backend 仅为条件触发。
- 必跑测试：已列出。
- 已读取具体 guideline：frontend/backend/gateway/security/guides 均已读取，非仅 index。
- 需求不清：暂无；实现前先按代码证据定位根因。
- API / DB / frontend types / DTO 字段：现有 API 与 DTO/VO 已列出；默认不新增字段，不做 DB 变更。
