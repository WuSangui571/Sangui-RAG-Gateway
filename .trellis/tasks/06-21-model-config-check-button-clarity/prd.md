# 模型配置页“检查未保存配置”按钮语义收敛

## Goal

收敛模型配置页检查入口的语义和交互边界，让用户清楚区分：

- 顶部“草稿检查”会使用当前弹窗中输入的未保存字段调用草稿检查 API。
- 表格行内“已保存配置检查”会使用该行已保存配置调用保存配置检查 API。
- 未保存编辑弹窗中的表单草稿不会被行内已保存检查隐式读取或参与后端检测。

本任务优先做前端交互与文案语义收敛，不预期修改后端、数据库、DTO 或迁移。

## Scope Classification

Expanded Task with two bounded tracks:

- Track A: Simple frontend task with explicit model-config check API-boundary notes.
- Track B: Small infra/build fix for backend Docker Maven dependency resolution in GitHub CI/CD.

理由：

- 目标明确，主要落点在 `ModelConfigPage`、typed i18n、页面测试。
- 现有后端已区分 `POST /api/admin/model-configs/check` 与 `POST /api/admin/model-configs/{id}/check`。
- Track A 的当前问题是用户对“未保存配置”按钮语义的信任边界，而不是模型检查服务契约缺失。
- Track B 的当前问题是 GitHub CI/CD backend Docker build 中 Maven 依赖解析被单一 public mirror 卡死/失败，属于构建稳定性与 mirror 回退策略问题。

## Track B: GitHub CI/CD Backend Docker Maven Resolution Fix

### Failure Evidence

GitHub CI/CD failure:

```text
docker build -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
RUN mvn -B -ntp -DskipTests package
Could not transfer artifact software.amazon.awssdk:profiles:pom:2.29.52 from/to aliyun-public
https://maven.aliyun.com/repository/public -> 502 Bad Gateway
Could not transfer artifact software.amazon.awssdk:retries:pom:2.29.52 from/to aliyun-public
```

Current repo state:

- `backend/Dockerfile` copies `settings.xml` into `/root/.m2/settings.xml`.
- `backend/settings.xml` defines one mirror, `aliyun-public`, with `<mirrorOf>*</mirrorOf>`.
- `backend/pom.xml` depends on `software.amazon.awssdk:s3:2.29.52`, whose transitive descriptor resolution includes `profiles` and `retries`.
- `.github/workflows/ci.yml` backend Docker job builds directly from `backend/` context and therefore uses this settings file inside the container.

Root-cause hypothesis for implementation:

- The container is using repo-local Maven settings as intended.
- The failing component is not missing Dockerfile wiring; it is the mirror policy.
- Because `aliyun-public` mirrors `*`, Maven has no effective fallback to Maven Central when Aliyun returns 502 for AWS SDK descriptors.

### Track B Requirements

- Make backend Docker Maven dependency resolution resilient to a transient or partial Aliyun mirror failure.
- Prefer a settings-level fix over changing application dependencies.
- Keep `backend/settings.xml` public-metadata-only: no credentials, private repository URLs, tokens, or secrets.
- Preserve a clear Dockerfile build step; do not reintroduce quiet `dependency:go-offline -q` that hides Maven progress.
- Do not remove the AWS SDK S3 dependency unless code research proves it is unused and the user explicitly accepts scope reduction.
- Do not change runtime Docker environment, Compose service env vars, storage behavior, database migrations, or backend business code for this CI issue.

### Track B Candidate Fix Direction

Preferred direction for DeepSeek to validate:

- Change `backend/settings.xml` so Maven Central remains reachable when Aliyun is unavailable.
- A likely pattern is to avoid `<mirrorOf>*</mirrorOf>` for Aliyun. Use a narrower mirror selector, or define explicit repositories/profiles where Central is not fully replaced by Aliyun.
- If a mirror is kept, document why it will not block Central fallback for artifacts unavailable or temporarily failing on the mirror.

