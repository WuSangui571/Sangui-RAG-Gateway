# #12 CI 缺安全/镜像运行验证

## Goal

把上一轮 Docker runtime exposure hardening 的部署安全合同固化到 CI 和文档中，避免后续改动重新引入 PostgreSQL/Redis 默认 host 端口暴露、backend root runtime、失效 healthcheck、不可写上传目录或真实 secret 泄漏。

本任务是 infra / CI / deployment / security 验证任务。Codex 本轮只完成规划、PRD、spec/context 准备和代码研究；实现由 DeepSeek 端执行。

## Classification

- Scope: Complex Task
- Type: infra / CI / Docker / Compose / security documentation
- Hotfix vs structural: Structural. 这不是单点修补 CI，而是把部署合同表达成可重复执行的 CI 断言和 README/spec 失败边界。

## Current State

- 当前分支: `feature/ci-image-runtime-validation`
- 上一轮 journal 已记录 `Session 82: Docker runtime exposure hardening`
- 已完成合同:
  - 默认 `deploy/docker-compose.yml` 下 `postgres` / `redis` 无 host `ports`
  - `deploy/docker-compose.host-ports.yml` 显式 opt-in 后才暴露 PG/Redis host ports
  - `backend/Dockerfile` runtime stage 创建并切换到非 root 用户 `sangui`
  - `/app/data/uploads` 创建并归属 `sangui`
  - backend Compose healthcheck 调用 `/api/health`
- 未完全闭环证据:
  - backend Docker image build 曾受基础镜像层下载/TLS/descriptor 问题阻塞
  - Compose runtime smoke 尚未自动验证 `whoami=sangui`、uploads 可写、`/api/health` 真正可用

## Requirements

- 保留现有 Maven backend checks、frontend lint/test/typecheck/build/visual smoke checks。
- CI 中继续构建 backend 和 frontend Docker images，不推送 registry。
- CI 增加 Compose contract check:
  - 默认 Compose config 渲染必须通过。
  - 默认 config 下 `postgres` 和 `redis` 服务不得存在 host `ports`。
  - 默认 config 下 backend 必须使用 `postgres:5432` 和 `SPRING_DATA_REDIS_HOST=redis`。
  - 默认 config 下 backend 必须挂载 `backend-data` 到 `/app/data/uploads`。
  - 叠加 `deploy/docker-compose.host-ports.yml` 后，`postgres` 和 `redis` 才必须出现 host ports。
- CI 增加 runtime smoke:
  - 启动 Compose stack 后等待 backend healthy 或明确失败。
  - 从 host 访问 backend `/api/health`，断言 HTTP 200 且响应包含 `code=OK` / `data.status=UP`。
  - 在 backend container 内执行 `whoami`，断言输出为 `sangui`。
  - 在 backend container 内写入 `/app/data/uploads` 下的临时文件并删除，断言上传目录可写。
  - smoke 结束必须清理 Compose stack 和 volume，避免影响后续 job。
- CI 增加 security/runtime scan:
  - 扫描 `.github/workflows/ci.yml`、`backend/Dockerfile`、`frontend/Dockerfile`、`deploy/docker-compose.yml`、`deploy/docker-compose.host-ports.yml`、`.env.example`、`backend/settings.xml`、README/spec 更新片段中没有真实 secret、生成的 `sk-sangui-*` key、provider key、docker login/push 或 private repo credential。
  - 断言 backend runtime stage 没有回退 root: `USER sangui` 必须存在，且不能有后续 `USER root`。
  - 断言 `backend/settings.xml` 只含公开 Maven mirror 元数据，并保持 Maven Central fallback。
- README/spec 补充 CI 验证矩阵和失败排查边界:
  - 区分代码失败、Compose contract failure、runtime smoke failure、Docker Hub/base image pull/network failure。
  - 说明基础镜像拉取、registry TLS、rate limit、missing content descriptor 一类问题不是直接证明 Dockerfile 代码失败；需要查看失败阶段和重跑策略。
  - 说明 CI 不需要 provider keys、app API keys、docker registry push credentials。

## API / Command / Payload Contract

No public API, DTO, frontend type, database schema, or migration change is expected.

Commands / CI assertions that define this task:

