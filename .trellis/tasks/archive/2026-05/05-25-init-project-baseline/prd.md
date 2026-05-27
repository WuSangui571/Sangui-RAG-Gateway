# 初始化 Sangui-RAG-Gateway 项目脚手架与工程基线

## Task Classification

Complex Task.

Reason: this task creates the project baseline across backend, frontend placeholder structure, deployment, database migration, Redis/PostgreSQL integration, local commands, README, and health-check API contracts. It changes infrastructure and cross-layer development conventions, so implementation must be planned before coding.

## Goal

在空项目中建立 Sangui-RAG-Gateway 的基础工程结构和本地开发环境。后端采用 Java 21 + Spring Boot 3.x；PostgreSQL + pgvector 作为主数据库和向量存储基础；Redis 作为缓存与后续限流基础。本任务只建立可运行、可扩展、可持续迭代的工程基线，不实现具体 RAG 业务。

## Product Boundary

本任务服务于轻量级 OpenAI-compatible RAG gateway 的工程基础能力：

- 提供后续 backend 模块、数据库迁移、Docker Compose、本地启动和健康检查的稳定入口。
- 不扩展成低代码平台、聊天平台、知识库管理功能或完整 OpenAI API。
- 不实现登录注册、知识库上传、RAG 检索、OpenAI-compatible API 或前端页面。

## Requirements

- 创建项目顶层目录结构：
  - `backend/`
  - `frontend/`
  - `deploy/`
  - `docs/`
  - 保留并继续使用 `.trellis/spec/`
- 初始化 Spring Boot 后端工程：
  - Java 21
  - Spring Boot 3.x
  - Maven 优先，除非实现阶段发现已有构建系统要求不同
  - root package: `com.sangui.raggateway`
- 配置后端基础能力：
  - 统一响应结构，供后续 admin API 使用
  - 统一异常处理，隐藏内部异常和堆栈
  - 安全日志基础规则，不记录 secrets、Authorization、完整私有内容
  - PostgreSQL datasource 连接配置
  - Redis 连接配置
  - Flyway 数据库迁移机制
- 配置 Docker Compose：
  - PostgreSQL + pgvector
  - Redis
  - 推荐容器名、端口、volume 和健康检查
  - 提供 `.env.example`
- 创建基础健康检查接口：
  - 简单应用健康接口
  - 至少验证应用进程可访问
  - 可选或推荐暴露 Actuator health，用于依赖健康检查
- 编写 README：
  - 项目定位
  - 本地依赖
  - Docker Compose 启动方式
  - 后端启动方式
  - 健康检查访问方式
  - 明确说明当前未实现登录、知识库、RAG、OpenAI-compatible API、前端页面
  - 必须包含：`This project supports a compatible subset of OpenAI Chat Completions API.`

## Non-Goals

- 不实现登录注册。
- 不实现知识库上传。
- 不实现文档解析、chunk、embedding、RAG 检索。
- 不实现 `GET /v1/models` 或 `POST /v1/chat/completions`。
- 不实现前端管理端页面或 UI 组件。
- 不引入 MinIO、消息队列、OAuth、工作流/agent/plugin 平台能力。
- 不创建业务表结构，如 app、api key、knowledge base、document chunk。

## Acceptance Criteria

- [ ] 后端服务可以在 Java 21 环境下正常启动。
- [ ] Docker Compose 可以启动 PostgreSQL + pgvector 和 Redis。
- [ ] 后端可以通过配置连接 PostgreSQL。
- [ ] Flyway 自动执行基础迁移，至少完成 pgvector 扩展或 baseline 初始化。
- [ ] 后端可以连接 Redis，启动时无连接配置错误。
- [ ] 健康检查接口可访问，并返回明确的成功响应。
- [ ] `README.md` 能指导本地从零启动依赖和后端。
- [ ] 项目结构清晰，后续可以继续开发 gateway、app、apikey、knowledge、document、retrieval、rag 等模块。
- [ ] 未实现或误暴露业务范围外 API。

