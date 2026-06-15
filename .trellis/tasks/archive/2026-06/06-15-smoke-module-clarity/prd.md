# 冒烟测试模块用户意义澄清与信息架构调整

## Task Classification

Complex Task.

理由：本任务以 frontend 信息架构、交互文案、状态映射和诊断语义整理为主，但会触碰 smoke 页面、i18n、现有 request-log/readiness 诊断概念和安全 evidence 展示边界。它不应改 DB、Docker、RAG 核心流程、后端 API 或公共 `/v1` 行为。

## Goal

把 Smoke Test 页面从偏开发者的线性验收脚本 UI，调整成 admin 用户能理解的诊断/验收入口，让用户明确知道：

- 它服务谁：管理后台用户、运维/演示人员、验收人员。
- 它验证什么：环境健康、配置完整性、API 可用性、RAG 可用性、日志可观测性。
- 它输出什么结论：当前应用是否具备对外提供 RAG gateway 能力；若失败，失败大致落在哪个可处理边界。

## Non-Goals

- 不新增后端 API、DTO、VO、数据库字段、迁移、Docker/Compose 配置。
- 不改变 `/v1/chat/completions`、`/api/admin/apps/{appId}/readiness`、request-log 或 hit-chunks API 契约。
- 不保存或展示完整 prompt、完整回答、chunk content、summary 正文、provider raw body、stack trace、API key、key hash、upstream key、embedding、storage path。
- 不把 Smoke Test 做成聊天 playground、工作流平台、低代码诊断平台或 provider 专属调试台。
- 不引入新全局状态或持久化 runtime evidence。

## Requirements

1. Smoke 页面信息架构重组
   - 将页面组织为 admin 可理解的诊断域：环境健康、配置完整性、API 可用性、RAG 可用性、日志可观测性、可选认证负例。
   - 保留现有功能能力：选择应用、粘贴临时 API key、非流式请求、流式请求、request-log 校验、可选 revoked key 校验。
   - 将「Step 1/2/3/4」等偏脚本术语转为业务含义更清楚的标题和结果摘要；可以保留步骤顺序，但必须让标题说明验证意义。

2. Admin 用户语义
   - 文案应回答「这个模块对普通 admin 用户有什么意义」：它验证配置是否完整、API key 是否可用、RAG 是否命中、日志是否能用于排障。
   - 失败态文案必须指向可行动边界，而不是只暴露工程错误。
   - 状态标签应稳定展示 idle/running/pass/fail/skip 和 readiness 状态，且不依赖颜色单独表达。

3. 安全 evidence 边界
   - 允许展示：request id、HTTP status、status、error code、latency/upstream latency、model/provider、messages count、token usage、content length、SSE data line count/chunk count/[DONE] 是否存在、hit chunk ids/count、chunk_id、document_id、knowledge_base_id、source_filename、chunk_index、readiness check status/count/safe metadata。
   - 禁止展示：API key 原文（输入框除当前临时输入外不可回显到证据区）、key hash、Authorization、upstream API key、api_key_encrypted、prompt/messages/full_messages/augmented_prompt、answer text、chunk content、chunk summary text、provider_response_body、stack_trace、embedding、storage_path、内部文件路径、环境变量。
   - 如果页面继续显示 `question_summary`，它必须保持为 backend 已返回的 bounded summary，且不得把用户本次完整回答或完整 prompt 作为 evidence。

4. 状态、空态、失败态补齐
   - 未连接 admin user、未选择 app、无 active key、readiness 加载失败、readiness 非 READY、未粘贴完整 key、非流式未通过导致日志校验不可运行、request-log 无匹配、hit-chunks 为空、revoked-key 校验未启用等状态都应有明确 UI 文案。
   - load/error/retry/disabled 状态不要被静默吞掉。
   - 不新增隐藏 fallback 或 mock success。