Avoid:

- Adding multiple mirrors with the same `mirrorOf=*` and assuming Maven will fallback between mirrors automatically.
- Baking secrets or private repository credentials into `settings.xml`.
- Masking the failure with retries that still point only to the same unavailable mirror.
- Switching dependency versions only to dodge one failing transitive artifact, unless dependency compatibility is researched and tested.

### Track B Validation / Error Matrix

| Case | Trigger | Expected |
|---|---|---|
| CI Docker backend build | `docker build -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend` | Maven can resolve Spring Boot, AWS SDK S3, and transitive AWS descriptors without Aliyun-only 502 failure |
| Compose backend build | `docker compose --progress=plain --env-file .env -f deploy/docker-compose.yml build backend --no-cache` | Same backend image build path succeeds, if local Docker is available |
| Backend compile | `cd backend && mvn -q -DskipTests compile` | Still compiles outside Docker |
| settings secret scan | Inspect `backend/settings.xml` | Contains no credentials, private repo URLs, tokens, env-expanded secrets, or provider keys |
| Mirror outage simulation, if practical | Aliyun unavailable/502 | Build should not be forced to fail solely because all artifacts are mirrored to Aliyun |

### Track B Files Likely To Modify

- `backend/settings.xml`
  - Adjust mirror/repository policy so Maven Central fallback remains possible.
- `backend/Dockerfile`
  - Expected to stay mostly unchanged; only modify if needed to pass explicit settings path or improve transparent Maven invocation.
- `.github/workflows/ci.yml`
  - Expected to stay unchanged unless Docker build command needs an explicit build arg or cache-safe setup.

### Track B Required Tests

Minimum:

```bash
cd backend
mvn -q -DskipTests compile
```

Docker build check, when Docker is available:

```bash
docker build --progress=plain -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
```

Compose build check, if `.env` and Docker services are available:

```bash
docker compose --progress=plain --env-file .env -f deploy/docker-compose.yml build backend --no-cache
```

If Docker cannot be run locally, the implementer must state that clearly and at least verify:

- `backend/settings.xml` is syntactically valid XML.
- `mvn -q -DskipTests compile` still passes from `backend/`.
- The Dockerfile still copies the same settings path used by the CI build.

## Current Behavior Found

- 顶部按钮文案为 `model-config.checkUnsaved`，当前 zh-CN 为“检查未保存配置”，en-US 为 `Check Unsaved Config`。
- 顶部按钮打开独立检查弹窗，用户重新输入 `capability/base_url/api_key/chat_model` 或 `embedding_model/embedding_dimension` 后调用 `checkUnsavedModelConfig(request)`。
- `checkUnsavedModelConfig` 调用 `/admin/model-configs/check`，请求体包含草稿字段。
- 表格行内按钮文案为 `model-config.checkButton`，当前为“检查”/`Check`。
- 表格行内按钮调用 `checkSavedModelConfig(record.id, {})`，即 `/admin/model-configs/{id}/check`，请求体为空，后端使用已保存配置、已保存加密 upstream key 和该行字段。
- 编辑弹窗里的未保存表单草稿不会参与行内已保存检查。

## Requirements

- 将顶部检查入口命名和弹窗说明收敛为“检查草稿配置”语义，而不是泛泛的“检查未保存配置”。
- 在草稿检查弹窗中明确说明：该检查仅使用弹窗内字段，不会保存配置，也不会读取正在编辑弹窗中的未保存表单。
- 将行内检查入口命名为“检查已保存配置”或等价明确语义，避免用户误以为它会读取当前未保存编辑内容。
- 行内已保存检查在任一行检查进行中应保持清晰 loading/disabled 状态；不要因固定宽度按钮导致文案截断。
- 草稿检查运行时应有 loading/disabled 状态，避免重复提交。
- 保持模型检查结果弹窗复用现有结果展示，不引入新的结果结构。
- 保持 secrets 边界：页面不得回显或持久化 upstream API key；测试不得断言或暴露明文 key。
- i18n 必须同时更新 zh-CN 和 en-US，并保持 typed dictionary parity。

