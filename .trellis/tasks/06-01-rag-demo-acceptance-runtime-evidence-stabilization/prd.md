# RAG Demo Acceptance End-to-End Runtime Evidence Stabilization

## Task Classification

Complex Task.

This task crosses runtime documentation, smoke automation, public gateway behavior, Admin API evidence, frontend proxy behavior, and secret-safe RAG observability. It should be treated as acceptance-loop stabilization, not new feature development.

## Goal

Stabilize the end-to-end RAG demo acceptance loop so a fresh operator can repeatedly validate the MVP path with concrete runtime evidence:

```text
backend health
frontend /api proxy
Sanguicode chat provider config
DashScope embedding provider config
knowledge base READY
non-streaming RAG chat success
streaming RAG chat success
request-log detail
hit-chunks evidence
revoked-key 401 invalid_api_key
```

The expected result is a clearer runbook, a hardened `scripts/demo-smoke.ps1` contract where needed, and synchronized README/spec executable acceptance rules. Do not expand RAG capability or change business behavior unless a verified script/docs mismatch requires a minimal correction.

## Current Project State From Journal

- V0.2 beta already has the full RAG pipeline, Admin API contracts, README runbooks, and smoke script baseline.
- Recent accepted runtime evidence proved health, frontend proxy, non-streaming RAG chat, streaming SSE `[DONE]`, request-log success fields, and safe hit-chunk evidence.
- Manual runs exposed PowerShell 5.1 JSON encoding issues; formal JSON commands must use UTF-8 no-BOM temp files and `curl.exe --data-binary`.
- Recent cleanup aligned Admin endpoints, especially:
  - `PUT /api/admin/apps/{appId}/default-model-config`
  - `PUT /api/admin/apps/{appId}/knowledge-base`
  - request-log list/detail/hit-chunks endpoints
- Current working tree before this task had only untracked `.kilo/`; do not stage or modify it.

## Requirements

- Document a provider-split demo setup:
  - Sanguicode chat provider for `POST /v1/chat/completions`.
  - DashScope embedding provider for document ingestion/query embedding.
  - Use placeholders only for provider keys and generated app keys.
  - Document known example fields without real secrets:
    - Sanguicode base URL: `https://api.sanguicode.com` or normalized `/v1` equivalent as accepted by current model-config behavior.
    - Chat model: `deepseek-v4-pro`.
    - DashScope compatible base URL: `https://dashscope.aliyuncs.com/compatible-mode/v1`.
    - Embedding model: `text-embedding-v4`.
    - Embedding dimension: `1024`.
- Review and, if necessary, harden `scripts/demo-smoke.ps1`:
  - Parameters must be clear and PowerShell 5.1 compatible.
  - Failure boundaries must stay explicit: `health`, `proxy`, `auth`, `upstream`, `embedding`, `retrieval`, `request-log`, `unknown`.
  - Output must print only safe runtime evidence.
  - JSON bodies must be written as UTF-8 no-BOM temp files and sent by `curl.exe --data-binary`.
  - Script must not echo `-ApiKey`, upstream keys, full prompts, chunk summary text, provider raw bodies, key hashes, encrypted keys, stack traces, or embeddings.
  - If revoked-key verification is added to the script, it must be opt-in and must not require storing plaintext keys in repo files.
- Create or synchronize an evidence checklist covering:
  - backend health
  - frontend `/api` proxy health
  - model config presence and enabled status
  - KB status `READY`
  - non-streaming chat success
  - streaming SSE success with `[DONE]`
  - request-log list/detail success fields
  - hit-chunks safe metadata
  - revoked-key HTTP 401 with OpenAI-compatible `invalid_api_key`
- Sync README and `.trellis/spec/` executable contract only when smoke script behavior or runtime acceptance instructions differ from the current source of truth.
- Keep the task focused on acceptance evidence. Do not implement new provider routing, fallback, retrieval algorithms, frontend UI, database schema, auth model, or RAG prompt behavior.

## API / Command / Payload Fields

### Smoke command

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

Current parameters:

| Parameter | Required | Contract |
|---|---:|---|
| `ApiKey` | yes | Plaintext app API key used only in `Authorization`; never echoed. |
| `BackendBaseUrl` | no | Base URL for direct backend `/api/health`. |
| `FrontendBaseUrl` | no | Base URL for frontend `/api` and `/v1` proxy validation. |
| `Message` | no | Sent to chat and used for request-log `question_summary` prefix matching. |
| `AppId` | no | Enables request-log automation only when supplied with `AdminUserId`. |
| `AdminUserId` | no | Temporary Admin identity header value for request-log APIs. |

If adding revoked-key automation, prefer explicit opt-in parameters such as:

| Parameter | Required | Contract |
|---|---:|---|
| `RevokedApiKey` | no | Plaintext revoked key used only for one negative auth call; never echoed. |
| `VerifyRevokedKey` | no | Switch to enable negative auth verification. |

