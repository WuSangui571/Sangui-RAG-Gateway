# Sangui-RAG-Gateway

> Lightweight OpenAI-compatible RAG enhancement gateway.
>
> This project supports a compatible subset of OpenAI Chat Completions API.

Let existing business systems gain private-document RAG capability with low modification and low user-facing awareness.

## Current Status

**V0.1 baseline** - project scaffold and local development environment. No RAG business logic implemented yet.

### Implemented

- Spring Boot 3.x backend scaffold with health check
- PostgreSQL + pgvector Docker Compose service
- Redis Docker Compose service
- Flyway database migration for the pgvector extension
- Admin response envelope and global exception handling

### Not Yet Implemented

- Login / registration
- Knowledge base management and document upload
- RAG retrieval, embedding, or document pipeline
- `GET /v1/models` and `POST /v1/chat/completions` (currently unimplemented gateway endpoints return a safe 404 response instead of 500)
- Frontend admin pages (placeholder only)

## Local Dependencies

| Dependency | Version | Notes |
|---|---|---|
| Java | 21+ | |
| Maven | 3.9+ | Maven wrapper is not generated yet |
| Docker | 24+ | for PostgreSQL + pgvector and Redis |
| Docker Compose | 2.x | |

## Quick Start

### 1. Clone and prepare environment

```bash
git clone <repo-url>
cd Sangui-RAG-Gateway
cp .env.example .env
```

### 2. Start infrastructure (PostgreSQL + Redis)

```bash
docker compose --env-file .env -f deploy/docker-compose.yml up -d
```

Wait for both services to become healthy:

```bash
docker ps
```

### 3. Start backend

```bash
cd backend
mvn spring-boot:run
```

If a Maven wrapper is generated later, the equivalent commands are `./mvnw spring-boot:run` on Linux/macOS and `mvnw.cmd spring-boot:run` on Windows.

### 4. Verify health

```bash
curl http://localhost:8080/api/health
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

Actuator health is also exposed in local dev:

```bash
curl http://localhost:8080/actuator/health
```

### 5. Run tests

```bash
cd backend
mvn test
```

If a Maven wrapper is generated later, use `./mvnw test` on Linux/macOS or `mvnw.cmd test` on Windows.

## Project Structure

```text
Sangui-RAG-Gateway/
backend/                          # Spring Boot backend
  src/main/java/com/sangui/raggateway/
    common/
      config/                     # Spring beans and properties
      exception/                  # BusinessException + GlobalExceptionHandler
      response/                   # ApiResponse envelope
    health/                       # HealthController
frontend/                         # Placeholder, no UI yet
deploy/                           # Docker Compose and infra config
docs/                             # Documentation
.trellis/                         # Workflow and spec files
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `POSTGRES_DB` | `sangui_rag_gateway` | Database name |
| `POSTGRES_USER` | `sangui` | Database user |
| `POSTGRES_PASSWORD` | `sangui_password` | Database password |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `REDIS_PORT` | `6379` | Redis port |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring active profile |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/sangui_rag_gateway` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `sangui` | Datasource username |
| `SPRING_DATASOURCE_PASSWORD` | `sangui_password` | Datasource password |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis host |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis port |
| `RAG_GATEWAY_SECRET_KEY` | `local-dev-change-me` | Gateway secret key |

## License

MIT