## API / Command / Payload Contracts

### Local Commands

Implementation should document and support these commands or equivalent Maven/Docker commands:

```text
docker compose --env-file .env up -d
cd backend
./mvnw spring-boot:run
./mvnw test
./mvnw verify
```

On Windows, README should also mention Maven wrapper command shape:

```text
mvnw.cmd spring-boot:run
mvnw.cmd test
```

If Maven wrapper is not generated, use:

```text
mvn spring-boot:run
mvn test
```

### Environment Variables

Expected local environment keys:

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

Rules:

- `.env.example` may contain safe placeholder values.
- `.env` should not be committed if generated.
- No real secrets should be committed.

### Health Check API

Required custom application health endpoint:

```http
GET /api/health
```

Expected success response should use the project's admin response envelope:

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

Allowed implementation detail:

- Actuator may also expose `GET /actuator/health`.
- If exposed, README should state whether it is public in local dev only.

### Admin Response Envelope

Suggested baseline shape:

```json
{
  "code": "OK",
  "message": "success",
  "data": {}
}
```

For failures:

```json
{
  "code": "INTERNAL_ERROR",
  "message": "Internal server error",
  "data": null
}
```

Notes:

- This envelope is for admin/internal APIs only.
- Future `/v1/*` gateway errors must use OpenAI-compatible error shape instead.

## Validation / Error Matrix

| Area | Condition | Expected Behavior | Assertion Point |
|---|---|---|---|
| Health API | Backend running | `GET /api/health` returns 200 and `data.status=UP` | Controller test or curl |
| Health API | Unexpected exception | Global exception handler returns non-stacktrace JSON | Unit/web test |
| Datasource | PostgreSQL reachable | App starts and Flyway runs | `spring-boot:run` or integration smoke |
| Datasource | PostgreSQL unreachable | App fails fast with safe logs, no secret leakage | Manual negative note or test if practical |
| Migration | pgvector extension migration | Migration succeeds against pgvector image | Docker + app startup |
| Redis | Redis reachable | App starts with Redis configuration loaded | Startup smoke |
| Config | Missing required env in local defaults | Dev profile uses documented safe defaults or clear failure | README + config review |
| Logging | Request/error logging | Does not print Authorization, DB passwords, API keys, private content | Code review/check |
| API boundary | `/v1/*` not implemented | No accidental OpenAI-compatible endpoints in this task | Route/code search |
| Frontend boundary | No admin UI pages | `frontend/` may be placeholder only | File review |

## Good / Base / Bad Cases

### Good Case

Fresh clone, Java 21 and Docker available:

1. Copy `.env.example` to `.env`.
2. Run Docker Compose.
3. Start backend.
4. Flyway creates baseline database state.
5. `GET /api/health` returns success JSON.
6. Tests pass.

### Base Case

Docker Compose services are already running:

1. Start backend with dev profile.
2. Backend reuses existing PostgreSQL and Redis.
3. Flyway reports no pending migration after first run.
4. Health endpoint remains stable.

### Bad Cases

- PostgreSQL is not running: backend startup should fail clearly or health should report dependency down if app is designed to start without DB; implementation should document chosen behavior.
- Redis is not running: backend startup should fail clearly or health should report dependency down; implementation should document chosen behavior.
- `.env` contains wrong DB password: failure must not log plaintext password.
- A caller hits `/v1/chat/completions`: endpoint should not exist yet; do not return a fake success.
- A caller hits unknown endpoint: default framework 404 or safe error JSON, no stack trace.

## Implementation Plan For DeepSeek

1. Create repository root structure: `backend/`, `frontend/`, `deploy/`, `docs/`.
2. Initialize backend Spring Boot project under `backend/`.
3. Add backend dependencies conservatively:
   - Spring Web
   - Spring Validation
   - Spring Actuator
   - PostgreSQL driver
   - Flyway
   - Spring Data Redis
   - Lombok only if configured cleanly and accepted by tests
   - MyBatis-Plus may be included now for baseline, but avoid unused mapper/business scaffolding if it adds complexity.
