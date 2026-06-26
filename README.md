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
  - Admin authentication (username/password login, JWT-based session, `POST /api/admin/auth/login`)
  - App management (create, list, detail, disable, enable)
  - API key management (create, list, disable, revoke, one-time display)
  - Model config management (create, update, detail, list, disable, enable, encrypted upstream key storage)
  - App-to-model-config binding
  - Knowledge base management (create, list, detail, delete)
  - Document upload (txt, md, markdown) with sync parsing, chunking, embedding, and status tracking
  - Document delete with storage cleanup
  - App-to-knowledge-base binding with retrieval configuration
  - Request log observability (list, detail, hit chunk summaries, filtering)
- Upstream API key encryption (AES-256-GCM)
- Tenant isolation on all admin and retrieval operations
- Safe structured logging (no secrets, keys, document content, or provider bodies)
- Full-stack Docker Compose one-command deployment

### Roadmap (Not Yet Implemented)

- PDF / DOCX parsing
- API-key level rate limiting
- Source citations in chat responses
- Redis-based rate limit and quota enforcement
- Asynchronous document processing
- Rerank and hybrid retrieval
- MinIO / S3-compatible object storage for production file storage

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

PostgreSQL and Redis are internal-only inside the Compose network by default. Only the backend (`${BACKEND_PORT:-8080}`) and frontend (`${FRONTEND_PORT:-3000}`) are published to the host.

If you need to inspect PostgreSQL or Redis from your local machine (e.g. with a database GUI), use the explicit opt-in override:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d --build
```

This additionally publishes PostgreSQL on `${POSTGRES_PORT:-5432}` and Redis on `${REDIS_PORT:-6379}`.

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

3. **Create an app**: give it a name, then bind the model config and knowledge base through the app detail page. Retrieval configuration (top_k, similarity threshold, context limits) is initialized with documented defaults and persisted per app. Admin UI-based retrieval config editing is planned for a future release.

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

All admin APIs require `Authorization: Bearer <admin-jwt>` (obtained via `POST /api/admin/auth/login`) and return the `ApiResponse<T>` envelope (`code`, `message`, `data`).

| Operation | Method | Route |
|---|---|---|
| Create app | `POST` | `/api/admin/apps` |
| List apps | `GET` | `/api/admin/apps` |
| Get app detail | `GET` | `/api/admin/apps/{id}` |
| Create model config | `POST` | `/api/admin/model-configs` |
| Update model config | `PUT` | `/api/admin/model-configs/{id}` |
| Get model config detail | `GET` | `/api/admin/model-configs/{id}` |
| List model configs | `GET` | `/api/admin/model-configs` |
| Disable app | `POST` | `/api/admin/apps/{id}/disable` |
| Enable app | `POST` | `/api/admin/apps/{id}/enable` |
| Disable model config | `POST` | `/api/admin/model-configs/{id}/disable` |
| Enable model config | `POST` | `/api/admin/model-configs/{id}/enable` |
| Bind app default model config | `PUT` | `/api/admin/apps/{appId}/default-model-config` |
| Bind app default knowledge base | `PUT` | `/api/admin/apps/{appId}/knowledge-base` |
| Delete document | `DELETE` | `/api/admin/documents/{documentId}` |
| Delete knowledge base | `DELETE` | `/api/admin/knowledge-bases/{id}` |
| Create API key | `POST` | `/api/admin/apps/{appId}/api-keys` |
| List API keys | `GET` | `/api/admin/apps/{appId}/api-keys` |
| Disable API key | `POST` | `/api/admin/api-keys/{id}/disable` |
| Revoke API key | `POST` | `/api/admin/api-keys/{id}/revoke` |
| List request logs | `GET` | `/api/admin/apps/{appId}/request-logs` |
| Get request log detail | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}` |
| Get hit chunk summaries | `GET` | `/api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks` |

### Disable and Gateway Impact

Disabling different resources has distinct effects on public `/v1/*` gateway calls:

| Disabled resource | Gateway effect | Error response |
|---|---|---|
| **App** | All API keys under the app fail `/v1/*` auth. | `401` `invalid_api_key` |
| **Model Config** | App key may authenticate, but default model resolution fails for `/v1/models` and `/v1/chat/completions`. | `409` `model_config_not_ready` |
| **API Key** | Only that specific key fails `/v1/*` auth; other keys under the same app remain valid. | `401` `invalid_api_key` |

Disabling is idempotent: disabling an already-disabled resource returns success. Re-enabling restores normal gateway readiness behavior without creating, rotating, or clearing any bindings or keys.

Model config key handling is separate from status lifecycle actions: `PUT /api/admin/model-configs/{id}` without `api_key` preserves the existing encrypted upstream key, blank `api_key` is invalid, and model config disable/enable never rotates or clears the upstream key.

## Error Handling and Safety Boundaries

### Response Envelope by API Type

| API family | Success shape | Error shape |
|---|---|---|
| Public `/v1/*` (gateway) | OpenAI-compatible (e.g. `{"choices":[...]}` for chat) | OpenAI-compatible `{"error":{"message":"...","type":"...","code":"..."}}` |
| `/api/admin/**` (admin) | `ApiResponse<T>` envelope with `code`, `message`, `data` | `ApiResponse<T>` with `code` (e.g. `INVALID_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`), `message`, `data=null` |
| `/api/health` (public) | `ApiResponse<T>` envelope with `code=OK` | N/A |

