# Full-stack Docker Compose Deployment and CI Image Build Baseline

## Task Classification

Complex Task.

This task spans backend packaging, frontend static serving/proxy behavior, Docker Compose service networking, environment variable contracts, persistent storage, CI image builds, and README deployment documentation. It is infrastructure/documentation work only; it must not change backend business behavior, frontend feature behavior, database schema, API DTO/VO contracts, authentication rules, RAG retrieval logic, or admin workflow logic unless a build/runtime issue proves a minimal config-only adjustment is required.

## Goal

Create a reproducible full-stack deployment baseline for Sangui-RAG-Gateway:

- Build and run backend, frontend, PostgreSQL/pgvector, and Redis with one Docker Compose command.
- Package backend as a Java 21 Spring Boot container.
- Package frontend as Vite-built static assets served behind a lightweight HTTP server that proxies `/api` and `/v1` to the backend service.
- Keep uploaded knowledge files persistent through a named Docker volume.
- Document safe local/deployment environment variable usage, especially secrets and upstream provider keys.
- Add a GitHub Actions workflow that verifies backend/frontend build quality and Docker image buildability.

## Current Project State Summary

The latest workspace journal records the Admin console configuration workflow as completed and committed in `c66c186`. Manual acceptance confirmed:

- Admin pages are usable.
- Non-streaming gateway smoke succeeds through `POST /v1/chat/completions`.
- Request log detail shows a successful RAG request with model/provider, latency, token usage, question summary, and hit chunk ID.

Current repository state before this task:

- `deploy/docker-compose.yml` includes only `postgres` and `redis`.
- `README.md` still describes an early scaffold state and says several now-implemented features are missing.
- `.env.example` contains local infrastructure/backend placeholders but lacks full-stack container port and frontend/deployment guidance.
- `frontend` is React 18 + TypeScript + Vite + Ant Design and currently calls relative `/api` and `/v1`, making same-origin reverse proxy deployment feasible.
- Existing uncommitted changes include prior Trellis archive/workspace files and local manual smoke artifacts; implementation must avoid staging or rewriting those unrelated artifacts.

## Requirements

- Add `backend/Dockerfile` using a Maven build stage and a Java 21 runtime image.
- Add `frontend/Dockerfile` using an npm/Vite build stage and Nginx or another lightweight static server runtime.
- Add frontend runtime proxy config so `/api` and `/v1` route to the backend Compose service name.
- Extend `deploy/docker-compose.yml` with `backend` and `frontend` services.
- Backend service must use Compose service names for dependencies:
  - PostgreSQL host/service: `postgres`
  - Redis host/service: `redis`
- Backend upload storage must persist outside the container, preferably through named volume `backend-data` mounted to `/app/data/uploads`.
- Compose must expose local host ports for backend and frontend through environment variables with safe defaults.
- Update `.env.example` with safe placeholders for backend/frontend ports, datasource, Redis, storage, RAG secret, retrieval/document settings, and provider-key guidance.
- `.env.example` must not contain real API keys, production secrets, app API keys, upstream provider keys, or personal tokens.
- Add a GitHub Actions workflow under `.github/workflows/` that:
  - runs backend compile/tests,
  - runs frontend typecheck/build,
  - builds backend Docker image,
  - builds frontend Docker image.
- Optional GHCR push must be left disabled or gated behind an explicit future decision because repository permissions and image naming are not confirmed.
- Update README to reflect current project status and include:
  - one-command full-stack Compose startup,
  - health check command,
  - frontend URL,
  - first Admin manual configuration smoke flow,
  - CI local-equivalent commands and GitHub trigger conditions,
  - safe secret/provider key handling rules.

## Non-Goals and Explicitly Forbidden Scope

- Do not change backend API behavior.
- Do not change frontend page workflows, API payloads, component behavior, or visual design.
- Do not add login/auth beyond existing temporary Admin `X-Admin-User-Id` contract.
- Do not add database migrations or schema changes.
- Do not add MinIO unless explicitly approved; local persistent upload storage is sufficient for this baseline.
- Do not implement GHCR push unless the user confirms repository owner/image naming and package permissions.
- Do not commit `.env`, generated `dist`, `node_modules`, Maven `target`, uploaded files, or manual smoke artifacts.
- Do not bake any secret into Docker images through `ARG`, `ENV`, copied files, or README examples.

## API, Command, and Environment Contracts

### Public/Runtime Commands

Primary full-stack startup:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

Health check:

```bash
curl http://localhost:${BACKEND_PORT:-8080}/api/health
```

Frontend access:

```text
http://localhost:${FRONTEND_PORT:-3000}
```

