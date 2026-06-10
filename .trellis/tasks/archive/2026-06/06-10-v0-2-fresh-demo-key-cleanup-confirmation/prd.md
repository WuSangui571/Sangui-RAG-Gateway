# V0.2 Fresh Demo Key Cleanup Confirmation

## Goal

Close the only remaining V0.2 release-candidate blocker by confirming the fresh demo app API key has been revoked and that the revoked key is rejected by the public gateway with HTTP `401` and `error.code=invalid_api_key`.

This is a security evidence and release-boundary task. It must not change backend/frontend business behavior. The output must be metadata-only and must not commit plaintext API keys, `Authorization` header values, raw runtime responses, terminal transcripts, provider bodies, prompts, messages, chunk content, or stack traces.

## Task Classification

Simple Task, with security-sensitive validation.

Rationale: implementation changes are not expected. The work is runtime/Admin API verification plus safe release evidence recording. It touches API key lifecycle and release status, so it requires the full Trellis task/context workflow and security guideline coverage.

## Background

The previous V0.2 release readiness closeout recorded the project as `READY WITH OPERATOR-ACTION REQUIRED`. The implementation, evidence pack, docs, and static scans were complete; the single blocker was that the fresh demo key final server-side status remained `PENDING MANUAL CONFIRMATION` / `UNCONFIRMED`.

This task should move that item to `REVOKED` only after runtime evidence proves:

- the fresh demo key record was identified by safe metadata,
- the key was revoked through Admin API or Admin Console,
- a public `/v1/chat/completions` call with the revoked key returned HTTP `401`,
- the response error code was `invalid_api_key`,
- no plaintext key or raw unsafe runtime material is committed.

## Requirements

- Confirm fresh demo key safe identity metadata:
  - key id,
  - key name,
  - app id,
  - user/admin id only if already needed for the Admin API,
  - status before/after when visible,
  - revocation timestamp when visible.
- Do not record the plaintext key. Do not record `Authorization: Bearer ...`.
- Revoke the key through one of:
  - Admin API: `POST /api/admin/api-keys/{id}/revoke`, or
  - Admin Console action that calls the same backend behavior.
- Verify the revoked plaintext key fails a public gateway call:
  - `POST /v1/chat/completions`
  - expected HTTP `401`
  - expected body shape includes `error.code=invalid_api_key`.
- Record confirmation in one safe location:
  - preferred: `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`,
  - acceptable alternative: update archived `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md` to replace `UNCONFIRMED` with `REVOKED`,
  - if updating the archived release readiness file, keep the change scoped to the fresh demo key status and final decision text.
- Re-run repository secret/forbidden-field scans after writing evidence.
- Archive this task only after confirmation evidence and scans are complete.
- Record the session in the workspace journal after completion.

## Non-Goals / Forbidden Scope

- Do not modify backend source files under `backend/src`.
- Do not modify frontend source files under `frontend/src`.
- Do not modify database migrations.
- Do not modify Docker Compose, CI, or deployment files.
- Do not change API behavior, DTO/VO fields, validation logic, or smoke script behavior.
- Do not generate or store any new long-lived app API key unless the operator explicitly requires it.
- Do not add fallbacks, mock success evidence, or placeholder claims that imply revocation without runtime proof.
- Do not paste raw response bodies if they contain unreviewed fields. Record only HTTP status and the safe error code.

## API / Command / Payload Contract

### List app API keys

Used only to identify the fresh demo key by safe metadata.

```http
GET /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: <admin-user-id>
```

Expected safe fields:

```text
id
app_id
name
key_prefix
status
expires_at
created_at
last_used_at
revoked_at
```

Forbidden fields in committed evidence:

```text
key
key_hash
api_key
api_key_encrypted
Authorization header
plaintext token
```

### Revoke app API key

```http
POST /api/admin/api-keys/{keyId}/revoke
X-Admin-User-Id: <admin-user-id>
```

