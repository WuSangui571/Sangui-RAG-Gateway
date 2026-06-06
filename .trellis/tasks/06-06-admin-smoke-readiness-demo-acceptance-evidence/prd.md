# Admin Smoke Readiness Demo Acceptance Evidence

## Goal

整理并验证 Admin Smoke Readiness 到 Demo Acceptance 的端到端回归证据链，把 readiness endpoint、frontend Smoke page、`scripts/demo-smoke.ps1`、request-log safe evidence、hit-chunks metadata、revoked-key auth boundary 串成一套最小但完整的本地验收路径。

本任务默认是验证/证据整理任务。只有在 spec 与代码不一致、脚本无法真实覆盖合同、或本地验收暴露真实缺口时，才进入最小实现修复。

## Scope Classification

Complex Task.

理由：
- 跨 backend admin API、frontend Smoke 页面、PowerShell smoke 脚本、OpenAI-compatible gateway、request-log observability、RAG retrieval evidence、安全字段边界。
- 涉及 API/command/payload 合同、错误矩阵、safe evidence forbidden-field scan、streaming/non-streaming 两条 gateway 路径。
- 本轮采用双端协作：Codex 仅负责 PRD、spec/code research、Trellis context 和交接；DeepSeek 后续负责实现或 runbook 补齐。

## Non-Goals / Boundaries

- 不在 Codex 本轮修改业务实现文件。
- 不创建新的产品功能，不扩大为通用测试平台、CI 新链路、agent/workflow 平台。
- 不改变 `/v1/chat/completions` 支持范围，不引入 `/v1/responses`、tools、vision、response_format 等未支持 OpenAI API。
- 不新增 DB schema、migration、DTO 字段，除非 DeepSeek 在实跑中发现现有合同无法表达必要证据且用户确认。
- 不提交真实 API key、upstream key、生成的 `sk-sangui-*`、provider payload、prompt、chunk content、embedding、Playwright artifact 内容、上传文件内容或 runtime raw logs。
- 不用 silent fallback、mock success、假通过来补齐验收证据；失败必须暴露 boundary。

## Primary User Story

作为项目维护者，我希望用一套最小但完整的本地验收路径确认：
- Admin 配置 readiness 可解释当前 app 是否可做 smoke。
- Demo smoke 同时覆盖 backend/frontend health、non-streaming chat、streaming chat。
- Request-log list/detail/hit-chunks 只暴露安全元数据并能关联本次 smoke。
- revoked key 或 revoked app key 明确返回 public gateway `401 invalid_api_key`。
- 证据输出可以被记录，但不会泄露 secrets、prompt、chunk content、provider raw body 或 stack trace。

## Acceptance Path

最小本地验收顺序：

1. Backend health
   - `GET {BackendBaseUrl}/api/health`
   - Expected: HTTP 200, envelope `code=OK`, `data.status=UP`.
   - Boundary on failure: `health`.

2. Frontend proxy health
   - `GET {FrontendBaseUrl}/api/health`
   - Expected: HTTP 200 JSON, `code=OK`; response must not be SPA HTML.
   - Boundary on failure: `proxy`.

3. App readiness
   - `GET /api/admin/apps/{appId}/readiness`
   - Header: `X-Admin-User-Id: <adminUserId>`
   - Expected for runnable app: `code=OK`, `data.overall_status=READY`, checks include app/default_model_config/default_knowledge_base/knowledge_base_status/active_api_key/embedding_config.
   - Safe metadata only; forbidden fields absent.

4. Non-streaming chat
   - `POST {FrontendBaseUrl}/v1/chat/completions`
   - Header: `Authorization: Bearer <active-app-api-key>`
   - Body:
     ```json
     {
       "model": "ignored",
       "messages": [
         { "role": "user", "content": "What integration style does Sangui RAG Gateway provide?" }
       ],
       "stream": false
     }
     ```
   - Expected: HTTP 200, `choices[0].message.content` present.
   - Evidence allowed: id/object/model/finish_reason/content length/token counts. Do not print answer text.
   - Boundary on gateway errors: `auth`, `retrieval`, `embedding`, `upstream`, or `proxy` according to error code/status.

