# Demo Smoke Runtime Evidence Checklist Finalization

## Scope Classification

Complex Task.

Reason:
- The work is primarily documentation/runbook/template consolidation, but it crosses the canonical demo smoke command, Admin readiness API, request-log list/detail/hit-chunks evidence, revoked-key auth verification, README safety rules, and project spec contracts.
- It changes what future demo acceptance evidence may be committed, so it must define command fields, allowed metadata, forbidden fields, validation/error matrix, and Good/Base/Bad cases before implementation.
- This round uses split execution: Codex prepares PRD, research, Trellis context, and handoff only. DeepSeek will perform the actual documentation/template edits later.

## Goal

Finalize a small, reusable runtime evidence checklist/runbook for demo smoke acceptance so future demo runs can record repeatable metadata-only evidence without ad-hoc judgment.

The checklist must align:
- `scripts/demo-smoke.ps1` actual output and failure boundaries.
- `README.md` demo acceptance and safe evidence sections.
- `.trellis/spec/sangui-rag-gateway.md` implemented demo acceptance automation rule.
- Task-local runtime evidence templates or examples.

## Non-Goals / Forbidden Scope

- Do not modify backend business code, frontend business code, database migrations, DTO/VO field contracts, auth logic, retrieval logic, prompt logic, or gateway behavior.
- Do not add new features, new smoke steps, new CI jobs, new provider support, or a generic testing platform.
- Do not record or commit raw assistant answers, raw SSE payloads, raw request/response bodies, API keys, upstream provider keys, prompts, messages, chunk content, chunk summary text, provider raw bodies, embeddings, stack traces, `.env`, uploaded files, or generated `sk-sangui-*` values.
- Do not introduce silent fallbacks, mock success, or "best effort" passing evidence.
- Prefer documentation/template sync only. Do not change `scripts/demo-smoke.ps1` unless DeepSeek finds a concrete executable mismatch that cannot be solved by documenting the existing behavior; even then, changes must be limited to metadata-only output/help text and must not alter gateway/backend/frontend behavior.

## Current Project State Summary

Latest journal entry records `Admin smoke readiness demo acceptance evidence` completed and committed as `5c8c546`.

Current canonical smoke chain covers:
- backend health;
- frontend `/api` proxy health;
- Admin app readiness through `/api/admin/apps/{appId}/readiness`;
- non-streaming chat via `/v1/chat/completions`;
- streaming SSE with `[DONE]`;
- request-log list/detail validation;
- hit-chunks safe metadata validation;
- revoked-key `401 invalid_api_key`;
- recursive forbidden-field scans for readiness/list/detail/hit-chunks.

Current worktree already has unrelated Trellis archive/journal changes from the previous task. Do not revert or rewrite those existing changes.

## Findings From Focused Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary, implemented readiness baseline, request-log Admin API, demo smoke automation rule, safe/forbidden evidence fields, Good/Base/Bad cases.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields and forbidden log/response fields.
- `.trellis/spec/backend/error-handling.md`: OpenAI-compatible gateway errors, Admin envelope errors, request-log and auth failure contracts.
- `.trellis/spec/backend/quality-guidelines.md`: required focused tests for readiness, request-log, auth, gateway, retrieval, and full backend regression.
- `.trellis/spec/gateway/resilience.md`: upstream timeout/error normalization, streaming failure boundaries, request-log failure persistence.
- `.trellis/spec/rag/retrieval-quality.md`: tenant-safe retrieval, `hit_chunk_ids`, no-hit behavior, request-log traceability.
- `.trellis/spec/rag/prompt-context-policy.md`: no prompt leakage and no raw augmented prompt exposure.
- `.trellis/spec/rag/document-ingestion.md`: chunk metadata and document content boundaries.
- `.trellis/spec/security/rag-security.md`: safe evidence, tenant isolation, request-log/hit-chunk exposure limits.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this task touches command/runbook contracts across gateway, Admin API, RAG evidence, streaming, logs, and secrets.
- `.trellis/spec/frontend/type-safety.md`: request-log/readiness frontend DTO field alignment if frontend docs/types are touched.
- `.trellis/spec/frontend/quality-guidelines.md`: smoke UI safe display and no secret/content rendering expectations.

### Code Patterns Found

- `scripts/demo-smoke.ps1` prints:
  - safe startup metadata: backend URL, frontend URL, message length, app ID, admin user ID;
  - readiness metadata: `overall_status`, check count, required check presence, forbidden-field scan result;
  - non-streaming evidence: HTTP 200 and content length only;
  - streaming evidence: SSE data chunk count and `[DONE]` presence;
  - request-log evidence: `request_id`, `model`, `provider_name`, `latency_ms`, `hit_chunk_ids` array/count;
  - detail evidence: `request_id`, `user_id`, `messages_count`;
  - hit-chunk metadata: `chunk_id`, `document_id`, `knowledge_base_id` printed as `kb_id`, `source_filename`, `chunk_index`;
  - revoked-key metadata: HTTP 401 and `error.code=invalid_api_key`.
