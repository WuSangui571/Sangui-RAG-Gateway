# Sangui-RAG-Gateway

[中文](README.zh-CN.md)

> Lightweight OpenAI-compatible RAG enhancement gateway.
>
> This project supports a compatible subset of OpenAI Chat Completions API.

Let existing business systems gain private-document RAG capability with low modification and low user-facing awareness.

## Current Status

**V0.2 beta** - full RAG pipeline with admin console.

### Implemented

- **Backend**: Spring Boot 3.4, Java 21, Flyway + PostgreSQL/pgvector, Redis, MyBatis-Plus
- **Frontend**: React 18, TypeScript, Vite, Ant Design admin console
- **Gateway**: `GET /v1/models`, `POST /v1/chat/completions` (non-streaming and streaming)
- **Admin**: login, model configs, knowledge bases, document upload/status/retry/delete, apps, API keys, smoke/test chat, request logs
- **RAG**: app-bound KB retrieval, prompt augmentation, request-log safe evidence, opt-in source citations
- **Rate limits**: Redis-backed API-key request/token quotas for public chat calls
- **Auth**: public `/v1/*` uses app API keys (Bearer `sk-sangui-*`), admin uses JWT
- **Secrets**: upstream API keys encrypted at rest (AES-256-GCM), app keys hashed, full key shown once only
- **Deployment**: full-stack Docker Compose one-command start

### Roadmap (Not Yet Implemented)

- PDF / DOCX parsing
- Asynchronous document processing
- Rerank and hybrid retrieval
- MinIO / S3-compatible object storage for production

## Quick Start

### Prerequisites

| Dependency | Version |
|---|---|
| Docker | 24+ |
| Docker Compose | 2.x |

### 1. Prepare environment

```bash
git clone https://github.com/WuSangui571/Sangui-RAG-Gateway.git
cd Sangui-RAG-Gateway
cp .env.example .env
```

### 2. Start everything

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

This starts PostgreSQL/pgvector, Redis, backend (port 8080), and frontend (port 3000). Flyway migrations run automatically on first startup.

