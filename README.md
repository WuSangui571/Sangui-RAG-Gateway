# Sangui-RAG-Gateway

> Lightweight OpenAI-compatible RAG enhancement gateway.
>
> This project supports a compatible subset of OpenAI Chat Completions API.

Let existing business systems gain private-document RAG capability with low modification and low user-facing awareness.

## Current Status

**V0.2 beta** — full RAG pipeline with admin console.

### Implemented

- Spring Boot 3.4 backend with health check
- PostgreSQL + pgvector + Redis Docker Compose services
- Flyway database migrations (pgvector, app/api key, model config, knowledge base, document, chunk, embedding, request log)
- App API key authentication for `/v1/*` (Bearer `sk-sangui-*`)
- `GET /v1/models` — OpenAI-compatible model list for authenticated apps
- `POST /v1/chat/completions` — non-streaming and streaming (`stream=true`) pass-through with RAG retrieval
- Admin console:
  - App management (create, list, detail)
  - API key management (create, list, disable, revoke, one-time display)
  - Model config management (create, update, detail, list, disable, encrypted upstream key storage)
  - App-to-model-config binding
  - Knowledge base management (create, list, detail)
  - Document upload (txt, md, markdown) with sync parsing, chunking, embedding, and status tracking
  - App-to-knowledge-base binding with retrieval configuration
  - Request log observability (list, detail, hit chunk summaries, filtering)
- Upstream API key encryption (AES-256-GCM)
- Tenant isolation on all admin and retrieval operations
- Safe structured logging (no secrets, keys, document content, or provider bodies)
- Full-stack Docker Compose one-command deployment

### Roadmap (Not Yet Implemented)

- Admin login / registration (current temporary identity uses `X-Admin-User-Id` header)
- PDF / DOCX parsing
- API-key level rate limiting
- Source citations in chat responses
- Redis-based rate limit and quota enforcement
- Asynchronous document processing
- Rerank and hybrid retrieval
- MinIO for production file storage

## Local Dependencies

| Dependency | Version | Notes |
|---|---|---|
| Java | 21+ | |
| Maven | 3.9+ | |
| Node.js | 20+ | |
| Docker | 24+ | for Docker Compose and image builds |
| Docker Compose | 2.x | |

## Quick Start (Full Stack)

### 1. Clone and prepare environment

```bash
git clone <repo-url>
cd Sangui-RAG-Gateway
cp .env.example .env
```

### 2. Start everything with one command

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

This starts PostgreSQL/pgvector, Redis, backend, and frontend. The backend automatically runs Flyway migrations on first startup.

### 3. Verify health

```bash
curl http://localhost:${BACKEND_PORT:-8080}/api/health
```