5. 复用现有诊断模式
   - 优先复用或对齐 `RequestDiagnosticsPanel` / `requestDiagnostics.ts` 的 boundary 语义：auth、readiness、retrieval、embedding、upstream、streaming、request-log、unknown。
   - 如需要让 Smoke 页面也产出诊断摘要，优先提取或复用现有 helper，避免同一错误分类逻辑在多个页面平行漂移。
   - i18n key 必须保持 zh-CN/en-US parity，并通过 `DictionaryKeyParity` 类型检查。

6. Spec 更新条件
   - 如果只改 Smoke 页面 IA/文案/状态，不必强制更新 spec。
   - 如果实现过程中新增 evidence 分类、改变安全 evidence 规则、改变诊断字段解释或把 smoke 模块定义为新的长期产品入口，必须更新 `.trellis/spec/frontend/quality-guidelines.md` 或 `.trellis/spec/guides/`/项目 spec 中对应的前端 diagnostics/smoke 规则。

## Expected Information Architecture

建议页面结构：

1. 顶部上下文区
   - 当前 app、active API key 参考、临时完整 API key 输入。
   - 明确说明完整 key 只在当前页面内存中使用，不写入 storage，不作为 evidence 展示。

2. 总览结论区
   - 展示当前应用整体诊断阶段和最近一次校验结论。
   - 用 admin 语言概括：环境健康、配置完整性、API 可用性、RAG 可用性、日志可观测性。

3. 配置完整性 / Readiness
   - 使用 readiness checks 显示 app、default model config、default KB、KB status、active API key、embedding config。
   - 非 READY 时给出边界化提示，不阻止用户看到原因。

4. API 可用性
   - 非流式请求验证 gateway 基本可用性。
   - evidence 只展示 id/object/model/finish_reason/content_length/token usage 等 safe metadata。

5. 流式可用性
   - 验证 SSE 通道和 `[DONE]`。
   - evidence 展示 HTTP status、data line count、chunk count、done present。

6. RAG 可用性与日志可观测性
   - request-log 校验应说明它证明「调用被记录、可追踪、命中 chunk ID 可观测」。
   - evidence 展示 request_id、model/provider、latency、messages_count、hit_chunk_ids/count、hit chunk metadata。

7. 可选认证负例
   - revoked key 校验用于证明旧 key 不再可用。
   - 默认可跳过；启用时只验证 401 invalid_api_key，不展示 key 原文。

## API / Command / Payload Contract

本任务预期不改变接口，只使用现有契约：

- `GET /api/admin/apps`
- `GET /api/admin/apps/{appId}/readiness`
- `GET /api/admin/apps/{appId}/api-keys`
- `POST /v1/chat/completions` with `stream=false`
- `POST /v1/chat/completions` with `stream=true`
- `GET /api/admin/apps/{appId}/request-logs`
- `GET /api/admin/apps/{appId}/request-logs/{requestId}`
- `GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks`

Payload/field rules:

- Do not add request fields.
- Do not add response fields.
- Do not reinterpret backend enum values at the contract layer.
- Keep frontend types aligned with current DTO/VO shapes in `frontend/src/types/app.ts`, `frontend/src/types/openai.ts`, and `frontend/src/types/request-log.ts`.

## Validation / Error Matrix

