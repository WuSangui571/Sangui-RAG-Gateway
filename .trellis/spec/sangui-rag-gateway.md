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
similarity_threshold = 0.70 - 0.75
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

Validation matrix for this baseline:

| Area | Good/Base Case | Bad Case | Required Check |
|---|---|---|---|
| README commands | Documented commands match files in repo | Wrapper commands are primary when wrapper files do not exist | Review README against file tree |
| Docker Compose | `postgres` and `redis` services define ports, volumes, and health checks | Real secrets are committed or `.env` is tracked | Review `.env.example`, `.gitignore`, `deploy/docker-compose.yml` |
| Migration | `V1__init_pgvector.sql` creates only pgvector extension | Business tables are created before domain schema is specified | Review migration file |
| Health API | `GET /api/health` returns the admin envelope with `data.status=UP` | Endpoint returns stack traces or exposes unsupported `/v1/*` behavior | MockMvc test and route search |
| Tests | Context, health endpoint, and exception envelope tests pass | Tests require local PostgreSQL or Redis for unit-level checks | `mvn test` under `backend/` |

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