4. Add Spring configuration files:
   - `application.yml`
   - `application-dev.yml`
   - optional `application-test.yml`
5. Add common response and exception baseline under `com.sangui.raggateway.common`.
6. Add health endpoint under a small health/system package or `common`-adjacent package. Keep it out of future OpenAI gateway modules.
7. Add Flyway migration under `backend/src/main/resources/db/migration/`.
8. Add Docker Compose and `.env.example` under repo root or `deploy/` with README-consistent paths.
9. Add README with local startup and current scope limitations.
10. Add tests for health endpoint, response envelope/global exception behavior where practical, and application context startup.

## Files Likely To Modify

- `README.md`: create local startup guide and scope notes.
- `.env.example`: create safe local defaults.
- `.gitignore`: add generated/local files if missing.
- `docker-compose.yml` or `deploy/docker-compose.yml`: PostgreSQL/pgvector + Redis.
- `backend/pom.xml`: backend build and dependencies.
- `backend/src/main/java/com/sangui/raggateway/SanguiRagGatewayApplication.java`: Spring Boot entry point.
- `backend/src/main/java/com/sangui/raggateway/common/response/ApiResponse.java`: admin response envelope.
- `backend/src/main/java/com/sangui/raggateway/common/exception/*`: base exception and global handler.
- `backend/src/main/java/com/sangui/raggateway/common/config/*`: config/properties if needed.
- `backend/src/main/java/com/sangui/raggateway/health/HealthController.java`: `GET /api/health`.
- `backend/src/main/resources/application.yml`: shared Spring config.
- `backend/src/main/resources/application-dev.yml`: dev datasource/redis/flyway config.
- `backend/src/main/resources/db/migration/V1__init_pgvector.sql`: baseline migration.
- `backend/src/test/java/...`: health/global exception/application context tests.
- `frontend/README.md` or `.gitkeep`: placeholder only, no UI implementation.
- `docs/`: optional developer notes if README gets too long.

## Required Tests And Assertion Points

Must run before handing back to Codex check/finish-work:

```text
cd backend
mvn test
mvn -q -DskipTests compile
```

If Maven wrapper exists:

```text
cd backend
./mvnw test
./mvnw -q -DskipTests compile
```

Local smoke verification:

```text
docker compose --env-file .env up -d postgres redis
cd backend
mvn spring-boot:run
curl http://localhost:8080/api/health
```

Assertions:

- Spring application context loads.
- Health endpoint returns HTTP 200 and expected JSON fields.
- Flyway migration succeeds against pgvector image.
- Backend connects to PostgreSQL and Redis with documented dev config.
- No `/v1/models` or `/v1/chat/completions` endpoint exists yet.
- README commands match actual file locations and command names.

## Risks / Boundary Notes

- This is infrastructure setup, so env variable names and Docker Compose paths become future contracts. Keep them simple and documented.
- Do not create business tables prematurely; doing so would force early domain choices outside this task.
- Do not expose OpenAI-compatible endpoints as stubs; future compatibility behavior requires its own task and tests.
- Do not log secrets or full configuration values.
- Keep frontend as a placeholder structure only because this task explicitly excludes admin pages.
- Prefer conservative dependencies. Avoid adding broad infrastructure that is not needed for this baseline.

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden/out-of-scope areas are explicit.
- [x] Expected modified files are listed.
- [x] Required test commands are listed.
- [x] Concrete backend/frontend/guides guideline files were read, not only indexes.
- [x] No unresolved user clarification is required before implementation.
- [x] API/command/env payload fields are documented for this baseline.
- [x] DB migration boundary is documented; no business schema is requested.
- [x] Frontend type/DTO changes are not required because frontend UI/API client work is out of scope.
