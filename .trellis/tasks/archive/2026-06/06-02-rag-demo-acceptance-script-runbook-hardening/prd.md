# RAG Demo Full-Chain Acceptance Script And Runbook Hardening

## Goal

Turn the existing RAG demo acceptance flow into a repeatable PowerShell 5.1-compatible script and runbook that verifies the real full-stack path:

```text
backend health
frontend /api proxy health
frontend /v1 chat non-streaming
frontend /v1 chat streaming with data: [DONE]
revoked or invalid key -> 401 invalid_api_key
request-log list/detail/hit-chunks safe evidence
split-provider setup and model-config key rotation runbook
```

This is a hardening task for the existing demo acceptance baseline. It must reduce manual drift without changing RAG retrieval, prompt construction, upstream forwarding, API-key authentication semantics, database schema, or admin business behavior unless a real defect is found and explicitly scoped.

## Current State

- `scripts/demo-smoke.ps1` already exists and covers backend health, frontend `/api` health, non-streaming chat, streaming chat, optional request-log list/hit-chunks validation, and optional revoked-key 401 validation.
- `README.md` already documents split-provider setup, PowerShell 5.1 smoke commands, request-log manual checks, key revocation, and the optional smoke script.
- `.trellis/spec/sangui-rag-gateway.md` already contains an implemented demo acceptance automation rule.
- Existing gap: script validates request-log list and hit-chunks, but not request-log detail.
- Existing gap: script does not run a systematic forbidden-field scan over list/detail/hit-chunk JSON.
- Existing gap: non-streaming success currently prints assistant answer preview, which can expose private KB-derived content and conflicts with the safe-evidence-only script output rule.

## Task Classification

Complex Task.

Reasons:
- Crosses backend gateway, frontend proxy, RAG runtime, request-log APIs, security evidence boundaries, README/runbook, scripts, and Trellis specs.
- Requires executable command contracts and validation matrices.
- Must preserve secret safety while using real runtime evidence.

## In Scope

1. Harden `scripts/demo-smoke.ps1`.
   - Keep PowerShell 5.1 compatibility.
   - Keep `curl.exe`; never rely on the PowerShell `curl` alias.
   - Keep UTF-8 no-BOM temp-file request bodies via `New-Object System.Text.UTF8Encoding($false)`.
   - Clean temp files in `finally`.
   - Validate request-log detail after matching the smoke request.
   - Validate forbidden fields are absent from request-log list item, detail, and hit-chunk responses.
   - Stop printing assistant answer content or chunk summary text as success evidence. Print safe metadata only.
   - Preserve explicit failure boundary labels: `health`, `proxy`, `auth`, `upstream`, `embedding`, `retrieval`, `request-log`, and `unknown`.
   - Ensure streaming success specifically asserts SSE `data:` chunks and final `data: [DONE]`.

2. Harden README/runbook.
   - Explain split-provider setup: Sanguicode chat plus DashScope embedding.
   - Explain Model Config key rotation validation after `PUT /api/admin/model-configs/{id}`.
   - Document script invocation with safe placeholders.
   - Document expected safe evidence output fields.
   - Document forbidden output fields.
   - Keep all manual commands PowerShell 5.1-safe.

3. Sync spec if the stable acceptance contract changes.
   - Update `.trellis/spec/sangui-rag-gateway.md` or a focused guide under `.trellis/spec/guides/` only when the command or acceptance matrix changes.
   - Do not duplicate a second source of truth unnecessarily; prefer updating the existing "Implemented Demo Acceptance Automation Rule" if it already owns the contract.

4. Validate without hiding failures.
   - Script exits `0` only when all enabled checks pass.
   - Script fails visibly with a boundary tag when a required assertion fails.
   - No mock success paths, no broad silent fallbacks.

## Out Of Scope

- No backend business-code changes unless implementation finds a real mismatch between documented contract and existing API behavior.
- No database migration.
- No frontend UI redesign.
- No new provider preset feature.
- No embedding config semantics change.
- No request-log schema or DTO/VO field addition unless a documented safety bug requires it and the user approves.
- No automatic creation of real provider configs, knowledge bases, documents, or keys in the smoke script. The script validates a prepared demo environment; setup remains documented runbook/API/UI steps.
- No printing or persisting plaintext app API keys, upstream keys, chunk content, full prompts, provider raw bodies, stack traces, embeddings, or local upload files.

## Command Contract

Primary command:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "sk-sangui-<revoked-demo-key>" `
  -VerifyRevokedKey
```

Parameters:

