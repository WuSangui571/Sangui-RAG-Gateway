# High #3 检索阈值第二真相源

## 当前项目状态

- 当前分支：`feature/retrieval-threshold-single-source`，非 `main`，工作区在任务开始前为 clean。
- Trellis 当前无 active/current task；本任务目录为 `.trellis/tasks/06-23-retrieval-threshold-single-source`。
- workspace journal 已记录上一轮 `JWT AES secret split closeout`，状态为 Completed，提交为 `bed318be fix: split JWT and AES secret configuration`；无后续待继续实现任务。

## 任务分类

Complex Task。

理由：本任务穿过 DB migration/default、YAML/env、App service、gateway chat retrieval 调用链、retrieval evaluation、request-log retrieval evidence、README/spec，以及可能的 frontend AppVO/types/UI。它不是大重构，但必须消除多处默认值和 fallback 的第二真相源，避免后续 SQL READY 过滤与 ANN 索引任务继续被配置漂移干扰。

## Goal

统一 RAG 检索运行时配置的唯一来源：`rag_app` 持久化的 app retrieval config。

创建/迁移默认值只负责初始化 app row；运行时查询链路不得在 `ChatCompletionGatewayService`、`RetrievalEvaluationService` 或其他 service 中再次硬编码 `top_k`、`similarity_threshold`、`max_context_*` 默认值。手动配置覆盖必须以 app 持久化字段为准，并实际传入 `RetrievalService.retrieve(...)`。

## Scope

必须覆盖：

- `retrieval_top_k`
- `retrieval_similarity_threshold`
- `retrieval_max_context_chunks`
- `retrieval_max_context_chars`
- `retrieval_max_single_chunk_chars`
- `no_hit_policy` 只做来源一致性/保留 `STRICT_RAG`，不扩展策略实现

必须盘点并收敛：

- DB migration/default：`V7__add_app_default_knowledge_base.sql`、`V8__lower_default_retrieval_threshold.sql`
- YAML/env/default：`application.yml`、`.env.example`、`deploy/docker-compose.yml`
- service fallback：`ChatCompletionGatewayService.performRetrieval(...)`、`RetrievalEvaluationService.run(...)`
- frontend defaults/types：`frontend/src/types/app.ts`、`frontend/src/pages/apps/AppConfigPage.tsx`、`frontend/src/api/apps.ts`
- docs/spec：`README.md`、`.trellis/spec/sangui-rag-gateway.md`、RAG/backend/frontend specs if contract changes

## Non-Goals / Forbidden Scope

- 不修改检索 SQL 的 READY 过滤或 ANN/HNSW/IVFFlat 索引；那是后续 High #4。
- 不改变 pgvector distance metric，不改召回/重排/混合检索策略。
- 不改变 no-hit 策略实现；默认仍是 `STRICT_RAG`。
- 不扩大 OpenAI-compatible `/v1/chat/completions` 请求/响应字段。
- 不引入静默 pass-through、静默 fallback、mock success 或 broad catch-all。
- 不把 YAML/env 当成查询链路运行时来源。
- 不在前端硬编码 retrieval 默认值；若前端显示/编辑配置，初始值必须来自后端 AppVO/API。
- 不直接改业务代码于本 Codex 规划轮；实现由 DeepSeek 端执行。

## Source Of Truth Decision

运行时唯一来源：

```text
GatewayRequestContext.appId
  -> AppService loads AppEntity
  -> AppService or a focused app-domain resolver validates persisted retrieval config
  -> ChatCompletionGatewayService / RetrievalEvaluationService receive resolved config
  -> RetrievalService.retrieve(query, kb, topK, threshold, maxChunks, maxChars, maxSingleChars)
```

Allowed initialization sources:

- DB migration/default may initialize rows created outside Java service.
- App creation service may initialize fields from a single default definition if needed, but the persisted row is still the runtime source.
- YAML/env defaults are allowed only as app creation/bootstrap defaults, not as retrieval execution fallback.

Disallowed:

- `app.getRetrievalTopK() != null ? app.getRetrievalTopK() : 5`
- `app.getRetrievalSimilarityThreshold() != null ? ... : 0.300`
- Any duplicate hardcoded fallback in gateway/evaluation/retrieval orchestration.

## API / Command / Payload Fields

Public `/v1/chat/completions`:

- No request payload field change.
- No response shape change.
- Existing request-log `retrieval_evidence.top_k`, `retrieval_evidence.similarity_threshold`, and `retrieval_evidence.max_context_chunks` must reflect the effective persisted app config.

Admin App API:

- Existing endpoints must keep working:
  - `POST /api/admin/apps`
  - `GET /api/admin/apps`
  - `GET /api/admin/apps/{id}`
  - `PUT /api/admin/apps/{appId}/default-model-config`
  - `PUT /api/admin/apps/{appId}/knowledge-base`
- If implementation exposes retrieval config for display or edit, add/align explicit AppVO fields:
  - `retrieval_top_k: number`
  - `retrieval_similarity_threshold: number`
  - `retrieval_max_context_chunks: number`
  - `retrieval_max_context_chars: number`
  - `retrieval_max_single_chunk_chars: number`
  - `no_hit_policy: 'STRICT_RAG' | string`
- If implementation adds an update endpoint, keep it focused:

```http
PUT /api/admin/apps/{appId}/retrieval-config
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "retrieval_top_k": 5,
  "retrieval_similarity_threshold": 0.300,
  "retrieval_max_context_chunks": 5,
  "retrieval_max_context_chars": 12000,
  "retrieval_max_single_chunk_chars": 3000,
  "no_hit_policy": "STRICT_RAG"
}
```

Implementation may defer this endpoint if the task is limited to runtime source convergence, but then frontend UI/default changes must also be deferred and README wording must not claim a UI editing capability that does not exist.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Create app through service/API | Persisted app retrieval fields are initialized to the documented defaults; returned AppVO includes them if AppVO contract is expanded | `AppServiceTest`, `AppAdminControllerTest` if VO/API touched |
| Existing/manual app row has custom retrieval values | Gateway and evaluation pass those exact values to `RetrievalService.retrieve(...)` | `ChatCompletionGatewayServiceTest`, `RetrievalEvaluationServiceTest` with argument captors |
| Runtime app row has null retrieval config despite migration contract | Fail visibly through a single resolver; do not silently use service fallback literals | resolver/service unit test; gateway/evaluation test if exposed |
| Runtime app row has invalid retrieval config, e.g. non-positive topK/context or threshold outside allowed range | Fail visibly; do not call embedding/retrieval/upstream with fabricated defaults | resolver/service unit test |
| YAML fallback differs from DB/docs | Tests or diff review catch it; docs/spec/env/application defaults must be aligned | focused config/spec test or explicit grep/diff review |
| Frontend displays config | Values come from `AppVO`; no frontend-only default literals are used as source of truth | frontend type/component tests if UI touched |
| Frontend submits invalid config, if endpoint is added | Backend rejects with admin `ApiResponse` `400 INVALID_REQUEST`; frontend validation is UX only | controller/service tests |
| Public RAG no-hit under `STRICT_RAG` | Upstream is still called with no-hit context; no pass-through fallback is introduced | existing/new `ChatCompletionGatewayServiceTest`, `RagPromptBuilderTest` |

Public gateway error mapping for impossible invalid persisted config should prefer a visible OpenAI-compatible failure. If a new error code is introduced, update `.trellis/spec/backend/error-handling.md`, `.trellis/spec/rag/retrieval-quality.md`, README, and tests together. Avoid new public error codes unless implementation cannot reuse an existing safe config-not-ready boundary.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | App row has custom values, e.g. `topK=3`, `threshold=0.620`, `maxChunks=2`, `maxChars=4096`, `maxSingle=512`; non-streaming and streaming preparation pass exactly those values to `RetrievalService`; retrieval evidence records the same effective values where applicable. |
| Base | Fresh app created through Admin API has documented default retrieval values persisted; no runtime fallback is needed when chat/evaluation runs later. |
| Base | Legacy/direct-SQL app row relies on DB defaults; values are read back from DB and become persisted app config before runtime use. |
| Bad | `application.yml` says `0.700`, DB/README say `0.300`, while service fallback silently chooses `0.300`; tests pass by accident because fixtures set values. |
| Bad | Gateway and evaluation each carry their own `: 5`, `: 0.300`, `: 12000`, `: 3000` fallback literals. |
| Bad | Frontend form initializes missing backend fields to local constants and submits them as if they were server truth. |

## Acceptance Criteria

- [ ] Runtime retrieval config is resolved in one place from persisted app config.
- [ ] `ChatCompletionGatewayService` no longer contains numeric retrieval fallback defaults.
- [ ] `RetrievalEvaluationService` no longer contains numeric retrieval fallback defaults.
- [ ] Fresh app creation persists or returns documented retrieval defaults consistently.
- [ ] Manual/custom app config overrides are passed exactly to `RetrievalService.retrieve(...)`.
- [ ] DB defaults, YAML/env defaults, README, and `.trellis/spec` no longer disagree on baseline values.
- [ ] If AppVO/frontend exposes retrieval config, backend DTO/VO, frontend types, API client, and UI are aligned.
- [ ] No changes are made to retrieval SQL READY filtering or ANN index behavior.
- [ ] Focused backend tests and compile pass; frontend typecheck/build run if frontend is touched.
- [ ] `git diff --check` passes.