Expected response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "status": "UP",
    "service": "sangui-rag-gateway"
  }
}
```

### 4. Open admin console

```text
http://localhost:${FRONTEND_PORT:-3000}
```

## Manual Admin Configuration Smoke Flow

After starting the full stack, configure the gateway through the admin console for a first smoke test:

1. **Create a model config**: navigate to Model Configs, add an OpenAI-compatible provider (e.g. OpenAI, DeepSeek, or any `/v1`-compatible endpoint). Configure `base_url`, `chat_model`, and the provider API key. Leave `api_key` blank on update to preserve the existing encrypted key.

2. **Create a knowledge base**: create a KB with an embedding model name and dimension (e.g. `text-embedding-3-small` / 1536). Upload a `.txt` or `.md` file, then wait for the document status to reach `READY`.

3. **Create an app**: give it a name, then bind the model config and knowledge base through the app detail page. Configure retrieval settings (top_k, similarity threshold, context limits) via the app detail UI.

4. **Create an API key**: under the app, generate a key. Copy the full key immediately — it will not be shown again.

5. **Smoke test**: use the console smoke page or `curl` to call:

   ```bash
   curl -s http://localhost:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer sk-sangui-<your-key>" \
     -d '{"model":"ignored","messages":[{"role":"user","content":"Hello, summarize the uploaded document."}]}'
   ```

6. **Verify**: check the Request Logs page under the app. The log should show status `success`, resolved model/provider, latency, token usage, question summary, and hit chunk IDs.

## Admin API Endpoint Reference

All admin APIs require the temporary identity header `X-Admin-User-Id: <positive-long>` and return the `ApiResponse<T>` envelope (`code`, `message`, `data`).

| Operation | Method | Route |
|---|---|---|
| Create app | `POST` | `/api/admin/apps` |
| List apps | `GET` | `/api/admin/apps` |
| Get app detail | `GET` | `/api/admin/apps/{id}` |
| Create model config | `POST` | `/api/admin/model-configs` |
| Update model config | `PUT` | `/api/admin/model-configs/{id}` |
| Get model config detail | `GET` | `/api/admin/model-configs/{id}` |
| List model configs | `GET` | `/api/admin/model-configs` |
| Disable model config | `POST` | `/api/admin/model-configs/{id}/disable` |
| Bind app default model config | `PUT` | `/api/admin/apps/{appId}/default-model-config` |
| Bind app default knowledge base | `PUT` | `/api/admin/apps/{appId}/knowledge-base` |
| Create API key | `POST` | `/api/admin/apps/{appId}/api-keys` |
| List API keys | `GET` | `/api/admin/apps/{appId}/api-keys` |
| Disable API key | `POST` | `/api/admin/api-keys/{id}/disable` |
| Revoke API key | `POST` | `/api/admin/api-keys/{id}/revoke` |
| List request logs | `GET` | `/api/admin/apps/{appId}/request-logs` |
| Get request log detail | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}` |
| Get hit chunk summaries | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks` |

## Split-Provider Runtime Setup

The demo gateway uses two separate upstream providers:

| Role | Provider | Base URL | Model | Notes |
|---|---|---|---|---|
| Chat | Sanguicode | `https://api.sanguicode.com` | `deepseek-v4-pro` | Used for `POST /v1/chat/completions` upstream forwarding. |
| Embedding | DashScope | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `text-embedding-v4` | Used for document ingestion and query embedding. Dimension: `1024`. |

### Why two providers

- The chat provider handles conversational completions.
- The embedding provider generates vector embeddings for document chunks and query retrieval.
- Both providers must be configured as `ENABLED` model configs under the same admin user.
- The app binds one model config as its default (for chat). The embedding config is resolved automatically by `findEnabledEmbeddingConfig(userId, embeddingModel, embeddingDimension)` during document ingestion and query embedding.

### Model config payloads (split-provider)

**Option A: Two separate model configs (recommended for clarity)**

Sanguicode chat config:

```powershell
$modelConfigBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($modelConfigBodyPath, '{"name":"demo-sanguicode-chat","provider_name":"openai-compatible","base_url":"https://api.sanguicode.com","api_key":"<sanguicode-provider-key>","chat_model":"deepseek-v4-pro","status":"ENABLED"}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/model-configs" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$modelConfigBodyPath"
Remove-Item -LiteralPath $modelConfigBodyPath -Force
```

DashScope embedding config:

```powershell
$modelConfigBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($modelConfigBodyPath, '{"name":"demo-dashscope-embedding","provider_name":"openai-compatible","base_url":"https://dashscope.aliyuncs.com/compatible-mode/v1","api_key":"<dashscope-provider-key>","chat_model":"unused-embedding-config","embedding_model":"text-embedding-v4","embedding_dimension":1024,"status":"ENABLED"}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/model-configs" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$modelConfigBodyPath"
Remove-Item -LiteralPath $modelConfigBodyPath -Force
```

**Option B: One combined model config**

A single config can carry both chat and embedding fields. This works when both providers share the same base URL, but for split providers, Option A is clearer.

### Operational notes