| Scenario | Expected UI behavior | Assertion point |
|---|---|---|
| No admin user connected | App shell login remains the entry boundary | Existing shell behavior; smoke page not reachable until connected |
| No app selected | Show clear empty/disabled state for readiness/key/smoke actions | Buttons disabled, no fake checks |
| App selected but readiness loading | Show loading state for configuration completeness | No stale readiness conclusions after app switch |
| Readiness API fails | Show visible warning/error with retry or reload path | Does not block other visible page state |
| Readiness not READY | Show failing readiness check and boundary category | No hidden downgrade to success |
| No active API key exists | Show API key availability problem as admin action item | Do not invent or expose key plaintext |
| Full key not pasted | API smoke buttons disabled with explanation | Key remains in memory only |
| Non-streaming success | Show safe metadata and content length only | No answer text rendered |
| Non-streaming failure | Show HTTP status/error code/message and diagnostic boundary | No provider raw body or stack trace |
| Streaming success | Show HTTP status, data lines, chunk count, `[DONE]` | No streamed content rendered |
| Streaming missing `[DONE]` | Mark streaming boundary failed | Failure visible |
| Request log validation before non-streaming pass | Disabled with reason | No stale request-log pass |
| No matching request log | Request-log observability failure visible | No silent pass |
| Hit chunk IDs empty on success | Classify as RAG/retrieval availability issue, not full success | No chunk content shown |
| Hit chunks endpoint returns summaries | Do not render summary text unless explicitly retained as bounded existing field; prefer metadata only for smoke evidence | No chunk content/summary evidence leak |
| Revoked-key check disabled | Mark optional/skip clearly | No false failure |
| Revoked key returns 401 invalid_api_key | Auth negative case passes | Key plaintext not echoed |
| i18n key added in one locale only | Typecheck fails through dictionary parity | `cmd /c npm run typecheck` |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | App readiness READY, active key pasted, non-streaming and streaming checks pass, request-log validation finds matching success row, hit chunk IDs/count and safe chunk metadata are visible, revoked-key negative check passes if enabled. Page conclusion is understandable as "this app is ready and observable". |
| Base | App/readiness exists but one prerequisite is missing or the user has not pasted a full key. Page remains useful: it explains the missing setup step and keeps unsafe checks disabled. |
| Bad | Page shows a green/pass conclusion while readiness is non-ready, logs are stale/missing, hit chunk evidence is absent for RAG validation, or any raw answer/prompt/chunk/API key/provider body/stack trace is displayed. |

## Files Likely To Modify

- `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - Primary IA/layout/state/message rewrite.
- `frontend/src/app/i18n/dict.ts`
  - Rename/add smoke diagnostics strings in zh-CN and en-US with parity.
- `frontend/src/components/domain/requestDiagnostics.ts`
  - Optional: reuse/extract boundary classification so Smoke and request-log detail stay aligned.
- `frontend/src/components/domain/RequestDiagnosticsPanel.tsx`
  - Optional: make panel reusable enough for smoke context without weakening request-log detail.
- `frontend/src/types/request-log.ts`
  - Only if adding frontend-only diagnostic result types; do not add backend fields.
- `frontend/src/types/app.ts`
  - Only if current readiness display needs narrower frontend helper typing; do not alter API contract.
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`
  - Usually not required. Only touch if page-level smoke visual coverage is deliberately added.
- `.trellis/spec/frontend/quality-guidelines.md` or `.trellis/spec/sangui-rag-gateway.md`
  - Only if evidence/smoke product rule changes beyond IA copy.

## Explicitly Forbidden Modification Scope

- No backend Java files.
- No Flyway migration.
- No DB schema/entity/mapper/service changes.
- No Docker/Compose/env changes.
- No request-log persistence changes.
- No RAG retrieval SQL, prompt builder, chunking, embedding, model config, API key lifecycle, or auth filter changes.
- No frontend localStorage/sessionStorage persistence for API keys, request logs, prompts, answers, chunks, or runtime evidence.
- No new package dependency unless explicitly approved.

## Required Tests

Run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual
```

Run from repo root:

```bash
git diff --check
```

Optional only if spec files are changed:

```bash
python ./.trellis/scripts/task.py validate 06-15-smoke-module-clarity
```

Backend tests are not required if implementation stays frontend-only. If any backend file or API contract changes despite the planned boundary, stop and re-plan before coding.

## Planning Self-Check

- Acceptance criteria are defined above.
- Forbidden scope is explicit.
- Expected modification files are listed.
- Required validation commands are listed.
- Specific frontend/security/guides/backend logging guideline files were read before handoff.
- No user clarification is currently required; scope is constrained enough to execute.
- API/DB/frontend DTO fields remain unchanged and aligned to existing types.