5. Streaming chat
   - Same endpoint and auth, `stream=true`.
   - Expected: HTTP 200 SSE, at least one `data:` line, final `data: [DONE]`.
   - Evidence allowed: HTTP status, data line count, chunk count, `[DONE]` present/absent. Do not render raw SSE content.

6. Request-log list/detail/hit-chunks
   - `GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success`
   - `GET /api/admin/apps/{appId}/request-logs/{requestId}`
   - `GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks`
   - Header: `X-Admin-User-Id: <adminUserId>`
   - Match current smoke run by `question_summary` prefix from the non-streaming message.
   - Expected safe list fields: `request_id`, `app_id`, `api_key_id`, `model`, `provider_name`, `status=success`, `latency_ms`, `messages_count`, `question_summary`, `hit_chunk_ids`, `created_at`.
   - Expected safe detail fields: all list fields plus `user_id`, `updated_at`.
   - Expected hit chunk metadata: `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`; bounded `summary` may exist but must not be printed by smoke script output.

7. Revoked-key auth boundary
   - `POST {FrontendBaseUrl}/v1/chat/completions`
   - Header: `Authorization: Bearer <revoked-app-api-key>`
   - Expected: HTTP 401 OpenAI-compatible error, `error.code=invalid_api_key`.
   - Evidence allowed: HTTP status and error code only.

8. Forbidden field scan
   - Scan readiness response metadata, request-log list item, detail, hit-chunk response, smoke script output, and any committed evidence checklist/runbook.
   - Forbidden fields must be absent:
     ```text
     key_hash, api_key, api_key_encrypted, upstream_api_key, provider_response_body,
     stack_trace, embedding, prompt, messages, full_messages, augmented_prompt,
     authorization, storage_path, content, chunk_content, full answer text,
     raw SSE content, provider raw body, real generated sk-sangui-* values
     ```

## Command Contract

Canonical automated smoke command:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

PowerShell requirements:
- Must remain PowerShell 5.1 compatible.
- Must use `curl.exe`, not PowerShell `curl` alias.
- JSON request bodies for formal acceptance must be UTF-8 no-BOM temp files and submitted via `--data-binary`.
- Temp files must be cleaned in `finally`.
- Script exits `0` only if every enabled check passes; otherwise exits non-zero.

Optional modes:
- Without both `-AppId` and `-AdminUserId`, request-log automation skips neutrally while health/chat/stream checks still run.
- Supplying only one of `-AppId` or `-AdminUserId` is a `request-log` failure.
- Without `-VerifyRevokedKey`, revoked-key check skips neutrally.
- Supplying `-VerifyRevokedKey` with blank `-RevokedApiKey` is an `auth` failure.

## API / Payload Contract

### Readiness

```http
GET /api/admin/apps/{appId}/readiness
X-Admin-User-Id: <adminUserId>
```

Response shape:
- `ApiResponse<AppReadinessVO>`
- `data.app_id`
- `data.user_id`
- `data.overall_status`: `READY | MISSING | DISABLED | NOT_READY`
- `data.checks[]`: `key`, `label`, `status`, `message`, `metadata`

Required checks:
- `app`
- `default_model_config`
- `default_knowledge_base`
- `knowledge_base_status`
- `active_api_key`
- `embedding_config`

