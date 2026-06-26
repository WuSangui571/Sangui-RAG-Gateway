# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product/deployment source of truth; already documents `GET /api/health` with `data.status=UP` and `data.service=sangui-rag-gateway`; runtime smoke sections currently emphasize `code=OK` / `data.status=UP`.
- `.trellis/spec/backend/directory-structure.md`: health controller belongs under backend controller package structure; keep controller simple and avoid service-layer overbuild for static health metadata.
- `.trellis/spec/backend/error-handling.md`: `/api/health` is explicitly outside `/v1/*` gateway auth and uses `ApiResponse`, not OpenAI-compatible error shape.
- `.trellis/spec/backend/logging-guidelines.md`: no new logging should be needed; if changed, health logs must not include secrets or request bodies.
- `.trellis/spec/backend/quality-guidelines.md`: backend Docker runtime healthcheck must work with the non-root `sangui` user and available runtime tools.
- `.trellis/spec/gateway/resilience.md`: useful mainly as a boundary reminder; do not turn health into provider fallback/timeout/readiness work.
- `.trellis/spec/security/rag-security.md`: confirms `/api/health` should not expose keys, prompts, storage paths, provider bodies, or environment values.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: applies because the task crosses API/docs/deployment verification; define payload fields and Good/Base/Bad cases before implementation.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search existing constants/fields before adding a second service-name source.

## Code Patterns Found

- Static health controller pattern:
  - `backend/src/main/java/com/sangui/raggateway/health/HealthController.java`
  - Current endpoint returns `ApiResponse<Map<String, String>>` with `status=UP` and `service=sangui-rag-gateway`.
- Focused MockMvc controller test:
  - `backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java`
  - Current test asserts `$.code`, `$.message`, `$.data.status`, and `$.data.service`.
- Auth bypass test pattern:
  - `backend/src/test/java/com/sangui/raggateway/common/security/GatewayAuthFilterTest.java`
  - `shouldNotFilterNonV1Paths()` asserts `/api/health` is not handled by gateway app-key auth.
- Compose healthcheck pattern:
  - `deploy/docker-compose.yml`
  - Backend healthcheck uses `curl -sf http://localhost:${SERVER_PORT:-8080}/api/health | grep -q '\"code\":\"OK\"' || exit 1`.
- Runtime smoke script pattern:
  - `scripts/demo-smoke.ps1`
  - Backend health currently checks `code=OK` and `data.status=UP`; frontend proxy health checks only JSON and `code=OK`.
- Evidence checklist pattern:
  - `docs/runtime-evidence-checklist.md`
  - Health evidence examples currently record `code=OK` and `data.status=UP`, not `data.service`.

## Files Likely To Modify

Expected implementation should modify only files with proven drift:

- `scripts/demo-smoke.ps1`: likely add `data.service == "sangui-rag-gateway"` assertion and safe PASS message for backend health; consider whether frontend proxy health should also assert the same service field.
- `docs/runtime-evidence-checklist.md`: likely update health evidence templates/examples to record `data.service=sangui-rag-gateway`.
- `README.md`: likely update runtime smoke / proxy health wording where it only says `code=OK` / `data.status=UP`; the main expected response block already includes service.
- `.trellis/spec/sangui-rag-gateway.md`: likely update runtime smoke/health matrix wording where it only says `code=OK` / `data.status=UP`; the endpoint expected response block already includes service.
- `deploy/docker-compose.yml`: optional only. Keep minimal `code=OK` check if avoiding fragile shell parsing is preferred; if changed, must remain compatible with Alpine `curl`/`grep` and no `jq`.
- `backend/src/main/java/com/sangui/raggateway/health/HealthController.java`: probably no change unless DeepSeek chooses to extract a single controller-level constant for the service name.
- `backend/src/test/java/com/sangui/raggateway/health/HealthControllerTest.java`: probably no change unless implementation moves the service-name constant or adds a regression name assertion style.

## Risk / Boundary Notes

- Do not introduce version/timestamp fields in this task. They are not part of the current health contract and would widen assertions.
- Do not add dependency probes for PostgreSQL, Redis, storage, upstream provider, worker, Flyway, or Actuator.
- Do not change `/v1/*` gateway response shapes or auth filters.
- Do not change Admin JWT/app API-key behavior; `/api/health` remains public.
- Do not add `jq` or new runtime packages just for Compose health JSON parsing.
- If Compose healthcheck remains minimal, document that it is a container liveness probe while tests/smoke/docs assert the stronger API contract.
- Avoid adding duplicate service-name constants across docs/code. If code changes are needed, prefer one local constant in `HealthController` and one assertion in `HealthControllerTest`.

## Required Tests

Backend targeted tests:

```bash
cd backend
mvn -q "-Dtest=HealthControllerTest,GatewayAuthFilterTest" test
mvn -q -DskipTests compile
```

Repo-root checks:

```bash
docker compose --env-file .env.example -f deploy/docker-compose.yml config
git diff --check
```

PowerShell script syntax check if `scripts/demo-smoke.ps1` changes:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

## Research Summary

The implementation and main expected-response docs already agree on the service field: `data.service=sangui-rag-gateway`. The remaining likely drift is in operational verification surfaces that only assert or record `code=OK` and `data.status=UP`. The task should tighten those surfaces without turning `/api/health` into a richer readiness endpoint.

