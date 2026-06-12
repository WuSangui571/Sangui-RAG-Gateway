# 模型配置能力收敛与真实检查优化

## 背景

上一轮已完成模型配置能力拆分与检查按钮基础能力：后端引入 `ModelConfigCapability`，前端模型配置页支持 `CHAT` / `EMBEDDING` / `CHAT_EMBEDDING`，检查接口可对 chat 与 embedding 做真实上游探测。随后 API Key detect 功能被撤回，真实可用性检查被明确收敛到模型配置页。

当前问题是 `CHAT_EMBEDDING` 已成为历史兼容产物，但仍暴露在新建/编辑 UX、DTO/type、service 校验、列表过滤、app 绑定、KB embedding 自动填充和 spec 中。它会让“一个模型配置到底代表 chat 还是 embedding”变得含糊，也会干扰后续 app binding、知识库 embedding 配置、可用性诊断和检查按钮语义。

## 任务分类

Complex Task。原因：涉及 DB 数据修正、后端 enum/DTO/VO/service/controller/mapper、前端类型与页面 UX、真实上游检查、安全日志、spec 更新和较大的测试矩阵。不要按局部热修补处理。

## 目标

将模型配置能力模型收敛为两个可新建/可编辑能力：

- `CHAT`
- `EMBEDDING`

`CHAT_EMBEDDING` 仅作为 legacy 读取兼容输入存在，不再允许通过 Admin API/前端创建或更新，不再在 UI 下拉/单选中暴露，不再作为列表/绑定/知识库选择对用户展示的第三类语义。

同时优化模型配置检查逻辑和展示，使检查结果清晰表达：当前 `api_key`、`base_url`、`model` 是否真实可用；`CHAT` 查 chat completions endpoint，`EMBEDDING` 查 embeddings endpoint，两类均通过真实请求判断。

## 非目标 / 禁止范围

- 不实现 API Key detect，不恢复 API Key 检测按钮或端点。
- 不新增 provider catalog、provider routing、fallback、retry、circuit breaker 或多 provider 编排。
- 不改变公开 `/v1/chat/completions` OpenAI-compatible payload 语义。
- 不把 Sangui-RAG-Gateway 扩展成 workflow/agent/low-code 平台。
- 不暴露 upstream key、encrypted key、provider raw body、prompt、answer、embedding vector、chunk content、Authorization header 或 stack trace。
- 不做大规模 UI 重设计；只调整模型配置页与受影响选择器的能力展示/检查结果。
- 不在本轮 Codex 规划阶段修改业务实现文件。DeepSeek 编码阶段也应只改本 PRD 列出的相关范围。

## 当前项目状态摘要

- 当前分支：`feature/model-config-capability-cleanup`。
- 工作区：创建任务前为 clean。
- 无 active task。
- 最新 journal 记录显示：
  - 模型能力拆分已合入，当前代码存在 `CHAT` / `EMBEDDING` / `CHAT_EMBEDDING`。
  - 模型配置页已有 capability selector 和 saved/unsaved check。
  - API Key detect 已被撤回，后续真实连通性检查应落在模型配置页。
  - journal 明确后续模型配置清理应在新任务中移除 `CHAT_EMBEDDING` 创建并迁移/规范化历史混合配置。

## 需求

### 后端能力模型

- `ModelConfigCapability` 的可新建/可更新值只允许 `CHAT` 和 `EMBEDDING`。
- `CHAT_EMBEDDING` 必须从 Admin create/update/check 输入校验中禁止。
- 保留必要 legacy 读取兼容：
  - 老库中仍存在 `capability='CHAT_EMBEDDING'` 时，服务启动不能失败。
  - legacy 行读取后应按规范化后的语义参与列表、绑定、readiness、embedding lookup。
  - 推荐在 DB migration 中先把符合条件的 legacy 行规范化，代码再保留防御性读取兼容。
- `CHAT` 配置：
  - `chat_model` 必填。
  - `embedding_model` / `embedding_dimension` 必须为空。
  - 允许作为 app default model config。
  - 检查只调用 chat completions endpoint。
