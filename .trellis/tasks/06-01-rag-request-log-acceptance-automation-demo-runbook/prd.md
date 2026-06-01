# RAG Request Log Acceptance Automation and Demo Runbook

## Goal

Make the RAG demo acceptance flow reproducible and evidence-friendly by extending the existing PowerShell 5.1 smoke script and README runbook to verify request-log persistence after a successful non-streaming RAG chat.

This task is an acceptance automation and documentation/spec task. It must reuse existing read-only Admin request-log APIs and must not change backend business behavior, database schema, request-log DTO contracts, frontend UI, RAG retrieval, prompt construction, upstream forwarding, or streaming semantics.

## Classification

Complex Task.

Reason: the implementation touches command-line script behavior, Admin API read paths, README demo instructions, executable acceptance rules in project spec, PowerShell 5.1 JSON/curl behavior, and secret-safe evidence output. It is cross-layer from a validation perspective, but should remain read-only against existing backend contracts.

## Current Project State

- RAG retrieval and prompt augmentation are implemented for `/v1/chat/completions`.
- Existing `scripts/demo-smoke.ps1` validates backend health, frontend `/api` proxy health, non-streaming chat through frontend `/v1`, and streaming SSE with `[DONE]`.
- Request-log evidence is currently documented as a manual Admin UI check.
- Recent manual acceptance confirmed successful non-streaming and streaming RAG calls, request log `SUCCESS`, model/provider/latency/question summary/hit chunk evidence, and frontend proxy behavior.

## Scope

### In Scope

- Extend `scripts/demo-smoke.ps1` with optional:
  - `-AppId`
  - `-AdminUserId`
- After non-streaming chat succeeds, query:
  - `GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success`
- Validate the latest relevant request log contains:
  - `status = success`
  - non-blank `model`
  - non-blank `provider_name`
  - numeric positive or non-negative `latency_ms`
  - `question_summary` matching the smoke message prefix
  - non-empty numeric `hit_chunk_ids` for the retrieval-hit demo path
- Optionally query:
  - `GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks`
- Print only safe evidence:
  - request ID
  - model
  - provider name
  - latency
  - hit chunk count
  - chunk IDs
  - hit chunk summary count
  - source filename/chunk index if already returned by API
- Do not print full chunk summary text, full private document content, full prompt, upstream provider body, app API key, key hash, or upstream key.
- Update `README.md` demo acceptance instructions for:
  - PowerShell 5.1 request-log API verification
  - revoked-key `401 invalid_api_key` verification
  - UTF-8 no BOM temp body files plus `curl.exe --data-binary`
- Update `.trellis/spec/sangui-rag-gateway.md` or a relevant guide with an executable demo acceptance rule for request-log automation.

### Out of Scope

- Backend Java business logic changes.
- Frontend TypeScript/UI changes.
- Database migrations, schema, indexes, or entity changes.
- New Admin API endpoints or response fields.
- Changes to request-log persistence behavior.
- Changes to RAG retrieval, prompt construction, no-hit policy, embedding, model config, API key auth, or streaming behavior.
- Storing demo API keys or request bodies in tracked files.
- Adding broad test frameworks or new runtime dependencies.

## Command Contract

### Smoke Script

Expected invocation:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<your-key>" `
  -AppId 3 `
  -AdminUserId 1 `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

Parameters:

| Parameter | Required | Default | Notes |
|---|---:|---|---|
| `ApiKey` | yes | none | Plaintext app key used only in Authorization header. Never echo full value. |
| `BackendBaseUrl` | no | `http://localhost:8080` | Existing backend health base URL. |
| `FrontendBaseUrl` | no | `http://localhost:3000` | Existing frontend proxy base URL and Admin API origin. |
| `Message` | no | existing demo question | Used for chat and request-log `question_summary` assertion. |
| `AppId` | no | none | Enables request-log automation when present. |
| `AdminUserId` | no | none | Enables request-log automation when present. |

Request-log automation must be skipped with a clear neutral message when either `AppId` or `AdminUserId` is missing. Skipping this optional step must not turn an otherwise passing smoke run into failure unless the user explicitly supplied one but not the other.

### Admin Request-Log API

List latest logs:

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
X-Admin-User-Id: <adminUserId>
```

Expected response shape:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "request_id": "uuid-or-id",
        "status": "success",
        "model": "deepseek-v4-pro",
        "provider_name": "openai-compatible",
        "latency_ms": 11521,
        "question_summary": "What integration style...",
        "hit_chunk_ids": [12]
      }
    ],
    "page": 1,
    "page_size": 5,
    "total": 1
  }
}
```