- `scripts/demo-smoke.ps1` forbidden-field scanner currently scans these property names recursively: `key_hash`, `api_key`, `api_key_encrypted`, `provider_response_body`, `stack_trace`, `embedding`, `prompt`, `messages`, `full_messages`, `augmented_prompt`, `authorization`, `upstream_api_key`, `storage_path`, `content`, `chunk_content`.
- `README.md` Demo Acceptance Evidence Checklist includes readiness and request-log/hit-chunk/revoked-key checks, but its "Safe Evidence Fields" list does not currently include readiness `overall_status` and check count even though the script and project spec allow them.
- `.trellis/spec/sangui-rag-gateway.md` already allows `readiness overall_status and check count` in safe evidence fields.
- Historical `.trellis/tasks/archive/2026-06/06-01-rag-demo-acceptance-runtime-evidence-stabilization/runtime-evidence.md` is useful as an older template, but it includes bounded answer preview examples. The new final checklist must prohibit raw/bounded assistant answer text and record content length only.

## Expected Files To Modify

Default expected scope:
- `README.md`: synchronize Demo Acceptance Flow, Evidence Checklist, Safe Evidence Fields, Forbidden Output Fields, and Good/Base/Bad examples with `scripts/demo-smoke.ps1` and project spec.
- `.trellis/spec/sangui-rag-gateway.md`: add or tighten a short runtime evidence checklist/runbook contract if README changes establish a durable project rule.
- `.trellis/tasks/06-10-demo-smoke-runtime-evidence-checklist-finalization/runtime-evidence-checklist.md`: new task-local reusable template with Good/Base/Bad examples and metadata-only recording format.

Only if strictly necessary:
- `scripts/demo-smoke.ps1`: metadata-only help/output label consistency, no behavioral expansion. Avoid this unless a concrete mismatch cannot be solved in docs.

Do not modify:
- `backend/src/**`
- `frontend/src/**`
- `backend/src/main/resources/db/**`
- `deploy/**`
- `.github/workflows/**`
- provider/runtime secret files such as `.env` or `backend/data/**`

## Command / Payload Contract

Canonical full smoke command:

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

Parameters:

| Parameter | Required | Evidence rule |
|---|---:|---|
| `ApiKey` | yes | Plaintext app key used only in `Authorization`; never printed or committed. |
| `BackendBaseUrl` | no | Safe URL metadata may be recorded. |
| `FrontendBaseUrl` | no | Safe URL metadata may be recorded. |
| `Message` | no | Record message label/length or known demo prompt only; do not record arbitrary private message content. |
| `AppId` | no | Safe numeric metadata; enables readiness and request-log checks when paired with `AdminUserId`. |
| `AdminUserId` | no | Safe numeric temporary identity metadata; do not treat as future production auth evidence. |
| `RevokedApiKey` | no | Plaintext revoked key used only for negative auth call; never printed or committed. |
| `VerifyRevokedKey` | no | Enables revoked-key 401 assertion. |

Admin API evidence surfaces:

```http
GET /api/admin/apps/{appId}/readiness
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
X-Admin-User-Id: <adminUserId>
```

Gateway evidence surface:

```http
POST /v1/chat/completions
Authorization: Bearer <app-api-key>
Content-Type: application/json
```

Supported request fields remain the existing OpenAI-compatible subset: `model`, `messages`, `temperature`, `max_tokens`, `top_p`, `stream`.

## Allowed Metadata

Runtime evidence records may include only:

```text
backend base URL
frontend base URL
app_id
admin_user_id
message length or known non-sensitive demo message label
readiness overall_status
readiness check count
readiness required check names and statuses
request_id
model
provider_name
latency_ms
messages_count
hit_chunk_ids (numeric IDs and count)
chunk_id
document_id
knowledge_base_id
source_filename
chunk_index
HTTP status
boundary label
SSE data line count
SSE [DONE] present/absent
non-streaming content length only
script exit code
test command names and PASS/FAIL result
```

## Forbidden Fields

Runtime evidence records, README examples, spec examples, task-local templates, and committed files must not contain:

```text
plaintext app API key
real generated sk-sangui-* key
Authorization header value
upstream provider key
api_key
key_hash
api_key_encrypted
provider_response_body
provider raw body
stack_trace
Java stack trace
embedding
embedding vectors
prompt
messages
full_messages
augmented_prompt
raw assistant answer
bounded assistant answer preview
raw SSE payload
chunk content
chunk_content response field
chunk summary text
storage_path
real .env secrets
backend/data upload artifacts
Playwright report/artifact contents
```

## Validation / Error Matrix