PostgreSQL and Redis are internal-only inside the Compose network by default. If you need local host access (e.g. for a database GUI), add the opt-in override:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d --build
```

### 3. Verify health

```bash
curl http://localhost:8080/api/health
```

Expected:

```json
{"code":"OK","message":"success","data":{"status":"UP","service":"sangui-rag-gateway"}}
```

### 4. Open admin console

```
http://localhost:3000
```

Default dev credentials: `admin` / `admin123`.

> For production, replace all secrets in `.env`, especially `RAG_ADMIN_AUTH_JWT_SECRET` and `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`, with strong values of at least 32 characters.

## First Admin Setup Flow

After deployment, configure the gateway through the admin console:

1. **Create a model config**: navigate to Model Configs, add an OpenAI-compatible provider. Configure `base_url`, `chat_model`, and the provider API key.
2. **Create a knowledge base**: set embedding model name and dimension (e.g. `text-embedding-v4` / 1024). Upload a `.txt` or `.md` file and wait for status to reach `READY`.
3. **Create an app**: give it a name, then bind the model config and knowledge base through the app detail page.
4. **Create an API key**: under the app, generate a key. **Copy the full key immediately** - it will never be shown again.
5. **Call the gateway**:

   ```bash
   curl -s http://localhost:8080/v1/chat/completions \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $SANGUI_APP_API_KEY" \
     -d '{"model":"ignored","messages":[{"role":"user","content":"Summarize the uploaded document."}]}'
   ```

6. **Verify**: check the Request Logs page under the app for status, latency, token usage, and hit chunk IDs.

## Gateway API

### Supported Endpoints

| Endpoint | Method | Description |
|---|---|---|
| `/v1/models` | `GET` | List models available to the authenticated app |
| `/v1/chat/completions` | `POST` | RAG-enhanced chat (non-streaming and streaming) |

### Supported Payload Fields

`model`, `messages`, `temperature`, `max_tokens`, `top_p`, `stream`

### Source Citations

Non-streaming chat responses omit `sangui_citations` by default. To include bounded citation metadata, send:

```http
X-Sangui-Return-Citations: true
```

Streaming responses do not emit citation SSE events; request logs still keep safe retrieval evidence.

### Unsupported

The following OpenAI APIs and features are **not** supported:

`/v1/responses`, `/v1/embeddings`, `/v1/images`, tools, function calling, vision, audio, `response_format`

### Auth

Public gateway (`/v1/*`):

```http
Authorization: Bearer <app-api-key>
```

Generated app keys use the `sk-sangui-` prefix and are shown only once.

Admin API (`/api/admin/*`):

```http
Authorization: Bearer <admin-jwt>
```

The two auth domains are independent - admin JWTs cannot access public gateway endpoints, and app API keys cannot access admin APIs.

### Integration Pattern

Replace your existing system's LLM `base_url` with `http://<gateway-host>:8080` and use an app API key. Chat requests are automatically augmented with knowledge base context.

## Document Support

V0.2 beta supports text-like documents: **txt, md, markdown**.

PDF and DOCX parsing are roadmap items only. Complex PDFs, Excel files, table QA, and structured extraction are not supported.

Max file size: 1 MB (configurable via `RAG_DOCUMENT_MAX_FILE_SIZE_BYTES`).

## Security

- Full app API key is shown **once only**; stored as hash, never recoverable.
- Upstream provider keys are encrypted at rest with AES-256-GCM.
- Admin APIs use JWT; public `/v1/*` uses app API keys - separate auth domains.
- Request logs contain safe metadata only (status, latency, token counts, hit chunk IDs, question summary). Full prompts, raw answers, chunk content, provider bodies, stack traces, API keys, and storage paths are never logged or returned.
- All retrieval and admin operations are tenant-scoped by `user_id` and `app_id`.

For key rotation, revocation, and leak recovery, see [docs/key-management-runbook.md](docs/key-management-runbook.md).

## Project Structure

```text
backend/                          # Spring Boot 3.4 backend
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
    log/                          # Request log persistence and queries
  Dockerfile                      # Multi-stage Maven + Java 21 image
frontend/                         # React 18 + TypeScript + Vite + Ant Design
  src/
    api/                          # HTTP client
    pages/                        # Admin console pages
    components/                   # Shared UI components
    types/                        # TypeScript type definitions
  Dockerfile                      # Multi-stage Node + Nginx image
deploy/                           # Docker Compose and infra config
scripts/                          # Automation scripts
docs/                             # Extended documentation
.trellis/                         # AI-assisted development workflow
```

## Development

### Start infrastructure only

```bash
docker compose --env-file .env -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml up -d postgres redis
```

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

The Vite dev server proxies `/api` and `/v1` to `http://localhost:8080`.

### Run Tests

**Backend:**

```bash
cd backend
mvn test                              # full suite
mvn -q -DskipTests compile            # compile check only
```

**Frontend:**

```bash
cd frontend
cmd /c npm run lint                   # ESLint
cmd /c npm run test                   # Vitest
cmd /c npm run typecheck              # TypeScript check
cmd /c npm run build                  # production build
```

**Diff check:**

```bash
git diff --check
```

**Compose config sanity:**

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

## Environment Variables

Key variables in `.env.example`:

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | Database name |
| `POSTGRES_USER` | `sangui` | Database user |
| `POSTGRES_PASSWORD` | `sangui_password` | Database password (override in production) |
| `BACKEND_PORT` | `8080` | Host backend port |
| `FRONTEND_PORT` | `3000` | Host frontend port |
| `RAG_ADMIN_AUTH_JWT_SECRET` | dev placeholder | Admin JWT HS256 signing secret (min 32 chars) |
| `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` | dev placeholder | AES-256-GCM key for upstream provider keys (min 32 chars) |
| `FILE_STORAGE_TYPE` | `local` | Storage backend: `local` or `object` |
| `RAG_DOCUMENT_CHUNK_SIZE` | `800` | Text chunk size |
| `RAG_DOCUMENT_CHUNK_OVERLAP` | `100` | Chunk overlap |
| `RAG_RETRIEVAL_DEFAULT_TOP_K` | `5` | Default retrieval top-K |

`.env.example` contains safe dev defaults only. For production, override secrets via environment or a deployment `.env` file. Production profiles (`prod`/`production`) will refuse to start with dev defaults active.

## Screenshots

No screenshot assets are currently committed. Recommended future insertion points:

- **Admin console overview**: after "What it does now" or the setup flow, showing the sidebar with model configs, knowledge bases, apps, API keys, request logs.
- **Model config / App detail**: showing bindings between app, model config, and knowledge base.
- **Request logs**: list/detail view with safe metadata fields (no prompts, answers, keys, or chunk content).

Any screenshot must redact API keys, upstream keys, prompts, answers, chunk content, provider bodies, stack traces, and storage paths.

## Further Reading

- [Key Management Runbook](docs/key-management-runbook.md) - API key rotation, revocation, and leak recovery
- [Gateway Error Codes](docs/gateway-error-codes.md) - public `/v1/*` error response reference
- [Runtime Evidence Checklist](docs/runtime-evidence-checklist.md) - demo acceptance evidence template
- [Admin API Reference](docs/admin-api-reference.md) - full admin endpoint reference
- [CI Workflow](.github/workflows/ci.yml) - CI pipeline definition
- Smoke script: `scripts/demo-smoke.ps1` - automated acceptance validation

## License

MIT