- Bind the Sanguicode chat config as the app default model config via `PUT /api/admin/apps/{appId}/default-model-config`.
- The DashScope embedding config is resolved automatically during document ingestion and query embedding by matching `embedding_model` and `embedding_dimension`.
- Both configs must belong to the same admin user and have status `ENABLED`.
- Current Admin model-config creation requires `chat_model` on every config. For the DashScope embedding config, keep `chat_model` as a non-empty placeholder and do not bind that config as the app default chat config.
- Replace `<sanguicode-provider-key>` and `<dashscope-provider-key>` with actual provider keys. These are encrypted at rest and never returned in responses.

## Admin API Setup Runbook (Formal Commands)

These commands set up the gateway through the Admin API from PowerShell 5.1. All formal commands use UTF-8 no-BOM temp files and `curl.exe --data-binary` to avoid PowerShell 5.1 encoding issues. Set common variables first:

```powershell
$BackendBaseUrl = "http://localhost:8080"
$AdminUserId = "1"
$utf8 = New-Object System.Text.UTF8Encoding($false)
```

### Create a model config

```powershell
$modelConfigBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($modelConfigBodyPath, '{"name":"demo-chat","provider_name":"openai-compatible","base_url":"https://example.com/v1","api_key":"<upstream-provider-key>","chat_model":"deepseek-v4-pro","embedding_model":"text-embedding-v4","embedding_dimension":1024,"status":"ENABLED"}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/model-configs" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$modelConfigBodyPath"
Remove-Item -LiteralPath $modelConfigBodyPath -Force
```

Expected: `code=OK`, `data` contains `id`, `name`, `api_key_masked` (masked, never plaintext or encrypted). Replace `<upstream-provider-key>` with the actual provider key; it is encrypted at rest and never returned in responses.

### Bind app default model config

```powershell
$bindModelBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($bindModelBodyPath, '{"model_config_id":<model-config-id>}', $utf8)
curl.exe -s -X PUT "$BackendBaseUrl/api/admin/apps/<app-id>/default-model-config" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$bindModelBodyPath"
Remove-Item -LiteralPath $bindModelBodyPath -Force
```

Expected: `code=OK`, `data` contains `app_id`, `user_id`, `default_model_config_id`. The model config must belong to the same user and be `ENABLED`.

### Bind app default knowledge base

```powershell
$bindKbBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($bindKbBodyPath, '{"knowledge_base_id":<kb-id>}', $utf8)
curl.exe -s -X PUT "$BackendBaseUrl/api/admin/apps/<app-id>/knowledge-base" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$bindKbBodyPath"
Remove-Item -LiteralPath $bindKbBodyPath -Force
```

Expected: `code=OK`, `data` contains `app_id`, `user_id`, `default_knowledge_base_id`. The knowledge base must belong to the same user and have status `READY`.

### Create an API key

```powershell
$createKeyBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($createKeyBodyPath, '{"name":"demo-acceptance-YYYYMMDD","expires_at":null}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/apps/<app-id>/api-keys" `
  -H "X-Admin-User-Id: $AdminUserId" `
  -H "Content-Type: application/json" `
  --data-binary "@$createKeyBodyPath"
Remove-Item -LiteralPath $createKeyBodyPath -Force
```

Expected: `code=OK`, `data` contains `key` (full plaintext, shown **only once**), `key_prefix`, `id`, `app_id`, `status=ACTIVE`. Copy the `key` immediately; it will never be returned again. Never commit the plaintext key.

### Disable an API key

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/disable" `
  -H "X-Admin-User-Id: $AdminUserId"