Do not add parameters that read API keys from repo files.

### Admin setup payloads

Model config create payload for provider-split demo:

```json
{
  "name": "demo-chat-and-embedding",
  "provider_name": "openai-compatible",
  "base_url": "https://example-provider/v1",
  "api_key": "<upstream-provider-key>",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024,
  "status": "ENABLED"
}
```

If the runbook recommends two model configs for clarity, document the operational impact explicitly:

- One Sanguicode-enabled config can be bound as app default model config for chat.
- One DashScope-enabled config can satisfy `findEnabledEmbeddingConfig(userId, embeddingModel, embeddingDimension)`.
- Both configs must be same user, `ENABLED`, and have encrypted upstream keys.
- Avoid ambiguous duplicate enabled embedding configs for the same user/model/dimension unless current implementation is intentionally documented as using latest updated row.

App binding payloads:

```json
{"model_config_id": 123}
{"knowledge_base_id": 456}
```

Public chat payload:

```json
{
  "model": "ignored",
  "messages": [
    {"role": "user", "content": "What integration style does Sangui RAG Gateway provide?"}
  ],
  "stream": true
}
```

## Validation / Error Matrix

| Boundary | Scenario | Expected evidence | Failure handling |
|---|---|---|---|
| `health` | Backend `/api/health` | HTTP 200, `code=OK`, `data.status=UP` | Fail with `health`; do not continue as success. |
| `proxy` | Frontend `/api/health` | JSON response, not SPA HTML | Fail with `proxy`. |
| `retrieval` | App has no enabled model config or no ready bound KB | 409 `model_config_not_ready` or `knowledge_base_not_ready` | Script/README should point to config/KB binding, not upstream. |
| `embedding` | Query embedding provider missing, invalid, timeout, or non-2xx | 502 `embedding_failed` | No upstream chat call expected. |
| `upstream` | Chat provider timeout/non-2xx/malformed | 502 `upstream_error` or 504 `upstream_timeout` | No provider body printed. |
| `auth` | Missing/invalid/disabled/revoked/expired app key | HTTP 401 OpenAI-compatible `invalid_api_key` | Response must not use Admin envelope. |
| `request-log` | Both `AppId` and `AdminUserId` supplied | Latest matching success log found with safe fields | Fail if stale/missing/unsafe/empty evidence. |
| `request-log` | Only one of `AppId` or `AdminUserId` supplied | Non-zero failure | Fail with `request-log`. |
| `request-log` | Neither supplied | Neutral skip | Health/chat/stream still decide exit code. |
| `request-log` | Hit chunks requested | Safe chunk metadata only | Do not print full summary text or chunk content. |

## Acceptance Criteria

- [ ] README clearly explains the Sanguicode chat + DashScope embedding split-provider runtime setup using placeholders only.
- [ ] `scripts/demo-smoke.ps1` is checked against the complete acceptance path and either unchanged with documented rationale or minimally hardened.
- [ ] Smoke script parameter docs, failure boundaries, and safe output fields are aligned across script, README, and spec.
- [ ] Evidence checklist covers backend health, frontend proxy, model config, KB ready, chat success, stream success, request-log detail, hit-chunks, and revoked-key 401.
- [ ] README and `.trellis/spec/sangui-rag-gateway.md` remain synchronized for executable acceptance contracts.
- [ ] PowerShell 5.1 syntax check instructions are preserved.
- [ ] No real API keys, generated `sk-sangui-*` keys, upstream keys, upload artifacts, prompt bodies, chunk contents, or provider bodies are committed.
- [ ] No backend Java, frontend TypeScript, database schema, API contract, RAG retrieval semantics, prompt behavior, Docker, Redis, or MQ behavior is changed unless a concrete runtime acceptance mismatch is discovered and documented before the change.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh demo app has enabled Sanguicode chat config, enabled DashScope embedding config, ready KB, active app key, and bound app config. Smoke passes health/proxy/non-stream/chat stream/request-log/hit-chunks and optional revoked-key negative auth verification. |
| Base | Operator runs smoke without `AppId`/`AdminUserId`. Script skips request-log automation neutrally but still validates backend health, frontend proxy, non-streaming chat, and streaming SSE. |
| Base | Operator uses manual README commands instead of script. Commands are PowerShell 5.1-safe and produce the same evidence checklist. |
| Bad | Script exits 0 when chat passed but request-log fields are stale, missing, unsafe, or empty after request-log validation is enabled. |
| Bad | README/spec/script mention real secrets, generated full app keys, chunk content, full prompts, provider raw bodies, or use PowerShell `curl` alias for formal acceptance. |
| Bad | Implementation changes RAG semantics, provider routing, DB schema, frontend UI, or auth behavior to make the demo pass. |

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, implemented RAG retrieval, request-log Admin API, and automated demo acceptance rule.
- `.trellis/spec/backend/error-handling.md`: public `/v1/*` error shapes, auth failures, RAG retrieval error codes, request-log Admin API error matrix.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields, forbidden sensitive fields, retrieval/prompt logging boundaries.
- `.trellis/spec/backend/quality-guidelines.md`: required backend validation for auth, request logs, retrieval, prompt, streaming.
- `.trellis/spec/gateway/resilience.md`: upstream/embedding timeout and safe failure mapping.
- `.trellis/spec/rag/retrieval-quality.md`: tenant-safe retrieval, `hit_chunk_ids`, no-hit behavior, request-log traceability.
- `.trellis/spec/rag/prompt-context-policy.md`: original message preservation, bounded RAG context, no-fabrication behavior.
- `.trellis/spec/rag/document-ingestion.md`: document/KB status and embedding failure expectations.
- `.trellis/spec/security/rag-security.md`: secret-safe logs, evidence API boundaries, hit chunk safe fields.
- `.trellis/spec/frontend/type-safety.md`: request-log and hit-chunk VO field expectations if frontend docs/types are touched.