| Scenario | Expected evidence behavior | Boundary |
|---|---|---|
| Full smoke passes | Record metadata-only PASS rows for all enabled steps and exit code `0`. | n/a |
| Readiness `overall_status != READY` | Record status/check metadata only; classify by failing check when possible; do not proceed as accepted full smoke. | `readiness` / `retrieval` / `auth` / `embedding` |
| Readiness response contains forbidden fields | Fail evidence check; record only offending field names, not values. | `readiness` |
| Request-log list has no matching `question_summary` prefix | Fail as stale/missing evidence; do not accept another success row. | `request-log` |
| Request-log list/detail missing required safe fields | Fail and record missing field names only. | `request-log` |
| `hit_chunk_ids` empty for retrieval-hit demo | Fail full smoke evidence unless explicitly documented as a no-hit Base case. | `request-log` |
| Hit-chunks endpoint returns empty while `hit_chunk_ids` is non-empty | Fail. | `request-log` |
| Hit-chunks includes forbidden content fields | Fail and record offending field names only. | `request-log` |
| Revoked-key check skipped because `-VerifyRevokedKey` absent | Record SKIP as Base only, not full revoked-key acceptance. | `auth` |
| `-VerifyRevokedKey` supplied but key blank | Fail. | `auth` |
| Revoked key returns non-401 or wrong error code | Fail. | `auth` |
| Script output or evidence template contains raw answer/SSE/key/prompt/chunk content | Fail documentation review before commit. | security |

## Good / Base / Bad Examples

### Good: Complete Passing Smoke

Record only:
- backend health: HTTP 200, `code=OK`, `data.status=UP`;
- frontend proxy health: HTTP 200 JSON, not SPA HTML;
- readiness: `overall_status=READY`, required checks present, check count;
- non-streaming chat: HTTP 200, content length only;
- streaming chat: SSE data line count and `[DONE]` present;
- request-log: matching `request_id`, `model`, `provider_name`, `latency_ms`, `messages_count`, numeric `hit_chunk_ids`;
- hit-chunks: `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`;
- revoked-key: HTTP 401, `error.code=invalid_api_key`;
- script exit code `0`;
- forbidden-field scan PASS.

Do not record the answer body, raw SSE, keys, prompt, messages array, chunk content, or chunk summary text.

### Base: Readiness Not READY

Record only:
- command mode and safe IDs;
- readiness `overall_status`;
- failing readiness check key/status metadata;
- failure boundary, such as `embedding`, `auth`, `retrieval`, or `readiness`;
- script exit code non-zero.

Do not paste readiness raw JSON if it contains any unreviewed values.

### Base: Request-Log No Matching Row

Record only:
- non-streaming chat metadata that triggered log persistence;
- request-log list query parameters (`page=1`, `page_size=5`, `status=success`);
- failure: no recent success log matched the smoke `Message` prefix;
- boundary `request-log`;
- script exit code non-zero.

Do not accept stale request rows as substitute evidence.

### Bad: Revoked-Key Verification Fails

Record only:
- revoked-key step enabled;
- observed HTTP status;
- observed safe error code if present;
- expected `401 invalid_api_key`;
- boundary `auth`;
- script exit code non-zero.

Never record the revoked key value.

## Acceptance Criteria

- [ ] README and project spec safe evidence field lists are consistent with the current `scripts/demo-smoke.ps1` output, including readiness `overall_status` and check count.
- [ ] README/spec/template explicitly prohibit raw answer text, bounded answer previews, raw SSE payloads, keys, prompts, messages, chunk content, chunk summary text, provider raw bodies, embeddings, stack traces, and uploaded artifacts.
- [ ] A task-local runtime evidence checklist/template exists with Good/Base/Bad examples for:
  - complete pass;
  - readiness non-READY;
  - request-log no matching row;
  - revoked-key failure.
- [ ] Template records metadata only and has placeholders such as `<redacted>`, `<app-id>`, `<request-id>`, never real secrets.
- [ ] Validation matrix and required tests are present in README/spec or task-local template.
- [ ] No backend/frontend business files are modified unless a concrete contract mismatch is found and explicitly justified.
- [ ] `git diff --check` passes.
- [ ] If `scripts/demo-smoke.ps1` changes, PowerShell PSParser syntax check passes.
- [ ] If only docs/task template/spec change, no backend/frontend test run is required; document that tests were not run because behavior was unchanged.

## Required Tests / Checks

Always run after DeepSeek edits:

```powershell
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{8,}|api_key_encrypted|key_hash|provider_response_body|stack_trace|augmented_prompt|chunk_content|Authorization: Bearer sk-sangui-" README.md .trellis\spec .trellis\tasks\06-10-demo-smoke-runtime-evidence-checklist-finalization scripts
```

If `scripts/demo-smoke.ps1` changes:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

If backend behavior changes unexpectedly, stop and ask the user before continuing. If approved later, targeted backend tests would be:

```powershell
cd backend
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

If frontend source/types change unexpectedly, stop and ask the user before continuing. If approved later, checks would be:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden modification scope is explicit.
- [x] Expected files to modify are listed.
- [x] Required checks/tests are listed.
- [x] Specific guidelines were read, not just spec indexes.
- [x] API/command/payload fields are aligned with existing contracts.
- [x] Validation/error matrix is included.
- [x] Good/Base/Bad examples are included.
- [x] No user clarification is required before DeepSeek starts; provider keys/app IDs are only needed for real manual smoke and must never be committed.