Hit chunks:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
X-Admin-User-Id: <adminUserId>
```

Expected response shape:

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "chunk_id": 12,
      "document_id": 1,
      "knowledge_base_id": 2,
      "source_filename": "demo.md",
      "chunk_index": 0,
      "summary": "bounded summary omitted by script output"
    }
  ]
}
```

Script output must not print `summary` text unless a later explicit requirement allows it.

### Key Revocation Verification

README should include a PowerShell 5.1 command that:

- Revokes a demo key:

```http
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: <adminUserId>
```

- Then verifies the revoked key fails:

```http
POST /v1/chat/completions
Authorization: Bearer <revoked-key>
Content-Type: application/json
```

Expected public gateway result:

```text
HTTP 401, error.code = invalid_api_key
```

This 401 is expected and should be classified as `auth`.

## Validation and Error Matrix

| Scenario | Expected behavior | Boundary |
|---|---|---|
| `AppId` and `AdminUserId` omitted | Health/chat/stream checks run; request-log automation skipped with a neutral message. | none |
| Only one of `AppId` or `AdminUserId` supplied | Fail clearly: both are required for request-log automation. | request-log |
| Request-log list returns non-200 | Fail and print HTTP status plus safe body preview. | request-log |
| Request-log list returns non-JSON or Admin envelope `code != OK` | Fail with safe body preview or envelope code/message. | request-log |
| List has no success item | Fail: no success request log found for app. | request-log |
| Latest success log is stale or does not match message prefix | Prefer matching by `question_summary` prefix among recent items; fail if no match. | request-log |
| Matching log has `status != success` | Fail. | request-log |
| Matching log has blank `model` or `provider_name` | Fail. | request-log |
| Matching log has null/non-numeric/negative `latency_ms` | Fail. | request-log |
| Matching log has blank/mismatched `question_summary` | Fail. | request-log |
| Matching log has empty `hit_chunk_ids` | Fail for the retrieval-hit demo acceptance path. | retrieval/request-log |
| Hit-chunks endpoint returns 200 with summaries | Pass and print safe count/chunk IDs only. | request-log |
| Hit-chunks endpoint returns empty list despite non-empty hit IDs | Fail because chunk evidence cannot be reproduced. | request-log |
| Hit-chunks endpoint returns 400 because app has no default KB | Fail; setup is incomplete. | retrieval |
| Hit-chunks endpoint returns 403/404 | Fail; app/admin ownership or request ID mismatch. | request-log |
| Revoked key returns `401 invalid_api_key` | Pass for cleanup verification. | auth |
| Revoked key still succeeds or returns non-401 | Fail; key revocation is not verified. | auth |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Backend and frontend are running; app has ready KB and valid model config; non-streaming chat succeeds; request-log automation finds latest matching success row with model/provider/latency/question summary/hit chunk IDs; hit-chunks count matches safe evidence expectations; streaming still emits `[DONE]`. |
| Base | User runs script without `AppId`/`AdminUserId`; existing health/chat/stream checks still work; README provides manual and automated request-log commands. |
| Bad | Script accepts a passing chat run while request-log fields are missing, stale, unsafe, or empty; script prints full key/chunk content; README uses PowerShell default encoding or `curl` alias; spec omits executable request-log acceptance rule. |

## Required Tests and Assertion Points

Automated/local validation after DeepSeek implementation:

```powershell
# PowerShell parser syntax check
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)

# Run the smoke script against a prepared local/demo environment
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000"

# Verify revoked key behavior after demo cleanup
curl.exe -s -o <temp-output> -w "%{http_code}" -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <revoked-key>" `
  --data-binary "@<utf8-no-bom-json-file>"