```bash
# existing backend checks
cd backend
mvn -q -DskipTests compile
mvn test

# existing frontend checks
cd frontend
npm ci
npm run lint
npm run test
npx playwright install chromium
npm run typecheck
npm run build
npm run test:visual:ci

# docker image checks
docker build --progress=plain -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
docker build --progress=plain -t sangui-rag-gateway-frontend:ci -f frontend/Dockerfile frontend

# compose contract checks
docker compose --env-file .env.example -f deploy/docker-compose.yml config
docker compose --env-file .env.example -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml config

# compose runtime smoke
docker compose --env-file .env.example -f deploy/docker-compose.yml up -d --build
docker compose --env-file .env.example -f deploy/docker-compose.yml ps
docker compose --env-file .env.example -f deploy/docker-compose.yml exec -T backend whoami
docker compose --env-file .env.example -f deploy/docker-compose.yml exec -T backend sh -c 'test "$(whoami)" = "sangui"'
docker compose --env-file .env.example -f deploy/docker-compose.yml exec -T backend sh -c 'touch /app/data/uploads/.ci-write-test && rm /app/data/uploads/.ci-write-test'
curl --fail --silent --show-error http://localhost:8080/api/health
docker compose --env-file .env.example -f deploy/docker-compose.yml down -v
```

Environment / Compose fields that must stay aligned:

| Field | Expected contract |
|---|---|
| `POSTGRES_PORT` | Only used by `deploy/docker-compose.host-ports.yml`, not default Compose host exposure |
| `REDIS_PORT` | Only used by `deploy/docker-compose.host-ports.yml`, not default Compose host exposure |
| `SPRING_DATASOURCE_URL` in backend Compose env | `jdbc:postgresql://postgres:5432/...` |
| `SPRING_DATA_REDIS_HOST` in backend Compose env | `redis` |
| `FILE_STORAGE_LOCAL_PATH` in backend Compose env | `/app/data/uploads` |
| backend volume | `backend-data:/app/data/uploads` |
| backend runtime user | `sangui` |
| backend health endpoint | `/api/health` returns `code=OK`, `data.status=UP` |

## Validation / Error Matrix

| Scenario | Expected result | Failure boundary |
|---|---|---|
| Backend Maven compile/test fails | CI fails; Maven error is the primary evidence | backend |
| Frontend lint/test/typecheck/build/visual smoke fails | CI fails; frontend command output is primary evidence | frontend |
| Backend Docker build fails before project files are copied because base image cannot be pulled | CI fails, but README/spec classify as registry/network/base-image pull boundary | image-pull |
| Backend Docker build fails during `mvn -B -ntp -DskipTests package` | CI fails as backend Docker build/code/dependency boundary | docker-backend |
| Frontend Docker build fails during `npm ci` or `npm run build` | CI fails as frontend Docker build/dependency boundary | docker-frontend |
| Default Compose config has `postgres.ports` or `redis.ports` | CI fails | compose-exposure |
| Host-port override lacks PG/Redis ports | CI fails | compose-override |
| Backend Compose env uses `localhost` for PostgreSQL/Redis | CI fails | compose-service-discovery |
| Backend healthcheck or host `/api/health` never reaches `code=OK` / `data.status=UP` | CI fails; inspect backend logs and production guard/env | runtime-health |
| `docker compose exec backend whoami` is not `sangui` | CI fails | runtime-user |
| `/app/data/uploads` write/delete fails | CI fails | runtime-storage |
| CI or deployment files contain real secrets or generated app keys | CI fails or check review fails | secret-scan |
| Dockerfile runtime stage has `USER root` after `USER sangui` or no `USER sangui` | CI fails | runtime-user |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh CI runner checks backend/frontend, builds backend/frontend images, proves default Compose has no PG/Redis host ports, proves host-port override publishes only when included, starts Compose, `/api/health` returns OK/UP, backend `whoami` is `sangui`, `/app/data/uploads` is writable, and no committed secret or registry push credential is needed. |
| Base | Docker registry/base image pull has transient network/TLS/rate-limit/content-descriptor failure. CI fails visibly, README/spec identify the boundary as image-pull rather than silently marking success. Local fallback checks still include Compose config rendering, Dockerfile/runtime-user static assertions, Maven/frontend checks, and secret scan. |
| Bad | CI only builds images but never starts runtime; default Compose reintroduces PG/Redis host ports; backend uses `localhost` inside Compose; runtime runs as root; uploads directory is not writable; workflow needs provider keys or docker push credentials; docs treat image pull infrastructure outages as proven code failures. |

