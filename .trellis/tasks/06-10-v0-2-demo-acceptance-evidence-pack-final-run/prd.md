# V0.2 Demo Acceptance Evidence Pack Final Run

## Goal

Run one formal, auditable, metadata-only V0.2 demo acceptance evidence pass using the stable demo smoke checklist and evidence contract. The output must move the project from "the rules are documented" to "the release decision has a reproducible evidence pack" without committing secrets, raw provider payloads, raw answers, prompts, messages, chunk content, or runtime logs.

## Scope Classification

Complex Task.

Reasons:

- The acceptance path crosses backend health, frontend proxy health, admin readiness, OpenAI-compatible gateway calls, streaming SSE, request-log observability, RAG hit-chunk metadata, API-key revocation, and evidence safety.
- The intended deliverable is task-local evidence and validation notes, not a product feature.
- Business-code changes are out of scope unless the formal run exposes a concrete boundary defect.

## Non-Goals

- Do not add new product features.
- Do not change gateway behavior, RAG retrieval, prompt construction, request-log APIs, frontend types, Docker/CI, or database schema during the planning handoff.
- Do not commit real app keys, revoked keys, upstream provider keys, raw answers, raw SSE streams, prompt/messages, chunk content, provider bodies, stack traces, or full runtime logs.
- Do not silently patch around readiness, request-log, proxy, auth, upstream, or retrieval failures.

## Required Deliverables

- A task-local metadata-only evidence pack, expected path:
  - `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`
- If a run fails before a complete pack can be produced, record a metadata-only failure note in the same task directory with:
  - failing step
  - boundary label
  - safe HTTP/status/error metadata
  - no secrets or raw bodies
- No business implementation files should change unless a concrete runtime defect is confirmed and separately scoped.

## Demo Run Command Contract

Primary command shape:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "<demo question>" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

The evidence pack must record only safe command metadata:

| Field | Record? | Notes |
|---|---:|---|
| Backend base URL | yes | Safe local URL only. |
| Frontend base URL | yes | Safe local URL only. |
| App ID | yes | Numeric metadata. |
| Admin user ID | yes | Numeric temporary identity metadata. |
| Message | yes, bounded | Demo question may be recorded only if it contains no secrets or private data. |
| Fresh app key | no | Record `<redacted>` only. |
| Revoked key | no | Record `<redacted>` only. |
| Raw curl output | no | Summarize safe metadata only. |
| Raw script transcript | no | Do not commit full runtime log. |

## Payload / API Fields Under Acceptance

### Health

```http
GET /api/health
```

Expected safe fields:

- `code=OK`
- `data.status=UP`
- HTTP status

### Readiness

```http
GET /api/admin/apps/{appId}/readiness
X-Admin-User-Id: <admin-user-id>
```

Expected safe fields:

- `overall_status`
- check count
- required check names and statuses:
  - `app`
  - `default_model_config`
  - `default_knowledge_base`
  - `knowledge_base_status`
  - `active_api_key`
  - `embedding_config`

Forbidden fields in readiness evidence:

- `api_key`, `key_hash`, `api_key_encrypted`, `upstream_api_key`
- `authorization`
- `prompt`, `messages`, `full_messages`, `augmented_prompt`
- `content`, `chunk_content`, `summary text content`
- `embedding`
- `provider_response_body`
- `stack_trace`
- `storage_path`

### Chat Completions

```http
POST /v1/chat/completions
Authorization: Bearer <fresh-demo-key>
Content-Type: application/json
```

Request fields under acceptance:

| Field | Expected |
|---|---|
| `messages` | Non-empty array; demo question as last user message. |
| `stream` | `false` or absent for non-streaming; `true` for streaming pass. |
| `temperature`, `max_tokens`, `top_p` | Optional; record only if explicitly used. |

Evidence may record:

- HTTP status
- non-streaming content length only
- streaming SSE data line count
- `[DONE]` observed or not

Evidence must not record:

- answer text
- raw SSE chunks
- full request JSON if it includes sensitive/private text
- Authorization header value

### Request Log

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

Expected safe fields:

- `request_id`
- `model`
- `provider_name`
- `latency_ms`
- `messages_count`
- `question_summary` as bounded prefix only
- `hit_chunk_ids`
- detail `user_id`
- detail `updated_at`
- hit chunk `chunk_id`
- hit chunk `document_id`
- hit chunk `knowledge_base_id` or script label `kb_id`
- hit chunk `source_filename`
- hit chunk `chunk_index`