Backend local verification:

```bash
cd backend
mvn -q -DskipTests compile
mvn test
```

Frontend local verification:

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

Docker image build verification:

```bash
docker build -t sangui-rag-gateway-backend:local -f backend/Dockerfile backend
docker build -t sangui-rag-gateway-frontend:local -f frontend/Dockerfile frontend
```

### Environment Variables

Required or documented variables:

| Variable | Example/default | Notes |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | Safe local placeholder allowed. |
| `POSTGRES_USER` | `sangui` | Safe local placeholder allowed. |
| `POSTGRES_PASSWORD` | `sangui_password` | Local placeholder only; deployers must override. |
| `POSTGRES_PORT` | `5432` | Host port for local access. |
| `REDIS_PORT` | `6379` | Host port for local access. |
| `BACKEND_PORT` | `8080` | Host port mapped to backend container `SERVER_PORT`. |
| `FRONTEND_PORT` | `3000` | Host port mapped to frontend static server. |
| `SPRING_PROFILES_ACTIVE` | `dev` | Compose can use `dev` unless a prod profile is introduced later. |
| `SERVER_PORT` | `8080` | Backend container port. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/sangui_rag_gateway` | Compose runtime uses service name, not `localhost`. |
| `SPRING_DATASOURCE_USERNAME` | `${POSTGRES_USER}` | May be wired in Compose. |
| `SPRING_DATASOURCE_PASSWORD` | `${POSTGRES_PASSWORD}` | Must not be real production secret in repo. |
| `SPRING_DATA_REDIS_HOST` | `redis` | Compose runtime uses service name. |
| `SPRING_DATA_REDIS_PORT` | `6379` | Container port. |
| `RAG_GATEWAY_SECRET_KEY` | `local-dev-change-me` | Placeholder only; real value in local `.env` or deployment secret. |
| `FILE_STORAGE_TYPE` | `local` | Baseline local storage. |
| `FILE_STORAGE_LOCAL_PATH` | `/app/data/uploads` | Container path for persisted upload volume. |
| `RAG_DOCUMENT_CHUNK_SIZE` | `800` | Preserve existing config contract. |
| `RAG_DOCUMENT_CHUNK_OVERLAP` | `100` | Preserve existing config contract. |
| `RAG_DOCUMENT_MAX_FILE_SIZE_BYTES` | `1048576` | Preserve existing config contract. |
| `RAG_RETRIEVAL_DEFAULT_TOP_K` | `5` | Preserve existing config contract. |
| `RAG_RETRIEVAL_DEFAULT_SIMILARITY_THRESHOLD` | current application default | Do not silently change semantics without an explicit decision. |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHUNKS` | `5` | Preserve existing config contract. |
| `RAG_RETRIEVAL_DEFAULT_MAX_CONTEXT_CHARS` | `12000` | Preserve existing config contract. |
| `RAG_RETRIEVAL_DEFAULT_MAX_SINGLE_CHUNK_CHARS` | `3000` | Preserve existing config contract. |

Provider/upstream keys are not global bootstrap env vars in current app behavior; users configure them manually through Admin model config pages. README may describe placeholder provider values for manual smoke, but real provider API keys must be entered in local Admin UI or deployment secret handling and never committed.

### HTTP/Proxy Contract

Frontend runtime proxy must preserve these routes:

| Incoming path | Target in Compose | Notes |
|---|---|---|
| `/api/*` | `http://backend:8080/api/*` | Admin APIs and health endpoint. |
| `/v1/*` | `http://backend:8080/v1/*` | OpenAI-compatible gateway API. Preserve streaming/SSE headers. |
| static assets | frontend runtime | Serve `dist`. |
| SPA fallback | `index.html` | Required for frontend route refresh if routing is introduced. |

## Validation and Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| Compose build succeeds | Backend/frontend images build without copying ignored/generated/secrets files | `docker compose ... up -d --build` exits successfully. |
| Backend dependency wiring works | Backend connects to `postgres` and `redis` by service name | `curl /api/health` returns `code=OK`, backend logs show no datasource/Redis host failures. |
| Frontend proxy `/api` works | Browser/static runtime can call backend Admin APIs through same origin | Frontend pages load helper lists or health/API call through `/api`. |
| Frontend proxy `/v1` works | Smoke page can call `/v1/chat/completions` with generated app key | Manual smoke succeeds after Admin configuration. |
| Upload volume persists | Uploaded files survive backend container recreation | `backend-data` named volume mounted to `/app/data/uploads`; no host path secret leakage. |
| Missing real provider key | Manual smoke fails visibly at Admin/gateway boundary, not at Docker/CI layer | README explains provider key must be configured locally, not committed. |
| `.env` absent | User can copy `.env.example` to `.env` and start with safe placeholders | README gives exact command. |
| CI without secrets | Tests and Docker builds run without upstream provider keys | Workflow does not require real provider API key. |
| GHCR push not configured | No accidental package publish | Workflow only builds images unless user later approves push. |