| Parameter | Required | Contract |
|---|---:|---|
| `ApiKey` | yes | Plaintext app API key used only in `Authorization`; never echoed. |
| `BackendBaseUrl` | no | Base URL for direct backend `/api/health`; default `http://localhost:8080`. |
| `FrontendBaseUrl` | no | Base URL for frontend `/api` and `/v1` proxy validation; default `http://localhost:3000`. |
| `Message` | no | Sent to chat and used for request-log `question_summary` matching. |
| `AppId` | no | Enables request-log automation only when supplied together with `AdminUserId`. |
| `AdminUserId` | no | Temporary admin identity header value for request-log APIs. |
| `RevokedApiKey` | no | Plaintext revoked key used only for a negative auth call; never echoed. Required only when `-VerifyRevokedKey` is supplied. |
| `VerifyRevokedKey` | no | Switch that enables revoked-key validation. |

## API And Payload Contracts

### Backend health

```http
GET /api/health
```

Expected:

```json
{"code":"OK","data":{"status":"UP"}}
```

### Frontend proxy health

```http
GET <FrontendBaseUrl>/api/health
```

Expected:
- HTTP 200.
- JSON envelope with `code=OK`.
- Body must not be SPA HTML.

### Non-streaming chat

```http
POST <FrontendBaseUrl>/v1/chat/completions
Authorization: Bearer <ApiKey>
Content-Type: application/json
```

Payload:

```json
{
  "model": "ignored",
  "messages": [
    {"role": "user", "content": "<Message>"}
  ]
}
```

Expected:
- HTTP 200.
- OpenAI-compatible JSON.
- `choices[0].message.content` exists, but script must not print it.
- Safe success evidence may include response type, model if available, and content length only.

### Streaming chat

Payload:

```json
{
  "model": "ignored",
  "messages": [
    {"role": "user", "content": "<Message>"}
  ],
  "stream": true
}
```

Expected:
- HTTP 200.
- SSE output contains at least one `data:` line.
- Final stream marker includes `data: [DONE]`.
- Script must fail if chunks exist without `[DONE]`.

### Request-log list

```http
GET <FrontendBaseUrl>/api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
X-Admin-User-Id: <AdminUserId>
```

Expected:
- HTTP 200.
- Admin envelope `code=OK`.
- Latest matching success log is found by `question_summary` prefix.
- Matched log has non-blank `request_id`, `model`, `provider_name`, `question_summary`.
- `status=success`.
- `latency_ms` is numeric and non-negative.
- `hit_chunk_ids` is a non-empty numeric array for the prepared retrieval-hit demo.

### Request-log detail

```http
GET <FrontendBaseUrl>/api/admin/apps/{appId}/request-logs/{requestId}
X-Admin-User-Id: <AdminUserId>
```

Expected:
- HTTP 200.
- Admin envelope `code=OK`.
- `request_id` equals the matched list row.
- Safe detail fields are present: `user_id`, `app_id`, `api_key_id`, `model`, `provider_name`, `status`, `latency_ms`, `messages_count`, `question_summary`, `hit_chunk_ids`, `created_at`, `updated_at`.
- Forbidden fields are absent.

### Hit chunk summaries

```http
GET <FrontendBaseUrl>/api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
X-Admin-User-Id: <AdminUserId>
```

Expected:
- HTTP 200.
- Admin envelope `code=OK`.
- Non-empty list when matched log has non-empty `hit_chunk_ids`.
- Each item has safe metadata: `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`.
- Script must not print `summary` text.
- Forbidden fields are absent.

### Revoked or invalid key check

```http
POST <FrontendBaseUrl>/v1/chat/completions
Authorization: Bearer <RevokedApiKey>
Content-Type: application/json
```

Expected:
- HTTP 401.
- OpenAI-compatible error shape.
- `error.code = invalid_api_key`.

## Safe Evidence Fields

Allowed in script output:

```text
request_id
model
provider_name
latency_ms
messages_count
hit_chunk_ids count and numeric IDs
chunk_id
document_id
knowledge_base_id
source_filename
chunk_index
HTTP status
boundary label
SSE data line count
content length only
```

Forbidden in script output, README examples, and committed evidence:

```text
plaintext app API key
key_hash
Authorization header value
upstream API key
api_key_encrypted
chunk content
chunk summary text
full assistant answer content
full prompt
messages content beyond configured smoke Message label/length
provider raw body
embedding vectors
stack trace
storage_path
real .env secrets
backend/data upload artifacts
```

## Validation And Error Matrix