```

Expected: `code=OK`, `data` contains `status=DISABLED`. The response does not include `key` or `key_hash`. After disabling, the key must fail public `/v1/*` calls with `401 invalid_api_key`.

### Revoke an API key

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "X-Admin-User-Id: $AdminUserId"
```

Expected: `code=OK`, `data` contains `status=REVOKED` and `revoked_at`. The response does not include `key` or `key_hash`. After revocation, the key must fail public `/v1/*` calls with `401 invalid_api_key`.

### Request log list

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs?page=1&page_size=5&status=success" `
  -H "X-Admin-User-Id: $AdminUserId"
```

Expected: `code=OK`, `data.items` contains request logs with safe fields: `request_id`, `model`, `provider_name`, `status`, `error_code`, `latency_ms`, `question_summary`, `hit_chunk_ids`.

### Request log detail

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>" `
  -H "X-Admin-User-Id: $AdminUserId"
```

Expected: `code=OK`, `data` contains the full log detail with `user_id`, `updated_at`, and all list fields.

### Hit chunk summaries

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>/hit-chunks" `
  -H "X-Admin-User-Id: $AdminUserId"
```

Expected: `code=OK`, `data` contains chunk summaries with `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`, `summary` (bounded to 200 characters). Full chunk content, embeddings, and provider bodies are never returned.

## Demo Acceptance Flow (PowerShell 5.1)

After completing the admin setup above, run these steps from Windows PowerShell 5.1 to validate the full acceptance flow. All commands use `curl.exe` (Windows system curl), not the PowerShell `curl` alias which maps to `Invoke-WebRequest`.

### Failure Boundary Classification

Failures are classified by boundary so the tester knows where to investigate:

| Boundary | Meaning |
|---|---|
| `health` | Backend `/api/health` is down or returns an unexpected envelope. |
| `proxy` | Frontend `/api` or `/v1` proxy returns HTML, non-JSON, buffered/missing SSE, or wrong status. |
| `auth` | Public `/v1/*` returns `401 invalid_api_key` (key is missing, invalid, disabled, revoked, or expired). |
| `upstream` | Gateway returns `upstream_error`, `upstream_timeout`, or a post-start SSE error event. |
| `embedding` | Gateway returns `embedding_failed` (query embedding provider is unreachable or returned an error). |
| `retrieval` | Gateway returns `knowledge_base_not_ready`, `model_config_not_ready`, or RAG retrieval produced no hits. |

Set variables used throughout:

```powershell
$BackendBaseUrl = "http://localhost:8080"
$FrontendBaseUrl = "http://localhost:3000"
$ApiKey = "sk-sangui-<your-key>"
```

### 1. Backend health (boundary: `health`)

```powershell
curl.exe -s "$BackendBaseUrl/api/health"
```

Expected: JSON with `"code":"OK"` and `"data":{"status":"UP"}`. If unreachable or returns unexpected content, the boundary is `health`.

### 2. Frontend proxy health (boundary: `proxy`)

```powershell
curl.exe -s "$FrontendBaseUrl/api/health"
```

Expected: JSON (starts with `{`), NOT SPA HTML. Same `code=OK` and `data.status=UP` as backend. If the response is HTML or curl fails with connection errors, the boundary is `proxy`.

### 3. Non-streaming chat (via frontend /v1 proxy)

Formal acceptance command (UTF-8 no-BOM temp file + `--data-binary`):

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$chatBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($chatBodyPath, '{"model":"ignored","messages":[{"role":"user","content":"What integration style does Sangui RAG Gateway provide?"}]}', $utf8)
curl.exe -s -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  --data-binary "@$chatBodyPath"
Remove-Item -LiteralPath $chatBodyPath -Force
```

A quick inline one-liner is also acceptable for non-formal manual checks:

```powershell
curl.exe -s -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  -d '{"model":"ignored","messages":[{"role":"user","content":"test"}]}'
```

Expected: HTTP 200 with JSON `choices[0].message.content` containing an answer grounded in the uploaded knowledge base.

Failure boundary by error code:
- `401 invalid_api_key` -> boundary `auth`
- `502 upstream_error` / `504 upstream_timeout` -> boundary `upstream`
- `502 embedding_failed` -> boundary `embedding`
- `409 knowledge_base_not_ready` / `409 model_config_not_ready` -> boundary `retrieval`
- Other non-200 or JSON parse failure -> boundary `proxy`

### 4. Streaming chat (via frontend /v1 proxy)

Formal acceptance command (UTF-8 no-BOM temp file + `--data-binary`):

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$streamBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($streamBodyPath, '{"model":"ignored","messages":[{"role":"user","content":"What integration style does Sangui RAG Gateway provide?"}],"stream":true}', $utf8)
curl.exe -s -N -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  --data-binary "@$streamBodyPath"
Remove-Item -LiteralPath $streamBodyPath -Force
```

A quick inline one-liner is also acceptable for non-formal manual checks:

```powershell
curl.exe -s -N -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  -d '{"model":"ignored","messages":[{"role":"user","content":"test"}],"stream":true}'
```

The `-N` flag disables output buffering. Expected: visible `data:` SSE chunks and a final `data: [DONE]` at stream end. Same boundary rules apply as non-streaming.

### 5. Verify request logs (automated)

Run the automated smoke script with `-AppId` and `-AdminUserId` to validate request-log persistence after non-streaming chat:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<your-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "sk-sangui-<revoked-key>" `
  -VerifyRevokedKey
```

When `-AppId` and `-AdminUserId` are both supplied, the script queries the Admin request-log API after non-streaming chat succeeds and validates:

- `status = success`
- Non-blank `model` and `provider_name`
- Numeric non-negative `latency_ms`
- `question_summary` matching the smoke `-Message` prefix (up to 512 chars)
- Non-empty `hit_chunk_ids` for the retrieval-hit demo path
- Hit-chunk summaries return chunk IDs, document IDs, source filenames, and chunk indices

The script prints only safe evidence: request ID, model, provider name, latency, hit chunk IDs/count, and chunk metadata. It never prints chunk summary text, full prompts, API keys, key hashes, or upstream provider bodies.

If both `-AppId` and `-AdminUserId` are missing, the script skips request-log automation with a neutral message and does not turn a passing smoke run into a failure. Supplying only one of the two is an error because the Admin request-log API needs both app scope and admin identity.

### 5b. Verify request logs (manual via PowerShell)

To query the request-log API directly from PowerShell 5.1:

```powershell
curl.exe -s "$FrontendBaseUrl/api/admin/apps/<app-id>/request-logs?page=1&page_size=5&status=success" `
  -H "X-Admin-User-Id: <admin-user-id>"
```

Expected: JSON response with `code=OK`, `data.items` containing the latest success log with the fields listed above.

To query a specific request log detail:

```powershell
curl.exe -s "$FrontendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>" `
  -H "X-Admin-User-Id: <admin-user-id>"
```

Expected: `code=OK`, `data` contains the full detail including `user_id`, `updated_at`, and all list fields. No full prompts, messages, API keys, or provider bodies are returned.

To query hit-chunk summaries:

```powershell
curl.exe -s "$FrontendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>/hit-chunks" `
  -H "X-Admin-User-Id: <admin-user-id>"
```

The script and these commands must use `curl.exe` (Windows system curl), not the PowerShell `curl` alias which maps to `Invoke-WebRequest`.

### 5c. Verify request logs (manual via Admin UI)

Open the admin console Request Logs page for the app. After a successful non-streaming RAG chat, the log entry must show the same safe evidence fields. Optionally, click the Detail button and inspect "Hit Chunks" for chunk summary evidence. The summary is bounded to 200 characters per chunk and does not expose full chunk content or embeddings.

### Notes

- Do not use `curl` (PowerShell alias); always use `curl.exe`.
- For **formal acceptance and regression tests**, write JSON request bodies to temp files using `New-Object System.Text.UTF8Encoding($false)` and submit with `curl.exe --data-binary "@<path>"`. This avoids PowerShell 5.1 `curl.exe -d $variable` encoding corruptions. Quick one-liners using literal inline `-d '{"key":"value"}'` (single-quoted JSON, not variable-based) are acceptable only for non-formal manual checks.
- If backend or frontend is not running, `curl.exe` will exit non-zero or return empty output. No fake success should be accepted.
- Never commit the full plaintext API key, upstream provider keys, or `backend/data/` to git.

## Demo Acceptance Evidence Checklist

After completing the admin setup and running the smoke flow, verify that each item below has concrete runtime evidence. No real API keys, generated `sk-sangui-*` keys, upstream keys, upload artifacts, prompt bodies, chunk contents, or provider bodies should appear in committed files or terminal output.

| # | Check | Expected evidence | Boundary |
|---|---|---|---|
| 1 | Backend health | HTTP 200, `code=OK`, `data.status=UP` | `health` |
| 2 | Frontend `/api` proxy health | JSON response (not SPA HTML), `code=OK` | `proxy` |
| 3 | Model config presence | App has `ENABLED` Sanguicode chat config bound as default | `retrieval` |
| 4 | KB status `READY` | App has bound knowledge base with status `READY` | `retrieval` |
| 5 | Non-streaming chat success | HTTP 200, `choices[0].message.content` present | `upstream` |
| 6 | Streaming SSE success | `data:` chunks received, `data: [DONE]` present | `upstream` |
| 7 | Request-log list/detail | `status=success`, non-blank `model`/`provider_name`, numeric `latency_ms`, non-empty `hit_chunk_ids` | `request-log` |
| 8 | Hit-chunks safe metadata | `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index` present; no full chunk content | `request-log` |
| 9 | Revoked-key 401 | HTTP 401 with `error.code=invalid_api_key` after key revocation | `auth` |
| 10 | No secrets in output | No API keys, key hashes, encrypted keys, provider bodies, stack traces, or embedding vectors in script output or committed files | — |

## Key Rotation and Revocation

### Revoke an old API key

```
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: <your-user-id>
```

```powershell
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "X-Admin-User-Id: 1"
```

After revocation, the key must fail public `/v1/*` calls with HTTP 401 `invalid_api_key`.

### Disable an API key

```
POST /api/admin/api-keys/{id}/disable
X-Admin-User-Id: <your-user-id>
```

```powershell
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/disable" `
  -H "X-Admin-User-Id: 1"
```

Expected: `code=OK`, `data` contains `status=DISABLED`. The response does not include `key` or `key_hash`. After disabling, the key must fail public `/v1/*` calls with `401 invalid_api_key`. Disabling an already-disabled key is idempotent. Disabling a revoked key returns `400 INVALID_REQUEST`.

### Create a fresh API key

```
POST /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: <your-user-id>
Content-Type: application/json
{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$createBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($createBodyPath, '{"name":"demo-acceptance-20260601","expires_at":null}', $utf8)
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/apps/<app-id>/api-keys" `
  -H "X-Admin-User-Id: 1" `
  -H "Content-Type: application/json" `
  --data-binary "@$createBodyPath"
Remove-Item -LiteralPath $createBodyPath -Force
```

Copy the full `key` field from the create response immediately; it will not be shown again. Never commit the plaintext key to source code or documentation.

### After Demo - Revocation Checklist

After a demo session completes, run these steps to revoke the demo key and clean up:

1. **Revoke the demo API key** through Admin UI or API:
   ```powershell
   curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
     -H "X-Admin-User-Id: 1"
   ```
2. **Verify the revoked key is rejected** (boundary: `auth`):
   ```powershell
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
   Expected: HTTP `401` with response body `{"error":{"code":"invalid_api_key"}}`.
   A simpler one-liner using inline `-d` is also acceptable for quick manual checks:
   ```powershell
   curl.exe -s -X POST "$FrontendBaseUrl/v1/chat/completions" `
     -H "Content-Type: application/json" `
     -H "Authorization: Bearer <revoked-key>" `
     -d '{"messages":[{"role":"user","content":"test"}]}'
   ```
   But for formal acceptance verification, prefer the `--data-binary` with UTF-8 no-BOM temp file approach to avoid encoding issues.

3. **Delete any local plaintext key copy-paste artifacts** from your terminal clipboard, scratch files, or notes. Do not save the full key to disk.

4. **Remove uploaded knowledge files** from `backend/data/` if they contain proprietary content. The directory is git-ignored but not auto-cleaned.

## Automated Smoke Script (Optional)

A PowerShell 5.1 smoke script is available at `scripts/demo-smoke.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<your-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "sk-sangui-<revoked-key>" `
  -VerifyRevokedKey
```

| Parameter | Required | Default | Notes |
|---|---|---|---|
| `ApiKey` | yes | none | Plaintext app key used only in Authorization header. Never echoed. |
| `BackendBaseUrl` | no | `http://localhost:8080` | Backend health base URL. |
| `FrontendBaseUrl` | no | `http://localhost:3000` | Frontend proxy and Admin API base URL. |
| `Message` | no | demo question | Used for chat and request-log `question_summary` assertion. |
| `AppId` | no | none | Enables request-log automation when present with `AdminUserId`. |
| `AdminUserId` | no | none | Enables request-log automation when present with `AppId`. |
| `RevokedApiKey` | no | none | Plaintext revoked key used only for one negative auth call. Never echoed. Required when `-VerifyRevokedKey` is supplied. |
| `VerifyRevokedKey` | no | off | Switch to enable revoked-key 401 verification (step 6). |

The script checks backend health, frontend proxy health, non-streaming chat, streaming chat, and optionally request-log persistence and revoked-key rejection. When `-AppId` and `-AdminUserId` are both supplied, it additionally queries the Admin request-log API to validate persistence, field integrity, and hit-chunk evidence. When `-VerifyRevokedKey` is supplied with `-RevokedApiKey`, it verifies that the revoked key is rejected with HTTP 401 and `error.code=invalid_api_key`. The script requires `-ApiKey` (never reads from repo files) and exits non-zero on any failure.

Request-log automation is skipped with a neutral message when both `-AppId` and `-AdminUserId` are missing. Supplying only one of the two is an error. Revoked-key verification is skipped unless `-VerifyRevokedKey` is explicitly supplied.

## Development (Local, Without Docker Images)

### Start infrastructure only

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d postgres redis
```

### Start backend

```bash
cd backend
mvn spring-boot:run
```

### Start frontend

```bash
cd frontend
npm ci
npm run dev
```

The Vite dev server proxies `/api` and `/v1` to `http://localhost:8080`, matching the same-origin production proxy shape.

## Run Tests

```bash
# Backend tests
cd backend
mvn test

# Frontend checks
cd frontend
npm run typecheck
npm run build
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | Database name |
| `POSTGRES_USER` | `sangui` | Database user |
| `POSTGRES_PASSWORD` | `sangui_password` | Database password (override in production) |
| `POSTGRES_PORT` | `5432` | Host PostgreSQL port |
| `REDIS_PORT` | `6379` | Host Redis port |
| `BACKEND_PORT` | `8080` | Host backend port |
| `FRONTEND_PORT` | `3000` | Host frontend port |
| `SERVER_PORT` | `8080` | Backend container port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring active profile |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/sangui_rag_gateway` | JDBC URL (uses service name `postgres` inside Compose) |
| `SPRING_DATASOURCE_USERNAME` | `sangui` | Datasource username |
| `SPRING_DATASOURCE_PASSWORD` | `sangui_password` | Datasource password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host (uses service name `redis` inside Compose) |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `RAG_GATEWAY_SECRET_KEY` | `local-dev-change-me` | AES encryption master key (override in production) |
| `FILE_STORAGE_TYPE` | `local` | Storage backend type |
| `FILE_STORAGE_LOCAL_PATH` | `./data/uploads` | Upload storage path |
| `RAG_DOCUMENT_CHUNK_SIZE` | `800` | Chunk size for text splitting |
| `RAG_DOCUMENT_CHUNK_OVERLAP` | `100` | Chunk overlap |
| `RAG_DOCUMENT_MAX_FILE_SIZE_BYTES` | `1048576` | Max upload file size |
| `RAG_RETRIEVAL_DEFAULT_TOP_K` | `5` | Default retrieval top-K |
| `RAG_RETRIEVAL_DEFAULT_SIMILARITY_THRESHOLD` | `0.300` | Default retrieval similarity threshold |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHUNKS` | `5` | Max chunks in RAG context |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHARS` | `12000` | Max characters in RAG context |
| `RAG_RETRIEVAL_DEFAULT_MAX_SINGLE_CHUNK_CHARS` | `3000` | Max characters per chunk in context |

Inside Docker Compose, the backend service automatically uses `postgres` and `redis` as hostnames. The `.env.example` contains safe local development defaults. For deployment, override secrets through environment variables or a deployment `.env` file.

## Secret and Provider Key Handling

- `.env.example` contains only safe local development placeholders. It must not contain real API keys, provider keys, or production secrets.
- The `.env` file is gitignored. Deployment secrets should be provided via environment or deployment secret management.
- Upstream provider API keys are configured through the Admin Model Config UI, encrypted at rest with AES-256-GCM, and never returned in admin list/detail responses.
- Generated app API keys (`sk-sangui-*`) are shown only once and stored as hashes. The full key is never persisted in plaintext or returned outside creation.
- Logs never contain API keys, encrypted upstream keys, document content, provider response bodies, or augmented prompts.

## CI

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push and pull request to `main`:

- **Backend**: Maven compile + full test suite (requires PostgreSQL and Redis service containers).
- **Frontend**: `npm ci` + typecheck + production build.
- **Docker**: builds both backend and frontend Docker images (no push to registry).

Local equivalent:

```bash
# Backend
cd backend
mvn -q -DskipTests compile
mvn test

# Frontend
cd frontend
npm ci
npm run typecheck
npm run build

# Docker images
docker build -t sangui-rag-gateway-backend:local -f backend/Dockerfile backend
docker build -t sangui-rag-gateway-frontend:local -f frontend/Dockerfile frontend
```

Image push to GHCR is not configured — it requires explicit confirmation of repository owner, image naming, and package permissions.

## Project Structure

```text
Sangui-RAG-Gateway/
backend/                          # Spring Boot backend
  src/main/java/com/sangui/raggateway/
    common/                       # Config, exception, response, security, utils
    app/                          # App management
    apikey/                       # API key management
    model/                        # Model config management
    knowledge/                    # Knowledge base management
    document/                     # Document upload and processing
    embedding/                    # Embedding client
    retrieval/                    # Vector retrieval
    rag/                          # RAG prompt and pipeline
    gateway/                      # OpenAI-compatible public API
      openai/                     # /v1/models, /v1/chat/completions
      completion/                 # Chat completion orchestration
      upstream/                   # Upstream provider client
      stream/                     # Streaming support
    log/                          # Request log persistence and queries
  Dockerfile                      # Multi-stage Maven + Java 21 image
frontend/                         # React 18 + TypeScript + Vite + Ant Design
  src/
    api/                          # HTTP client and OpenAI client
    pages/                        # Admin console pages
    components/                   # Shared UI components
    types/                        # TypeScript type definitions
  Dockerfile                      # Multi-stage Node + Nginx image
  nginx.conf                      # Nginx static serving + /api /v1 proxy
deploy/                           # Docker Compose and infra config
  docker-compose.yml              # Full-stack Compose
.github/workflows/
  ci.yml                          # CI pipeline
.trellis/                         # Workflow and spec files
```

## License

MIT