The two families must never be mixed: `/v1/*` responses are never wrapped in `ApiResponse`, and admin API errors never use the OpenAI-compatible `{"error":{...}}` shape.

### Gateway Error Codes (`/v1/*`)

| Error code | HTTP status | Meaning |
|---|---|---|
| `invalid_api_key` | 401 | Missing, invalid, disabled, revoked, or expired app API key. No key detail or status is exposed. |
| `invalid_request` | 400 | Malformed JSON, null body, missing/empty messages, missing role/content, unsupported role, or raw `IllegalArgumentException` caught at the HTTP boundary. |
| `rate_limit_exceeded` | 429 | App API key exceeded per-minute request limit, per-minute token limit, or daily quota. |
| `model_config_not_ready` | 409 | App has no enabled default model config, no chat model, or no usable upstream key. |
| `knowledge_base_not_ready` | 409 | App has no bound knowledge base or its status is not `READY`. |
| `embedding_failed` | 502 | Query embedding provider returned an error or timed out. |
| `upstream_error` | 502 | Upstream chat provider returned non-2xx, network error, or malformed success body. Provider body is never exposed. |
| `upstream_timeout` | 504 | Upstream chat call timed out. |
| `internal_error` | 500 | Internal failure such as Redis limiter unavailability. No stack traces or keys exposed. |

### Admin API Error Codes

Admin API errors use the `ApiResponse<T>` envelope with these primary codes:

| Code | HTTP | Meaning |
|---|---|---|
| `OK` | 200 | Success. |
| `INVALID_REQUEST` | 400 | Validation failure or raw `IllegalArgumentException` caught at the Admin/common HTTP boundary. |
| `UNAUTHORIZED` | 401 | Missing, non-Bearer, invalid, or expired admin JWT. |
| `FORBIDDEN` | 403 | Authenticated user attempts to access another user's resource. |
| `NOT_FOUND` | 404 | Resource does not exist for the authenticated user. |
| `KNOWLEDGE_BASE_IN_USE` | 409 | Cannot delete a knowledge base still bound to an app. |

### IllegalArgumentException Safety Rule

Raw `IllegalArgumentException#getMessage()` is **not client-safe** by default and must never be returned or logged at HTTP boundaries. The `GlobalExceptionHandler` enforces:

| Boundary | Raw IAE behavior | Safe response |
|---|---|---|
| Public `/v1/*` | Caught by handler; raw message is replaced. | `400` OpenAI-compatible `invalid_request` with generic `Invalid request.` |
| Admin `/api/admin/**` and common endpoints | Caught by handler; raw message is replaced. | `400` Admin `ApiResponse` with `code=INVALID_REQUEST`, generic `Invalid request` |
| Structured logging | Raw IAE message is never logged; only safe metadata. | Exception class, request ID, safe IDs only. |

Client-safe validation messages must be carried by:

- **`BusinessException`** for Admin/common APIs — carries a safe `code` and `message` visible in the Admin `ApiResponse` envelope.
- **`GatewayException`** for public `/v1/*` APIs — carries a safe `message`, `type`, `code`, and HTTP status visible in the OpenAI-compatible error shape.

### Request-Log Persistence Failure

When a gateway request reaches the logging boundary but the database insert fails, the gateway response is NOT affected. A stable ERROR event `request_log.persist_failed` is emitted with only safe fields: `request_id`, `user_id`, `app_id`, `api_key_id`, request-log `status`, `error_code`, and exception class simple name. The exception message, stack trace, command fields (`question_summary`, `output_preview`, `retrieval_evidence`, `hit_chunk_ids`), API keys, Authorization headers, provider bodies, prompt content, and chunk content are **never** logged in persistence failure events.

### Safe Evidence Rules (Runtime Output and Logs)

All smoke scripts, manual commands, request logs, and runtime evidence records must follow these rules:

**Allowed safe fields:**
```
request_id, user_id, app_id, api_key_id, model, provider_name, status,
error_code, latency_ms, upstream_latency_ms, messages_count, question_summary (bounded prefix),
hit_chunk_ids, retrieval_evidence (metadata only), output_capture_status (metadata only),
chunk_id, document_id, knowledge_base_id, source_filename, chunk_index,
HTTP status, boundary label, SSE data line count, content length (non-streaming), script exit code
```

**Forbidden fields (never returned, logged, or committed):**
```
plaintext app API key, real sk-sangui-* key, Authorization header value, upstream provider key,
api_key_encrypted, key_hash, provider raw body, stack trace, Java stack trace, embedding vectors,
prompt, messages, full_messages, augmented_prompt, raw assistant answer, bounded answer preview,
raw SSE payload, chunk content, chunk summary text, storage_path, real .env secrets, uploaded file artifacts
```

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
  -H "Authorization: Bearer $AdminToken" `
  -H "Content-Type: application/json" `
  --data-binary "@$modelConfigBodyPath"