### Code Patterns Found

- `scripts/demo-smoke.ps1`: already uses PowerShell 5.1-compatible `curl.exe`, UTF-8 no-BOM temp body files, explicit step boundaries, frontend proxy validation, non-streaming/streaming chat checks, request-log list validation, and safe hit-chunk metadata printing.
- `README.md`: already contains Admin endpoint reference, PowerShell 5.1 formal setup commands, Demo Acceptance Flow, request-log automation docs, key revocation checklist, and smoke script parameter table.
- `.trellis/spec/sangui-rag-gateway.md`: already contains the implemented demo acceptance automation rule, including safe fields, forbidden fields, PowerShell compatibility, required backend tests, and Good/Base/Bad cases.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`: request-log list/detail/hit-chunks endpoint contract.
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`: app default model config and knowledge base binding endpoints.
- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`: revoked/disabled/invalid key boundary returns OpenAI-compatible `401 invalid_api_key`.

### Files Likely To Modify

- `README.md`: add or tighten split-provider runtime acceptance guide and evidence matrix if current docs are incomplete.
- `scripts/demo-smoke.ps1`: only if script contract needs hardening for full evidence loop, parameter help, revoked-key opt-in, or safer output/error boundaries.
- `.trellis/spec/sangui-rag-gateway.md`: sync executable contract if README/script behavior changes.
- Optional docs/spec only if Qwen finds drift:
  - `.trellis/spec/backend/quality-guidelines.md`
  - `.trellis/spec/gateway/resilience.md`
  - `.trellis/spec/security/rag-security.md`

### Files Not Expected To Modify

- `backend/src/main/java/**`
- `backend/src/main/resources/db/migration/**`
- `frontend/src/**`
- `deploy/**`
- `.env`, provider keys, generated app keys, `backend/data/**`, `.kilo/**`

## Required Tests and Assertion Points

Backend targeted tests, each with a hard timeout of 60 seconds:

```powershell
cd backend
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest,OpenAiCompatibleEmbeddingClientTest,DocumentAdminControllerTest,ModelConfigServiceTest,AppAdminControllerTest" test
```

Frontend checks:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Script/static validation:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
git diff --check
rg -n 'sk-sangui-[A-Za-z0-9_-]{20,}|api_key_encrypted|key_hash|provider_response_body|stack_trace|Authorization: Bearer sk-sangui-' README.md scripts .trellis\spec
rg -n 'curl\s' README.md scripts
rg -n 'curl\.exe.*-d\s+\$|Invoke-RestMethod\s+-Form|Invoke-WebRequest' README.md scripts
```

Runtime/manual acceptance instructions to preserve:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

If revoked-key automation is not added to the script, README must still include a formal PowerShell 5.1 manual revoked-key check that captures both HTTP 401 and `error.code=invalid_api_key`.

## Risk / Boundary Notes

- This is not a provider compatibility feature. Do not add routing or fallback to support Sanguicode embeddings if DashScope is the intended embedding provider.
- Do not weaken strict RAG behavior to make smoke pass. Missing KB/model/embedding config should fail at the documented boundary.
- Do not print model answers or chunk summaries unless explicitly bounded and safe. Existing script already prints a bounded answer preview; if changed, ensure no private source text is overexposed.
- Do not rely on PowerShell `curl` alias. Formal commands must use `curl.exe`.
- Do not commit real keys, `.env`, generated API keys, provider keys, upload files, or terminal scratch artifacts.
- Treat `.kilo/` as unrelated untracked local state.

## Planning Self-Check

- Acceptance standards are explicit: yes.
- Forbidden modification scope is explicit: yes.
- Expected modification files are listed: yes.
- Required tests and assertion points are listed: yes.
- Specific guidelines were read, not just spec index: yes.
- Open questions requiring user confirmation: none currently.
- API/DB/frontend types/DTO alignment risk: low if task stays docs/script/spec; any actual API/DTO change is out of scope.
