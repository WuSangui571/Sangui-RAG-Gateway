# Demo Credential Rotation and Acceptance Data Cleanup

## Task Classification

Simple Task with cross-layer validation.

This is primarily an operational and documentation cleanup task, not a backend/frontend feature task. It touches API key revocation, gateway auth verification, README/manual commands, local demo artifacts, and optional PowerShell helper/runbook behavior, so it must still follow backend, gateway, RAG, security, and cross-layer specs.

## Background

The previous RAG demo acceptance work produced a demo app, model config, knowledge base, uploaded documents, request logs, and at least one exposed/generated app API key. Journal notes identify demo app `3`, API key id `5`, model config `2`, and knowledge base `2` as the acceptance setup, but implementation must verify live state before mutating anything.

PowerShell 5.1 acceptance also exposed a command compatibility issue: `curl.exe -d $body` can corrupt JSON or behave inconsistently when the body is held in a PowerShell variable. Formal JSON POST/PUT acceptance should use a UTF-8 no-BOM temp file and `curl.exe --data-binary "@<path>"`.

## Goal

Close the local demo environment after acceptance:

- Revoke the exposed demo app API key and prove public `/v1/*` returns `401 invalid_api_key`.
- Decide whether demo app/model config/knowledge base/request logs should be retained for evidence or disabled/marked for cleanup.
- Remove local temporary markdown/JSON test artifacts and avoid staging ignored upload data.
- Ensure README JSON POST/PUT manual commands avoid PowerShell 5.1 `curl.exe -d $body` patterns.
- Add or update a small PowerShell helper/runbook only if it reduces repeated JSON body mistakes without changing core product behavior.

## Non-Goals

- Do not change backend Java business logic unless a verified bug is found during implementation.
- Do not change frontend TypeScript business UI unless a verified UI bug is found during implementation.
- Do not change database schema, RAG retrieval semantics, prompt construction, streaming behavior, upstream provider behavior, or request-log response fields.
- Do not add hidden fallbacks, fake success paths, or mock acceptance.
- Do not commit, push, or archive this task before human acceptance.

## Scope

Allowed changes:

- `README.md` demo acceptance, key rotation, cleanup, and PowerShell 5.1 command examples.
- `scripts/demo-smoke.ps1` if extending the existing helper is the smallest clear solution.
- A new small script/runbook under `scripts/` only if README-only documentation is insufficient; prefer reusing `Invoke-CurlCapture` style from `scripts/demo-smoke.ps1`.
- Trellis task/context files.
- Removal of local root-level manual artifacts such as `manual-*.md` and `manual-*.json` when they are confirmed to be test scratch files.

Forbidden without explicit user approval:

- Backend implementation files under `backend/src/main/java`.
- Frontend implementation files under `frontend/src`.
- DB migrations.
- `.env` or any real secret files.
- Broad deletion of `backend/data/uploads/**` without confirming the directory is local demo data and not needed for ongoing manual evidence.

## Existing Runtime Targets

Treat these as likely previous-demo values, not immutable truth:

| Item | Likely value | Required handling |
|---|---:|---|
| Admin user id | `1` | Verify against Admin API examples or current setup before use. |
| Demo app id | `3` | Confirm before using for request-log or key operations. |
| Demo API key id | `5` | Revoke only if it still exists and belongs to the admin user. |
| Demo model config id | `2` | Usually retain unless user explicitly wants disable/delete. |
| Demo KB id | `2` | Usually retain for reproducible demo evidence unless content is sensitive. |

## API / Command / Payload Contracts

### Revoke demo key

```http
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: <admin-user-id>
```

Expected success:

```json
{
  "code": "OK",
  "data": {
    "id": 5,
    "status": "REVOKED",
    "revoked_at": "<timestamp>"
  }
}
```

Response must not include `key` or `key_hash`.

### Verify revoked key

```http
POST /v1/chat/completions
Authorization: Bearer <revoked-key>
Content-Type: application/json
```

Formal PowerShell 5.1 verification command must write JSON using:

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$bodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($bodyPath, '{"messages":[{"role":"user","content":"test"}]}', $utf8)
curl.exe -s -o <response-path> -w "%{http_code}" -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <revoked-key>" `
  --data-binary "@$bodyPath"
