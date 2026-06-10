# Focused Research: V0.2 RC Tagging and Reproducible Smoke Runbook

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, V0.2 deployment contract, Docker Compose command, health endpoint, OpenAI-compatible gateway scope, demo automation rule, safe/forbidden evidence fields, known limitations.
- `.trellis/spec/backend/quality-guidelines.md`: validation expectations, when backend tests are required, and release-quality review checks for auth, request logs, streaming, tenant isolation, and safe logging.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields and forbidden logging/persistence fields; applies to smoke evidence and release records.
- `.trellis/spec/backend/error-handling.md`: public `/v1/*` OpenAI-compatible error shape and revoked/disabled/expired API keys returning `401 invalid_api_key`.
- `.trellis/spec/gateway/resilience.md`: upstream failure boundaries and safe evidence expectations for `upstream_error`, `upstream_timeout`, and streaming behavior.
- `.trellis/spec/security/rag-security.md`: safe evidence fields, forbidden fields, tenant/logging/prompt/content boundaries.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this release task references Docker/env, gateway/admin APIs, request logs, frontend proxy, smoke commands, and secret boundaries.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend proxy/smoke surface expectations and secret-safety constraints.
- `docs/runtime-evidence-checklist.md`: durable metadata-only smoke evidence template.

## Code Patterns Found

- `README.md` demo acceptance sections:
  - Backend health: `curl.exe -s "$BackendBaseUrl/api/health"`.
  - Frontend proxy health: `curl.exe -s "$FrontendBaseUrl/api/health"` and must return JSON, not SPA HTML.
  - Smoke command: `powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 ... -VerifyRevokedKey`.
  - Safe Evidence Fields and Forbidden Output Fields lists are the canonical recording rules.
- `scripts/demo-smoke.ps1`:
  - Uses `curl.exe`, temp files, UTF-8 no-BOM JSON bodies, and `finally` cleanup.
  - Prints safe metadata only: content length, request ID, model/provider, latency, hit chunk IDs/count, chunk metadata, SSE line count, and revoked-key status/code.
  - Revoked-key step expects HTTP 401 and `error.code=invalid_api_key`; blank revoked key with `-VerifyRevokedKey` is a visible auth failure.
- `deploy/docker-compose.yml`:
  - Full stack services: `postgres`, `redis`, `backend`, `frontend`.
  - Backend uses Compose service names (`postgres`, `redis`), exposes `${BACKEND_PORT:-8080}`, and health-checks `/api/health`.
  - Frontend exposes `${FRONTEND_PORT:-3000}` and proxies to `http://backend:${SERVER_PORT:-8080}`.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`:
  - Final decision is `READY FOR V0.2 RELEASE CANDIDATE`.
  - Fresh demo key blocker is closed.
  - No backend/frontend/API/DB/infra/Docker/CI/smoke behavior changed in the release closeout.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`:
  - Formal metadata-only smoke pass recorded on 2026-06-10 17:57 UTC+8.
  - App ID 5, Admin User ID 1, backend/frontend localhost URLs.
  - Steps 1-7 all PASS; script exit code 0.
  - Request-log evidence: `request_id=6a67c4a6-8a89-49eb-a678-5a37285d46e7`, model `deepseek-v4-pro`, provider `sanguicode`, latency `4120`, hit chunk `[5]`.
  - At that time, fresh demo key cleanup was pending.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`:
  - Fresh demo key `demo-acceptance-20260610`, key ID 28, app ID 5, key prefix `sk-sangui-yuE2Roo9`.
  - Status transitioned from `ACTIVE` to `REVOKED`; `revoked_at=2026-06-10T11:03:19`.
  - Public gateway verification returned HTTP 401 with `error.code=invalid_api_key`.

## Current Git / Tag State At Planning Time

- `git status --short`: only the new Trellis task directory is untracked.
- `git rev-parse HEAD`: `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2`.
- `git log --oneline -8`:
  - `ea55a1c5 chore:记录v0.2 fresh demo key清理会话`
  - `3be0282e docs:确认v0.2 fresh demo key清理`
  - `8a10655c docs:完善v0.2发布就绪收尾记录`
  - `89332c20 chore:记录v0.2 demo验收证据归档会话`
  - `1180ad88 docs:补充v0.2 demo验收证据包`
  - `d17b8061 docs:完善demo smoke runtime evidence清单`
  - `5c8c546c fix:完善demo smoke readiness验收检查`
  - `9085cd91 chore:记录GitHub CI远程证据收尾会话`
- `git tag --list "v0.2.0-rc.*"`: no existing RC tag found.

## Files Likely To Modify

- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/prd.md`: already created; keep as source requirements.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/research.md`: this research handoff.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/release-candidate-runbook.md`: expected implementation artifact containing final RC status summary, reproducible smoke commands, tag preflight results, tag command, rollback notes, and safe evidence.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/implement.jsonl`: context for implementation agent.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/check.jsonl`: context for check agent.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/debug.jsonl`: default context file from Trellis.
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/task.json`: status/phase metadata only.

Optional if the operator wants durable top-level release docs:

- `docs/v0.2-rc-runbook.md`

Do not modify:

- `backend/src/**`
- `frontend/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/docker-compose.yml`
- `.github/workflows/**`
- `scripts/demo-smoke.ps1`
- `frontend/nginx.conf`
- API DTO/VO/type files

## Risk / Boundary Notes

- Release tag must not be created from a dirty tree. The Trellis task files themselves must either be committed first or the tag target must intentionally remain the prior clean release commit; do not tag a mixed uncommitted state.
- The latest release candidate target before this task is `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2`; after this task creates release runbook metadata, a later commit may become the tag target only if it contains metadata-only release records and passes checks.
- The task should not alter product behavior. If runtime smoke fails, record the failure boundary and stop; do not patch backend/frontend behavior in this task.
- Runtime smoke depends on operator-held secrets and configured local resources: `.env`, upstream provider keys, app ID, admin user ID, ready KB, active app key, revoked key. These must remain outside committed files.
- Existing evidence proves a complete V0.2 smoke pass and fresh-key cleanup, but a “clean checkout to smoke pass to tag” final runbook still needs a fresh final operation record.
- `scripts/demo-smoke.ps1` can include bounded HTTP body previews on failures. Any copied evidence must be manually reviewed before committing.
- The archived evidence pack records a safe request ID and hit chunk IDs. These are safe metadata, but do not copy raw JSON responses or answer text.
- README examples include placeholders like `sk-sangui-<your-key>` and forbidden-field rule text. Secret scans will produce expected hits; implementation must classify them as placeholders/rules, not secrets.
- Tag name recommendation: `v0.2.0-rc.1`. Existing RC tag scan is empty at planning time.

## Required Tests

For metadata-only release task:

```powershell
git status --short
git diff --check
git log --oneline -5
git rev-parse HEAD
git tag --list "v0.2.0-rc.*"
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
```

When runtime prerequisites are available:

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml config
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
curl.exe -sS http://localhost:8080/api/health
curl.exe -sS http://localhost:3000/api/health
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-active-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "What integration style does Sangui RAG Gateway provide?" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

If any implementation file changes unexpectedly, reroute through the relevant backend/frontend validation:

```powershell
cd backend
mvn -q -DskipTests compile
mvn test
cd ..\frontend
cmd /c npm run typecheck
cmd /c npm run build
```