## Technical Approach

Recommended structural approach:

1. Add a focused app-domain retrieval config value object/resolver, likely near `app` module, for example:
   - `AppRetrievalConfig`
   - `AppRetrievalConfigDefaults` or `AppRetrievalProperties` only for initialization
   - `AppService.resolveRetrievalConfig(AppEntity app)` or equivalent
2. Move validation and null/invalid handling into that single resolver.
3. Use the resolver from:
   - `ChatCompletionGatewayService.performRetrieval(...)`
   - `RetrievalEvaluationService.run(...)`
4. Ensure app creation initializes the same persisted fields that runtime later reads.
5. Align config/docs:
   - `application.yml` fallback currently says `0.700`; `.env.example`, compose, README, V8, and service fallback say `0.300`.
   - Choose the project baseline (`0.300` per V8/README/env unless product decision changes) and update every contract together.
6. If adding admin retrieval config editing:
   - Add DTO/VO fields and validation in backend.
   - Extend frontend types/API/page from backend values only.
   - Add frontend tests/typecheck/build.

## Relevant Specs Read

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/rag/retrieval-quality.md`
- `.trellis/spec/rag/prompt-context-policy.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/quality-guidelines.md`

## Code Patterns Found

- `AppService.create(...)` currently creates enabled app rows but does not set retrieval fields in Java; it relies on DB defaults or later hydrated rows.
- `ChatCompletionGatewayService.performRetrieval(...)` currently hardcodes fallback defaults for `topK`, `threshold`, `maxChunks`, `maxChars`, and `maxSingleChars`.
- `RetrievalEvaluationService.run(...)` repeats the same fallback defaults.
- `RetrievalService.retrieve(...)` already accepts explicit retrieval config parameters and records effective `topK`, `similarityThreshold`, and `maxContextChunks` into `RetrievalEvidence`; it should not become the app-config resolver.
- `AppVO` and frontend `AppVO` currently do not expose retrieval config fields.
- `AppConfigPage` currently has no retrieval settings UI despite README saying app detail can configure retrieval settings.

## Files Likely To Modify

Backend likely:

- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppEntity.java`
- `backend/src/main/java/com/sangui/raggateway/app/vo/AppVO.java`
- new app-domain value object/resolver under `backend/src/main/java/com/sangui/raggateway/app/`
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/evaluation/RetrievalEvaluationService.java`
- `backend/src/main/resources/application.yml`
- possibly new migration only if existing rows need data repair beyond V8; do not add migration just to restate existing defaults

Backend tests likely:

- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/retrieval/evaluation/RetrievalEvaluationServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java` if VO/API changes
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java` only if adding/binding retrieval properties

Docs/spec likely:

- `README.md`
- `.env.example`
- `deploy/docker-compose.yml` if defaults/env contract changes
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/rag/retrieval-quality.md`
- `.trellis/spec/frontend/type-safety.md` if AppVO/frontend fields change

Frontend likely only if AppVO/UI exposed:

- `frontend/src/types/app.ts`
- `frontend/src/api/apps.ts`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/__tests__/pages/AppConfigPage.test.tsx`
- `frontend/src/app/i18n/dict.ts`

## Required Tests

Backend focused:

```bash
cd backend
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q -DskipTests compile
```

If config binding/startup properties are added:

```bash
cd backend
mvn -q "-Dtest=ProductionContextSmokeTest" test
```

If frontend touched:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Always:

```bash
git diff --check
```

Backend unit tests should use a 60-second timeout per command when feasible.

## Planning Self-Check

- 验收标准：已明确。
- 禁止修改范围：已明确，特别是 SQL READY 过滤、ANN、no-hit 扩展、公有 `/v1` payload。
- 预计修改文件：已列出 backend/docs/frontend 条件路径。
- 必跑测试：已列出 focused backend tests、compile、diff check；frontend 触及时补 typecheck/build。
- 具体 guideline：已读取 backend/rag/security/gateway/frontend/guides 的具体文件，不只 index。
- 需求不清：主要分歧是是否在本任务新增 Admin retrieval config 编辑 UI/API。建议 DeepSeek 先做运行时单源收敛；若选择补 UI/API，必须按本 PRD 的 DTO/VO/validation matrix 同步实现。
- API/DB/frontend types/DTO：当前不对齐点已记录：AppVO/frontend 缺 retrieval 字段，README 声称 UI 可配置但现有 UI 未实现。
