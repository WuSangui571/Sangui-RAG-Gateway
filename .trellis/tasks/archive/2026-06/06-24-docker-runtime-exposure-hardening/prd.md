# Docker Runtime Exposure Hardening

## Classification

Complex Task.

This is an infrastructure and cross-layer deployment contract change. It touches Docker Compose, Dockerfiles, README deployment instructions, environment examples, and Trellis specs. It must not change backend business APIs, database schema, RAG retrieval, prompt behavior, frontend application code, or CI behavior in this task.

## Background

The current full-stack Compose baseline publishes PostgreSQL and Redis to the host by default:

- `deploy/docker-compose.yml`: `postgres` publishes `${POSTGRES_PORT:-5432}:5432`.
- `deploy/docker-compose.yml`: `redis` publishes `${REDIS_PORT:-6379}:6379`.
- `.env.example`: defines `POSTGRES_PORT=5432` and `REDIS_PORT=6379`.
- `README.md` and `.trellis/spec/sangui-rag-gateway.md` document PG/Redis host ports as the default service contract.

This creates unnecessary default exposure for internal infrastructure services. The backend already reaches PostgreSQL and Redis through Compose service names (`postgres:5432`, `redis:6379`), so host publication should be opt-in for local debugging or external tooling.

The task also reviews Docker root user and healthcheck behavior directly related to Compose/Dockerfile:

- `backend/Dockerfile` runtime image currently runs the Java process as the image default user after installing `curl`.
- `frontend/Dockerfile` runtime image inherits nginx default user behavior.
- Compose defines backend healthcheck through `curl` against `/api/health`; PostgreSQL and Redis healthchecks already exist.
- There is no Dockerfile-level `HEALTHCHECK`; Compose currently owns runtime healthchecks.

## Goal

Harden the default Docker runtime deployment contract by keeping PostgreSQL and Redis internal-only by default, requiring explicit opt-in for host publication, and documenting/verifying the container user and healthcheck contract consistently.

## Non-Goals

- Do not change Java business logic, controllers, services, DTO/VO types, DB migrations, RAG retrieval, prompt construction, API-key auth, admin auth, request logs, or frontend app code.
- Do not add a CI security job in this task. CI/image runtime validation is a follow-up task after the Docker runtime contract is stable.
- Do not introduce a new secrets manager, TLS termination, reverse proxy, network policy engine, or production orchestration system.
- Do not make MinIO/object storage part of this task unless required to keep existing docs coherent.
- Do not remove local development ability to inspect PostgreSQL/Redis from the host; make it explicit opt-in instead.

## Required Contract

### Command / Environment / Compose Fields

Primary command remains:

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
```

Required default behavior:

| Service | Internal contract | Host exposure default | Opt-in behavior |
|---|---|---|---|
| `postgres` | Backend connects to `postgres:5432` inside Compose network | No host port is published by default | A documented opt-in path may publish `${POSTGRES_PORT:-5432}:5432` for local DB tooling |
| `redis` | Backend connects to `redis:6379` inside Compose network | No host port is published by default | A documented opt-in path may publish `${REDIS_PORT:-6379}:6379` for local Redis tooling |
| `backend` | Uses service-name DB/Redis dependencies and stores uploads at `/app/data/uploads` | Published as `${BACKEND_PORT:-8080}:${SERVER_PORT:-8080}` | Still published for local gateway/admin API access |
| `frontend` | Proxies `/api` and `/v1` to backend service name | Published as `${FRONTEND_PORT:-3000}:80` | Still published for admin console access |

Acceptable implementation options:

- Preferred: remove PG/Redis `ports` from `deploy/docker-compose.yml` and add an explicit opt-in override file such as `deploy/docker-compose.host-ports.yml`.
- Also acceptable if better aligned with the repo: Compose `profiles` for PG/Redis host ports, provided the default `docker compose ... config` renders no host port for PG/Redis and README commands are clear.

`.env.example` should stop implying PG/Redis host ports are part of the default runtime surface. If `POSTGRES_PORT` and `REDIS_PORT` remain, they must be clearly scoped to the opt-in host-port override/profile only.

### Docker User Contract

Backend runtime container:

- Prefer running the Java process as a non-root user.
- Ensure `/app` and `/app/data/uploads` are writable by that runtime user when local storage is used.
- Keep the Maven build stage contract intact: `backend/settings.xml` may still be copied to the Maven build user's settings path, and `mvn -B -ntp -DskipTests package` must remain visible.
- Do not reintroduce `dependency:go-offline -q` or hidden dependency prefetch steps.

Frontend runtime container:

- Review whether the nginx runtime can safely run as non-root without breaking listen port `80`, template rendering, cache/temp directories, or existing Compose port mapping.
- If non-root frontend runtime requires invasive nginx changes, document it as a bounded follow-up instead of adding a risky partial fix.

### Healthcheck Contract

- Compose remains the source of runtime healthchecks unless the implementation intentionally adds Dockerfile `HEALTHCHECK` with matching behavior and docs.
- PostgreSQL healthcheck must continue to use database/user values safely.
- Redis healthcheck must continue to use `redis-cli ping`.
- Backend healthcheck must continue to verify `/api/health` returns the `OK` admin envelope and must work under the selected runtime user.
- README and spec must clearly state where healthchecks live and how to validate them.

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| Default Compose config with `.env.example` | `postgres` and `redis` have no host port publication; backend and frontend remain published | `docker compose --env-file .env.example -f deploy/docker-compose.yml config` |
| Opt-in host-port mode enabled | `postgres` publishes `${POSTGRES_PORT:-5432}:5432`; `redis` publishes `${REDIS_PORT:-6379}:6379` | Documented override/profile config command |
| Backend service dependencies | Backend datasource uses `jdbc:postgresql://postgres:5432/...`; Redis host is `redis` and port `6379` | Rendered Compose config |
| Backend runtime non-root | Java process runs as a non-root user and can write to `/app/data/uploads` | Dockerfile inspection plus image/container smoke if Docker is available |
| Backend healthcheck | Healthcheck command can execute under runtime user and checks `/api/health` for `code=OK` | Rendered Compose config; runtime smoke if Docker is available |
| Frontend proxy unchanged | `/api` and `/v1` proxy behavior remains unchanged | No changes to `frontend/nginx.conf` unless explicitly needed; build/config smoke |
| README/spec consistency | Docs no longer describe PG/Redis host ports as default exposure | README and `.trellis/spec/sangui-rag-gateway.md` diff review |
| Secrets hygiene | Dockerfiles, Compose, README, and `.env.example` do not bake provider keys or real secrets | `rg` secret/port scan and diff review |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh checkout with `.env.example` renders a Compose config where only backend/frontend are host-published; backend reaches PostgreSQL/Redis through service names; opt-in host-port command is documented and renders PG/Redis host ports only when explicitly requested; backend image runs as non-root and healthchecks still work. |
| Base | Docker is unavailable locally; static config validation, Dockerfile inspection, `mvn -q -DskipTests compile`, README/spec consistency, and Compose config rendering still pass. Missing image/runtime evidence is stated explicitly. |
| Bad | PG/Redis still publish host ports in the default Compose command; `.env.example` suggests default DB/Redis host exposure; backend breaks because datasource/Redis accidentally point to localhost inside Compose; non-root user cannot write uploads; healthcheck relies on a tool no longer installed or cannot run under the selected user; docs and spec disagree. |