```

Expected assertions:

- Script exits `0` only when all enabled checks pass.
- Script exits non-zero for request-log API failure, stale/mismatched log, missing fields, empty hit chunks, or unsafe response shape.
- Request-log pass output includes only safe evidence.
- Script remains PowerShell 5.1 compatible.
- JSON request bodies are written as UTF-8 without BOM and submitted with `curl.exe --data-binary`.
- README examples use `curl.exe`, not the PowerShell `curl` alias.
- README revoke check captures HTTP status and asserts `401` + `invalid_api_key`.

Recommended repository checks:

```powershell
git diff --check
cmd /c npm run typecheck
cmd /c npm run build
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
```

Backend tests should pass unchanged because this task should not alter Java behavior. If Java tests fail after only script/docs/spec changes, investigate environment or accidental edits before changing backend code.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product source of truth; contains request-log admin API endpoints, RAG request-log fields, demo/deployment acceptance rules, and existing run commands.
- `.trellis/spec/backend/logging-guidelines.md`: defines safe request-log fields and forbidden sensitive fields.
- `.trellis/spec/backend/error-handling.md`: defines Admin request-log error matrix and gateway `401 invalid_api_key` behavior.
- `.trellis/spec/backend/database-guidelines.md`: documents request-log schema and tenant-scoped admin query behavior.
- `.trellis/spec/backend/quality-guidelines.md`: defines request-log admin API tests and regression checks.
- `.trellis/spec/frontend/type-safety.md`: documents request-log VO field names and forbidden frontend/log response fields.
- `.trellis/spec/frontend/quality-guidelines.md`: reinforces request-log list safety and no full prompt/content display.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this is an acceptance flow spanning gateway call, admin API, secret safety, and request logs.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: use existing curl/temp-file helpers in the smoke script instead of duplicating ad hoc request handling.

### Code Patterns Found

- `scripts/demo-smoke.ps1`: existing `Invoke-CurlCapture`, `Write-Pass`, `Write-FailBoundary`, UTF-8 no BOM temp body handling, and boundary classification should be extended.
- `frontend/src/api/request-logs.ts`: typed frontend client already calls `GET /admin/apps/{appId}/request-logs`, detail, and hit-chunks through the same API shape the script should use via `/api/admin/...`.
- `frontend/src/types/request-log.ts`: field names and nullability for `model`, `provider_name`, `latency_ms`, `question_summary`, and `hit_chunk_ids`.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`: route contract and validation for list/detail/hit-chunks.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`: list ordering by `created_at DESC`, hit chunk summaries preserving original hit ID order, and safe 200-char summaries.
- `README.md`: existing demo flow already documents health/proxy/non-streaming/streaming/manual request-log/revoke sections and should be updated, not replaced wholesale.

### Files Likely To Modify

- `scripts/demo-smoke.ps1`: add optional `AppId`/`AdminUserId`, request-log list validation, hit-chunks safe summary count, request-log boundary classification.
- `README.md`: update PowerShell 5.1 runbook, request-log command block, revoked-key 401 check, and JSON body temp-file/`--data-binary` guidance.
- `.trellis/spec/sangui-rag-gateway.md`: add executable demo acceptance rule for automated request-log verification.
- `.trellis/tasks/06-01-rag-request-log-acceptance-automation-demo-runbook/prd.md`: keep this PRD as the implementation contract.

### Risk / Boundary Notes

- Latest log selection must avoid false positives from older successful runs. Prefer matching the smoke `Message` against `question_summary` among the first page of recent success logs.
- Do not print the app API key; current script prints base URLs and message only, which is acceptable.
- Do not print hit chunk summary text by default; even bounded summaries may contain private knowledge content.
- Admin API uses `X-Admin-User-Id`; scripts must not imply this is production auth.
- `hit_chunk_ids` can be empty for no-hit or pre-retrieval failures, but this demo acceptance is explicitly a retrieval-hit path, so empty IDs should fail when request-log automation is enabled.
- Streaming usage can be nullable; this task should validate request-log fields after non-streaming success, not rely on streaming log usage.
- PowerShell 5.1 default file encoding is unsafe for JSON body files; use `System.Text.UTF8Encoding($false)` and `curl.exe --data-binary`.

## Planning Self-Check

- Acceptance standards are explicit: script must validate request-log fields and safe hit chunk evidence after non-streaming success.
- Prohibited scope is explicit: no Java business logic, DB, frontend UI/types, API contract, RAG, auth, or streaming behavior changes.
- Expected modified files are listed.
- Required tests and runtime smoke checks are listed.
- Concrete guideline files were read, not just spec indexes.
- No clarification is currently required; the task can proceed under the stated assumptions.
- API/DB/frontend DTO fields are aligned to existing `ApiRequestLogVO`, `ApiRequestLogDetailVO`, and `HitChunkSummaryVO`; no new fields are required.

## DeepSeek Execution Notes

Implement only the files listed in "Files Likely To Modify". Keep the change as an acceptance automation/documentation/spec update. If implementation appears to require backend API/DTO/schema changes, stop and report the mismatch instead of expanding scope.