## Non-Goals / Forbidden Scope

- 不修改 backend Java 实现、数据库 migration、DTO/VO 字段、API 路径、认证逻辑或 provider 检查策略。
- 不把模型配置页改造成冒烟测试页、聊天 playground 或大范围信息架构重做。
- 不新增全局状态、localStorage/sessionStorage 持久化、URL state 或跨页缓存。
- 不新增第二套模型检查 API client；继续使用 `checkUnsavedModelConfig` 和 `checkSavedModelConfig`。
- 不在 request log、diagnostics、app binding、knowledge base 页面做顺手改动。
- 不吞掉 API 错误，不制造 mock success 或 silent fallback。

## API / Payload Contract

### Draft Check

Command/API:

```text
POST /api/admin/model-configs/check
frontend: checkUnsavedModelConfig(request)
```

Payload fields:

```ts
{
  capability: 'CHAT' | 'EMBEDDING'
  provider_name?: string
  base_url: string
  api_key: string
  chat_model?: string
  embedding_model?: string
  embedding_dimension?: number
}
```

Expected semantics:

- Uses only the request payload.
- Requires `capability`, `base_url`, `api_key`, and the model field required by capability.
- Does not persist config.
- Does not require an existing config ID.

### Saved Check

Command/API:

```text
POST /api/admin/model-configs/{id}/check
frontend: checkSavedModelConfig(id, request)
```

Current frontend payload:

```ts
{}
```

Expected semantics:

- Uses the saved row identified by `{id}`.
- Uses saved encrypted upstream key after backend decrypts it in memory.
- Does not read create/edit form drafts from the frontend.
- Should remain row-scoped in the UI.

Response contract, unchanged:

```ts
{
  capability: 'CHAT' | 'EMBEDDING'
  overall_status: 'SUCCESS' | 'FAILED' | 'PARTIAL'
  base_url_checked: boolean
  chat: { status: 'SUCCESS' | 'FAILED' | 'PARTIAL'; model: string; message: string } | null
  embedding: {
    status: 'SUCCESS' | 'FAILED' | 'PARTIAL'
    model: string
    actual_dimension: number | null
    configured_dimension: number | null
    message: string
  } | null
}
```

Sensitive response fields that must not appear:

```text
raw provider body, embedding vectors, stack traces, plaintext keys, prompts, assistant answers
```

## Validation / Error Matrix

| Case | Trigger | Expected UI |
|---|---|---|
| Draft missing base URL | User runs draft check without `base_url` | Form validation message; no API call |
| Draft missing API key | User runs draft check without `api_key` | Form validation message; no API call |
| Draft CHAT missing chat model | `capability=CHAT` without `chat_model` | Form validation message; no API call |
| Draft EMBEDDING missing embedding model | `capability=EMBEDDING` without `embedding_model` | Form validation message; no API call |
| Draft check API returns non-OK | `checkUnsavedModelConfig` resolves with non-OK response | Existing page error alert shows safe message; result modal does not open |
| Draft check network/API error | `checkUnsavedModelConfig` rejects | Existing page error alert shows safe message; result modal does not open |
| Saved check API returns success | Row button clicked | Result modal opens and, when available, includes row id/name context |
| Saved check API returns non-OK | `checkSavedModelConfig` resolves non-OK | Existing page error alert shows safe message; result modal does not open |
| Saved check network/API error | `checkSavedModelConfig` rejects | Existing page error alert shows safe message; result modal does not open |
| Row check in progress | One row saved check is running | Saved-check buttons disabled or otherwise cannot double-submit; running row exposes loading/busy state |

## Good / Base / Bad Cases