Remove-Item -LiteralPath $bodyPath -Force
```

Expected public response:

```json
{
  "error": {
    "message": "Invalid API key.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

### Optional fresh key creation

Only if the demo environment still needs a reusable app key after revocation:

```http
POST /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: <admin-user-id>
Content-Type: application/json

{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

PowerShell 5.1 formal command must use UTF-8 no-BOM temp body plus `--data-binary`, not `curl.exe -d $createBody`.

## Validation / Error Matrix

| Scenario | Expected result | Boundary | Assertion point |
|---|---|---|---|
| Revoke existing same-user active/disabled demo key | Admin API returns 200 `code=OK`, `status=REVOKED`, `revoked_at` set | admin-api | HTTP status, envelope, safe fields only |
| Revoke already revoked key | Admin API returns 200 `code=OK`, remains `REVOKED` | admin-api | Idempotent result, no plaintext key |
| Revoke missing key id | Admin API returns 404 `NOT_FOUND` | admin-api | No local doc claims success |
| Revoke cross-user key | Admin API returns 403 `FORBIDDEN` | security | Generic `Access denied`, no data leak |
| Call `/v1/chat/completions` with revoked key | HTTP 401 OpenAI-compatible `invalid_api_key` | auth | Status + JSON error code |
| Formal PowerShell JSON POST/PUT command | Uses no-BOM temp file and `--data-binary` | runbook | README/script contains no `curl.exe -d $body` or `curl.exe -d $createBody` for formal manual steps |
| Request-log evidence retained | Safe operational fields remain available | observability | No full prompt, key, chunk content, provider body printed |
| Local scratch artifacts cleaned | Root-level `manual-*.md/json` removed if confirmed scratch | hygiene | `git status --short` shows only intended tracked docs/scripts/Trellis changes |
| Upload directory cleanup considered | No broad deletion unless explicitly accepted | storage | Keep or document `backend/data/uploads/**` state |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Exposed demo key is revoked, revoked key receives `401 invalid_api_key`, README/script formal JSON POST/PUT examples use `--data-binary`, local root scratch files are removed, and app/model/KB retention decision is documented. |
| Base | Key is already revoked or missing; runbook records the observed status and still verifies no valid public access remains. Demo app/model/KB are retained for reproducible evidence. |
| Bad | README keeps formal PowerShell 5.1 `curl.exe -d $body` examples, a plaintext key is written to disk or git, backend/frontend behavior changes without need, or ignored upload data is deleted blindly. |

## Required Implementation Plan

1. Confirm live local state:
   - Check `git status --short`.
   - Identify root-level `manual-*.md/json` scratch files.
   - If backend/frontend are running, use Admin API to revoke the known demo key id after confirming id/user ownership.
2. Revoke or confirm revocation:
   - Revoke key id `5` only after validating it is the demo key or after the user supplies the current key id.
   - Verify revoked plaintext key returns HTTP `401` with `error.code=invalid_api_key`.
3. Decide retained demo data:
   - Default: retain app/model config/KB/request logs as acceptance evidence.
   - Disable/delete only if the user explicitly requests it or the data contains sensitive material.
4. Clean local scratch files:
   - Remove confirmed root-level manual test markdown/JSON files.
   - Do not stage or delete `backend/data/uploads/**` blindly; document any remaining local ignored artifacts.
5. Fix documentation/runbook:
   - Replace formal README JSON POST/PUT PowerShell examples that use variable-based `-d $body` or `-d $createBody`.
   - Prefer one reusable no-BOM temp-body snippet or a small helper script function.
   - Keep quick one-liners only if clearly labeled as non-formal quick checks and not based on variable-body `-d`.
6. Validate:
   - Run PowerShell parser check for any changed `.ps1`.
   - Run `git diff --check`.
   - Run targeted backend auth/key tests if backend code changed; otherwise list them as not required.
   - Run secret scan for committed files.

## Files Likely To Modify

Expected:

- `README.md`: formal JSON command examples, cleanup checklist, rotation/runbook wording.
- `scripts/demo-smoke.ps1`: only if adding revoked-key validation or extracting existing no-BOM body helper is cleaner than README duplication.
- Optional new `scripts/demo-cleanup.ps1` or `scripts/powershell-json-body-runbook.ps1`: only if justified by repeated commands.
- `.trellis/tasks/06-01-demo-credential-rotation-acceptance-cleanup/*`: PRD and context.

Potential local deletions:

- `manual-app-create.json`
- `manual-app-key-create.json`
- `manual-app-key-final.json`
- `manual-app-key-rerun.json`
- `manual-bind-kb.json`
- `manual-bind-model.json`
- `manual-chat-config-update.json`
- `manual-embedding-config-update.json`
- `manual-kb-unique.md`
- `manual-kb.md`
- `manual-rag-smoke-local.md`
- `manual-rag-smoke.md`
- `manual-v1-invalid-response.json`
- `manual-v1-valid-response.json`

Do not treat ignored upload files under `backend/data/uploads/knowledge/**` as source changes.

## Required Tests and Assertion Points

Always run after implementation:

```powershell
git diff --check
rg -n "curl\.exe.*-d\s+\$body|curl\.exe.*-d\s+\$createBody|curl\s" README.md scripts
rg -n "sk-sangui-[A-Za-z0-9_-]{20,}" README.md scripts .trellis/spec .trellis/tasks
```

If any PowerShell script changed:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

If backend Java changes despite the intended boundary:

```powershell
cd backend
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=ApiKeyAdminControllerTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
```

Manual runtime acceptance when services are running:

- Revoke demo key via Admin API or Admin UI.
- Verify revoked key returns HTTP `401`.
- Verify response body contains `error.code = invalid_api_key`.
- Verify no command output prints full plaintext keys except the user's transient local input.

## Planning Self-Check

- Acceptance criteria are explicit: revoke/401, PowerShell-safe JSON commands, scratch cleanup, retention decision.
- Prohibited scope is explicit: no backend/frontend core logic, DB migrations, RAG behavior, streaming behavior, or broad upload deletion without approval.
- Expected files are listed.
- Required tests and assertion points are listed.
- Concrete guidelines were read: project spec, backend directory/database/error/logging/quality, gateway resilience, RAG retrieval/prompt/document, security, frontend type/quality, and cross-layer/code-reuse guides.
- Open question: live runtime key/app/KB IDs may differ from journal values. Implementation must verify current state before mutation rather than assuming id `5` is still the active demo key.