Remove-Item -LiteralPath $modelConfigBodyPath -Force
```

DashScope embedding config:

```powershell
$modelConfigBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($modelConfigBodyPath, '{"name":"demo-dashscope-embedding","provider_name":"openai-compatible","base_url":"https://dashscope.aliyuncs.com/compatible-mode/v1","api_key":"<dashscope-provider-key>","chat_model":"unused-embedding-config","embedding_model":"text-embedding-v4","embedding_dimension":1024,"status":"ENABLED"}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/model-configs" `
  -H "Authorization: Bearer $AdminToken" `
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
$utf8 = New-Object System.Text.UTF8Encoding($false)

# 1. Login and get admin JWT
$loginBodyPath = [System.IO.Path]::GetTempFileName()
try {
  [System.IO.File]::WriteAllText($loginBodyPath, '{"username":"admin","password":"<admin-password>"}', $utf8)
  $loginResp = curl.exe -s -X POST "$BackendBaseUrl/api/admin/auth/login" `
    -H "Content-Type: application/json" `
    --data-binary "@$loginBodyPath"
} finally {
  Remove-Item -LiteralPath $loginBodyPath -Force -ErrorAction SilentlyContinue
}
try {
  $loginJson = $loginResp | ConvertFrom-Json
} catch {
  Write-Host "Login failed: response was not valid JSON" -ForegroundColor Red
  exit 1
}
if ($loginJson.code -ne 'OK') {
  Write-Host "Login failed: code=$($loginJson.code) message=$($loginJson.message)" -ForegroundColor Red
  exit 1
}
$AdminToken = $loginJson.data.access_token
Write-Host "Admin JWT obtained (expires: $($loginJson.data.expires_at))"
```

### Create a model config

```powershell
$modelConfigBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($modelConfigBodyPath, '{"name":"demo-chat","provider_name":"openai-compatible","base_url":"https://example.com/v1","api_key":"<upstream-provider-key>","chat_model":"deepseek-v4-pro","embedding_model":"text-embedding-v4","embedding_dimension":1024,"status":"ENABLED"}', $utf8)
curl.exe -s -X POST "$BackendBaseUrl/api/admin/model-configs" `
  -H "Authorization: Bearer $AdminToken" `
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
  -H "Authorization: Bearer $AdminToken" `
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
  -H "Authorization: Bearer $AdminToken" `
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
  -H "Authorization: Bearer $AdminToken" `
  -H "Content-Type: application/json" `
  --data-binary "@$createKeyBodyPath"
Remove-Item -LiteralPath $createKeyBodyPath -Force
```

Expected: `code=OK`, `data` contains `key` (full plaintext, shown **only once**), `key_prefix`, `id`, `app_id`, `status=ACTIVE`. Copy the `key` immediately; it will never be returned again. Never commit the plaintext key.

### Disable an API key

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/disable" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data` contains `status=DISABLED`. The response does not include `key` or `key_hash`. After disabling, the key must fail public `/v1/*` calls with `401 invalid_api_key`.

### Revoke an API key

```powershell
curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data` contains `status=REVOKED` and `revoked_at`. The response does not include `key` or `key_hash`. After revocation, the key must fail public `/v1/*` calls with `401 invalid_api_key`.

### Request log list

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs?page=1&page_size=5&status=success" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data.items` contains request logs with safe fields: `request_id`, `model`, `provider_name`, `status`, `error_code`, `latency_ms`, `question_summary`, `hit_chunk_ids`.

### Request log detail

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data` contains the full log detail with `user_id`, `updated_at`, and all list fields.

### Hit chunk summaries

```powershell
curl.exe -s "$BackendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>/hit-chunks" `
  -H "Authorization: Bearer $AdminToken"
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

Expected: JSON with `"code":"OK"`, `"data":{"status":"UP","service":"sangui-rag-gateway"}`. If unreachable or returns unexpected content, the boundary is `health`.

### 2. Frontend proxy health (boundary: `proxy`)

```powershell
curl.exe -s "$FrontendBaseUrl/api/health"
```

Expected: JSON (starts with `{`), NOT SPA HTML, with `code=OK`. The backend direct health step owns the full `data.status=UP` / `data.service=sangui-rag-gateway` contract. If the response is HTML or curl fails with connection errors, the boundary is `proxy`.

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
  -H "Authorization: Bearer $AdminToken"
```

Expected: JSON response with `code=OK`, `data.items` containing the latest success log with the fields listed above.

To query a specific request log detail:

```powershell
curl.exe -s "$FrontendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data` contains the full detail including `user_id`, `updated_at`, and all list fields. No full prompts, messages, API keys, or provider bodies are returned.

To query hit-chunk summaries:

```powershell
curl.exe -s "$FrontendBaseUrl/api/admin/apps/<app-id>/request-logs/<request-id>/hit-chunks" `
  -H "Authorization: Bearer $AdminToken"
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
| 1 | Backend health | HTTP 200, `code=OK`, `data.status=UP`, `data.service=sangui-rag-gateway` | `health` |
| 2 | Frontend `/api` proxy health | JSON response (not SPA HTML), `code=OK` | `proxy` |
| 3 | App readiness | `GET /api/admin/apps/{appId}/readiness` returns `code=OK`, `data.overall_status=READY`, checks include app/default_model_config/default_knowledge_base/knowledge_base_status/active_api_key/embedding_config; no forbidden fields in readiness metadata | `readiness` / `retrieval` / `auth` / `embedding` |
| 4 | Model config presence | App has `ENABLED` Sanguicode chat config bound as default | `retrieval` |
| 5 | KB status `READY` | App has bound knowledge base with status `READY` | `retrieval` |
| 6 | Non-streaming chat success | HTTP 200, `choices[0].message.content` present | `upstream` |
| 7 | Streaming SSE success | `data:` chunks received, `data: [DONE]` present | `upstream` |
| 8 | Request-log list/detail | `status=success`, non-blank `model`/`provider_name`, numeric `latency_ms`, non-empty `hit_chunk_ids`; detail returns `user_id`, `updated_at`, and all list fields; no forbidden fields in list, detail, or hit-chunk responses | `request-log` |
| 9 | Hit-chunks safe metadata | `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index` present; no full chunk content | `request-log` |
| 10 | Revoked-key 401 | HTTP 401 with `error.code=invalid_api_key` after key revocation | `auth` |
| 11 | No secrets in output | No API keys, key hashes, encrypted keys, provider bodies, stack traces, or embedding vectors in script output or committed files | — |

### Safe Evidence Fields (Allowed in Script Output and Recorded Evidence)

The smoke script, manual commands, and runtime evidence records may contain only these safe metadata fields:

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
knowledge_base_id (script output label may be kb_id)
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

### Forbidden Output Fields (Never Printed or Committed)

The smoke script, README examples, spec examples, task-local templates, and committed evidence must never contain:

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

### Runtime Evidence Recording

When recording demo acceptance evidence for audit or commit, use the durable [Runtime Evidence Checklist Template](docs/runtime-evidence-checklist.md). The current Trellis task also keeps a task-local copy at `.trellis/tasks/06-10-demo-smoke-runtime-evidence-checklist-finalization/runtime-evidence-checklist.md` for review history. The template provides a metadata-only recording format with `<redacted>` placeholders for secrets and `PASS/FAIL/SKIP` rows for each smoke step.

#### Good / Base / Bad Recording Cases

| Case | Recording rule |
|---|---|
| **Good** (complete pass) | Record only safe metadata: HTTP 200, readiness `overall_status=READY` and check count, non-streaming content length, SSE `[DONE]` and data line count, request-log `request_id`/`model`/`provider_name`/`latency_ms`/`hit_chunk_ids`, hit-chunk `chunk_id`/`document_id`/`knowledge_base_id` (script label `kb_id`)/`source_filename`/`chunk_index`, revoked-key HTTP 401 `invalid_api_key`, script exit code `0`. Never record answer body, raw SSE, keys, prompt, messages, chunk content, or chunk summary text. |
| **Base** (partial or non-ready) | Record readiness `overall_status` and failing check boundaries (`embedding`, `auth`, `retrieval`) without pasting raw readiness JSON that contains unreviewed values. Record request-log no-match failures with query parameters and boundary `request-log`. Record revoked-key failures with HTTP status and safe error code, boundary `auth`. Never record revoked key value. |
| **Bad** (rejected before commit) | Any recording that contains raw assistant answer, bounded answer preview, raw SSE payload, API keys, key hashes, encrypted keys, prompts, messages, full_messages, augmented_prompt, chunk content, chunk summary text, provider raw bodies, embedding vectors, stack traces, or uploaded file artifacts. Do not commit. |

## Key Rotation and Revocation

### Revoke an old API key

```
POST /api/admin/api-keys/{id}/revoke
Authorization: Bearer <admin-jwt>
```

```powershell
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
  -H "Authorization: Bearer $AdminToken"
```

After revocation, the key must fail public `/v1/*` calls with HTTP 401 `invalid_api_key`.

### Disable an API key

```
POST /api/admin/api-keys/{id}/disable
Authorization: Bearer <admin-jwt>
```

```powershell
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/disable" `
  -H "Authorization: Bearer $AdminToken"
```

Expected: `code=OK`, `data` contains `status=DISABLED`. The response does not include `key` or `key_hash`. After disabling, the key must fail public `/v1/*` calls with `401 invalid_api_key`. Disabling an already-disabled key is idempotent. Disabling a revoked key returns `400 INVALID_REQUEST`.

### Create a fresh API key

```
POST /api/admin/apps/{appId}/api-keys
Authorization: Bearer <admin-jwt>
Content-Type: application/json
{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$createBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($createBodyPath, '{"name":"demo-acceptance-20260601","expires_at":null}', $utf8)
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/apps/<app-id>/api-keys" `
  -H "Authorization: Bearer $AdminToken" `
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
     -H "Authorization: Bearer $AdminToken"
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

## Model Config Key Rotation

After updating an upstream provider API key via `PUT /api/admin/model-configs/{id}`, validate that the new key is active and the old key is no longer used.

### Rotate the upstream key

```powershell
$utf8 = New-Object System.Text.UTF8Encoding($false)
$updateBodyPath = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText($updateBodyPath, '{"name":"demo-sanguicode-chat","provider_name":"openai-compatible","base_url":"https://api.sanguicode.com","api_key":"<new-provider-key>","chat_model":"deepseek-v4-pro"}', $utf8)
curl.exe -s -X PUT "$BackendBaseUrl/api/admin/model-configs/<config-id>" `
  -H "Authorization: Bearer $AdminToken" `
  -H "Content-Type: application/json" `
  --data-binary "@$updateBodyPath"
Remove-Item -LiteralPath $updateBodyPath -Force
```

Expected: `code=OK`, `data` contains `api_key_masked` reflecting the new key (masked). The response never includes `api_key_encrypted` or plaintext. Omitting `api_key` from the PUT body preserves the existing encrypted key unchanged.

### Validate after rotation

1. **Verify detail returns masked key**: `GET /api/admin/model-configs/<config-id>` should return the updated `api_key_masked` value.
2. **Run a non-streaming chat**: call `POST /v1/chat/completions` with a valid app API key. A successful HTTP 200 response confirms the new upstream key is decrypted and used correctly.
3. **Run the smoke script**: the full acceptance script validates chat success and request-log persistence after rotation.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

### Key rotation rules

- `PUT /api/admin/model-configs/{id}` without `api_key` preserves the existing encrypted upstream key.
- Blank `api_key` (empty string) is rejected with `400 INVALID_REQUEST`.
- Non-blank `api_key` rotates the encrypted value (new ciphertext, new mask).
- Model config disable/enable never rotates or clears the upstream key.
- The upstream key is encrypted at rest with AES-256-GCM and never returned in admin responses.

## Lost or Leaked API Key Runbook

### If the plaintext key is lost

The full plaintext key is shown only once at creation time and is never stored or recoverable from the backend (only a hash is stored). If the plaintext is lost:

1. **Create a new API key** for the affected app through the Admin UI or API:
   ```powershell
   $utf8 = New-Object System.Text.UTF8Encoding($false)
   $createBodyPath = [System.IO.Path]::GetTempFileName()
   [System.IO.File]::WriteAllText($createBodyPath, '{"name":"replacement-key","expires_at":null}', $utf8)
   curl.exe -s -X POST "$BackendBaseUrl/api/admin/apps/<app-id>/api-keys" `
     -H "Authorization: Bearer $AdminToken" `
     -H "Content-Type: application/json" `
     --data-binary "@$createBodyPath"
   Remove-Item -LiteralPath $createBodyPath -Force
   ```
2. **Copy the new key immediately** from the creation response.
3. **Update all clients** that were using the old key to use the new key.
4. **Optionally revoke the old key** if you are certain no client still needs it. The old key cannot be used without the plaintext, but revoking it removes the hash from the database as a precaution.

### If the plaintext key is leaked

If you suspect the plaintext key has been exposed (e.g., committed to a repository, shared in logs, or visible in a screenshot):

1. **Revoke the leaked key immediately** through the Admin UI or API:
   ```powershell
   curl.exe -s -X POST "$BackendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
     -H "Authorization: Bearer $AdminToken"
   ```
2. **Verify the revoked key is rejected** by calling the gateway with the leaked key:
   ```powershell
   curl.exe -s -X POST "$BackendBaseUrl/v1/chat/completions" `
     -H "Content-Type: application/json" `
     -H "Authorization: Bearer <leaked-key>" `
     -d '{"messages":[{"role":"user","content":"test"}]}'
   ```
   Expected: HTTP `401` with `error.code=invalid_api_key`.
3. **Create a fresh API key** for the affected app and copy it immediately.
4. **Update all clients** to use the new key.
5. **Remove all plaintext artifacts**: clear clipboard, delete scratch files, remove the key from terminal history, and scrub any repositories or logs where the key appeared. Never commit plaintext API keys.

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

The script checks backend health, frontend proxy health, app readiness, non-streaming chat, streaming chat, and optionally request-log persistence and revoked-key rejection. When `-AppId` and `-AdminUserId` are both supplied, it additionally queries the Admin readiness API and request-log API to validate readiness checks and list/detail/hit-chunk evidence. Specifically:

- **Readiness**: validates the frontend-proxied Admin readiness endpoint, requires `overall_status=READY`, verifies all required checks are present, scans readiness metadata recursively for forbidden fields, and classifies non-ready failures by the failing check when possible.
- **List**: finds the latest success log matching the smoke `-Message` prefix, validates safe fields (`status`, `model`, `provider_name`, `latency_ms`, `question_summary`, `hit_chunk_ids`), and scans for forbidden fields.
- **Detail**: fetches `GET /api/admin/apps/{appId}/request-logs/{requestId}`, validates that safe detail fields (`user_id`, `app_id`, `api_key_id`, `model`, `provider_name`, `status`, `latency_ms`, `messages_count`, `question_summary`, `hit_chunk_ids`, `created_at`, `updated_at`) are present, `request_id` matches the list row, and no forbidden fields appear.
- **Hit-chunks**: fetches chunk summaries, validates safe metadata (`chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`), and scans each item for forbidden fields.
- **Non-streaming chat**: prints only content length, never the assistant answer text.

When `-VerifyRevokedKey` is supplied with `-RevokedApiKey`, it verifies that the revoked key is rejected with HTTP 401 and `error.code=invalid_api_key`. The script requires `-ApiKey` (never reads from repo files) and exits non-zero on any failure.

Request-log automation is skipped with a neutral message when both `-AppId` and `-AdminUserId` are missing. Supplying only one of the two is an error. Revoked-key verification is skipped unless `-VerifyRevokedKey` is explicitly supplied.

> **Known drift**: The smoke script (`scripts/demo-smoke.ps1`) currently sends `X-Admin-User-Id` as the Admin API identity header rather than `Authorization: Bearer <admin-jwt>`. The `-AdminUserId` parameter value is passed directly as the header value. This is a known script-level limitation tracked for a future update. For manual Admin API calls, always use `Authorization: Bearer <admin-jwt>` obtained via `POST /api/admin/auth/login` as documented in the Admin API Setup Runbook.

## Frontend Smoke Test Page

The admin console Smoke Test page (`/smoke`) performs the same demo acceptance checks as the PowerShell script through a browser UI. It is an acceptance/operations surface, not a chat playground.

### Steps

| Step | Action | PASS condition | Evidence shown |
|---|---|---|---|
| 1. Non-Streaming | `POST /v1/chat/completions` with `stream=false` | HTTP 200 with valid completion | id, object, model, finish_reason, content length only, token counts |
| 2. Streaming | `POST /v1/chat/completions` with `stream=true` | SSE data chunks received and `[DONE]` present | HTTP status, data line count, chunk count, `[DONE]` present/absent |
| 3. Request-Log | Query Admin request-log list/detail/hit-chunks | Matching success row found with all safe fields | request_id, model, provider_name, latency_ms, messages_count, hit_chunk_ids, detail user_id/updated_at, chunk metadata |
| 4. Revoked-Key | `POST /v1/chat/completions` with revoked key | HTTP 401 with `error.code=invalid_api_key` | Status and error code only |

### Rules

- The plaintext API key is held only in transient in-memory state and is clearable. It is never persisted or logged.
- Non-streaming success shows content length only; the assistant answer body is never rendered.
- Streaming evidence shows chunk count and `[DONE]` status; raw SSE content is not displayed.
- Request-log validation shows only safe metadata; chunk summary text is never rendered in the smoke UI.
- The revoked-key value is never printed or persisted.
- Each step has an explicit PASS/FAIL/SKIP status tag.
- The PowerShell smoke script (`scripts/demo-smoke.ps1`) remains the CLI/CI-style repeatable validation path.

## Development (Local, Without Docker Images)

### Start infrastructure only

PostgreSQL and Redis are internal-only by default. For local development where you run the backend and frontend directly on the host, include the host-ports override to expose PG/Redis to the host:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d postgres redis
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

### Backend

Run the full test suite:

```bash
cd backend
mvn test
```

Targeted backend tests for focused regression checks:

```bash
cd backend
mvn -q -DskipTests compile

# Error handling and IAE safety
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test

# Admin validation and BusinessException
mvn -q "-Dtest=ApiKeyServiceTest,ApiKeyAdminControllerTest,AppAdminControllerTest,ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest" test

# Gateway error mapping and OpenAI-compatible shapes
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,GatewayAuthFilterTest,OpenAiModelsControllerTest" test

# Runtime streaming smoke (RANDOM_PORT, no PostgreSQL/Redis/Docker)
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test

# Backend compile-only check
mvn -q -DskipTests compile
```

### Frontend

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

### Diff Check (Documentation-Only and Script Changes)

```bash
git diff --check
```

### Compose Config Sanity (When Deployment Docs Change)

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | Database name |
| `POSTGRES_USER` | `sangui` | Database user |
| `POSTGRES_PASSWORD` | `sangui_password` | Database password (override in production) |
| `POSTGRES_PORT` | `5432` | PostgreSQL host port (opt-in, used only with `deploy/docker-compose.host-ports.yml`) |
| `REDIS_PORT` | `6379` | Redis host port (opt-in, used only with `deploy/docker-compose.host-ports.yml`) |
| `BACKEND_PORT` | `8080` | Host backend port |
| `FRONTEND_PORT` | `3000` | Host frontend port |
| `SERVER_PORT` | `8080` | Backend container port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring active profile |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/sangui_rag_gateway` | JDBC URL (uses service name `postgres` inside Compose) |
| `SPRING_DATASOURCE_USERNAME` | `sangui` | Datasource username |
| `SPRING_DATASOURCE_PASSWORD` | `sangui_password` | Datasource password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host (uses service name `redis` inside Compose) |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `RAG_GATEWAY_SECRET_KEY` | `local-dev-hs256-secret-change-me-32chars` (dev default) | **Deprecated.** Replaced by `RAG_ADMIN_AUTH_JWT_SECRET` and `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` below. Kept only for backward compatibility; no longer read by AdminJwtService or UpstreamApiKeyEncryptor. |
| `RAG_ADMIN_AUTH_JWT_SECRET` | `local-dev-admin-jwt-secret-change-me-32chars` (dev default) | Admin JWT HS256 signing secret. At least 32 UTF-8 characters. Must be distinct from the encryption secret in production. |
| `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` | `local-dev-aes-key-secret-change-me-32chars` (dev default) | AES-256-GCM encryption master key for upstream provider API keys. At least 32 UTF-8 characters. Must be distinct from the JWT secret in production. To migrate from the deprecated shared secret, copy your old `RAG_GATEWAY_SECRET_KEY` value here. |
| `FILE_STORAGE_TYPE` | `local` | Storage backend type (`local` or `object`) |
| `FILE_STORAGE_LOCAL_PATH` | `./data/uploads` | Upload storage path |
| `FILE_STORAGE_OBJECT_ENDPOINT` | (none) | S3-compatible object storage endpoint |
| `FILE_STORAGE_OBJECT_BUCKET` | (none) | Object storage bucket name |
| `FILE_STORAGE_OBJECT_ACCESS_KEY` | (none) | Object storage access key (secret) |
| `FILE_STORAGE_OBJECT_SECRET_KEY` | (none) | Object storage secret key (secret) |
| `FILE_STORAGE_OBJECT_REGION` | `us-east-1` | Object storage region |
| `FILE_STORAGE_OBJECT_PATH_STYLE_ACCESS` | `true` | Path-style access for MinIO compatibility |
| `RAG_DOCUMENT_CHUNK_SIZE` | `800` | Chunk size for text splitting |
| `RAG_DOCUMENT_CHUNK_OVERLAP` | `100` | Chunk overlap |
| `RAG_DOCUMENT_MAX_FILE_SIZE_BYTES` | `1048576` | Max upload file size |
| `RAG_RETRIEVAL_DEFAULT_TOP_K` | `5` | Default retrieval top-K |
| `RAG_RETRIEVAL_DEFAULT_SIMILARITY_THRESHOLD` | `0.300` | Default retrieval similarity threshold |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHUNKS` | `5` | Max chunks in RAG context |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHARS` | `12000` | Max characters in RAG context |
| `RAG_RETRIEVAL_DEFAULT_MAX_SINGLE_CHUNK_CHARS` | `3000` | Max characters per chunk in context |
| `RAG_PRODUCTION_ALLOW_LOCAL_FILE_STORAGE` | `false` | Explicitly allow local filesystem storage in `prod`/`production` profiles |
| `RAG_PRODUCTION_ALLOW_OUTPUT_CAPTURE` | `false` | Explicitly allow global request-log output capture in `prod`/`production` profiles |
| `RAG_PRODUCTION_ALLOW_WEAK_LOCAL_SECRET` | `false` | Deprecated. The weak placeholder `local-dev-change-me` is now always rejected at startup. This flag no longer bypasses HS256 minimum strength checks. |
| `RAG_API_KEY_LIMITS_ENABLED` | `true` | Enable API-key scoped gateway rate limits |
| `RAG_API_KEY_LIMITS_DEFAULT_REQUESTS_PER_MINUTE` | `60` | Default requests per minute per API key |
| `RAG_API_KEY_LIMITS_DEFAULT_TOKENS_PER_MINUTE` | `60000` | Default estimated tokens per minute per API key |
| `RAG_API_KEY_LIMITS_DEFAULT_DAILY_REQUEST_QUOTA` | `1000` | Default daily request quota per API key |
| `RAG_API_KEY_LIMITS_DEFAULT_DAILY_TOKEN_QUOTA` | `1000000` | Default daily estimated token quota per API key |
| `RAG_API_KEY_LIMITS_DEFAULT_COMPLETION_TOKEN_RESERVATION` | `1024` | Completion token reservation when `max_tokens` is omitted |
| `RAG_GATEWAY_UPSTREAM_CONNECT_TIMEOUT_SECONDS` | `5` | TCP connect timeout in seconds for chat upstream calls |
| `RAG_GATEWAY_UPSTREAM_RESPONSE_TIMEOUT_SECONDS` | `30` | Read/response timeout in seconds for chat upstream calls |
| `RAG_GATEWAY_EMBEDDING_CONNECT_TIMEOUT_SECONDS` | `5` | TCP connect timeout in seconds for embedding calls and probes |
| `RAG_GATEWAY_EMBEDDING_RESPONSE_TIMEOUT_SECONDS` | `30` | Read/response timeout in seconds for embedding calls and probes |
| `RAG_GATEWAY_EMBEDDING_BATCH_SIZE` | `64` | Batch size for embedding calls during document ingestion. Range: 1-2048. |

The legacy `RAG_GATEWAY_UPSTREAM_TIMEOUT_SECONDS` and `RAG_GATEWAY_EMBEDDING_TIMEOUT_SECONDS` keys remain response-timeout fallbacks only when the matching new response timeout key is absent. Connect timeout stays controlled by the new connect timeout keys and defaults to 5 seconds.

Inside Docker Compose, the backend service automatically uses `postgres` and `redis` as hostnames. The `.env.example` contains safe local development defaults. For deployment, override secrets through environment variables or a deployment `.env` file. Production-like profiles (`prod` or `production`) fail startup when local defaults are still active; set the two `RAG_PRODUCTION_ALLOW_*` variables to `true` only as an explicit operational acknowledgement.

## Secret and Provider Key Handling

- `.env.example` contains only safe local development placeholders. It must not contain real API keys, provider keys, or production secrets.
- The `.env` file is gitignored. Deployment secrets should be provided via environment or deployment secret management.
- `RAG_GATEWAY_SECRET_KEY` is **deprecated**. It is no longer the primary source of truth for either admin JWT signing or upstream API key encryption. Use `RAG_ADMIN_AUTH_JWT_SECRET` and `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` instead.
- `RAG_ADMIN_AUTH_JWT_SECRET` is the HS256 signing key for admin JWTs. Rotating it invalidates existing admin tokens but does not affect stored encrypted provider keys.
- `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` is the AES-256-GCM master key for upstream provider API keys. Rotating it requires re-entering model config provider keys.
- The dev profile defaults are distinct non-production placeholders of at least 32 UTF-8 characters suitable for local development.
- The documented placeholder `<set-a-strong-32-char-secret>` is rejected by the startup guard and must be replaced with a real secret.
- In production-like profiles (`prod`/`production`), both secrets must be non-blank, at least 32 characters, must not be the known weak placeholder `local-dev-change-me` or the known local placeholders, and **must not be equal**.
- In non-test profiles without production indicators (e.g. `dev` or no active profile), the guard enforces a minimum of 32 UTF-8 characters. The weak placeholder `local-dev-change-me` (19 characters) is always rejected. The flag `rag.production-guard.allow-weak-local-secret` is deprecated and no longer bypasses this check.
- Upstream provider API keys are configured through the Admin Model Config UI, encrypted at rest with AES-256-GCM, and never returned in admin list/detail responses.
- Generated app API keys (`sk-sangui-*`) are shown only once and stored as hashes. The full key is never persisted in plaintext or returned outside creation.
- Logs never contain API keys, encrypted upstream keys, document content, provider response bodies, or augmented prompts.

## CI

A GitHub Actions workflow (`.github/workflows/ci.yml`) runs on every push and pull request to `main`:

- **Backend**: Maven compile + full test suite (requires PostgreSQL and Redis service containers).
- **Frontend**: `npm ci` + lint + unit/component test + typecheck + production build + visual smoke test.
- **Docker build backend**: Builds backend Docker image via `backend/Dockerfile`. No registry push.
- **Docker build frontend**: Builds frontend Docker image via `frontend/Dockerfile`. No registry push.
- **Compose contract**: Validates default Compose config renders; asserts `postgres` and `redis` have no host `ports`; asserts backend uses service-name dependencies (`postgres:5432`, `SPRING_DATA_REDIS_HOST=redis`); asserts `backend-data:/app/data/uploads` volume mount; validates host-ports override (`deploy/docker-compose.host-ports.yml`) exposes PG/Redis ports only when explicitly included.
- **Runtime smoke**: Starts from a clean Compose runtime state, starts the full stack, waits for backend to report healthy, asserts `/api/health` returns `code=OK` / `data.status=UP` / `data.service=sangui-rag-gateway`, asserts backend container runs as user `sangui`, asserts `/app/data/uploads` is writable, then tears down the stack with volume cleanup (`docker compose down -v --remove-orphans`).
- **Security scan**: Scans committed files (ci.yml, Compose files, Dockerfiles, `.env.example`, `settings.xml`) for docker registry credentials, real `sk-sangui-*` API keys, and provider keys; asserts `backend/Dockerfile` has `USER sangui` without a subsequent `USER root`; asserts `backend/settings.xml` uses only public Maven mirror metadata with Maven Central fallback.

### Failure Boundary Classification

Failures are classified by boundary so the investigator knows where to root-cause:

| Boundary | Examples |
|---|---|
| `backend` | Maven compile or test failures in the `backend` job. |
| `frontend` | Lint, test, typecheck, build, or visual smoke failures in the `frontend` job. |
| `docker-backend` | Backend Docker build failure during `mvn package` or Dockerfile layer construction. |
| `docker-frontend` | Frontend Docker build failure during `npm ci` or `npm run build`. |
| `image-pull` | Docker base image pull failure due to registry network errors, TLS issues, rate limiting, or missing content descriptors. This is an infrastructure boundary, not direct evidence of Dockerfile code failure. Inspect the failed layer and consider a retry. |
| `compose-exposure` | Default Compose config has PG/Redis host ports, or the host-ports override is missing them. |
| `compose-service-discovery` | Backend Compose env uses `localhost` instead of service names `postgres` / `redis`. |
| `runtime-health` | Backend `/api/health` never returns `code=OK` / `data.status=UP` / `data.service=sangui-rag-gateway`. |
| `runtime-user` | Backend container `whoami` is not `sangui`, or `USER root` appears after `USER sangui` in the Dockerfile. |
| `runtime-storage` | `/app/data/uploads` is not writable by the runtime user. |
| `secret-scan` | Committed file contains docker credentials, real API keys, or `settings.xml` credentials. |

### Image-Pull Failures

Docker base image pull failures (registry timeouts, TLS errors, rate limits, "missing content descriptor" messages) are infrastructure-side issues, not Dockerfile code bugs. When these occur:

1. The CI job fails visibly at the `FROM` line or early layer pull step.
2. The failure is classified as `image-pull`, not `docker-backend` or `docker-frontend`.
3. Local validation commands (Maven compile, Dockerfile static assertions, Compose config rendering, secret scan) still provide meaningful evidence.
4. Re-running the job after the registry recovers is the expected recovery path.

### What CI Does Not Need

The CI workflow does not require and must not contain:

- Upstream provider API keys, app API keys, or generated `sk-sangui-*` keys.
- Docker registry login credentials, image push targets, or GHCR publishing.
- Production `.env` files or real deployment secrets.

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