## Good / Base / Bad Cases

Good cases:

- Fresh checkout, copy `.env.example` to `.env`, run one Compose command, then `curl /api/health` returns OK.
- Frontend opens at `http://localhost:${FRONTEND_PORT:-3000}` and relative `/api` requests reach backend.
- After manual Admin setup, smoke page sends a non-streaming `/v1/chat/completions` request and request log shows SUCCESS.
- GitHub Actions on PR/push runs Maven compile/tests, npm typecheck/build, and both Docker image builds.

Base cases:

- Running only infrastructure for local Maven/Vite development remains possible or is documented separately.
- `mvn test` and `npm run build` continue to work outside Docker.
- Existing `.env` remains ignored; `.env.example` contains placeholders only.
- Local upload storage path differs between host dev (`./data/uploads`) and container runtime (`/app/data/uploads`) through environment configuration.

Bad cases:

- Any real API key, generated `sk-sangui-*` app key, upstream key, or production secret appears in committed files.
- Frontend runtime serves static assets but `/api` or `/v1` returns frontend HTML instead of proxying backend.
- Backend container uses `localhost` for PostgreSQL/Redis inside Compose.
- Docker build context copies `target`, `node_modules`, `.env`, upload data, or Trellis/local artifacts into images.
- CI workflow requires unavailable secrets or attempts to push images without confirmed GHCR permissions.

## Implementation Plan for DeepSeek

1. Add Docker ignore files if needed to keep image contexts small and secret-safe.
2. Add `backend/Dockerfile` with Maven build stage and Java 21 runtime stage.
3. Add `frontend/Dockerfile` and frontend runtime proxy config for `/api` and `/v1`.
4. Extend `deploy/docker-compose.yml` with backend/frontend services, health/dependency wiring, ports, env variables, and `backend-data` volume.
5. Update `.env.example` with full-stack-safe placeholders and comments if the file style permits comments.
6. Add `.github/workflows/ci.yml` for backend tests, frontend checks, and Docker image builds without pushing.
7. Update `README.md` current status, project structure, quick start, env table, one-command Compose startup, manual Admin smoke flow, CI commands, and secret handling.
8. Run targeted validation in required order and fix only deployment/config/doc issues.
9. Do a final diff review for secret leakage, hidden fallbacks, unmentioned behavior changes, generated files, and stale README claims.

## Expected Files To Modify

- `backend/Dockerfile`
- `backend/.dockerignore` if backend build context needs it
- `frontend/Dockerfile`
- `frontend/.dockerignore` if frontend build context needs it
- `frontend/nginx.conf` or equivalent runtime proxy config
- `deploy/docker-compose.yml`
- `.env.example`
- `.github/workflows/ci.yml`
- `README.md`

Optional only if proven necessary:

- `backend/src/main/resources/application.yml` or `application-dev.yml` for config-only alignment with already-supported env vars.
- `frontend/vite.config.ts` only if dev proxy must also include `/v1`; current production proxy should be handled by frontend runtime config.

## Required Tests and Assertion Points

Run in this order where environment allows:

```bash
cd backend
mvn -q -DskipTests compile
mvn test
```

Assertions:

- Maven compile succeeds on Java 21.
- Backend tests pass without external provider keys.

```bash
cd frontend
npm ci
npm run typecheck
npm run build
```

Assertions:

- TypeScript build passes.
- Vite build produces `dist`.
- Any chunk-size warning is acceptable if unchanged and documented.

```bash
docker build -t sangui-rag-gateway-backend:local -f backend/Dockerfile backend
docker build -t sangui-rag-gateway-frontend:local -f frontend/Dockerfile frontend
```

Assertions:

- Both image builds pass from their intended contexts.
- `.env`, uploaded data, `node_modules`, `dist`, and Maven `target` are not part of the committed build context.

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
curl http://localhost:8080/api/health
```

Assertions:

- All services become healthy or running.
- Health response contains `code=OK` and `data.status=UP`.

Manual smoke:

- Open frontend at configured frontend port.
- Use Admin flow to create/configure model config, knowledge base, document, app, and API key.
- Run non-streaming chat smoke through frontend smoke page or `curl`.
- Verify request log detail shows `SUCCESS`, resolved model/provider, latency/tokens, question summary, and hit chunk IDs when retrieval hits.

CI validation:

- Workflow triggers on `push` and `pull_request` to `main` unless project convention says otherwise.
- Workflow uses `actions/checkout`, Java 21 setup, Node setup, Maven cache or default setup, npm ci, and Docker build commands.
- No `docker login` or push step unless explicitly gated and disabled by default.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, deployment requirements, environment keys, health endpoint, supported OpenAI-compatible API subset.
- `.trellis/spec/backend/index.md`: backend must stay lightweight, API-first, Docker Compose is target stack.
- `.trellis/spec/backend/directory-structure.md`: resource layout and module boundaries; no business package changes needed.
- `.trellis/spec/backend/database-guidelines.md`: PostgreSQL/pgvector, Redis, migrations, tenant/secret constraints; this task should not add schema.
- `.trellis/spec/backend/error-handling.md`: gateway/admin response shapes must not be changed by deployment work.
- `.trellis/spec/backend/logging-guidelines.md`: secrets and provider bodies must never be logged or documented as commit-ready values.
- `.trellis/spec/backend/quality-guidelines.md`: required backend regression checks and secret-safety review.
- `.trellis/spec/frontend/index.md`: React/Vite admin console must remain workflow-driven.
- `.trellis/spec/frontend/directory-structure.md`: frontend build/proxy changes should not alter app/page organization.
- `.trellis/spec/frontend/type-safety.md`: no frontend contract/type changes expected.
- `.trellis/spec/frontend/state-management.md`: no new persistent secret state.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend deployment must preserve operational admin behavior and secret safety.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because Docker Compose/env/proxy crosses layers.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search existing config/contracts before adding parallel sources of truth.

### Code Patterns Found

- `deploy/docker-compose.yml`: existing Compose style uses service-level env defaults, named volumes, healthchecks, and explicit host port variables for postgres/redis.
- `backend/src/main/resources/application.yml`: backend already reads `SERVER_PORT`, `RAG_GATEWAY_SECRET_KEY`, `FILE_STORAGE_LOCAL_PATH`, document and retrieval env vars.
- `backend/src/main/resources/application-dev.yml`: datasource/Redis already read `SPRING_DATASOURCE_*` and `SPRING_DATA_REDIS_*`, so Compose can wire service names without code changes.
- `frontend/src/api/http.ts`: Admin API client uses relative `/api`, suitable for same-origin reverse proxy.
- `frontend/src/api/openai.ts`: smoke client uses relative `/v1`, so frontend runtime must proxy `/v1` as well as `/api`.
- `frontend/package.json`: existing scripts are `typecheck` and `build`; no lint/test script exists yet.
- `.gitignore`: `.env`, backend upload data, frontend `node_modules`, `dist`, `*.tsbuildinfo`, and Maven build output are ignored.

### Files Likely To Modify

- `backend/Dockerfile`: new backend image build/runtime.
- `backend/.dockerignore`: keep backend image context clean.
- `frontend/Dockerfile`: new frontend image build/runtime.
- `frontend/.dockerignore`: keep frontend image context clean.
- `frontend/nginx.conf`: static serving and `/api` + `/v1` proxy.
- `deploy/docker-compose.yml`: add backend/frontend services and `backend-data` volume.
- `.env.example`: full-stack env contract and safe placeholders.
- `.github/workflows/ci.yml`: CI compile/test/typecheck/build/docker-build baseline.
- `README.md`: current status, one-command Compose, smoke flow, CI, secret handling.

### Risk / Boundary Notes

- Frontend dev proxy currently includes `/api` only; production frontend proxy must include both `/api` and `/v1`.
- Backend container must not use `localhost` for postgres/redis.
- Upload storage path should be `/app/data/uploads` in container and backed by a named volume.
- Do not turn provider API keys into global env requirements; current product flow configures upstream keys through Admin model config and encrypted storage.
- README must be brought up to date with implemented RAG/Admin state; stale "not implemented" statements are misleading.
- CI should not depend on Docker Compose services unless backend tests require real database; current unit tests are expected to run without external provider keys.
- Docker image builds should be verified without GHCR push.

## Planning Self-Check

- Acceptance criteria are defined above through requirements, command contracts, validation matrix, and required tests.
- Forbidden modification scope is explicit under Non-Goals.
- Expected files are listed.
- Required tests and assertion points are listed.
- Concrete backend, frontend, project, and cross-layer guideline files have been read.
- No open API/DB/frontend DTO mismatch is expected because this task should not change DTOs or schema.
- One needs-user-confirmation item remains: GHCR push is intentionally out of scope until repository permissions and image names are confirmed.