| Scenario | Expected result | Failure boundary |
|---|---|---|
| Backend health unavailable | Non-zero script exit; clear message | `health` |
| Frontend `/api/health` returns SPA HTML | Non-zero script exit | `proxy` |
| Non-streaming chat returns `401 invalid_api_key` | Non-zero script exit | `auth` |
| Non-streaming chat returns `409 knowledge_base_not_ready` | Non-zero script exit | `retrieval` |
| Non-streaming chat returns `409 model_config_not_ready` | Non-zero script exit | `retrieval` |
| Non-streaming chat returns `502 embedding_failed` | Non-zero script exit | `embedding` |
| Non-streaming chat returns `502 upstream_error` or `504 upstream_timeout` | Non-zero script exit | `upstream` |
| Non-streaming chat succeeds but no `choices[0].message.content` | Non-zero script exit | `proxy` |
| Streaming returns JSON gateway error before SSE | Non-zero script exit with classified boundary | classified from `error.code` |
| Streaming returns SSE without `data: [DONE]` | Non-zero script exit | `upstream` |
| `AppId` and `AdminUserId` both missing | Request-log automation skipped neutrally | none |
| Only one of `AppId` or `AdminUserId` supplied | Non-zero script exit | `request-log` |
| Request-log list/detail/hit-chunks non-200 or non-JSON | Non-zero script exit | `request-log` |
| Matched request log missing required safe fields | Non-zero script exit | `request-log` |
| Forbidden field appears in list/detail/hit-chunks JSON | Non-zero script exit | `request-log` |
| `-VerifyRevokedKey` missing | Revoked-key automation skipped neutrally | none |
| `-VerifyRevokedKey` with blank `RevokedApiKey` | Non-zero script exit | `auth` |
| Revoked key returns anything except `401 invalid_api_key` | Non-zero script exit | `auth` |

## Good / Base / Bad Cases

Good:
- Backend and frontend are running.
- App has an enabled Sanguicode chat config bound as default.
- Same admin user has an enabled DashScope embedding config matching the KB embedding model and dimension.
- App has a bound `READY` knowledge base with retrieval hits.
- Fresh key succeeds for non-streaming and streaming chat.
- Request-log list/detail/hit-chunks return safe evidence.
- Revoked key returns `401 invalid_api_key`.
- Script output contains no secrets, chunk content, answer content, provider raw body, or stack traces.

Base:
- User runs script without `AppId` and `AdminUserId`; health/chat/stream still run and request-log automation is skipped with a neutral message.
- User runs script without `VerifyRevokedKey`; revoked-key check is skipped with a neutral message.
- README still provides manual PowerShell checks for request logs and key revocation.

Bad:
- Script passes while request-log detail is stale, absent, or unsafe.
- Script prints assistant answer text, chunk summary text, API keys, provider bodies, or stack traces.
- Script accepts streaming output without a final `data: [DONE]`.
- Script uses PowerShell `curl` alias or `Invoke-RestMethod -Form` patterns incompatible with Windows PowerShell 5.1.
- README suggests committing real keys or local smoke artifacts.

## Expected Files To Modify

Primary:
- `scripts/demo-smoke.ps1`
- `README.md`

Conditional:
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/guides/<focused-demo-acceptance-guide>.md` only if a new guide is justified; prefer updating existing project spec instead.

Likely no change:
- Backend Java implementation files.
- Frontend React implementation files.
- DB migrations.
- Docker Compose.

## Code Patterns To Follow

- `scripts/demo-smoke.ps1`: existing `Invoke-CurlCapture`, `Write-FailBoundary`, `Classify-GatewayError`, temp-file cleanup, UTF-8 no-BOM body writing.
- `README.md`: existing PowerShell 5.1 formal command style with `curl.exe --data-binary`.
- `ApiRequestLogAdminController` and `ApiRequestLogService`: request-log list/detail/hit-chunks API and safe-field behavior.
- `ApiRequestLogAdminControllerTest`: forbidden-field assertions and request-log error matrix.
- `GatewayAuthFilterTest`: revoked/disabled/expired key all map to `401 invalid_api_key`.
- `OpenAiChatCompletionsControllerTest`: non-streaming and streaming request-log fields are persisted at the controller boundary.
- `frontend/nginx.conf` and `frontend/vite.config.ts`: `/api` and `/v1` proxy contracts.

## Required Tests And Validation

Script syntax:

```powershell
$parseErrors = $null
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$parseErrors)
if ($parseErrors -and $parseErrors.Count -gt 0) { $parseErrors; exit 1 }
```

Backend targeted tests, from `backend/`, with 60-second timeout discipline:

```powershell
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest" test
```

Frontend validation if README or frontend proxy/types are touched:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

Diff and safety checks:

```powershell
git diff --check
rg -n "sk-sangui-|api_key_encrypted|key_hash|provider_response_body|stack_trace|Authorization: Bearer [^<]" README.md scripts .trellis/spec
```

Manual or semi-automated full-stack smoke:

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

If full-stack smoke cannot be run by Qwen, it must state the blocker and leave exact commands for human execution.

## Planning Self-Check

- Acceptance criteria are explicit in command, API, and validation matrices.
- Forbidden modification scope is explicit.
- Expected files are listed.
- Required tests are listed.
- Concrete backend/frontend/gateway/RAG/security guidelines were read before implementation planning.
- No open product question blocks implementation.
- No API, DB, frontend type, or DTO field addition is currently required.

