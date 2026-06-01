# RAG Demo Hardening and Acceptance Cleanup

Also known in the 2026-06-01 planning handoff as: **RAG Demo Acceptance Observability and Key Hygiene Polish**.

## Goal

Stabilize the current RAG demo acceptance flow after the full-stack Docker Compose and CI baseline. The work should make the demo reproducible on Windows PowerShell 5.1, remove or ignore scattered local manual artifacts, and ensure any previously pasted `sk-sangui-*` demo key is revoked before a clean demo key is generated.

This task is acceptance hardening, not a new product feature. It should reduce demo drift and secret-handling risk without expanding the gateway scope.

## Classification

Complex Task.

Reason: the work crosses admin API key management, public `/v1/chat/completions`, frontend `/v1` proxy behavior, streaming behavior, local demo commands, docs/git hygiene, and optional automation. The implementation risk is low-to-medium, but the acceptance surface is cross-layer.

## Current Project State

- The main RAG chain is implemented and manually accepted: Admin setup, document upload/readiness, app model/KB binding, app API key generation, non-streaming gateway chat, frontend `/v1` proxy, streaming SSE ending in `[DONE]`, request log success with hit chunk IDs, and backend restart persistence.
- Docker Compose and CI baseline are implemented through `deploy/docker-compose.yml`, backend/frontend Dockerfiles, `frontend/nginx.conf`, `.env.example`, README docs, and GitHub Actions.
- The last recorded session explicitly left a follow-up: revoke the exposed local `sk-sangui-*` key and avoid blindly staging local manual smoke artifacts.
- Root-level local artifacts currently include manual smoke/sample files such as `manual-kb.md`, `manual-kb-unique.md`, `manual-rag-smoke.md`, `manual-v1-valid-response.json`, `manual-v1-invalid-response.json`, and the deleted tracked `manual-chat-body.json`.

## Requirements

1. Revoke exposed/expired demo app API keys
   - Use the existing Admin API key revoke behavior.
   - Revoke the specific key record(s) that correspond to the pasted local `sk-sangui-*` demo key if identifiable from Admin UI/list output.
   - Generate a fresh clean demo key through the existing API key creation flow.
   - Do not commit or document the full new plaintext key.

2. Clean or ignore manual smoke artifacts
   - Remove tracked/manual smoke artifacts that should not be part of delivery, or explicitly ignore root-level `manual-*` local artifacts.
   - Do not stage uploaded knowledge data under `backend/data/`.
   - Do not preserve any full API key or provider key in markdown/json artifacts.

3. Solidify a PowerShell 5.1 friendly demo smoke flow
   - The documented flow must work with Windows PowerShell 5.1.
   - Prefer `curl.exe` for HTTP calls and multipart upload examples.
   - Avoid PowerShell 7-only features such as `Invoke-RestMethod -Form`.
   - Avoid UTF-8 BOM issues when writing JSON request bodies. If writing files is needed, use a .NET `UTF8Encoding($false)` pattern.
   - Cover:
     - backend health through `http://localhost:8080/api/health`
     - frontend proxy health through `http://localhost:3000/api/health`
     - frontend `/v1` proxy non-streaming chat completion
     - streaming chat completion with visible SSE chunks and final `[DONE]`
     - request log verification in the Admin UI
   - Passing output should point the tester to the next request-log verification step.
   - Failure output should classify the failing boundary where possible:
     - `health`: backend `/api/health` is down or returns an unexpected envelope.
     - `proxy`: frontend `/api` or `/v1` proxy returns HTML, non-JSON, buffered/missing SSE, or wrong status.
     - `auth`: public `/v1/*` returns `401 invalid_api_key`.
     - `upstream`: gateway returns `upstream_error`, `upstream_timeout`, or a post-start SSE error event.
     - `embedding`: gateway returns `embedding_failed`.
     - `retrieval`: gateway returns `knowledge_base_not_ready`, missing `question_summary`, or empty/missing `hit_chunk_ids` when the demo expects retrieval hits.

4. Optional minimal automated smoke script
   - If implemented, add a small script such as `scripts/demo-smoke.ps1`.
   - It should be PowerShell 5.1 compatible.
   - It may require the caller to pass an app API key rather than reading secrets from source files.
   - It must not create or persist secrets, provider keys, or full prompt logs.
   - It should fail visibly on bad responses; no mock success paths or silent fallback.
   - It should keep the full API key out of logs, temporary files that outlive the process, and committed output.

5. Request Logs acceptance observability
   - Document the exact manual or command-based check for non-streaming RAG request logs.
   - Acceptance evidence should show:
     - `status=success`
     - `model` and `provider_name`
     - `latency_ms` and, where upstream usage is available, `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`
     - `question_summary` matching the demo prompt prefix
     - non-empty `hit_chunk_ids` for a retrieval-hit demo
     - optional hit-chunk summary through the existing read-only endpoint
   - Do not expose full prompts, full chunk content, app API key plaintext, key hash, provider body, or upstream API key in evidence.