- Good: user opens “检查草稿配置”, fills CHAT fields, runs check, `checkUnsavedModelConfig` receives only the modal draft payload, result modal opens.
- Good: user clicks row “检查已保存配置”, `checkSavedModelConfig(id, {})` is called, no modal draft fields are sent.
- Base: empty model config list still shows create, draft check, and refresh actions with clear labels.
- Base: a saved row check result displays `#id name`, status, capability, base URL checked, and chat/embedding result details.
- Bad: missing draft fields should fail form validation before API call.
- Bad: failed draft or saved API call should show the existing alert and not open stale result data.
- Bad: edit modal draft changes must not affect row saved check payload.

## Files Likely To Modify

- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
  - Rename/clarify top-level draft check entry and modal text.
  - Rename/clarify row saved check button.
  - Ensure loading/disabled states are explicit for draft and saved checks.
  - Avoid fixed-width button truncation if label becomes longer.
- `frontend/src/app/i18n/dict.ts`
  - Update zh-CN and en-US model-config check labels/help text.
  - Preserve dictionary key parity.
- `frontend/src/__tests__/pages/ModelConfigPage.test.tsx`
  - Add coverage for draft check payload, saved check payload, failure errors, and loading/disabled behavior.

## Required Tests and Assertion Points

Targeted tests:

```bash
cd frontend
cmd /c npx vitest run src/__tests__/pages/ModelConfigPage.test.tsx
```

Assertions to add:

- Top-level draft button label communicates draft/unsaved-modal semantics.
- Draft check fills modal fields and calls `checkUnsavedModelConfig` with expected payload.
- Draft check does not call `checkSavedModelConfig`.
- Saved row check calls `checkSavedModelConfig(record.id, {})`.
- Saved row check does not include create/edit/check modal draft fields.
- Non-OK or rejected draft check shows page error and does not open result modal.
- Non-OK or rejected saved check shows page error and does not open stale result modal.
- Loading/disabled state prevents duplicate saved check calls while one is running.
- Updated copy exists in zh-CN and en-US through typed dictionary parity.

Full frontend validation after implementation:

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Not required unless implementation unexpectedly touches global visual layout/theme:

```bash
cd frontend
cmd /c npm run test:visual
```

Backend validation is not required for the intended frontend-only task. If backend files, DTOs, or API semantics are changed unexpectedly, run at minimum:

```bash
cd backend
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,ModelConfigCheckServiceTest" test
```

## Acceptance Criteria

### Track A: Model Config Check UI

- [ ] Users can distinguish draft check from saved row check from labels and modal/help text.
- [ ] Draft check uses `/admin/model-configs/check` with form payload and does not save.
- [ ] Saved row check uses `/admin/model-configs/{id}/check` with `{}` payload and does not read edit/create drafts.
- [ ] Loading/disabled states are explicit for draft and saved checks.
- [ ] Failure paths show safe existing error UI and do not display stale results.
- [ ] Tests cover draft, saved, failure, and loading/disabled states.
- [ ] Frontend lint, targeted page test, full test suite, typecheck, and build pass.
- [ ] No backend Java/API/DB/security/storage/RAG implementation files are modified for Track A.

### Track B: CI/CD Docker Maven Build

- [ ] `backend/settings.xml` no longer makes Aliyun the only effective source for every Maven artifact when it returns 502.
- [ ] `backend/settings.xml` contains public Maven metadata only and no credentials/tokens/private repository URLs.
- [ ] `backend/Dockerfile` still uses transparent Maven output (`mvn -B -ntp -DskipTests package`) and does not reintroduce quiet `dependency:go-offline -q`.
- [ ] Backend Docker build command is verified if Docker is available, or the inability to run Docker is documented with next-best checks.
- [ ] Backend compile still passes after the settings change.
- [ ] No backend business code, database migration, storage lifecycle, runtime env contract, or frontend feature code is modified for the CI/CD fix.