- `EMBEDDING` 配置：
  - `embedding_model` 必填。
  - `embedding_dimension` 可在创建时为空，但启用/ready/upload 使用前必须为正数。
  - `chat_model` 必须为空。
  - 不允许作为 app default model config。
  - 检查只调用 embeddings endpoint，并可回填/展示实际 dimension。

### DB 迁移 / 数据修正策略

新增 migration，不修改已发布 migration 文件。

建议策略：

1. 对历史 `capability='CHAT_EMBEDDING'` 且有 `embedding_model` 的配置，规范化为 `EMBEDDING`。
2. 规范化为 `EMBEDDING` 时，清空 `chat_model`，使字段语义与纯 embedding 类型一致。
3. 如果历史 `CHAT_EMBEDDING` 没有 embedding 信息但有 chat 信息，规范化为 `CHAT`。
4. 如果 legacy 行字段不足以推断能力：
   - 不要静默成功为可用配置。
   - 优先规范化为 `CHAT` 并保持 disabled/不可 ready，或按现有 status 保守处理；实现时必须用测试明确。
5. migration 应尽量幂等，适合 Flyway 在现有 V9 之后执行。

实现时必须解释并测试“为什么清空 chat_model”：一个 row 不再同时代表两类配置；历史 mixed row 被当作 embedding 配置保留 embedding 可用性，chat 侧应通过独立 CHAT 配置表达。

### Admin API 合约

现有端点保持，不新增新端点：

```http
GET  /api/admin/model-configs?status=ENABLED&capability=CHAT
GET  /api/admin/model-configs?status=ENABLED&capability=EMBEDDING
GET  /api/admin/model-configs/chat-capable
POST /api/admin/model-configs
PUT  /api/admin/model-configs/{id}
POST /api/admin/model-configs/check
POST /api/admin/model-configs/{id}/check
POST /api/admin/model-configs/{id}/enable
POST /api/admin/model-configs/{id}/disable
PUT  /api/admin/apps/{appId}/default-model-config
```

Create payload:

```json
{
  "capability": "CHAT",
  "name": "Sanguicode chat",
  "provider_name": "openai-compatible",
  "base_url": "https://api.example.com/v1",
  "api_key": "<plaintext input only>",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": null,
  "embedding_dimension": null
}
```