## Likely Files To Modify

- `deploy/docker-compose.yml`: remove default PG/Redis host publication and preserve service healthchecks/dependencies.
- Optional new `deploy/docker-compose.host-ports.yml`: explicit local debugging override for PG/Redis host ports, if this is the chosen opt-in mechanism.
- `.env.example`: re-scope `POSTGRES_PORT` and `REDIS_PORT` as opt-in variables or remove them from default runtime comments if no longer used by default.
- `backend/Dockerfile`: add bounded non-root runtime user and permissions if feasible without disrupting the Maven build stage.
- `frontend/Dockerfile`: review root behavior; change only if low-risk and verified.
- `README.md`: update deployment/local-dev commands, environment table, and healthcheck/runtime notes.
- `.trellis/spec/sangui-rag-gateway.md`: update implemented Docker Compose baseline, service exposure matrix, validation matrix, and Good/Base/Bad cases.
- Possibly `.trellis/spec/backend/quality-guidelines.md`: update Docker runtime build/user/healthcheck validation if backend Dockerfile contract changes.

## Required Tests And Assertion Points

Run from repository root unless noted:

```powershell
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

If an opt-in override file is added:

```powershell
docker compose --env-file .env.example -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml config
```

Backend compile after Dockerfile/spec-adjacent changes:

```powershell
cd backend
mvn -q -DskipTests compile
```

Docker image checks when Docker is available:

```powershell
docker build --progress=plain -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
docker build --progress=plain -t sangui-rag-gateway-frontend:ci -f frontend/Dockerfile frontend
docker compose --progress=plain --env-file .env.example -f deploy/docker-compose.yml build backend --no-cache
```

If runtime smoke is feasible:

```powershell
docker compose --env-file .env.example -f deploy/docker-compose.yml up -d --build
docker compose --env-file .env.example -f deploy/docker-compose.yml ps
curl.exe -sf http://localhost:8080/api/health
docker compose --env-file .env.example -f deploy/docker-compose.yml down
```

Diff and hygiene checks:

```powershell
git diff --check
rg -n -g '!frontend/node_modules/**' -g '!backend/target/**' "POSTGRES_PORT|REDIS_PORT|ports:|HEALTHCHECK|USER|RAG_GATEWAY_SECRET_KEY|api_key|password" deploy backend frontend README.md .env.example .trellis/spec
```

## Planning Notes For Implementer

- This is a structural deployment-contract fix, not a one-line hotfix. The invariant is: internal infrastructure dependencies are private inside the Compose network unless the operator explicitly opts into host publication.
- Keep default local full-stack onboarding simple: one command still starts the stack and exposes only the user-facing backend/frontend services.
- Do not weaken production guardrails or hide failures. If non-root runtime breaks file permissions or healthcheck execution, fix ownership/permissions explicitly or document the bounded follow-up.
- Prefer static, reproducible checks first; run full image/runtime smoke when Docker is available.
