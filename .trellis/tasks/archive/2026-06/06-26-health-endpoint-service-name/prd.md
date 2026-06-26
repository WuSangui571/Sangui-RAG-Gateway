# Health endpoint service name governance

## Goal

Tighten the stable output contract for the existing public health endpoint so operators, README examples, Docker Compose health checks, specs, and backend tests agree on the same service identity.

This is a small governance task adjacent to the README/deployment verification chain. It must stay focused on `/api/health` response naming and documentation drift, not expand into readiness/liveness, Actuator, dependency probes, or monitoring.

## Task Classification

Simple Task.

Rationale:
- The endpoint, response shape, and unit test already exist.
- The expected blast radius is narrow: health controller/test plus README/spec/Compose healthcheck text if drift is proven.
- It touches a public health API and deployment verification text, so the PRD records the API and validation contract explicitly.

## Current Evidence

- `backend/src/main/java/com/sangui/raggateway/health/HealthController.java` returns `ApiResponse.success(Map.of("status", "UP", "service", "sangui-rag-gateway"))`.
- `backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java` asserts `$.data.status == "UP"` and `$.data.service == "sangui-rag-gateway"`.
- `README.md` already documents the expected response with `data.service = "sangui-rag-gateway"`.
- `.trellis/spec/sangui-rag-gateway.md` already documents the expected response with `data.service = "sangui-rag-gateway"`.
- `deploy/docker-compose.yml` backend healthcheck currently checks only `code=OK`, not the service field.
- Runtime evidence wording in README/spec/docs frequently checks only `code=OK` and `data.status=UP`, so service-name expectations may still be under-specified in verification text.

## Stable Contract

### API

```http
GET /api/health
```

The endpoint is public and must not require Admin JWT or app API key authentication.

### Success Payload

HTTP 200 with admin-style `ApiResponse` envelope:

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

Field contract:

| Field | Requirement |
|---|---|
| `code` | Stable success code `OK`. |
| `message` | Stable success message `success`. |
| `data.status` | Stable literal `UP`. |
| `data.service` | Stable literal `sangui-rag-gateway`. |

Fields not required for this task:

| Field | Decision |
|---|---|
| `version` | Do not add unless a separate versioning contract is defined. |
| `timestamp` / `time` | Do not add; it makes assertions noisier and is not required for health. |
| dependency states | Do not add PostgreSQL, Redis, storage, upstream, or readiness checks. |

### Docker / Command Contract

Backend Compose healthcheck should remain a lightweight local endpoint check:

```bash
curl -sf http://localhost:${SERVER_PORT:-8080}/api/health
```

The command may assert `code=OK`, `data.status=UP`, and `data.service=sangui-rag-gateway` if the assertion can stay POSIX/alpine-compatible and not introduce fragile parsing dependencies. Do not add `jq` unless the image/build contract explicitly accepts it.

### Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| Direct backend health request | HTTP 200, `code=OK`, `message=success`, `data.status=UP`, `data.service=sangui-rag-gateway` | `HealthControllerTest` |
| `/api/health` without auth | Succeeds without Admin JWT or app API key | `GatewayAuthFilterTest` existing bypass coverage plus health test |
| Frontend proxy `/api/health` | JSON health envelope, not SPA HTML | README/manual smoke text; no frontend code change unless drift is found |
| Compose backend healthcheck | Uses backend local `/api/health` and remains compatible with backend runtime image tools | `docker compose --env-file .env.example -f deploy/docker-compose.yml config` and Dockerfile curl availability |
| Unknown route | No fake OpenAI response and no health payload reuse | Existing global route tests; do not change in this task |

No separate error payload is introduced for `/api/health`; failures should surface as normal process/HTTP failures.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Health endpoint returns the exact stable service identity; README, spec, backend test, and Compose/runtime verification text all describe the same field names and value. |
| Base | Compose healthcheck continues to assert only `code=OK` for minimal container health, while README/spec/tests document and verify the stronger direct API contract including `data.service`. This is acceptable if explicitly documented. |
| Bad | Response uses `serviceName`, `service_name`, display title, Spring application name fallback, or another value in some places; docs/tests/healthcheck assert different names; task expands into dependency readiness or Actuator-style monitoring. |

## Expected Implementation Scope

Likely files if drift is found:

```text
backend/src/main/java/com/sangui/raggateway/health/HealthController.java
backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java
README.md
deploy/docker-compose.yml
.trellis/spec/sangui-rag-gateway.md
docs/runtime-evidence-checklist.md
scripts/demo-smoke.ps1
```

The implementation should modify only the subset proven to be out of sync.

## Non-Goals / Forbidden Scope

- Do not add `/api/ready`, `/api/live`, `/actuator/health`, or monitoring endpoints.
- Do not probe PostgreSQL, Redis, storage, upstream providers, migrations, queues, or worker status.
- Do not add DB schema, migrations, frontend types, Admin API DTOs, or `/v1/*` API changes.
- Do not introduce a second health response source or duplicate service-name constants across multiple layers if a single controller-level constant is enough.
- Do not rely on hidden fallbacks, environment defaults, or Spring app name auto-binding unless explicitly tested and documented.
- Do not add `jq` or other runtime tooling to Docker just to parse health JSON.

## Required Tests And Checks

Run from `backend/` unless noted:

```bash
mvn -q "-Dtest=HealthControllerTest,GatewayAuthFilterTest" test
mvn -q -DskipTests compile
```

Run from repo root:

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
git diff --check
```

Optional when documentation or smoke script text changes:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

## Acceptance Criteria

- [ ] `/api/health` direct response contract is explicit and tested: `code=OK`, `message=success`, `data.status=UP`, `data.service=sangui-rag-gateway`.
- [ ] README, project spec, runtime evidence checklist, smoke script messaging, and Compose healthcheck are either synchronized to that contract or intentionally documented as minimal health probes.
- [ ] No new health/readiness/monitoring scope is introduced.
- [ ] No API, DB, frontend type, auth, gateway `/v1/*`, RAG, or storage behavior changes are introduced.
- [ ] Required backend tests and static/config checks pass, or any unrun check has a concrete environment reason.

