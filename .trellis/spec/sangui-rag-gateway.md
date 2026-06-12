# Sangui-RAG-Gateway Project Specification

> Project-level specification for Sangui-RAG-Gateway. Backend, frontend, and guide-specific rules reference this document as the product source of truth.

## Positioning

Sangui-RAG-Gateway is a lightweight OpenAI-compatible RAG enhancement gateway.

It is not a Dify/FastGPT clone and not a low-code AI platform. It is an API-first middleware layer for developers and business systems. Users manage private documents, knowledge bases, upstream model providers, applications, and application API keys in the admin console. Existing systems should only need to replace their original LLM `base_url` and `api_key` with this gateway's endpoint and key to receive private-knowledge RAG enhancement.

```text
Existing System -> Sangui-RAG-Gateway -> Upstream LLM API
                           |
                           +-> Private knowledge retrieval
                           +-> RAG context augmentation
                           +-> OpenAI-compatible response
```

Keywords:

```text
Lightweight
API-first
OpenAI-compatible
RAG Gateway
Low integration cost
Private document enhancement
Multi-application isolation
Observable
Deployable
Extensible
```

Core sentence:

> Let existing business systems gain private-document RAG capability with low modification and low user-facing awareness.

## Goals

MVP goals:

- Provide a compatible subset of OpenAI Chat Completions API.
- Allow users to create apps, knowledge bases, upstream model configs, and app-level API keys.
- Allow users to upload private documents and build private knowledge bases.
- On `POST /v1/chat/completions`, complete API key authentication, app config loading, knowledge retrieval, RAG context injection, upstream forwarding, and OpenAI-compatible response adaptation.
- Keep client integration close to a normal LLM API call.
- Keep the system lightweight.

MVP non-goals:

- No Dify/FastGPT replacement.
- No visual workflow orchestration.
- No agent platform.
- No plugin marketplace.
- No multimodal platform.
- No complex team workspace.
- No full OpenAI API compatibility.
- No support for every document type.
- No web crawling, Feishu, Yuque, Notion, database sync, or other external source ingestion.

## Architecture

System sides:

- Admin Console.
- Sangui-RAG-Gateway backend service.
- External business systems calling the gateway.

```text
                    Admin Console
     Upload docs / create KB / configure model / issue key
                          |
                          v
              Sangui-RAG-Gateway Backend
                          |
       ------------------------------------------------
       |                    |                         |
 Document Processing    RAG Retrieval              API Gateway
 Parse/chunk/embed      Vector search/context      OpenAI-compatible forward
       |                    |                         |
       v                    v                         v
PostgreSQL + pgvector     Redis                  Upstream LLM API
MinIO/local storage       logs/rate limits        OpenAI-compatible provider
```

Gateway call flow:

```text
Business System
     |
     | POST /v1/chat/completions
     | Authorization: Bearer sk-sangui-xxxx
     v
Sangui-RAG-Gateway
     |
     | 1. Validate API key
     | 2. Resolve app
     | 3. Load knowledge base and model config
     | 4. Retrieve relevant document chunks
     | 5. Build augmented prompt/messages
     | 6. Forward to upstream model
     | 7. Return OpenAI-compatible response
     v
Business System receives an enhanced answer
```

## Recommended Stack

Backend:

```text
Java 21
Spring Boot 3.x
Spring Security or Sa-Token
MyBatis-Plus
PostgreSQL + pgvector
Redis
MinIO or local file storage
WebClient/WebFlux
Docker Compose
```

Frontend:

```text
Vue 3 or React
TypeScript
Vite
Element Plus, Ant Design Vue, Arco Design, or another practical admin UI library
```

Storage:

```text
PostgreSQL: business data and pgvector vectors
Redis: rate limits, cache, task state support
MinIO/local storage: original uploaded documents
```

MVP may start with local file storage, but storage access must be abstracted.

Upstream model providers should be OpenAI-compatible first, not vendor-locked.

## Core Domain Model

The core aggregate is `App`.

`User` represents an admin console user who can create apps, knowledge bases, model configs, API keys, and view request logs.

`App` is the externally exposed RAG-enhanced API unit. It should bind:

- Owner user.
- App name.
- Default knowledge base.
- Default model config.
- System prompt.
- Retrieval config.
- API key(s).
- Rate and quota limits.

```text
App
  |-- KnowledgeBase
  |-- ModelConfig
  |-- PromptConfig
  |-- RetrievalConfig
  |-- ApiKey
```

`ApiKey` is the credential used by external systems.

- Bind every API key to an app.
- Show the full key only once.
- Store only a hash in the database.
- Show only the key prefix after creation.
- Support active, disabled, expired, revoked, and regenerated states.
- Support rate limits and quota controls.

`KnowledgeBase` represents a private knowledge base.

- Record owner, name, embedding provider/model/dimension, chunk strategy, and status.
- Use one fixed embedding model and vector dimension per knowledge base.
- Never mix different vector dimensions inside one knowledge base.

`Document` represents an uploaded original file.

- Record knowledge base, filename, file type, size, storage path, parsing status, chunk count, error message, and upload time.

`DocumentChunk` represents text after parsing and splitting.

- Record knowledge base, document, chunk index, content, token count, metadata, embedding vector, and create time.

`ModelConfig` represents upstream model configuration.

- Record provider name, base URL, encrypted API key, chat model, embedding model, embedding dimension, timeout, and status.
- Upstream API keys must be encrypted at rest.

`ApiRequestLog` represents one external API call.

- Record request ID, user ID, app ID, API key ID, model, question summary, hit chunk IDs, token counts, latency, status, error message, and create time.
- Do not store complete private document content or full augmented prompts by default.

## API Scope

MVP only supports:

```text
GET  /v1/models
POST /v1/chat/completions
```

`/v1/chat/completions` should support:

```text
model
messages
temperature
max_tokens
top_p
stream
```

MVP does not support:

```text
/v1/responses
/v1/embeddings
/v1/images
tools
function_call
vision
audio
response_format
parallel_tool_calls
```

README must state:

```text
This project supports a compatible subset of OpenAI Chat Completions API.
```

Authentication:

```http
Authorization: Bearer sk-sangui-xxxx
```

Authentication flow:

```text
1. Extract Bearer token
2. Hash token
3. Query ApiKey
4. Validate status, expiration, rate limit, and quota
5. Load app configuration by app ID
```

Chat completions flow:

```text
1. Receive OpenAI-compatible request
2. Validate API key
3. Load app config
4. Parse messages
5. Use the last user message as retrieval query
6. Generate query embedding
7. Retrieve relevant chunks
8. Filter by similarity threshold
9. Deduplicate, truncate, and control context length
10. Build RAG-augmented messages
11. Forward to upstream model
12. Return OpenAI-compatible response
13. Record request log
```

Streaming requirements:

- `stream=true` must use SSE-style streaming.
- Forward upstream tokens as they arrive.
- Cancel upstream when the client disconnects.
- On upstream errors after streaming begins, emit the most compatible error event possible and close the stream.
- Usage data may be unsupported in MVP streaming; document the limitation.

## RAG Rules

Document ingestion:

```text
Upload document
  -> Save original file
  -> Parse text
  -> Clean text
  -> Split chunks
  -> Call embedding model
  -> Save vectors
  -> Update document status
```

MVP document types:

```text
txt
md
pdf
docx
```

Not supported in MVP:

```text
excel
ppt
image OCR
archives
web crawling
third-party knowledge base sync
```

Parser abstraction:

```java
public interface DocumentParser {
    boolean supports(String contentType, String filename);
    ParsedDocument parse(InputStream inputStream);
}
```

Suggested parser implementations:

```text
TXT/MD: direct text read
PDF: PDFBox
DOCX: Apache POI
```

MVP chunk defaults:

```text
chunk_size: 500-800 Chinese characters
chunk_overlap: 80-120 Chinese characters
```

Retrieval defaults:

```text
top_k = 5
similarity_threshold = 0.30
max_context_tokens = 3000
max_single_chunk_tokens = 800
```

Vector retrieval SQL must include tenant and knowledge-base constraints.

Forbidden:

```sql
SELECT * FROM document_chunk
ORDER BY embedding <=> ?
LIMIT 5;
```

Allowed:

```sql
SELECT * FROM document_chunk
WHERE knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

Preferred:

```sql
SELECT * FROM document_chunk
WHERE user_id = ?
AND knowledge_base_id = ?
ORDER BY embedding <=> ?
LIMIT ?;
```

Prompt construction:

- Do not overwrite the user's original system prompt.
- Preserve original messages.
- Add an internal RAG system context.
- Clearly distinguish knowledge-base context from the user question.

No-hit policy:

- MVP default: `STRICT_RAG`.
- Still call upstream, but internally state that no valid knowledge-base context was retrieved.

Configurable policies:

```text
PASS_THROUGH
STRICT_RAG
ERROR
```

## Security

API key rules:

- Show full key only once.
- Store only hash.
- Show only prefix after creation.
- Support disable, revoke/delete, expiration, and regeneration.
- Never log complete API keys.
- Never return complete API keys in errors.

Recommended key format:

```text
sk-sangui-xxxxxxxxxxxxxxxx
```

Upstream key rules:

- Encrypt at rest.
- Read encryption master key from environment variables.
- Mask in logs.
- Do not return full plaintext to frontend APIs.
- Allow re-entry when editing model configs.

Multi-tenant isolation:

- Core data must carry a user/tenant boundary or be reachable through a required owner relation.
- Important objects: `App`, `ApiKey`, `KnowledgeBase`, `Document`, `DocumentChunk`, `ModelConfig`, `ApiRequestLog`.
- Vector retrieval must apply `knowledge_base_id` and preferably `user_id` constraints in SQL.

Logging safety:

- Log request time, app ID, API key ID, model, latency, token usage, status, error code, and hit chunk IDs.
- Do not log full API keys, upstream API keys, private documents, augmented prompts, or large sensitive user input.
- Store question summaries or bounded prefixes only.

## Rate Limits and Errors

MVP may start with API-key based limits:

```text
requests per minute
requests per day
daily token limit
concurrent requests
```

Suggested implementation:

```text
Redis + Lua
Bucket4j
Resilience4j
```

OpenAI-compatible error shape:

```json
{
  "error": {
    "message": "Specific error message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

Common error codes:

```text
invalid_api_key
rate_limit_exceeded
app_not_found
knowledge_base_not_ready
embedding_failed
upstream_timeout
upstream_error
internal_error
```

## Deployment

Docker Compose should support:

```text
backend
frontend
postgres-pgvector
redis
minio optional
```

Required deployment files:

```text
.env.example
docker-compose.yml
README deployment section
database initialization SQL or migration files
```

Environment examples:

```text
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=
RAG_GATEWAY_SECRET_KEY=
FILE_STORAGE_TYPE=local
FILE_STORAGE_LOCAL_PATH=
REDIS_HOST=
REDIS_PORT=
```

### Implemented Full-Stack Docker Compose and CI Baseline

The full-stack deployment baseline is implemented through these files:

```text
backend/Dockerfile
backend/.dockerignore
frontend/Dockerfile
frontend/.dockerignore
frontend/nginx.conf
deploy/docker-compose.yml
.env.example
.github/workflows/ci.yml
README.md
```

Runtime command:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

Service contracts:

| Service | Image/build source | Internal dependency contract | Host exposure |
|---|---|---|---|
| `postgres` | `pgvector/pgvector:pg16` | database name/user/password from `.env` | `${POSTGRES_PORT:-5432}:5432` |
| `redis` | `redis:7-alpine` | used by backend through service name `redis` | `${REDIS_PORT:-6379}:6379` |
| `backend` | `backend/Dockerfile` | PostgreSQL at `postgres:5432`, Redis at `redis:6379`, uploads at `/app/data/uploads` | `${BACKEND_PORT:-8080}:${SERVER_PORT:-8080}` |
| `frontend` | `frontend/Dockerfile` | Nginx proxies API calls to `${BACKEND_UPSTREAM:-http://backend:8080}` | `${FRONTEND_PORT:-3000}:80` |

Proxy contract in `frontend/nginx.conf`:

| Incoming path | Target | Requirement |
|---|---|---|
| `/api/*` | `${BACKEND_UPSTREAM}/api/*` | Admin APIs and `/api/health` must not fall through to SPA HTML. |
| `/v1/*` | `${BACKEND_UPSTREAM}/v1/*` | OpenAI-compatible gateway APIs must preserve streaming behavior with buffering disabled. |
| static assets | `/usr/share/nginx/html` | Serve Vite `dist`. |
| SPA routes | `/index.html` | Refresh of future frontend routes must work. |

Environment variables documented by `.env.example`:

```text
POSTGRES_DB
POSTGRES_USER
POSTGRES_PASSWORD
POSTGRES_PORT
REDIS_PORT
BACKEND_PORT
FRONTEND_PORT
SERVER_PORT
SPRING_PROFILES_ACTIVE
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
RAG_GATEWAY_SECRET_KEY
FILE_STORAGE_TYPE
FILE_STORAGE_LOCAL_PATH
RAG_DOCUMENT_CHUNK_SIZE
RAG_DOCUMENT_CHUNK_OVERLAP
RAG_DOCUMENT_MAX_FILE_SIZE_BYTES
RAG_RETRIEVAL_DEFAULT_TOP_K
RAG_RETRIEVAL_DEFAULT_SIMILARITY_THRESHOLD
RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHUNKS
RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHARS
RAG_RETRIEVAL_DEFAULT_MAX_SINGLE_CHUNK_CHARS
```

Secret rules:

- `.env.example` contains only safe local placeholders.
- Real `.env`, generated app API keys, upstream provider keys, Maven `target`, frontend `dist`, `node_modules`, and uploaded knowledge files must not be committed.
- Provider keys remain configured through Admin model config workflows and encrypted at rest; Docker images must not bake provider keys through `ARG`, `ENV`, copied files, or README examples.

CI baseline:

```text
.github/workflows/ci.yml
```

Required jobs:

| Job | Command contract |
|---|---|
| Backend check | `mvn -q -DskipTests compile`, then `mvn test` from `backend/` with PostgreSQL and Redis service containers. |
| Frontend check | `npm ci`, `npx playwright install chromium`, `npm run typecheck`, `npm run build`, then `npm run test:visual:ci` from `frontend/`. |
| Docker build backend | `docker build -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend`. |
| Docker build frontend | `docker build -t sangui-rag-gateway-frontend:ci -f frontend/Dockerfile frontend`. |

Validation matrix:

| Scenario | Expected result | Assertion point |
|---|---|---|
| Compose config renders | Compose file has `postgres`, `redis`, `backend`, `frontend`, and named volume `backend-data` | `docker compose --env-file .env -f deploy/docker-compose.yml config` succeeds. |
| Backend dependencies resolve in Compose | Backend uses service names, not `localhost`, for PostgreSQL and Redis | Rendered config contains `jdbc:postgresql://postgres:5432/...` and `SPRING_DATA_REDIS_HOST=redis`. |
| Upload persistence | Uploaded knowledge files survive backend container recreation | `backend-data` is mounted at `/app/data/uploads`. |
| Frontend `/api` proxy | Admin calls reach backend | `/api/health` through frontend origin returns JSON, not `index.html`. |
| Frontend `/v1` proxy | Gateway smoke calls reach backend and streaming is not buffered | `/v1/chat/completions` succeeds after Admin setup; `stream=true` emits SSE chunks. |
| CI without secrets | Workflow runs compile/test/build/image-build without provider keys | Workflow has no `docker login`, push, or provider secret dependency. |

Good/base/bad cases:

| Case | Expected result |
|---|---|
| Good | Fresh checkout, copy `.env.example` to `.env`, run the Compose command, backend health returns `code=OK`, and frontend opens on `${FRONTEND_PORT:-3000}`. |
| Base | Local development can still run infra-only Compose plus `mvn spring-boot:run` and `npm run dev`; Vite proxies both `/api` and `/v1` to backend. |
| Bad | Any real secret or generated `sk-sangui-*` key appears in committed files, frontend proxy returns SPA HTML for `/api` or `/v1`, or backend uses `localhost` for database/Redis inside Compose. |

## Baseline Engineering Contracts

The initial project baseline uses these concrete files and commands:

```text
backend/pom.xml
backend/src/main/resources/application.yml
backend/src/main/resources/application-dev.yml
backend/src/main/resources/db/migration/V1__init_pgvector.sql
deploy/docker-compose.yml
.env.example
README.md
```

Local infrastructure is started with:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

Backend development commands are:

```bash
cd backend
mvn spring-boot:run
mvn test
mvn -q -DskipTests compile
```

If a Maven wrapper is generated in the future, document the matching `./mvnw` and `mvnw.cmd` commands, but do not make wrapper commands the primary README path unless `mvnw` and `mvnw.cmd` exist in the repository.

Required local environment keys for the baseline are:

```text
POSTGRES_DB=sangui_rag_gateway
POSTGRES_USER=sangui
POSTGRES_PASSWORD=sangui_password
POSTGRES_PORT=5432
REDIS_PORT=6379
SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sangui_rag_gateway
SPRING_DATASOURCE_USERNAME=sangui
SPRING_DATASOURCE_PASSWORD=sangui_password
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379
RAG_GATEWAY_SECRET_KEY=local-dev-change-me
```

`.env.example` may use safe local placeholders. `.env` must remain ignored.

The first database migration must stay limited to PostgreSQL/pgvector baseline setup until a later task defines business tables:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

The custom application health endpoint is:

```http
GET /api/health
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

### Implemented Gateway Endpoint

`GET /v1/models` returns an OpenAI-compatible model list for authenticated apps:

```http
GET /v1/models
Authorization: Bearer sk-sangui-...
```

Success (200):

```json
{
  "object": "list",
  "data": [
    {
      "id": "gpt-4o-mini",
      "object": "model",
      "created": 0,
      "owned_by": "openai"
    }
  ]
}
```

Missing or disabled model config (409):

```json
{
  "error": {
    "message": "Default model config is not configured for this app.",
    "type": "invalid_request_error",
    "code": "model_config_not_ready"
  }
}
```

Invalid API key (401, from GatewayAuthFilter):

```json
{
  "error": {
    "message": "Invalid API key.",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

`POST /v1/chat/completions` has a non-streaming OpenAI-compatible pass-through baseline for authenticated apps:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Supported request fields:

| Field | Required | Behavior |
|---|---:|---|
| `model` | no | Accepted for client compatibility but not trusted for upstream selection. |
| `messages` | yes | Non-empty array. Baseline roles are `system`, `user`, and `assistant`; each message requires string `content`. |
| `temperature` | no | Forwarded to upstream when present. |
| `max_tokens` | no | Forwarded to upstream when present. |
| `top_p` | no | Forwarded to upstream when present. |
| `stream` | no | `true` activates SSE streaming forwarding; absent or `false` uses non-streaming forwarding. |

Upstream forwarding contract:

- Target URL is constructed from `base_url`:
  - Remove trailing slash characters.
  - If the resulting URL ends with `/v1`, append `/chat/completions`.
  - Otherwise, append `/v1/chat/completions`.
- Accepted `base_url` formats and resulting upstream request URLs:

  | Input `base_url` | Final upstream request URL |
  |---|---|
  | `https://api.example.com` | `https://api.example.com/v1/chat/completions` |
  | `https://api.example.com/` | `https://api.example.com/v1/chat/completions` |
  | `https://api.example.com/v1` | `https://api.example.com/v1/chat/completions` |
  | `https://api.example.com/v1/` | `https://api.example.com/v1/chat/completions` |

- `model` is always `ModelConfigEntity.chatModel` from the app default model config.
- `Authorization: Bearer <decrypted-upstream-api-key>` is used only for the outbound call and is never logged or returned.
- `stream` is forwarded as-is: absent/`false` uses whole-response forwarding; `true` uses stream chunk forwarding via `SseEmitter` and `text/event-stream`.
- Upstream non-2xx and network failures map to `502 upstream_error`; timeout maps to `504 upstream_timeout`.
- Upstream error bodies are not passed through to public callers.

Success (200) returns a chat completion shape without the admin envelope:

```json
{
  "id": "chatcmpl-test",
  "object": "chat.completion",
  "created": 1710000000,
  "model": "gpt-4o-mini",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 1,
    "completion_tokens": 1,
    "total_tokens": 2
  }
}
```

Validation and error matrix:

| Scenario | HTTP | Error code | Required behavior |
|---|---:|---|---|
| Missing/invalid/disabled app API key | 401 | `invalid_api_key` | Owned by `GatewayAuthFilter`; no controller re-authentication. |
| Missing/disabled default model config | 409 | `model_config_not_ready` | Same semantics as `/v1/models`. |
| Missing encrypted upstream key or decrypt failure | 409 | `model_config_not_ready` | Do not call upstream; do not expose config internals. |
| Malformed JSON, null body, empty `messages`, missing role/content, unsupported role | 400 | `invalid_request` | OpenAI-compatible error shape; no upstream call. |
| `stream=true` | 200 SSE | `text/event-stream` | Forwards upstream SSE chunks and `data: [DONE]`; pre-stream validation/config errors return JSON; post-start upstream errors use SSE error event. |
| Upstream non-2xx or network failure | 502 | `upstream_error` | Do not pass through provider body. |
| Upstream timeout | 504 | `upstream_timeout` | Generic client-facing message. |

Implemented files:

```text
backend/src/main/resources/db/migration/V3__create_model_config_and_app_default.sql
backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigStatus.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigMapper.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModelsController.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModel.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiModelsResponse.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionRequest.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionResponse.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatMessage.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/UpstreamChatCompletionRequest.java
```

Updated test files:

```text
backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiModelsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerTest.java
backend/src/test/java/com/sangui/raggateway/common/exception/GlobalExceptionHandlerIntegrationTest.java
```

Validation matrix for this baseline:

| Area | Good/Base Case | Bad Case | Required Check |
|---|---|---|---|
| README commands | Documented commands match files in repo | Wrapper commands are primary when wrapper files do not exist | Review README against file tree |
| Docker Compose | `postgres` and `redis` services define ports, volumes, and health checks | Real secrets are committed or `.env` is tracked | Review `.env.example`, `.gitignore`, `deploy/docker-compose.yml` |
| Migration | `V1__init_pgvector.sql` creates only pgvector extension | Business tables are created before domain schema is specified | Review migration file |
| Migration | `V2__create_app_api_key_tables.sql` creates app and API key tables | Plaintext keys stored or queried without hashing | Review migration + entity test |
| Migration | `V3__create_model_config_and_app_default.sql` creates model config and app FK | Plaintext upstream keys stored or cross-user config exposed | Review migration + service test |
| Health API | `GET /api/health` returns the admin envelope with `data.status=UP` | Endpoint returns stack traces or exposes unsupported `/v1/*` behavior | MockMvc test and route search |
| `/v1/models` | Authenticated app with enabled config returns 200 model list | Missing/disabled config returns 409 `model_config_not_ready`; unauthenticated returns 401 | `OpenAiModelsControllerTest` |
| `/v1/chat/completions` | Authenticated non-streaming request forwards to app default upstream model and returns chat completion JSON; `stream=true` forwards SSE chunks with `text/event-stream` | Invalid body/messages/role returns 400; missing config returns 409; upstream failure returns 502/504 pre-stream or SSE error post-stream | `OpenAiChatCompletionsControllerTest`, `ChatCompletionGatewayServiceTest`, `OpenAiCompatibleUpstreamClientTest` |
| Unmatched routes | Unknown paths, `/favicon.ico` return 404 `NOT_FOUND` envelope with no stack traces | Routes return 500 with stack traces or fake OpenAI responses | MockMvc test |
| Tests | Unit and focused MVC tests pass without local PostgreSQL or Redis | Tests require local infrastructure for unit-level checks | `mvn test` under `backend/` |

### Implemented Streaming Baseline

`POST /v1/chat/completions` now supports `stream=true` with SSE (`text/event-stream`) forwarding. The non-streaming path (`stream=false` or absent) is unchanged.

#### Streaming Behavior

- `stream=true` receives `text/event-stream` with forwarded upstream SSE chunks and `data: [DONE]`.
- Pre-stream errors (validation 400, model config not ready 409) still return OpenAI-compatible JSON.
- Upstream setup failures before the upstream 2xx stream is ready return OpenAI-compatible JSON (`502 upstream_error` or `504 upstream_timeout`).
- Post-start upstream errors after the upstream 2xx stream is ready send an SSE error event with OpenAI-compatible `{"error":{...}}` JSON data and close the stream safely.
- Client disconnect (detected as `IOException` on `SseEmitter.send()`) cancels upstream forwarding gracefully and is logged as `gateway.chat.stream_cancelled`, not an internal error.
- One `rag_request_log` row is persisted per streaming request. Usage token fields are nullable in this baseline.

#### Streaming Error Matrix

| Scenario | Before first byte? | Response | Error code |
|---|---|---|---|
| Missing/invalid API key | yes | 401 JSON | `invalid_api_key` |
| Malformed JSON | yes | 400 JSON | `invalid_request` |
| Validation failure (empty messages, missing role, etc.) | yes | 400 JSON | `invalid_request` |
| Model config missing/disabled/key decrypt failure | yes | 409 JSON | `model_config_not_ready` |
| Upstream non-2xx before streaming | yes | 502 JSON | `upstream_error` |
| Upstream connection failure before streaming | yes | 502 JSON | `upstream_error` |
| Upstream timeout before streaming | yes | 504 JSON | `upstream_timeout` |
| Upstream closes without `[DONE]` after chunks | no | SSE error then close | `upstream_error` |
| Client disconnect | no | Connection closed | none |
| Successful streaming | no | 200 SSE | none |

#### Request Log Contract (Streaming)

| Field | Streaming value |
|---|---|
| `request_id` | Controller-generated UUID. |
| `user_id`, `app_id`, `api_key_id` | From captured `GatewayRequestContext`. |
| `model`, `provider_name` | From resolved model config. |
| `status` | `success` or `failure`. |
| `error_code` | `invalid_request`, `model_config_not_ready`, `upstream_error`, or `upstream_timeout` for failures. |
| `latency_ms` | Total elapsed time from controller entry until stream close/failure/cancellation. |
| `upstream_latency_ms` | Time to upstream lifecycle completion. |
| `prompt_tokens`, `completion_tokens`, `total_tokens` | Null in baseline streaming. |
| `messages_count` | Count only; no message content. |

#### Streaming Log Events

```
gateway.chat.stream_started
gateway.chat.stream_completed
gateway.chat.stream_failed
gateway.chat.stream_cancelled
gateway.chat.completed (streaming success/failure finalization)
```

Safe fields: `request_id`, `app_id`, `api_key_id`, `user_id`, `provider_name`, `model`, `status`, `error_code`, `latency_ms`, `upstream_latency_ms`, `messages_count`, `error_class`.

Never logged: chunk payloads, request messages, provider raw bodies, Authorization headers, or secrets.

#### Implemented Files (New)

```text
backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java
backend/src/main/java/com/sangui/raggateway/gateway/stream/StreamLogContext.java
```

#### Updated Files

```text
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java
```

#### Updated Test Files

```text
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java
```

#### Streaming Test Commands

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

### Implemented Admin Model Config API Baseline

The admin model config CRUD + encryption + app binding baseline is implemented.

#### Temporary Admin Identity

Admin authentication is not yet implemented. The admin endpoints use a header for temporary user context:

```http
X-Admin-User-Id: <long>
```

Rules:

- Required on all `/api/admin/**` endpoints.
- Must parse to a positive long.
- This is temporary until admin login exists.

#### Admin API Endpoints

All admin APIs use `ApiResponse` envelope (not OpenAI-compatible errors):

```http
POST   /api/admin/model-configs              Create model config with encrypted upstream key
PUT    /api/admin/model-configs/{id}         Update model config (optional apiKey rotation)
GET    /api/admin/model-configs/{id}         Detail model config (masked key only)
GET    /api/admin/model-configs?status=...   List user's model configs
POST   /api/admin/model-configs/{id}/disable Disable model config
PUT    /api/admin/apps/{appId}/default-model-config  Bind app to same-user enabled model config
```

#### Upstream Key Encryption

- Algorithm: AES-256-GCM with random 12-byte IV.
- Stored format: `v1:<base64url-iv>:<base64url-ciphertext>`.
- Key derivation: SHA-256 of `RAG_GATEWAY_SECRET_KEY`.
- Masked display: first 3 + asterisks + last 4 characters (very short keys fully masked).
- `api_key_encrypted` is ciphertext, never plaintext, never returned in responses.
- `api_key_masked` is returned in responses, never equal to plaintext.

#### Tenant Isolation

- Admin model config CRUD endpoints enforce `user_id` ownership via `findById` + `findByIdAndUserId`.
- Cross-user access returns 403 `FORBIDDEN`; missing config returns 404 `NOT_FOUND`.
- App-model config binding validates both app and model config belong to the same user.
- Disabled configs are not bindable and not returned by `/v1/models`.

#### Implemented files (new):

```text
backend/src/main/java/com/sangui/raggateway/common/config/EncryptionProperties.java
backend/src/main/java/com/sangui/raggateway/common/config/EncryptionConfig.java
backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java
backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyMasker.java
backend/src/main/java/com/sangui/raggateway/model/dto/CreateModelConfigDTO.java
backend/src/main/java/com/sangui/raggateway/model/dto/UpdateModelConfigDTO.java
backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java
backend/src/main/java/com/sangui/raggateway/app/dto/BindAppDefaultModelConfigDTO.java
backend/src/main/java/com/sangui/raggateway/app/vo/BindAppDefaultModelConfigVO.java
backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java
```

#### Updated files:

```text
backend/src/main/java/com/sangui/raggateway/common/exception/BusinessException.java
backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/resources/application.yml
```

#### New test files:

```text
backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java
backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyMaskerTest.java
backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java
```

#### Validation matrix for this baseline:

| Area | Good Case | Bad Case | Required Check |
|---|---|---|---|
| Admin identity | `X-Admin-User-Id: 100` allows admin operations | Missing header returns 400 `INVALID_REQUEST`; non-numeric or <= 0 returns 400 | `ModelConfigAdminControllerTest` |
| Create config | Stores `api_key_encrypted` and `api_key_masked`, returns masked VO | Blank required fields return 400; no plaintext or encrypted key in response | `ModelConfigServiceTest`, `ModelConfigAdminControllerTest` |
| Update config | Omitted `api_key` preserves existing encrypted/masked values; non-blank `api_key` rotates | Blank `api_key` rejected; `status` not updatable via PUT | `ModelConfigServiceTest`, `ModelConfigAdminControllerTest` |
| Detail config | Same-user returns masked VO only | Missing config returns 404; different-user returns 403 | `ModelConfigAdminControllerTest` |
| List configs | Same-user rows only, masked keys | Invalid status filter returns 400 | `ModelConfigAdminControllerTest` |
| Disable config | Same-user enabled config becomes DISABLED; `/v1/models` returns 409 | Different-user returns 403 | `ModelConfigAdminControllerTest`, `OpenAiModelsControllerTest` |
| Bind app | Same-user enabled config binds successfully | Cross-user app/config returns 403; disabled config returns 400 `MODEL_CONFIG_NOT_READY` | `AppServiceTest`, `AppAdminControllerTest` |
| `/v1/models` | Bound enabled config returns 200 model list | Disabled/missing config returns 409 `model_config_not_ready` | `OpenAiModelsControllerTest` |
| Encryption | Encrypt/decrypt round-trip; different IV per encryption; blank secret rejected | Malformed payload decrypt fails safely without leaking secrets | `UpstreamApiKeyEncryptorTest` |
| Masking | Normal keys partially masked; short keys fully masked; mask != plaintext | Null input returns null | `UpstreamApiKeyMaskerTest` |

Run after changes:

```bash
cd backend
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,AppServiceTest,AppAdminControllerTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=UpstreamApiKeyEncryptorTest,UpstreamApiKeyMaskerTest" test
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,ApiKeyServiceTest,GatewayAuthFilterTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

### Implemented V0.3 Model Config Capability Split

`rag_model_config` now has explicit capability semantics via migration `V9__model_config_capability_split.sql` and normalization via `V10__normalize_legacy_chat_embedding.sql`:

| Capability | Required fields | Optional fields | Binding behavior |
|---|---|---|---|
| `CHAT` | `chat_model` | `embedding_model`, `embedding_dimension` must be null | Eligible for app default model binding |
| `EMBEDDING` | `embedding_model`; `embedding_dimension` may be null before successful check, but must be positive before enable/readiness/upload use | `chat_model` must be null/blank | Not eligible for app default model binding |

`CHAT_EMBEDDING` is legacy-only (V9 backfill). V10 migration normalizes all legacy rows to `CHAT` or `EMBEDDING`. Create/update/check inputs reject `CHAT_EMBEDDING` with `400 INVALID_REQUEST`.

**Server-side invariants:**
- App default model binding: requires `status=ENABLED`, chat capability (`CHAT` only), same-user, and non-blank `chat_model`.
- Embedding lookup: requires `status=ENABLED`, embedding capability (`EMBEDDING` only), matching `embedding_model`, matching positive `embedding_dimension`.
- Enabling an embedding config without positive `embedding_dimension` returns `400 INVALID_REQUEST`.

**Migration backfill (V9):**
- `chat_model` present + no embedding model -> `CHAT`
- `chat_model` present + embedding model present -> `CHAT_EMBEDDING`
- `chat_model` absent + embedding model present -> `EMBEDDING`
- Fallback -> `CHAT`

**Migration normalization (V10):**
- `CHAT_EMBEDDING` + `embedding_model` present -> `EMBEDDING`, `chat_model` cleared
- `CHAT_EMBEDDING` + `chat_model` present + no `embedding_model` -> `CHAT`
- Remaining `CHAT_EMBEDDING` rows with insufficient fields -> `CHAT`, `chat_model` cleared, `status=DISABLED`

**Listing / Filtering:**
```
GET /api/admin/model-configs?status=ENABLED&capability=CHAT       -> CHAT only (post-V10)
GET /api/admin/model-configs?status=ENABLED&capability=EMBEDDING  -> EMBEDDING only (post-V10)
```

Invalid capability filter returns `400 INVALID_REQUEST`. Chat-capable configs list accessible via `GET /api/admin/model-configs/chat-capable`.

**Model Config Check API:**
```
POST /api/admin/model-configs/check       (unsaved check)
POST /api/admin/model-configs/{id}/check  (saved check)
```

Check response:
```json
{
  "capability": "EMBEDDING",
  "overall_status": "SUCCESS | FAILED | PARTIAL",
  "base_url_checked": true,
  "chat": { "status": "SUCCESS", "model": "...", "message": "..." } | null,
  "embedding": { "status": "SUCCESS", "model": "...", "actual_dimension": 1024, "configured_dimension": null, "message": "..." } | null
}
```

Safety: check never returns raw provider body, embedding vectors, stack traces, plaintext keys, prompts, or assistant answers.

**New/updated files:**
- `V9__model_config_capability_split.sql`
- `ModelConfigCapability.java` (enum: CHAT, EMBEDDING, CHAT_EMBEDDING with `isChatCapable()`→CHAT only, `isEmbeddingCapable()`→EMBEDDING only)
- `V10__normalize_legacy_chat_embedding.sql` (normalization migration)
- `ModelConfigEntity.java` (+capability field)
- `CreateModelConfigDTO.java` (+capability field)
- `UpdateModelConfigDTO.java` (+capability field)
- `ModelConfigVO.java` (+capability field)
- `ModelConfigService.java` (capability-aware validation, list filtering, enable checks, `listEnabledChatCapableConfigs()`, `isChatCapable()`)
- `ModelConfigAdminController.java` (capability filter, check endpoints, chat-capable list)
- `ModelConfigCheckRequest.java` (check DTO)
- `ModelConfigCheckResult.java` (check VO with ChatCheckResult/EmbeddingCheckResult)
- `ModelConfigCheckService.java` (chat/embedding probe orchestration)
- `EmbeddingClient.java` (+probe method)
- `OpenAiCompatibleEmbeddingClient.java` (+probe impl, safe logging)
- `EmbeddingProbeResult.java` (model + dimension only)
- `AppService.java` (+chatCapable guard in `bindDefaultModelConfig`, readiness chatCapable check)
- `AppAdminController.java` (+isChatCapable pre-check on binding)
- `ModelConfigServiceTest.java`, `ModelConfigAdminControllerTest.java`, `AppServiceTest.java`, `AppAdminControllerTest.java` updated

**Frontend changes:**
- `model-config.ts`: capability, check types, nullable chat_model
- `model-configs.ts`: capability filter param, check/chat-capable API functions
- `ModelConfigPage.tsx`: capability radio selector, conditional fields, check modal with results, capability column/filter
- `AppConfigPage.tsx`: uses `listChatCapableConfigs` instead of `listModelConfigs('ENABLED')`
- `KnowledgeBasePage.tsx`: auto-fill from enabled embedding-capable configs
- `dict.ts`: capability/check i18n keys

**Run commands:**
```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest,ModelConfigCheckServiceTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
cmd /c npm run typecheck
cmd /c npm run build
```

### Implemented App API Key Admin API Baseline

The backend app and app API key management baseline is implemented. It reuses the existing `rag_app` and `rag_api_key` schema from `V2__create_app_api_key_tables.sql`; no new migration is required for this baseline.

#### Temporary Admin Identity

All endpoints below require:

```http
X-Admin-User-Id: <positive long>
```

Missing, non-numeric, or non-positive values return the admin `ApiResponse` envelope with `400 INVALID_REQUEST`.

#### Admin API Endpoints

All app/key admin APIs use `ApiResponse<T>` and never return OpenAI-compatible `error` objects:

```http
POST /api/admin/apps
GET  /api/admin/apps
GET  /api/admin/apps/{id}
POST /api/admin/apps/{appId}/api-keys
GET  /api/admin/apps/{appId}/api-keys
POST /api/admin/api-keys/{id}/disable
POST /api/admin/api-keys/{id}/enable
POST /api/admin/api-keys/{id}/revoke
```

Request contracts:

```json
POST /api/admin/apps
{
  "name": "Demo App"
}
```

```json
POST /api/admin/apps/{appId}/api-keys
{
  "name": "Production Key",
  "expires_at": "2026-12-31T23:59:59"
}
```

Response contracts use snake_case fields to match the existing admin model-config API:

```json
AppVO {
  "id": 1,
  "user_id": 100,
  "name": "Demo App",
  "status": "ENABLED",
  "default_model_config_id": 10,
  "created_at": "2026-05-27T10:00:00",
  "updated_at": "2026-05-27T10:00:00"
}
```

```json
ApiKeyCreateVO {
  "id": 1,
  "app_id": 10,
  "user_id": 100,
  "name": "Production Key",
  "key": "sk-sangui-returned-once",
  "key_prefix": "sk-sangui-abc123",
  "status": "ACTIVE",
  "expires_at": "2026-12-31T23:59:59",
  "last_used_at": null,
  "revoked_at": null,
  "created_at": "2026-05-27T10:00:00",
  "updated_at": "2026-05-27T10:00:00"
}
```

```json
ApiKeyVO {
  "id": 1,
  "app_id": 10,
  "user_id": 100,
  "name": "Production Key",
  "key_prefix": "sk-sangui-abc123",
  "status": "ACTIVE",
  "expires_at": "2026-12-31T23:59:59",
  "last_used_at": null,
  "revoked_at": null,
  "created_at": "2026-05-27T10:00:00",
  "updated_at": "2026-05-27T10:00:00"
}
```

Secret handling:

- `ApiKeyCreateVO.key` is returned only by `POST /api/admin/apps/{appId}/api-keys`.
- `ApiKeyVO` and list/disable/revoke responses never include `key` or `key_hash`.
- `ApiKeyService` persists `key_hash` and `key_prefix`; it never persists the plaintext key.
- Gateway auth continues to hash the presented plaintext key and rejects disabled, revoked, or expired keys with `401 invalid_api_key`.

Tenant behavior:

- App list/detail returns only resources owned by `X-Admin-User-Id`.
- Key creation/listing first verifies `{appId}` belongs to the current user.
- Disable/revoke verifies the key exists and belongs to the current user.
- Existing but cross-user app/key access returns `403 FORBIDDEN` with a generic `Access denied` message.
- Missing app/key access returns `404 NOT_FOUND`.

Status behavior:

- New apps are created as `ENABLED`.
- New keys are created as `ACTIVE`.
- `GET /api/admin/apps?status=ENABLED|DISABLED` filters same-user apps.
- `ACTIVE -> DISABLED` is allowed.
- `DISABLED -> DISABLED` is idempotent.
- `REVOKED -> DISABLED` returns `400 INVALID_REQUEST`.
- `DISABLED -> ACTIVE` is allowed through `POST /api/admin/api-keys/{id}/enable`.
- `ACTIVE -> ACTIVE` is idempotent through the same enable endpoint.
- `REVOKED|EXPIRED -> ACTIVE` through enable returns `400 INVALID_REQUEST`; revoked and expired keys are not restored by this action.
- `ACTIVE|DISABLED -> REVOKED` is allowed and sets `revoked_at`.
- `REVOKED -> REVOKED` is idempotent.

Implemented files:

```text
backend/src/main/java/com/sangui/raggateway/app/dto/CreateAppDTO.java
backend/src/main/java/com/sangui/raggateway/app/vo/AppVO.java
backend/src/main/java/com/sangui/raggateway/apikey/dto/CreateApiKeyDTO.java
backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyVO.java
backend/src/main/java/com/sangui/raggateway/apikey/vo/ApiKeyCreateVO.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java
```

Updated files:

```text
backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java
backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java
```

Validation matrix for this baseline:

| Area | Good Case | Bad Case | Required Check |
|---|---|---|---|
| Admin identity | Positive `X-Admin-User-Id` scopes all operations | Missing, non-numeric, or <= 0 returns `400 INVALID_REQUEST` | `AppAdminControllerTest`, `ApiKeyAdminControllerTest` |
| Create app | Persists same-user app with `status=ENABLED` | Blank/null body returns `400 INVALID_REQUEST` and inserts nothing | `AppAdminControllerTest`, `AppServiceTest` |
| List/detail app | Same-user rows only, optional `status` filter | Cross-user detail returns 403; missing app returns 404 | `AppAdminControllerTest`, `AppServiceTest` |
| Create key | Returns plaintext once and stores hash/prefix | Blank name, null body, past expiry, missing/cross-user app fail without persisting plaintext | `AppAdminControllerTest`, `ApiKeyServiceTest` |
| List keys | Same-user app keys return prefix/status metadata only | Cross-user app returns 403 and does not enumerate keys | `AppAdminControllerTest`, `ApiKeyServiceTest` |
| Disable key | Same-user active/disabled key returns safe `ApiKeyVO` with status `DISABLED` | Missing returns 404; cross-user returns 403; revoked returns 400 | `ApiKeyAdminControllerTest`, `ApiKeyServiceTest` |
| Enable key | Same-user disabled/active key returns safe `ApiKeyVO` with status `ACTIVE` | Missing returns 404; cross-user returns 403; revoked/expired returns 400 | `ApiKeyAdminControllerTest`, `ApiKeyServiceTest` |
| Revoke key | Same-user active/disabled key returns safe `ApiKeyVO` with `revoked_at` | Missing returns 404; cross-user returns 403 | `ApiKeyAdminControllerTest`, `ApiKeyServiceTest` |
| Gateway auth | Active key for enabled app remains valid | Disabled, revoked, or expired keys return OpenAI-compatible `401 invalid_api_key` | `GatewayAuthFilterTest` |

Run after changes:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,AppServiceTest,ApiKeyServiceTest" test
mvn -q "-Dtest=ApiKeyGeneratorTest,ApiKeyHasherTest,GatewayAuthFilterTest,OpenAiModelsControllerTest" test
mvn -q "-Dtest=GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

### Implemented Request Log Persistence Baseline

The request log persistence baseline stores one safe row per authenticated non-streaming `POST /v1/chat/completions` request in the `rag_request_log` table.

#### Table Schema

Introduced by `V4__create_request_log_table.sql`:

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | `BIGSERIAL` | yes | Primary key |
| `request_id` | `VARCHAR(64)` | yes | Unique per request |
| `user_id` | `BIGINT` | yes | Tenant boundary |
| `app_id` | `BIGINT` | yes | App boundary |
| `api_key_id` | `BIGINT` | yes | Safe key metadata ID only |
| `model` | `VARCHAR(255)` | no | Resolved model from config, null on validation failures |
| `provider_name` | `VARCHAR(128)` | no | Resolved provider from config, null on validation failures |
| `status` | `VARCHAR(32)` | yes | `success` or `failure` |
| `error_code` | `VARCHAR(64)` | no | Stable gateway error code |
| `latency_ms` | `BIGINT` | no | Total controller elapsed time |
| `upstream_latency_ms` | `BIGINT` | no | Upstream latency when available |
| `prompt_tokens` | `INTEGER` | no | From upstream usage |
| `completion_tokens` | `INTEGER` | no | From upstream usage |
| `total_tokens` | `INTEGER` | no | From upstream usage |
| `messages_count` | `INTEGER` | no | Count only, no content |
| `question_summary` | `VARCHAR(512)` | no | Null for this baseline |
| `hit_chunk_ids` | `JSONB` | no | Null for this baseline |
| `created_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |
| `updated_at` | `TIMESTAMP` | yes | Default `CURRENT_TIMESTAMP` |

Indexes:
- `idx_rag_request_log_app_created_at` on `(app_id, created_at DESC)`
- `idx_rag_request_log_user_created_at` on `(user_id, created_at DESC)`
- `idx_rag_request_log_api_key_created_at` on `(api_key_id, created_at DESC)`
- `idx_rag_request_log_request_id` unique on `request_id`

#### Persistence Rules

- One row is inserted per non-streaming authenticated request, covering success and failure.
- `ApiRequestLogService.record()` catches all exceptions internally; insert failures log at ERROR but never propagate to callers.
- Sensitive data never persisted: no app API key plaintext/hash, upstream key plaintext/encrypted, Authorization header, full messages, provider raw body, or stack traces.

#### Persisted Error Matrix

| Scenario | Persisted status | Persisted error_code | model/provider populated |
|---|---|---|---|
| Success (200) | `success` | null | yes |
| Validation failure (400) | `failure` | `invalid_request` | no |
| Model config not ready (409) | `failure` | `model_config_not_ready` | no |
| Upstream error (502) | `failure` | `upstream_error` | no |
| Upstream timeout (504) | `failure` | `upstream_timeout` | no |
| Malformed JSON (400) | not persisted | N/A | N/A |
| Auth failure from filter (401) | not persisted | N/A | N/A |

#### Implemented files (new)

```text
backend/src/main/resources/db/migration/V4__create_request_log_table.sql
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
```

#### Updated files

```text
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
```

#### Limitations (documented)

- Malformed JSON (400) requests are NOT persisted because the request body cannot be read to create a request ID before deserialization fails in `HttpMessageNotReadableException` handling.
- Auth failures from `GatewayAuthFilter` (401) are NOT persisted because the filter writes the response directly and does not have a safe persistence boundary.
- `question_summary` and `hit_chunk_ids` are null until future RAG retrieval implementation.
- Streaming requests are out of scope for this baseline.

Run after changes:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Trellis Workflow Rules

At the start of each task, classify it:

```text
Question
Trivial Fix
Simple Task
Complex Task
```

Complex tasks require a plan before coding. The plan should include:

- Goal.
- Affected modules.
- Data structure changes.
- API changes.
- Risks.
- Test approach.
- Step-by-step implementation.

Coding principles:

- Solve one clear problem per task.
- Do not do opportunistic refactors in unrelated modules.
- Do not add unnecessary dependencies.
- Do not break API compatibility without calling it out.
- Database changes must include migration notes.
- Security, authentication, and tenant-isolation changes require extra caution.
- RAG pipeline changes must explain effects on ingestion, retrieval, prompt construction, or upstream forwarding.

## MVP Roadmap

### V0.1 Minimum Usable Version

Goal: complete the core path.

```text
Admin login
Create knowledge base
Upload txt/md
Document chunking
Embedding storage
Create app
Issue API key
POST /v1/chat/completions non-streaming
API key bound to app
RAG retrieval augmentation
```

Done when a user uploads one Markdown document, calls `/v1/chat/completions` with the generated API key, and receives an answer grounded in that document.

### V0.2 Usable Experience Version

Goal: close to real use.

```text
PDF/DOCX support
stream=true support
request logs
topK and threshold configuration
upstream model configuration
API-key level rate limit
source citations
document processing status display
```

Done when a normal OpenAI-compatible client can integrate by replacing `base_url` and `api_key`, and receive streaming RAG-enhanced answers.

### V0.3 Engineering Enhancement Version

Goal: demonstrate backend engineering maturity.

```text
Redis rate limiting
asynchronous document processing
document status state machine
failure retry
token usage statistics
multiple knowledge bases per app
hybrid retrieval
rerank
Docker Compose one-command deployment
Actuator monitoring
```

Done when the project has enough engineering completeness to support demos, resumes, and technical blog posts.

## Boundary Checklist

Before adding a feature, answer:

- Does this serve an OpenAI-compatible RAG gateway?
- Does this reduce integration cost for existing systems?
- Does this improve private knowledge enhancement, reliability, security, or observability?
- Does this keep the system small and explainable?
- Does this avoid drifting into a full low-code AI platform?

Project direction:

```text
Small but complete
Lightweight but stable
API-first
Clear engineering
Secure and reliable
Demoable, explainable, extensible
```

One-sentence summary:

> Sangui-RAG-Gateway packages private-document RAG capability as a lightweight OpenAI-compatible API gateway, so existing systems can replace Base URL and API Key to gain knowledge-base enhancement.

### Implemented Embedding and Vector Storage Baseline

The embedding generation and pgvector storage baseline is implemented, extending the document ingestion pipeline from `PARSED` through `EMBEDDING` to `READY`.

#### Document Status Flow

```text
UPLOADED -> PARSING -> PARSED -> EMBEDDING -> READY
UPLOADED/PARSING/PARSED/EMBEDDING -> FAILED
```

`DocumentStatus` enum now includes `EMBEDDING` and `READY`.

#### Vector Storage

Vectors are stored in a separate `rag_document_chunk_embedding` table (migration `V6__create_document_chunk_embedding_table.sql`) with:
- Tenant-safe columns: `user_id`, `knowledge_base_id`, `document_id`, `chunk_id`
- `embedding` column (pgvector `VECTOR` type, variable dimension)
- Unique index on `chunk_id` (one vector per chunk)
- Lookup indexes on `(user_id, knowledge_base_id)` and `(document_id)`

#### Embedding Upstream Contract

`OpenAiCompatibleEmbeddingClient` implements the `EmbeddingClient` interface, calling an OpenAI-compatible `/v1/embeddings` endpoint:
- URL construction follows the same pattern as chat completions (trailing-slash + `/v1` normalization)
- Preserves input order by `data[].index`
- Validates response count, index order, and vector dimensions
- Normalizes non-2xx, timeout, network errors, and malformed responses to safe `EmbeddingException`
- Safe logging: no vectors, chunk content, upstream keys, or provider bodies
- Timeout configured via `rag.gateway.embedding.timeout-seconds` (default 30s)

#### Model Config Resolution

`ModelConfigService.findEnabledEmbeddingConfig(userId, embeddingModel, embeddingDimension)` resolves an enabled config by user, embedding model, dimension, and `ENABLED` status. If multiple enabled configs match, the latest updated row is used as the operational default. The upstream key is decrypted in-memory only; it is never logged or persisted.

#### Implementation Files

New:
```text
backend/src/main/resources/db/migration/V6__create_document_chunk_embedding_table.sql
backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java
backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java
backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingRequest.java
backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingResponse.java
backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingException.java
backend/src/main/java/com/sangui/raggateway/embedding/RestClientUtils.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEmbeddingEntity.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEmbeddingMapper.java
backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java
```

Updated:
```text
backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java
backend/src/main/java/com/sangui/raggateway/document/DocumentService.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
backend/src/main/resources/application.yml
backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java
backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java
```

#### Limitations

- ANN vector index (HNSW/IVFFlat) is deferred until the retrieval metric is chosen.
- Embedding is synchronous; no batching strategy beyond single-request embedding.
- No retry or async embedding pipeline in this baseline.

The knowledge base creation/list/detail and document upload/list/detail admin APIs are implemented with tenant isolation and synchronous document processing.

#### Admin API Endpoints

All endpoints use `ApiResponse<T>` and require `X-Admin-User-Id` header:

```http
POST   /api/admin/knowledge-bases                        Create knowledge base
GET    /api/admin/knowledge-bases?status=...              List user's knowledge bases
GET    /api/admin/knowledge-bases/{id}                   Detail knowledge base
POST   /api/admin/knowledge-bases/{kbId}/documents        Upload txt/md/markdown file
GET    /api/admin/knowledge-bases/{kbId}/documents?status=... List documents
GET    /api/admin/documents/{documentId}                 Detail document
```

#### Knowledge Base Status

| Status | Description |
|--------|-------------|
| `EMPTY` | Created with no documents. |
| `PROCESSING` | A document is being ingested. |
| `READY` | At least one document has completed embedding and vector persistence. |
| `FAILED` | Processing failure with no successfully parsed documents. |

#### Document Status

| Status | Description |
|--------|-------------|
| `UPLOADED` | File stored, not yet parsed. |
| `PARSING` | Parsing in progress. |
| `PARSED` | Successfully parsed and chunked; embedding has not finished yet. |
| `EMBEDDING` | Embedding generation and vector persistence are in progress. |
| `READY` | All chunks for the document have persisted vectors. |
| `FAILED` | Processing or embedding failed, with bounded error_message. |

#### Supported File Types

`.txt`, `.md`, `.markdown` only. Processing is synchronous for the baseline.

#### Storage

Local file storage under `rag.gateway.storage.local-path` (default `./data/uploads`). `FileStorageService` interface provides a future seam for MinIO. Storage paths are internal and not exposed in `DocumentVO` or admin responses.

#### Parsing and Chunking

- `PlainTextDocumentParser` for `.txt`, `MarkdownDocumentParser` for `.md/.markdown`.
- UTF-8 text read, CRLF normalization, excessive blank line collapse.
- Default chunk size: 800 characters, default overlap: 100 characters (configurable via `rag.gateway.document.chunk-size` and `rag.gateway.document.chunk-overlap`).

#### Database

Migration `V5__create_knowledge_document_tables.sql` introduces:
- `rag_knowledge_base`: tenant-scoped with `user_id`, embedding model/dimension contract, status.
- `rag_document`: metadata, `storage_path` (internal), status, chunk count, bounded `error_message`.
- `rag_document_chunk`: content, `chunk_index`, `token_count` placeholder, `metadata` JSONB.

#### Implemented Files (New)

```text
backend/src/main/resources/db/migration/V5__create_knowledge_document_tables.sql
backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseStatus.java
backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseEntity.java
backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseMapper.java
backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java
backend/src/main/java/com/sangui/raggateway/knowledge/dto/CreateKnowledgeBaseDTO.java
backend/src/main/java/com/sangui/raggateway/knowledge/vo/KnowledgeBaseVO.java
backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java
backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java
backend/src/main/java/com/sangui/raggateway/document/DocumentEntity.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEntity.java
backend/src/main/java/com/sangui/raggateway/document/DocumentMapper.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java
backend/src/main/java/com/sangui/raggateway/document/DocumentService.java
backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java
backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java
backend/src/main/java/com/sangui/raggateway/document/parser/DocumentParser.java
backend/src/main/java/com/sangui/raggateway/document/parser/ParsedDocument.java
backend/src/main/java/com/sangui/raggateway/document/parser/PlainTextDocumentParser.java
backend/src/main/java/com/sangui/raggateway/document/parser/MarkdownDocumentParser.java
backend/src/main/java/com/sangui/raggateway/document/chunk/TextChunker.java
backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java
backend/src/main/java/com/sangui/raggateway/document/storage/StoredFile.java
backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java
backend/src/main/java/com/sangui/raggateway/document/config/DocumentProperties.java
backend/src/main/java/com/sangui/raggateway/document/config/DocumentConfig.java
```

#### Updated Config Files

```text
backend/src/main/resources/application.yml
.env.example
```

#### Configuration Keys

```yaml
rag:
  gateway:
    storage:
      type: ${FILE_STORAGE_TYPE:local}
      local-path: ${FILE_STORAGE_LOCAL_PATH:./data/uploads}
    document:
      chunk-size: ${RAG_DOCUMENT_CHUNK_SIZE:800}
      chunk-overlap: ${RAG_DOCUMENT_CHUNK_OVERLAP:100}
       max-file-size-bytes: ${RAG_DOCUMENT_MAX_FILE_SIZE_BYTES:1048576}

### Implemented RAG Retrieval and Prompt Augmentation Baseline

The RAG retrieval and prompt augmentation baseline is implemented, completing the MVP RAG chat path for apps with a bound knowledge base.

#### Chat Flow (Updated)

```text
POST /v1/chat/completions
  -> API key auth (GatewayAuthFilter)
  -> validate request (messages, role, content)
  -> resolve app (AppService.findById)
  -> resolve default model config (AppService.resolveDefaultModelConfig)
  -> resolve default knowledge base (AppService.resolveDefaultKnowledgeBase)
  -> validate KB READY -> otherwise 409 knowledge_base_not_ready
  -> extract last user message as retrieval query
  -> generate query embedding (EmbeddingClient)
  -> pgvector retrieval scoped by user_id + knowledge_base_id
  -> filter by similarity_threshold, deduplicate, truncate
  -> build RAG-augmented messages (RagPromptBuilder)
  -> forward to upstream chat model
  -> return OpenAI-compatible response
  -> persist request log (question_summary, hit_chunk_ids)
```

#### App/KB Binding Admin API

```http
PUT /api/admin/apps/{appId}/knowledge-base
X-Admin-User-Id: <userId>
Content-Type: application/json

{"knowledge_base_id": 123}
```

Response: `ApiResponse<BindAppDefaultKnowledgeBaseVO>` with `app_id`, `user_id`, `default_knowledge_base_id`.

#### No-Hit Policy: STRICT_RAG

When retrieval returns no chunks above threshold, the gateway still calls upstream with an internal no-hit context message instructing the model to inform the user that the knowledge base does not contain enough information.

#### Request Log Retrieval Fields

| Field | Source | Notes |
|---|---|---|
| `question_summary` | Last user message truncated to 512 chars | Safe bounded prefix |
| `hit_chunk_ids` | JSON array of chunk IDs from retrieval | `[1,2,3]` or null for no-hits |

#### Implemented Files (New)

```text
backend/src/main/resources/db/migration/V7__add_app_default_knowledge_base.sql
backend/src/main/java/com/sangui/raggateway/app/dto/BindAppDefaultKnowledgeBaseDTO.java
backend/src/main/java/com/sangui/raggateway/app/vo/BindAppDefaultKnowledgeBaseVO.java
backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java
backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalMapper.java
backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalResult.java
backend/src/main/java/com/sangui/raggateway/retrieval/ChunkRow.java
backend/src/main/java/com/sangui/raggateway/rag/prompt/RagPromptBuilder.java
backend/src/main/java/com/sangui/raggateway/rag/prompt/NoHitPolicy.java
backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalServiceTest.java
backend/src/test/java/com/sangui/raggateway/rag/prompt/RagPromptBuilderTest.java
```

#### Updated Files

```text
backend/src/main/java/com/sangui/raggateway/app/AppEntity.java
backend/src/main/java/com/sangui/raggateway/app/vo/AppVO.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java
backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java
backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java
backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java
backend/src/main/resources/application.yml
backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java
backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java
backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java
backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java
```

### Implemented Request Log Observability Admin API

The request log observability admin API baseline is implemented, providing paginated, filtered request-log access and safe hit-chunk summaries for app owners.

#### Admin API Endpoints

All endpoints use `ApiResponse<T>` and require `X-Admin-User-Id`:

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=20&status=success&error_code=upstream_error&start_time=2026-05-31T00:00:00&end_time=2026-06-01T00:00:00
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

#### List Request Logs

Query params: `page` (default 1, positive), `page_size` (default 20, 1-100), `status` (success/failure, case-insensitive), `error_code` (exact match), `start_time` (ISO, inclusive), `end_time` (ISO, inclusive).

Response: `ApiResponse<ApiRequestLogPageVO<ApiRequestLogVO>>` with items, page, page_size, total. `hit_chunk_ids` is returned as a numeric array parsed from JSONB. `usage` may be null when token data is unavailable.

#### Request Log Detail

Returns `ApiResponse<ApiRequestLogDetailVO>` with all safe fields plus `user_id` and `updated_at`. Forbidden fields (prompt, messages, api_key, key_hash, upstream_api_key, chunk_content, embedding, provider_response_body, stack_trace) are never present in responses.

#### Hit Chunk Summary

Returns `ApiResponse<List<HitChunkSummaryVO>>` with `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`, and a bounded `summary` (first 200 chars only). Full chunk `content`, `storage_path`, and embedding data are never returned. Chunks are tenant-scoped by `user_id` + `knowledge_base_id`. Original `hit_chunk_ids` order is preserved.

#### Tenant Isolation

- App ownership verified via `AppService.findByIdAndUserId` before any log/chunk queries.
- Log queries scope by `user_id` and `app_id` at mapper level.
- Chunk queries scope by `user_id` and `knowledge_base_id` at mapper level.
- Cross-user app access returns 403 FORBIDDEN; missing app returns 404 NOT_FOUND.

#### Implemented Files (New)

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogQuery.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogPageVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/RequestLogUsageVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/HitChunkSummaryVO.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
```

#### Updated Files

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java
```

Run after changes:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppAdminControllerTest,DocumentAdminControllerTest,RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn test
```

### Implemented App Readiness Preflight Baseline

The app readiness preflight baseline provides a single admin endpoint that diagnoses configuration prerequisites before Smoke acceptance testing. All responses expose only safe metadata (IDs, statuses, provider/model names, counts) and never return secrets, prompts, chunk content, provider bodies, or stack traces.

#### Admin API Endpoint

```http
GET /api/admin/apps/{appId}/readiness
X-Admin-User-Id: <userId>
```

Response: `ApiResponse<AppReadinessVO>` with `app_id`, `user_id`, `overall_status`, and `checks[]`.

#### Readiness Status Union

| Status | Meaning |
|---|---|
| `READY` | Prerequisite is fully satisfied. |
| `MISSING` | Required resource does not exist. |
| `DISABLED` | Resource exists but is disabled. |
| `NOT_READY` | Resource exists but is not operational yet (e.g., KB PROCESSING, missing upstream key). |

#### Readiness Checks

| Check key | Conditions |
|---|---|
| `app` | App exists and `status=ENABLED`. Missing app uses 404, not a check row. Disabled app → `DISABLED`. |
| `default_model_config` | Bound config exists, same user, `ENABLED`, chat model present. Missing → `MISSING`. Disabled → `DISABLED`. Incomplete chat fields → `NOT_READY`. |
| `default_knowledge_base` | App has bound KB same user. Missing → `MISSING`. |
| `knowledge_base_status` | Bound KB status is `READY`. Non-READY → `NOT_READY` with current status in metadata. |
| `active_api_key` | At least one active (not expired) app API key exists. No keys → `MISSING`. Only disabled/revoked/expired → `DISABLED`. |
| `embedding_config` | Enabled model config exists matching KB's `embedding_model` + `embedding_dimension` with usable encrypted upstream key. Missing match → `MISSING`. Disabled-only match → `DISABLED`. Incomplete/key absent enabled match → `NOT_READY`. When multiple configs match, enabled configs are preferred before disabled configs so readiness aligns with the operational embedding lookup. |

#### Overall Status Computation

- `READY` only when all checks are `READY`.
- `MISSING` if any check is `MISSING` (highest priority).
- `DISABLED` if any check is `DISABLED` and no checks are `MISSING`.
- `NOT_READY` if any check is `NOT_READY` and no checks are `MISSING` or `DISABLED`.

#### Safe Metadata (Exposed)

```text
app_status, default_model_config_id, provider_name, chat_model,
default_knowledge_base_id, knowledge_base_status, embedding_model,
embedding_dimension, active_key_count, embedding_config_id,
embedding_provider_name
```

#### Forbidden Fields (Never Exposed)

```text
api_key, key_hash, api_key_encrypted, upstream_api_key, authorization,
prompt, messages, full_messages, augmented_prompt, chunk_content,
content, embedding, provider_response_body, stack_trace, storage_path
```

#### Validation / Error Matrix

| Scenario | HTTP | Code |
|---|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` |
| App does not exist | 404 | `NOT_FOUND` |
| App belongs to another user | 403 | `FORBIDDEN` |
| App disabled | 200 | app check `DISABLED` |
| Fully prepared app | 200 | all checks `READY` |

#### Implemented Files (New)

```text
backend/src/main/java/com/sangui/raggateway/app/AppReadinessStatus.java
backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessVO.java
backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessCheckVO.java
```

#### Updated Files

```text
backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java
backend/src/main/java/com/sangui/raggateway/app/AppService.java
backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java
backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java
backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java
```

#### Frontend Files

```text
frontend/src/types/app.ts
frontend/src/api/apps.ts
frontend/src/pages/smoke/SmokeTestPage.tsx
```

#### Frontend Contract

`AppReadinessVO` and `AppReadinessCheckVO` are typed with explicit `ReadinessStatus` union (`'READY' | 'MISSING' | 'DISABLED' | 'NOT_READY'`). The Smoke Test page loads readiness on app selection and shows a compact diagnostic panel with status tags and actionable guidance. Readiness is reset when the app changes. Existing non-streaming, streaming, request-log, and revoked-key smoke steps are unchanged.

Run after changes:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
cd ../frontend
cmd /c npm run typecheck
cmd /c npm run build
```

### Implemented Demo Acceptance Automation Rule

The demo acceptance flow includes an executable rule for automated request-log verification via `scripts/demo-smoke.ps1`.

#### Rule: Automated Request-Log Acceptance

When `-AppId` and `-AdminUserId` are both supplied, the script must validate request-log persistence after a successful non-streaming RAG chat.

**Invocation:**

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

**Parameters:**

| Parameter | Required | Contract |
|---|---:|---|
| `ApiKey` | yes | Plaintext app API key used only in `Authorization`; never echoed. |
| `BackendBaseUrl` | no | Base URL for direct backend `/api/health`. |
| `FrontendBaseUrl` | no | Base URL for frontend `/api`, `/api/admin`, and `/v1` proxy validation. |
| `Message` | no | Sent to chat and used for request-log `question_summary` prefix matching. |
| `AppId` | no | Enables readiness and request-log automation only when supplied with `AdminUserId`. |
| `AdminUserId` | no | Temporary Admin identity header value for readiness and request-log APIs. |
| `RevokedApiKey` | no | Plaintext revoked key used only for one negative auth call; never echoed. Required when `-VerifyRevokedKey` is supplied. |
| `VerifyRevokedKey` | no | Switch to enable negative auth verification (step 7). |

**Required assertions:**

| # | Assertion | Failure boundary |
|---|---|---|
| 1 | Script exits `0` only when all enabled checks pass. | — |
| 2 | `AppId` and `AdminUserId` both missing: readiness and request-log automation skipped with neutral messages; existing health/chat/stream checks still run. | — |
| 3 | Only one of `AppId`/`AdminUserId` supplied: readiness fails visibly and request-log automation also fails visibly. | `readiness` / `request-log` |
| 4 | Readiness API returns HTTP 200 with envelope `code=OK`, `overall_status=READY`, and required checks: app/default_model_config/default_knowledge_base/knowledge_base_status/active_api_key/embedding_config. | `readiness` / `retrieval` / `auth` / `embedding` |
| 5 | Readiness forbidden-field scan recursively covers `data`, including `checks[]` and nested `metadata`; forbidden fields fail the run. | `readiness` |
| 6 | Request-log list API returns HTTP 200 with envelope `code=OK`. | `request-log` |
| 7 | At least one recent success log's `question_summary` matches the smoke `-Message` prefix. | `request-log` |
| 8 | Matched list log: `status = success`, `model` non-blank, `provider_name` non-blank, `latency_ms` non-negative numeric, `hit_chunk_ids` non-empty. No forbidden fields in list item JSON. | `request-log` |
| 9 | Request-log detail API returns HTTP 200 with envelope `code=OK`. Detail `request_id` matches list row. Safe detail fields present: `user_id`, `app_id`, `api_key_id`, `model`, `provider_name`, `status`, `latency_ms`, `messages_count`, `question_summary`, `hit_chunk_ids`, `created_at`, `updated_at`. No forbidden fields in detail JSON. | `request-log` |
| 10 | Hit-chunks endpoint returns non-empty list with `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`. No forbidden fields in any hit-chunk item JSON. | `request-log` |
| 11 | Script output contains only safe evidence: request ID, model, provider_name, latency_ms, messages_count, readiness status/check counts, content length, hit chunk IDs/count, chunk metadata. Non-streaming chat prints content length only, never answer text. | `request-log` |
| 12 | Script output never prints: chunk summary text, full prompt, API key, key hash, upstream provider body, embedding vectors, storage_path, stack trace. | `request-log` |
| 13 | `-VerifyRevokedKey` not supplied: revoked-key step skipped with neutral message. | — |
| 14 | `-VerifyRevokedKey` supplied with blank `-RevokedApiKey`: fail with `auth` boundary. | `auth` |
| 15 | Revoked key returns HTTP 401 with error `code=invalid_api_key` (boundary: `auth`). | `auth` |

**Safe evidence fields (allowed in output):**

```text
request_id, model, provider_name, latency_ms, messages_count,
readiness overall_status and check count,
hit_chunk_ids (array + count), content length (non-streaming),
chunk_id, document_id, knowledge_base_id, source_filename, chunk_index,
HTTP status, boundary label, SSE data line count
```

**Forbidden fields (never printed by any smoke script or README example, and scanned in readiness/list/detail/hit-chunk JSON):**

```text
key_hash, api_key, api_key_encrypted, upstream_api_key, provider_response_body,
stack_trace, embedding, prompt, messages, full_messages, augmented_prompt,
authorization, storage_path, content (chunk content), chunk_content,
summary text content
```

**PowerShell compatibility requirements:**

- Script remains PowerShell 5.1 compatible.
- JSON request bodies in `Invoke-CurlCapture` are written as UTF-8 without BOM (`[System.Text.UTF8Encoding]::new($false)`).
- All `curl` calls use `curl.exe`, not the PowerShell `curl` alias.
- Temp files are always cleaned in `finally` blocks.

**Targeted test commands (backend unchanged by this spec rule):**

```bash
cd backend
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

**Manual validation (PowerShell 5.1):**

```powershell
# Syntax check
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

```powershell
# Revoked-key 401 verification after demo cleanup
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

**Split-provider demo setup:**

The demo uses two separate upstream providers:

| Role | Provider | Base URL | Model | Dimension |
|---|---|---|---|---|
| Chat | Sanguicode | `https://api.sanguicode.com` | `deepseek-v4-pro` | — |
| Embedding | DashScope | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `text-embedding-v4` | `1024` |

Both configs must be `ENABLED` under the same admin user. The Sanguicode `CHAT` config is bound as the app default model config and must omit embedding fields. The DashScope `EMBEDDING` config is resolved automatically by `findEnabledEmbeddingConfig(userId, embeddingModel, embeddingDimension)` and must keep `chat_model` null.

Admin model-config creation accepts only `CHAT` or `EMBEDDING`. Split-provider runbooks must create separate rows: `CHAT` with `chat_model` only, and `EMBEDDING` with `embedding_model` plus positive `embedding_dimension` only. `CHAT_EMBEDDING` is legacy-only after V10 normalization and must not be used in setup commands.

**Evidence checklist:**

| # | Check | Expected evidence | Boundary |
|---|---|---|---|
| 1 | Backend health | HTTP 200, `code=OK`, `data.status=UP` | `health` |
| 2 | Frontend `/api` proxy health | JSON response (not SPA HTML), `code=OK` | `proxy` |
| 3 | App readiness | `overall_status=READY`, required checks present, no forbidden fields in readiness metadata | `readiness` / `retrieval` / `auth` / `embedding` |
| 4 | Model config presence | App has `ENABLED` Sanguicode chat config bound as default | `retrieval` |
| 5 | KB status `READY` | App has bound knowledge base with status `READY` | `retrieval` |
| 6 | Non-streaming chat success | HTTP 200, `choices[0].message.content` present | `upstream` |
| 7 | Streaming SSE success | `data:` chunks received, `data: [DONE]` present | `upstream` |
| 8 | Request-log list/detail | `status=success`, non-blank `model`/`provider_name`, numeric `latency_ms`, non-empty `hit_chunk_ids`; detail returns `user_id`, `updated_at`, `messages_count`, and all list fields; no forbidden fields in list, detail, or hit-chunk JSON | `request-log` |
| 9 | Hit-chunks safe metadata | `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index` present; no full chunk content or forbidden fields | `request-log` |
| 10 | Revoked-key 401 | HTTP 401 with `error.code=invalid_api_key` after key revocation | `auth` |
| 11 | No secrets in output | No API keys, key hashes, encrypted keys, provider bodies, stack traces, or embedding vectors in script output or committed files | — |

**Good / Base / Bad cases:**

| Case | Expected result |
|---|---|
| Good | Backend and frontend running; app readiness is `READY` with all required checks and recursive forbidden-field scan passing; app has ready KB, enabled Sanguicode chat config, and enabled DashScope embedding config; non-streaming chat succeeds (script prints content length only, not answer text); request-log automation finds latest matching success row with model/provider/latency/question_summary/hit_chunk_ids; detail validation confirms safe fields and request_id match; forbidden-field scan passes on list, detail, and hit-chunk JSON; hit-chunks count matches safe evidence expectations; streaming emits `[DONE]`; revoked-key verification returns HTTP 401 with `error.code=invalid_api_key`. |
| Base | User runs script without `-AppId`/`-AdminUserId`; existing health/chat/stream checks still work; README provides manual and automated request-log commands. Readiness and revoked-key verification are skipped unless their required inputs are supplied. |
| Bad | Script accepts a passing chat run while readiness is non-ready, request-log fields are missing, stale, unsafe, or empty; script prints full key/chunk content/answer text; forbidden fields appear in readiness/list/detail/hit-chunk JSON without detection; README uses PowerShell default encoding or `curl` alias; revoked-key verification passes without actually checking HTTP 401 and `error.code`. |

**Runtime evidence recording:** When committing demo acceptance evidence, use the durable [Runtime Evidence Checklist Template](../../docs/runtime-evidence-checklist.md). The current task also keeps a task-local review copy at `../tasks/06-10-demo-smoke-runtime-evidence-checklist-finalization/runtime-evidence-checklist.md`. The template records only safe metadata with `<redacted>` placeholders and Good/Base/Bad recording rules. The README contains the canonical comprehensive Safe Evidence Fields and Forbidden Output Fields lists that apply to all recordings.

**Long-term rule:** README.md safe/forbidden field lists are the durable project rule. This spec rule describes the automation contract; the README defines the evidence contract that also applies to manual runs, task-local templates, and committed evidence.