6. Optional read-only Admin API smoke command block
   - If added, it must only list safe metadata needed to orient a demo:
     - apps and selected app ID
     - model config names/status/model/provider where already exposed by existing admin APIs
     - knowledge base names/status/dimension where already exposed by existing admin APIs
     - API key `id`, `name`, `key_prefix`, `status`, `last_used_at`, `revoked_at`
   - It must not print plaintext app keys, key hashes, upstream key plaintext, encrypted upstream keys, provider raw responses, full prompt content, chunk content, or embeddings.

7. Spec synchronization
   - If the implementation adds or changes executable demo acceptance rules, update `.trellis/spec/sangui-rag-gateway.md` or an appropriate `.trellis/spec/guides/` document.
   - Do not update specs for incidental README wording only.

8. Preserve existing implementation behavior
   - Do not change RAG retrieval semantics.
   - Do not change gateway request/response contracts unless a real bug is found and explicitly scoped.
   - Do not change database schema.
   - Do not change upstream model provider behavior.
   - Do not add new dependencies unless they are clearly necessary; this task should not require one.

## API / Command / Payload Contracts

### Existing Admin API Key Revoke

```http
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: <positive long>
```

Expected success response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 123,
    "app_id": 1,
    "user_id": 1,
    "name": "demo",
    "key_prefix": "sk-sangui-...",
    "status": "REVOKED",
    "expires_at": null,
    "last_used_at": "...",
    "revoked_at": "...",
    "created_at": "...",
    "updated_at": "..."
  }
}
```

Rules:

- Response must not include `key` or `key_hash`.
- Revoked key must fail public `/v1/*` authentication with OpenAI-compatible `401 invalid_api_key`.

### Existing Admin API Key Create

```http
POST /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: <positive long>
Content-Type: application/json

{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

Rules:

- Full `key` is returned only once in the create response.
- The full plaintext key must not be written into committed docs/scripts/artifacts.
- Subsequent list/revoke responses must show prefix/status metadata only.

### Smoke Script Command Contract (optional)

If a script is added:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<sk-sangui-key>" `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

Default values:

| Parameter | Default | Notes |
|---|---|---|
| `BackendBaseUrl` | `http://localhost:8080` | Direct backend health check. |
| `FrontendBaseUrl` | `http://localhost:3000` | Nginx/Vite same-origin proxy checks. |
| `Message` | A short RAG demo prompt | Safe non-secret prompt. |
| `ApiKey` | Required | Must be supplied by caller; do not read from repo files. |

Required script checks:

| Check | Endpoint | Required assertion |
|---|---|---|
| Backend health | `GET <BackendBaseUrl>/api/health` | JSON contains `code=OK` and `data.status=UP`. |
| Frontend proxy health | `GET <FrontendBaseUrl>/api/health` | JSON, not SPA HTML, contains `code=OK`. |
| Non-streaming chat | `POST <FrontendBaseUrl>/v1/chat/completions` | HTTP 200 and JSON has `choices[0].message.content`. |
| Streaming chat | `POST <FrontendBaseUrl>/v1/chat/completions` with `stream=true` | Output contains `data:` chunks and final `[DONE]`. |
| Request log list/detail | `GET <FrontendBaseUrl>/api/admin/apps/<appId>/request-logs?...` and optional `/{requestId}` | JSON envelope `code=OK`; latest non-streaming request has `status=success`, model/provider, latency, question summary, hit chunk IDs. |
| Hit chunk summary | `GET <FrontendBaseUrl>/api/admin/apps/<appId>/request-logs/<requestId>/hit-chunks` | JSON envelope `code=OK`; summaries are bounded and do not expose full content or embeddings. |

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| Missing `ApiKey` parameter in optional script | Script exits non-zero with a clear message | No request to `/v1/chat/completions` is attempted. |
| Revoked old key used for `/v1/chat/completions` | HTTP 401 OpenAI-compatible `invalid_api_key` | No admin envelope; no key echoed. |
| Fresh active key used for frontend `/v1` proxy | HTTP 200 OpenAI-compatible chat completion JSON | `choices[0].message.content` exists. |
| Frontend `/api/health` proxy misroutes to SPA | Smoke fails visibly | Response must parse as JSON with `code=OK`; HTML is failure. |
| Streaming proxy buffers or drops stream | Smoke fails visibly | Must observe `data:` lines and final `[DONE]`. |
| Backend or frontend not running | Smoke fails non-zero with endpoint/status context | No fake success. |
| Provider/upstream failure during chat | Smoke fails with HTTP status and safe gateway error code | Provider body and keys are not printed. |
| Embedding/query embedding failure | Smoke fails as `embedding` boundary with safe `embedding_failed` code | No upstream key, provider body, vector, or chunk content printed. |
| Knowledge base missing/not ready | Smoke fails as `retrieval` boundary with `knowledge_base_not_ready` | No pass-through success accepted. |
| Non-streaming request succeeds but request log missing required observability fields | Acceptance fails | Request log list/detail must show `success`, model/provider, latency, question summary, and hit chunk IDs for the retrieval-hit demo. |
| Manual artifact contains `sk-sangui-` | Cleanup/check fails | `rg "sk-sangui-" manual-* README.md scripts` must not expose real key material. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Existing exposed demo key is revoked, a fresh key is generated for local demo use only, docs/script run backend health, frontend `/api`, frontend `/v1` non-streaming, frontend `/v1` streaming, and request log manual verification. |
| Base | No script is added; README or a dedicated markdown doc provides copy-pasteable PowerShell 5.1 steps using `curl.exe`, with clear placeholders and no real secrets. |
| Bad | A real `sk-sangui-*` key is committed, manual files remain tracked without an ignore/cleanup decision, smoke docs require PowerShell 7-only syntax, or streaming validation only checks that a server started. |

## Expected Files To Modify

Likely:

- `.gitignore`: ignore root-level `manual-*` local smoke artifacts if cleanup chooses ignore over tracked examples.
- `README.md`: strengthen demo smoke flow with PowerShell 5.1-safe commands and key revocation/rotation guidance.
- `scripts/demo-smoke.ps1` or similar: optional minimal automated smoke script.
- `.trellis/spec/sangui-rag-gateway.md`: only if executable demo acceptance rules are added or changed.
- Possibly remove root-level local manual artifacts from git tracking if they are currently tracked and not intended as docs.

Only if a real issue is discovered:

- `frontend/src/api/openai.ts`: only if the smoke client needs a tiny fix for error surfacing.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: only if the frontend smoke page must support streaming or clearer acceptance behavior.
- `frontend/nginx.conf`: only if streaming proxy behavior is actually wrong.

Avoid unless explicitly approved:

- Backend gateway, retrieval, prompt, document ingestion, schema migrations, and API DTO/VO changes.
- Provider/model configuration behavior.
- New frontend routes or major UI redesign.
- New CI jobs beyond a very small docs/script check.

## Required Tests And Assertion Points

Run after implementation, depending on actual changed files:

```powershell
# Backend targeted checks if API key or gateway behavior is touched
cd backend
mvn -q "-Dtest=ApiKeyAdminControllerTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
```

```powershell
# Frontend checks if frontend code is touched
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

```powershell
# Docs/script/static hygiene from repo root
git diff --check
rg "sk-sangui-[A-Za-z0-9_-]{12,}" README.md .gitignore scripts manual-* .trellis/tasks/06-01-rag-demo-hardening-acceptance-cleanup
```

Manual acceptance:

- Revoke old key through Admin UI/API and verify it no longer works against `/v1/chat/completions`.
- Create fresh key and keep plaintext out of committed files.
- Run the PowerShell 5.1 demo flow or optional script.
- Verify Request Logs page shows `SUCCESS`, resolved model/provider, latency, token usage where available, `question_summary`, and hit chunk IDs for non-streaming RAG request.
- Verify streaming output ends with `[DONE]`.
- If a read-only Admin API smoke block is added, run it and verify it prints only safe metadata and no plaintext keys.

## Planning Self-Check

- Acceptance criteria are explicit for key revoke, fresh key, manual artifact hygiene, PowerShell 5.1 demo flow, optional script, non-streaming, streaming, and request logs.
- Forbidden scope is explicit: no RAG semantics, schema, upstream behavior, or broad UI/backend changes unless a concrete bug is found.
- Expected files are listed.
- Required tests and manual checks are listed.
- Specific backend, frontend, project, and cross-layer guidelines must be read before implementation.
- No current requirement needs user clarification before DeepSeek implementation. If DeepSeek chooses to add the optional script, the exact script filename can be chosen locally, with `scripts/demo-smoke.ps1` preferred.
- No new API, DB, DTO, or frontend type fields are expected.
- Codex planning handoff only: Codex must not change business implementation files in this round. Implementation is reserved for the DeepSeek side after this PRD/context handoff.

## DeepSeek Execution Notes

- Treat this as cleanup and acceptance stabilization.
- Prefer docs/script/git hygiene changes over business-code changes.
- Do not commit real secrets, generated API keys, upstream provider keys, or uploaded knowledge files.
- Use the existing Admin API key revoke/create contracts instead of adding a new key rotation API.
- Use `curl.exe` and PowerShell 5.1-compatible syntax in docs and scripts.
- Fail visibly on bad smoke responses; do not add mock success or silent fallback.
- When improving `scripts/demo-smoke.ps1`, classify failures by boundary (`health`, `proxy`, `auth`, `upstream`, `embedding`, `retrieval`) without printing secrets.
- When improving README acceptance steps, include the request-log verification evidence and revoke-after-demo checklist.