## Files Likely To Modify

- `.github/workflows/ci.yml`: add Compose contract check, runtime smoke, security/runtime scan, while preserving current backend/frontend checks and Docker builds.
- `README.md`: update CI matrix, local/CI validation commands, and image-pull failure boundary.
- `.trellis/spec/sangui-rag-gateway.md`: update implemented CI/deployment baseline and validation matrix.
- `.trellis/spec/backend/quality-guidelines.md`: update Docker runtime validation requirements if CI contract changes backend Docker checks.

Possible helper script if CI YAML becomes too large:

- `scripts/ci-compose-contract.*` or `scripts/ci-docker-runtime-smoke.*`

If a helper is added, keep it shell-compatible for GitHub Ubuntu runners and document any local Windows equivalent separately. Do not add a second implementation of the same assertions unless there is a clear cross-platform reason.

## Out Of Scope / Forbidden Changes

- Do not change backend business APIs, DTOs, services, mappers, migrations, RAG retrieval, prompt building, ingestion, request-log behavior, auth behavior, or frontend application source unless a failing CI check proves a directly related contract bug.
- Do not introduce Docker image push, GHCR publishing, registry login, deployment secrets, provider keys, generated `sk-sangui-*` keys, or real `.env` examples.
- Do not weaken production config guardrails to make Compose smoke pass.
- Do not re-expose PostgreSQL or Redis host ports in default `deploy/docker-compose.yml`.
- Do not change backend runtime back to root or add root-only runtime write paths.
- Do not mask failures with broad `|| true`, mock success paths, or silent fallbacks.

## Required Tests And Assertion Points

Minimum local/CI validation after implementation:

```bash
cd backend
mvn -q -DskipTests compile
mvn test

cd ../frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build

cd ..
docker compose --env-file .env.example -f deploy/docker-compose.yml config
docker compose --env-file .env.example -f deploy/docker-compose.yml -f deploy/docker-compose.host-ports.yml config
docker build --progress=plain -t sangui-rag-gateway-backend:ci -f backend/Dockerfile backend
docker build --progress=plain -t sangui-rag-gateway-frontend:ci -f frontend/Dockerfile frontend
docker compose --env-file .env.example -f deploy/docker-compose.yml up -d --build
docker compose --env-file .env.example -f deploy/docker-compose.yml exec -T backend whoami
docker compose --env-file .env.example -f deploy/docker-compose.yml exec -T backend sh -c 'touch /app/data/uploads/.ci-write-test && rm /app/data/uploads/.ci-write-test'
curl --fail --silent --show-error http://localhost:8080/api/health
docker compose --env-file .env.example -f deploy/docker-compose.yml down -v
git diff --check
```

Required assertions inside automated checks:

- `postgres` and `redis` have no `ports` in default rendered Compose config.
- `postgres` and `redis` have `ports` only when `deploy/docker-compose.host-ports.yml` is included.
- backend env contains service-name DB/Redis dependencies, not host `localhost`.
- backend container user is exactly `sangui`.
- `/app/data/uploads` write/delete works under that user.
- backend `/api/health` returns safe health JSON.
- scan output does not include real secret evidence, generated keys, Docker registry login/push, private Maven repository credentials, or broad hidden fallbacks.

## Planning Self-Check

- Acceptance criteria defined: yes.
- Forbidden modification scope defined: yes.
- Expected modified files listed: yes.
- Required tests and assertion points listed: yes.
- Concrete guideline files read before implementation: yes, including project spec, backend quality, backend directory/logging/error handling, frontend quality/directory, gateway resilience, RAG security, cross-layer guide.
- Open user questions: none before implementation. If Docker registry/network fails during validation, report it as a bounded infrastructure failure with logs rather than broadening code scope.
- API / DB / frontend DTO alignment: no public API, DB schema, frontend DTO/type change expected.
