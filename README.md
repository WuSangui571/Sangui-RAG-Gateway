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

```powershell
$body = '{"model":"ignored","messages":[{"role":"user","content":"What integration style does Sangui RAG Gateway provide?"}]}'
curl.exe -s -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  -d $body
```

Expected: HTTP 200 with JSON `choices[0].message.content` containing an answer grounded in the uploaded knowledge base.

Failure boundary by error code:
- `401 invalid_api_key` -> boundary `auth`
- `502 upstream_error` / `504 upstream_timeout` -> boundary `upstream`
- `502 embedding_failed` -> boundary `embedding`
- `409 knowledge_base_not_ready` / `409 model_config_not_ready` -> boundary `retrieval`
- Other non-200 or JSON parse failure -> boundary `proxy`

### 4. Streaming chat (via frontend /v1 proxy)

```powershell
$body = '{"model":"ignored","messages":[{"role":"user","content":"What integration style does Sangui RAG Gateway provide?"}],"stream":true}'
curl.exe -s -N -X POST "$FrontendBaseUrl/v1/chat/completions" `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $ApiKey" `
  -d $body
```

The `-N` flag disables output buffering. Expected: visible `data:` SSE chunks and a final `data: [DONE]` at stream end. Same boundary rules apply as non-streaming.

### 5. Verify request logs

Open the admin console Request Logs page for the app. After a successful non-streaming RAG chat, the log entry must show:

- `status`: `success`
- Resolved `model` and `provider_name`
- `latency_ms`
- `usage` (prompt_tokens, completion_tokens, total_tokens) where the upstream provider supplies them; nullable for streaming
- `question_summary` matching the demo prompt prefix (up to 512 chars)
- Non-empty `hit_chunk_ids` (e.g. `[8, 9, 10]`) for a retrieval-hit demo

Optionally, click the Detail button and inspect "Hit Chunks" for chunk summary evidence. The summary is bounded to 200 characters per chunk and does not expose full chunk content or embeddings.

### Notes

- Do not use `curl` (PowerShell alias) — always use `curl.exe`.
- Do not write JSON request bodies to temporary files with default PowerShell encoding to avoid UTF-8 BOM issues. Use inline `$body` variables as shown above.
- If backend or frontend is not running, `curl.exe` will exit non-zero or return empty output. No fake success should be accepted.
- Never commit the full plaintext API key, upstream provider keys, or `backend/data/` to git.

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

### Create a fresh API key

```
POST /api/admin/apps/{appId}/api-keys
X-Admin-User-Id: <your-user-id>
Content-Type: application/json
{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

```powershell
$createBody = '{"name":"demo-acceptance-20260601","expires_at":null}'
curl.exe -s -X POST "$FrontendBaseUrl/api/admin/apps/<app-id>/api-keys" `
  -H "X-Admin-User-Id: 1" `
  -H "Content-Type: application/json" `
  -d $createBody
```

Copy the full `key` field from the create response immediately — it will not be shown again. Never commit the plaintext key to source code or documentation.

### After Demo - Revocation Checklist

After a demo session completes, run these steps to revoke the demo key and clean up:

1. **Revoke the demo API key** through Admin UI or API:
   ```powershell
   curl.exe -s -X POST "$FrontendBaseUrl/api/admin/api-keys/<key-id>/revoke" `
     -H "X-Admin-User-Id: 1"
   ```
2. **Verify the key is rejected** (boundary: `auth`):
   ```powershell
   curl.exe -s -X POST "$FrontendBaseUrl/v1/chat/completions" `
     -H "Content-Type: application/json" `
     -H "Authorization: Bearer <revoked-key>" `
     -d '{"messages":[{"role":"user","content":"test"}]}'
   ```
   Expected: HTTP 401 with `{"error":{"code":"invalid_api_key"}}`.

3. **Delete any local plaintext key copy-paste artifacts** from your terminal clipboard, scratch files, or notes. Do not save the full key to disk.

4. **Remove uploaded knowledge files** from `backend/data/` if they contain proprietary content. The directory is git-ignored but not auto-cleaned.

## Automated Smoke Script (Optional)

A minimal PowerShell 5.1 smoke script is available at `scripts/demo-smoke.ps1`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "sk-sangui-<your-key>" `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?"
```

The script checks backend health, frontend proxy health, non-streaming chat, and streaming chat. It requires `-ApiKey` (never reads from repo files) and exits non-zero on any failure.

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