Forbidden request-log evidence fields:

- full prompt or full messages
- full chunk content
- chunk summary text
- provider raw body
- keys, key hash, encrypted key
- storage path
- stack trace

### Revoked Key

```http
POST /v1/chat/completions
Authorization: Bearer <revoked-demo-key>
```

Expected safe fields:

- HTTP `401`
- `error.code=invalid_api_key`
- boundary `auth`

Evidence must not include the revoked key value.

## Validation / Error Matrix

| Scenario | Expected result | Boundary | Evidence rule |
|---|---|---|---|
| Backend health OK | HTTP 200, `code=OK`, `data.status=UP` | `health` | Record status and code only. |
| Frontend `/api` proxy health OK | HTTP 200 JSON, not SPA HTML | `proxy` | Record JSON envelope and status only. |
| Readiness ready | HTTP 200, `code=OK`, `overall_status=READY`, required checks present | `readiness` | Record status, check count, check names/statuses. |
| Readiness non-ready | Script exits non-zero | `readiness` / `retrieval` / `auth` / `embedding` | Record failing check keys/statuses only; do not paste raw JSON. |
| Non-streaming chat OK | HTTP 200, answer content present | `upstream` | Record content length only. |
| Streaming OK | SSE data lines and `[DONE]` observed | `upstream` | Record line count and done flag only. |
| Request-log match OK | Recent success log matches message prefix and has safe required fields | `request-log` | Record safe metadata fields. |
| Hit-chunk metadata OK | Non-empty safe hit chunk metadata for non-empty `hit_chunk_ids` | `request-log` | Record chunk IDs and metadata only. |
| Revoked key rejected | HTTP 401 with `invalid_api_key` | `auth` | Record status and code only. |
| Forbidden field found | Run fails or pack rejected | matching boundary | Record field name and location only, not value. |
| Raw secret found in evidence file | Evidence rejected | `evidence-safety` | Remove secret and rotate if needed before continuing. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh key, revoked key, ready app, ready KB, enabled chat config, enabled embedding config, backend/frontend running. Smoke exits `0`. Evidence pack records health, readiness, non-streaming, streaming, request-log, hit-chunk, revoked-key, and forbidden-field scan metadata only. |
| Base | One acceptance prerequisite is missing or non-ready. Smoke exits non-zero. Evidence pack records the exact failing step and boundary using safe metadata only. No business code is changed in the same step; defect is routed to the concrete boundary. |
| Bad | Evidence pack includes raw answer, raw SSE, app key, revoked key, upstream key, prompt/messages, chunk content, provider body, stack trace, full runtime logs, or unreviewed JSON bodies. Evidence is not acceptable even if the smoke run passed. |

## Required Tests And Assertion Points

Run only the checks needed for the work actually performed.

### Evidence-only final run

```powershell
# PowerShell 5.1 syntax check
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

```powershell
# Formal metadata-only smoke run, with real keys passed at runtime and never committed.
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "<demo question>" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

```powershell
# Evidence-file forbidden-field scan. Review hits: rule text/placeholders are acceptable; real secrets/raw payloads are not.
rg -n --hidden --glob "!frontend/node_modules/**" --glob "!backend/target/**" --glob "!frontend/dist/**" --glob "!frontend/playwright-report/**" --glob "!frontend/test-results/**" `
  "sk-sangui-|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" `
  .trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run docs README.md scripts
```

### If business code changes become necessary

Backend:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

Frontend, only if frontend source/types change:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Full regression only if a broad cross-layer fix is made:

```bash
cd backend
mvn test
```

## Expected Files

Expected to create or update:

- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/prd.md`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/implement.jsonl`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/check.jsonl`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/debug.jsonl`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`

Possible read-only references:

- `docs/runtime-evidence-checklist.md`
- `README.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `scripts/demo-smoke.ps1`

Business implementation files are not expected to change in the final-run path.

## Planning Self-Check

- [ ] Acceptance criteria are explicit and metadata-only.
- [ ] Prohibited evidence fields are explicit.
- [ ] Business-code modification is prohibited unless a concrete boundary defect is confirmed.
- [ ] Expected task-local evidence path is known.
- [ ] Smoke command contract and input fields are known.
- [ ] Required tests and assertion points are listed.
- [ ] Relevant guideline files were read, not only spec indexes.
- [ ] API / DB / frontend DTO fields are expected to remain unchanged for evidence-only execution.