```json
{
  "capability": "EMBEDDING",
  "name": "DashScope embedding",
  "provider_name": "openai-compatible",
  "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "api_key": "<plaintext input only>",
  "chat_model": null,
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Update payload:

```json
{
  "capability": "EMBEDDING",
  "name": "DashScope embedding",
  "provider_name": "openai-compatible",
  "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "api_key": "<optional non-blank plaintext input only>",
  "chat_model": null,
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Check unsaved payload:

```json
{
  "capability": "CHAT",
  "provider_name": "openai-compatible",
  "base_url": "https://api.example.com/v1",
  "api_key": "<plaintext input only>",
  "chat_model": "deepseek-v4-pro"
}
```

```json
{
  "capability": "EMBEDDING",
  "provider_name": "openai-compatible",
  "base_url": "https://dashscope.aliyuncs.com/compatible-mode/v1",
  "api_key": "<plaintext input only>",
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Check saved payload may omit inherited fields:

```json
{
  "capability": "EMBEDDING",
  "embedding_dimension": 1024
}
```

Check response keeps the existing shape unless implementation proves a small additive field is needed:

```json
{
  "capability": "EMBEDDING",
  "overall_status": "SUCCESS",
  "base_url_checked": true,
  "chat": null,
  "embedding": {
    "status": "SUCCESS",
    "model": "text-embedding-v4",
    "actual_dimension": 1024,
    "configured_dimension": 1024,
    "message": "Embedding check succeeded."
  }
}
```

For `CHAT`, `embedding` must be null. For `EMBEDDING`, `chat` must be null. Do not return provider raw response body or assistant answer.

### Validation / Error Matrix

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global/header handling; no mutation. |
| Non-positive user id | 400 | `INVALID_REQUEST` | Existing controller validation. |
| Create/update/check `capability=CHAT_EMBEDDING` | 400 | `INVALID_REQUEST` | Reject with clear message: only `CHAT` and `EMBEDDING` are supported for new writes/checks. |
| Create `CHAT` without `chat_model` | 400 | `INVALID_REQUEST` | No row inserted. |
| Create `CHAT` with embedding fields | 400 | `INVALID_REQUEST` | No row inserted. |
| Create `EMBEDDING` without `embedding_model` | 400 | `INVALID_REQUEST` | No row inserted. |
| Create `EMBEDDING` with `chat_model` | 400 | `INVALID_REQUEST` | No row inserted. |
| Enable `EMBEDDING` without positive `embedding_dimension` | 400 | `INVALID_REQUEST` | Remains disabled/unchanged. |
| Bind `EMBEDDING` as app default | 400 | `MODEL_CONFIG_NOT_READY` | Binding rejected. Message should mention `CHAT` only, not `CHAT_EMBEDDING`. |
| List `capability=CHAT` | 200 | `OK` | Returns chat-capable configs after legacy normalization; should not expose third option in UI. |
| List `capability=EMBEDDING` | 200 | `OK` | Returns embedding configs after legacy normalization. |
| Invalid capability filter | 400 | `INVALID_REQUEST` | Only `CHAT` or `EMBEDDING`. |
| Saved check cross-user config | 403 | `FORBIDDEN` | No provider call. |
| Unsaved check missing required fields | 400 | `INVALID_REQUEST` | No provider call. |
| Chat check upstream non-2xx/network/timeout | 200 | `OK` envelope with check status `FAILED` | Do not expose raw provider body; message is safe. |
| Embedding check dimension mismatch | 200 | `OK` envelope with embedding `FAILED` | Shows configured/actual dimension only. |

### Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | A new `CHAT` config can be created, listed, checked via `/v1/chat/completions`, enabled, and bound as app default. A new `EMBEDDING` config can be created, checked via `/v1/embeddings`, listed for KB auto-fill, and used by document ingestion/retrieval matching. UI only shows two capability options. |
| Base | Historical `CHAT_EMBEDDING` rows are migrated/normalized and can be read without startup failure. If migration produced `EMBEDDING`, `chat_model` is cleared and app binding no longer treats it as chat-capable. Existing app/KB readiness reports clear missing/not-ready status instead of crashing. |
| Bad | API or UI still lets users create/edit `CHAT_EMBEDDING`; list/check result displays `CHAT_EMBEDDING` as a first-class option; binding accepts embedding-only config as chat default; provider raw body, prompt, answer, key, stack trace, or embedding vector appears in response/log/UI. |

## 前端需求

- `ModelConfigCapability` type 收敛为 `'CHAT' | 'EMBEDDING'`，除非为了 legacy VO 展示兼容必须引入内部 legacy union；禁止普通 create/update/check 表单发送 `CHAT_EMBEDDING`。
- 模型配置页新建/编辑/检查能力选择只展示 `CHAT` 和 `EMBEDDING`。
- 列表中如果后端仍返回 legacy `CHAT_EMBEDDING`：
  - 优先要求后端 VO 已规范化，不让前端暴露第三类。
  - 如短期防御，前端只能以只读/legacy 标记处理，不得作为新建/编辑 option。
- `CHAT` 表单只显示 chat model；`EMBEDDING` 表单只显示 embedding model/dimension。
- 检查弹窗根据能力显示对应字段，不要同时展示 chat 和 embedding 输入造成混淆。
- 检查结果用当前 key/base_url/model 的真实请求状态展示，失败信息必须安全、可读，不包含 provider raw body。
- App 绑定模型配置仍使用 `listChatCapableConfigs`，应只返回/展示 `CHAT`。
- KnowledgeBase 创建页 embedding config auto-fill 应只返回/展示 `EMBEDDING`。
- i18n 中删除或降级 `CHAT_EMBEDDING` 文案；保留必要 legacy 只读文案时应明确“历史数据已迁移/兼容”。

## Spec 更新要求

必须更新这些 spec，使后续实现不会继续把 `CHAT_EMBEDDING` 当成一等能力：

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/frontend/type-safety.md`

如实现影响 app readiness / embedding lookup / check UI，也同步更新对应段落中的 validation matrix 和 Good/Base/Bad cases。

## 预计修改文件

Backend:

- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCapability.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckRequest.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckResult.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/CreateModelConfigDTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/UpdateModelConfigDTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/resources/db/migration/V10__*.sql` 或下一个实际序号 migration
- Related tests under `backend/src/test/java/com/sangui/raggateway/model/`
- Related tests under `backend/src/test/java/com/sangui/raggateway/app/`
- Regression tests under `backend/src/test/java/com/sangui/raggateway/document/` and `backend/src/test/java/com/sangui/raggateway/retrieval/` only if helper contracts change.

Frontend:

- `frontend/src/types/model-config.ts`
- `frontend/src/api/model-configs.ts`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/pages/apps/AppConfigPage.tsx` if labels/filter assumptions change.
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx` if embedding config filtering/labels change.
- `frontend/src/app/i18n/dict.ts`

Specs:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/frontend/type-safety.md`

## Required Tests and Assertion Points

Backend targeted tests, run from `backend/` with 60s hard timeout per command:

```bash
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,ModelConfigCheckServiceTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=DocumentServiceTest,RetrievalServiceTest" test
mvn -q -DskipTests compile
```

Frontend checks, run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

Broader regression if time permits or if changed files touch gateway/RAG paths:

```bash
cd backend
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,OpenAiModelsControllerTest" test
mvn test
cd ..\frontend
cmd /c npm run test:visual
```

Assertions to add/update:

- Create `CHAT` succeeds and persists no embedding fields.
- Create `EMBEDDING` succeeds and persists no chat field.
- Create/update/check `CHAT_EMBEDDING` is rejected.
- Legacy `CHAT_EMBEDDING` row can be read without startup/service failure.
- Migration normalizes mixed row with embedding fields to `EMBEDDING` and clears `chat_model`.
- List `capability=CHAT` returns only chat configs after normalization.
- List `capability=EMBEDDING` returns only embedding configs after normalization.
- `listEnabledChatCapableConfigs` returns only `CHAT` configs.
- `findEnabledEmbeddingConfig` returns only `EMBEDDING` configs.
- App binding rejects `EMBEDDING`; readiness message no longer points users to `CHAT_EMBEDDING`.
- Check service calls only chat check for `CHAT` and only embedding probe for `EMBEDDING`.
- Check result never includes provider raw body, plaintext key, encrypted key, answer text, prompt, stack trace, or embedding vector.
- Frontend typecheck proves form/API types cannot send `CHAT_EMBEDDING` in normal create/update/check flows.
- Model config UI renders only two capability options.
- KB embedding config selector and App model bind selector remain semantically correct.

## Implementation Notes

- Prefer centralizing capability parsing/normalization in `ModelConfigService` or a small domain helper so controller/UI do not become second sources of truth.
- Avoid silent fallback: invalid new input should fail with `INVALID_REQUEST`; legacy DB state should be normalized or explicitly marked as legacy compatibility.
- Do not modify existing V9 migration; add a new migration.
- Keep saved-check behavior: saved config may inherit stored base URL/key/model fields; unsaved check requires explicit base URL/key/capability/model fields.
- Use existing `EmbeddingClient.probe(...)` for embeddings endpoint checks; do not hand-roll a parallel embedding HTTP client unless an existing abstraction cannot support the check.
- Keep chat check safe: small `max_tokens`, no answer text in response/log, no provider raw body leakage.

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden scope is explicit.
- [x] Expected modification files are listed.
- [x] Required tests and assertion points are listed.
- [x] Specific backend/frontend/gateway/rag/security/cross-layer guidelines were read before planning.
- [x] API / DB / frontend types / DTO payload fields are aligned in this PRD.
- [x] No unresolved requirement requires user confirmation before DeepSeek implementation.