Expected result:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": "<key-id>",
    "status": "REVOKED",
    "revoked_at": "<timestamp>"
  }
}
```

Only record safe metadata. Do not paste the full raw JSON if it contains unreviewed values.

### Verify revoked key rejection

```http
POST /v1/chat/completions
Content-Type: application/json
Authorization: Bearer <revoked-key>
```

Minimal payload:

```json
{
  "messages": [
    {
      "role": "user",
      "content": "test"
    }
  ]
}
```

Expected result:

```text
HTTP 401
error.code = invalid_api_key
```

Committed evidence may record:

```text
HTTP status = 401
error.code = invalid_api_key
boundary = auth
verification timestamp
```

Committed evidence must not record:

```text
plaintext key
Authorization header
raw request body with real key
raw terminal transcript
raw provider response
answer text
stack trace
```

## Validation / Error Matrix

| Scenario | Expected result | Evidence rule |
|---|---|---|
| Fresh demo key identified by metadata | Key id/name/app id are recorded safely | No plaintext key, no `Authorization` value |
| Key revoke succeeds | Admin API returns `code=OK`, `status=REVOKED`, `revoked_at` present or status already `REVOKED` | Record status/timestamp only |
| Key already revoked | Treat as acceptable if server metadata shows `REVOKED` and 401 verification passes | Record idempotent confirmation |
| Key cannot be found | Do not claim release readiness; record `UNCONFIRMED` with missing metadata | Stop and ask operator for correct app/key metadata |
| Admin revoke returns 403/404 | Do not retry with guessed IDs; record exact safe status and ask operator to confirm ownership/context | No raw response if unsafe |
| Revoked-key gateway call returns 401 + `invalid_api_key` | Confirmation passes | Record HTTP status and error code only |
| Revoked-key gateway call returns 200 | Confirmation fails; key is still active or wrong key was tested | Do not mark release ready |
| Gateway call returns non-401 error | Confirmation fails until root cause is understood | Record status/code only |
| Secret scan finds real generated key or Authorization header | Confirmation fails until removed from working tree | Do not archive task |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh demo key is identified by safe metadata, revoked or already `REVOKED`, 401 verification returns `invalid_api_key`, evidence note records `REVOKED`, release readiness can state `READY FOR V0.2 RELEASE CANDIDATE`, and repository scans show no real secrets/raw runtime evidence. |
| Base | Operator cannot provide runtime access or key metadata in this pass; task records what is missing and keeps the release blocker as `UNCONFIRMED`. No false readiness claim is made. |
| Bad | Plaintext key, real Authorization header, raw terminal transcript, raw response body, provider body, prompt/messages, chunk content, or stack trace is committed; or release readiness is marked unconditional without 401 proof. |

## Acceptance Criteria

- [ ] Fresh demo key safe metadata is recorded: key id/name/app id and final status.
- [ ] Plaintext key is not recorded in any committed file.
- [ ] Admin revoke action is completed or key is confirmed already `REVOKED`.
- [ ] Public `/v1/chat/completions` verification returns HTTP `401`.
- [ ] Public verification confirms `error.code=invalid_api_key`.
- [ ] Evidence note changes fresh demo key status from `UNCONFIRMED` / `PENDING MANUAL CONFIRMATION` to `REVOKED`.
- [ ] Release decision is upgraded to `READY FOR V0.2 RELEASE CANDIDATE` only if all evidence is present.
- [ ] Secret/forbidden-field scan passes with reviewed hits limited to rule text/placeholders/scanner arrays.
- [ ] No backend/frontend/API/DB/infra implementation files are modified.
- [ ] Task is archived and session is recorded after successful confirmation.

## Required Tests and Assertion Points

### Runtime checks

These require local runtime, Admin context, app id, key id, and the plaintext key held outside the repository.

```powershell
# Revoke by safe key id
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "X-Admin-User-Id: <admin-user-id>"
```

Assertion points:

- HTTP status is success.
- envelope `code=OK`.
- `data.status=REVOKED`.
- `data.revoked_at` is present unless the key was already revoked and prior timestamp is retained.
- no `key` or `key_hash` appears in the response.

```powershell
# Verify revoked key rejection; do not paste the real key into repository files
$utf8 = New-Object System.Text.UTF8Encoding($false)
$bodyPath = [System.IO.Path]::GetTempFileName()
$body = '{"messages":[{"role":"user","content":"test"}]}'
[System.IO.File]::WriteAllText($bodyPath, $body, $utf8)
$status = curl.exe -s -o NUL -w "%{http_code}" -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer <revoked-key>" `
  --data-binary "@$bodyPath"
Remove-Item -LiteralPath $bodyPath -Force
Write-Host "HTTP $status"
```

Assertion points:

- HTTP status is `401`.
- response body, if inspected safely outside the repo, has `error.code=invalid_api_key`.
- only status/error code metadata is committed.

### Repository safety checks

Run after evidence files are written:

```powershell
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
rg -n "READY WITH OPERATOR-ACTION REQUIRED|READY FOR V0.2 RELEASE CANDIDATE|UNCONFIRMED|PENDING MANUAL CONFIRMATION|REVOKED|fresh demo key" README.md docs .trellis/spec .trellis/tasks
git status --short
```

Expected:

- no whitespace errors,
- no real generated `sk-sangui-*` key,
- no real `Authorization: Bearer sk-sangui-*`,
- no key hashes or encrypted keys committed,
- release status text is consistent,
- changed files are limited to Trellis task/evidence/session files unless the operator explicitly approves a release-readiness note update.

### Automated unit/build tests

Not required if no implementation files change. If DeepSeek modifies backend/frontend implementation unexpectedly, stop and run the relevant checks before handing back:

```powershell
cd backend
mvn -q "-Dtest=ApiKeyAdminControllerTest,ApiKeyServiceTest,GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest" test
mvn -q -DskipTests compile
```

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

## Expected Files To Modify

Preferred:

- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/prd.md`
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/research.md`
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/task.json`
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/implement.jsonl`
- `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/check.jsonl`
- `.trellis/workspace/sangui/index.md`
- `.trellis/workspace/sangui/journal-2.md`

Optional, only if the executor chooses to update the prior release note instead of a task-local confirmation note:

- `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`

Business implementation files should not be modified.

## Open Questions

- The operator must provide or privately use the runtime-only plaintext key for the 401 verification. It must never be pasted into tracked files.
- If the fresh demo key cannot be uniquely identified by name/app id, the executor must ask for the exact key id or stop with `UNCONFIRMED`.