### Request Log APIs

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
X-Admin-User-Id: <adminUserId>
```

List response:
- `ApiResponse<ApiRequestLogPageVO<ApiRequestLogVO>>`
- `data.items[]`, `data.page`, `data.page_size`, `data.total`

Detail response:
- `ApiResponse<ApiRequestLogDetailVO>`

Hit chunks response:
- `ApiResponse<List<HitChunkSummaryVO>>`

### Gateway Chat APIs

```http
POST /v1/chat/completions
Authorization: Bearer <app-api-key>
Content-Type: application/json
```

Supported request fields:
- `model`
- `messages`
- `temperature`
- `max_tokens`
- `top_p`
- `stream`

Public gateway errors must use OpenAI-compatible shape:

```json
{
  "error": {
    "message": "Safe message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

## Validation / Error Matrix

| Scenario | Expected | Boundary / Assertion |
|---|---|---|
| Backend not running | curl non-zero or non-200 | `health`, fail visibly |
| Frontend `/api` proxy returns HTML | fail | `proxy`, no SPA fallback accepted |
| App missing | `404 NOT_FOUND` admin envelope | readiness/request-log controller |
| App belongs to another user | `403 FORBIDDEN`, generic `Access denied` | tenant boundary |
| App disabled | readiness HTTP 200, app check `DISABLED`, overall `DISABLED` unless `MISSING` exists | readiness |
| No default model config | readiness `MISSING`; gateway `/v1/models` or chat may return `model_config_not_ready` when reached | readiness/retrieval |
| Default KB not READY | readiness `NOT_READY`; gateway chat `409 knowledge_base_not_ready` | retrieval |
| No active app key | readiness `MISSING` or `DISABLED` depending key existence | readiness/auth |
| Matching embedding config missing | readiness `MISSING`; query embedding fails if smoke proceeds | embedding |
| Matching embedding config disabled-only | readiness `DISABLED` | embedding |
| Matching embedding config enabled but missing key | readiness `NOT_READY` | embedding |
| Non-streaming success | HTTP 200 and answer content present; output prints content length only | upstream |
| Non-streaming upstream failure | OpenAI-compatible `upstream_error` or `upstream_timeout` | upstream |
| Streaming success | SSE chunks plus `[DONE]` | upstream/proxy |
| Streaming returns JSON error | classify by OpenAI error code/status and fail | upstream/retrieval/auth |
| Request-log list no matching smoke row | fail | `request-log`, stale evidence not accepted |
| Request-log list/detail missing safe fields | fail | `request-log` |
| `hit_chunk_ids` empty for retrieval-hit demo path | fail | `request-log` |
| Hit-chunks empty while `hit_chunk_ids` non-empty | fail | `request-log` |
| Forbidden fields in list/detail/hit-chunks | fail | safe evidence boundary |
| Revoked key returns non-401 | fail | `auth` |
| Revoked key returns 401 without `invalid_api_key` | fail | `auth` |

## Good / Base / Bad Cases

Good:
- Backend and frontend running.
- App has enabled chat config, READY bound KB, active API key, matching enabled embedding config with usable upstream key.
- Readiness returns all READY.
- Non-streaming chat succeeds and prints only content length.
- Streaming emits `data:` chunks and `[DONE]`.
- Request-log automation finds latest matching success row, validates safe list/detail fields, validates non-empty numeric `hit_chunk_ids`, validates hit-chunk metadata, and forbidden-field scan passes.
- Revoked-key verification returns HTTP 401 with `error.code=invalid_api_key`.

Base:
- Script is run without `-AppId`/`-AdminUserId`; request-log automation skips with neutral message but health/chat/stream still run.
- Script is run without `-VerifyRevokedKey`; revoked-key check skips with neutral message.
- Frontend Smoke page can be used as an operator-facing companion to the PowerShell script, but script remains canonical repeatable evidence path.

Bad:
- Script or UI reports success using stale request-log rows.
- Script prints full assistant answer, API key, prompt, chunk summary text, chunk content, raw SSE content, provider body, key hash, or encrypted key.
- Request-log APIs expose forbidden fields or full chunk content.
- Revoked-key verification passes without checking both HTTP 401 and `error.code=invalid_api_key`.
- A failure is converted into a silent fallback or mock pass.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: source of truth for project boundary, implemented readiness baseline, request-log API, demo smoke automation, acceptance checklist.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because task crosses public gateway, admin APIs, frontend, request logs, RAG retrieval evidence, streaming, and secrets.
- `.trellis/spec/backend/error-handling.md`: gateway OpenAI-compatible errors, admin envelope, `invalid_api_key`, request-log admin error matrix.
- `.trellis/spec/backend/logging-guidelines.md`: safe operational fields and forbidden log/response fields.
- `.trellis/spec/backend/database-guidelines.md`: tenant-scoped request-log and hit chunk queries.
- `.trellis/spec/backend/quality-guidelines.md`: targeted backend tests for readiness, request-log, auth, gateway, retrieval.
- `.trellis/spec/gateway/resilience.md`: upstream timeout/error normalization, streaming pre/post commit behavior, request-log failure persistence.
- `.trellis/spec/rag/retrieval-quality.md`: `hit_chunk_ids`, retrieval-hit evidence, tenant/KB-scoped retrieval.
- `.trellis/spec/rag/prompt-context-policy.md`: prompt preservation, no-hit, no prompt leakage.
- `.trellis/spec/security/rag-security.md`: safe evidence, tenant isolation, forbidden fields.
- `.trellis/spec/frontend/type-safety.md`: explicit frontend DTO/VO types for readiness and request-log fields.
- `.trellis/spec/frontend/state-management.md`: page-local smoke evidence state and secret lifecycle.
- `.trellis/spec/frontend/quality-guidelines.md`: Smoke page quality, safe display, frontend validation commands.

### Code Patterns Found

- `scripts/demo-smoke.ps1`: canonical PowerShell smoke contract. Already covers health, proxy, non-streaming, streaming, request-log list/detail/hit-chunks, forbidden-field scan, revoked-key 401.
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`: `GET /api/admin/apps/{appId}/readiness`, header validation, 403/404 ownership split.
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`: `assembleReadiness`, check keys, overall status precedence, safe metadata.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`: list/detail/hit-chunks admin routes with `X-Admin-User-Id`, app ownership, list filter validation.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`: request-log safe VO conversion and hit chunk summaries.
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`: request-log persistence boundary for non-streaming and streaming paths.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: operator-facing smoke page covering readiness, non-streaming, streaming, request-log validation, revoked-key check.
- `frontend/src/api/openai.ts`: frontend `/v1/chat/completions` smoke client, OpenAI error extraction, SSE evidence counts.
- `frontend/src/api/request-logs.ts`: typed request-log API client.
- `README.md`: existing runbook and acceptance checklist; likely place for evidence/runbook refinement if no implementation gap exists.

### Files Likely To Modify

Only if real gaps are found:

- `scripts/demo-smoke.ps1`: refine command-level validation, forbidden-field scan, safe evidence output, or readiness integration if missing from acceptance flow.
- `README.md`: consolidate acceptance runbook/evidence checklist if code already meets the contract.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: align UI evidence with script contract only if mismatch is found.
- `frontend/src/api/openai.ts`: adjust streaming evidence parsing only if actual SSE handling mismatch is found.
- `frontend/src/api/request-logs.ts` and `frontend/src/types/request-log.ts`: adjust types only if backend contract and frontend types diverge.
- `frontend/src/types/app.ts` and `frontend/src/api/apps.ts`: adjust readiness typing/client only if contract drift is found.
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`, `AppService.java`, `AppReadiness*`: only if readiness response violates spec.
- `backend/src/main/java/com/sangui/raggateway/log/*`: only if request-log list/detail/hit-chunks violate safe evidence or tenant contracts.
- Backend tests listed below: update/add only for changed behavior.
- Frontend build/typecheck: update only for changed frontend behavior.

Do not modify business files if the current code already satisfies the contracts; instead produce a runbook/evidence checklist.

## Required Tests and Assertion Points

Backend targeted tests from `backend/`:

```powershell
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

Backend compile if any backend code changes:

```powershell
mvn -q -DskipTests compile
```

Frontend validation from `frontend/` if frontend code or types change:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

PowerShell syntax check if `scripts/demo-smoke.ps1` changes:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

Manual acceptance / smoke:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

Assertion points:
- all enabled script checks pass and exit code is `0`;
- no forbidden fields in script output or committed evidence docs;
- request-log match is based on current message prefix, not any success row;
- response shapes match admin envelope for `/api/admin/**` and OpenAI-compatible shape for `/v1/**`;
- revoked-key call checks both HTTP 401 and `error.code=invalid_api_key`;
- frontend Smoke page never displays full answer text, full prompt, full chunk content, full raw SSE, or keys.

## Planning Self-Check

- [x] 验收标准已明确。
- [x] 禁止修改范围已明确。
- [x] 预计修改文件已列出，且默认不改业务文件。
- [x] 必跑测试命令已列出。
- [x] 已读取具体 guideline，不只读 spec index。
- [x] API / command / payload 字段已列出。
- [x] validation / error matrix 已列出。
- [x] Good / Base / Bad cases 已列出。
- [x] 当前没有必须向用户确认的问题；若 DeepSeek 实跑需要真实 provider key/app id，应由用户提供或使用本地已有安全环境，不写入仓库。

