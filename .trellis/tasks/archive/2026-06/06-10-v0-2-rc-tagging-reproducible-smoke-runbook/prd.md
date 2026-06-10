# V0.2 Release Candidate Tagging and Reproducible Smoke Runbook

## Goal

Freeze the already validated V0.2 release-candidate state into a reproducible, deliverable, and rollback-aware release point. The deliverable is a release engineering record that lets an operator start from a clean checkout, restore documented runtime prerequisites, run the smoke checks, verify revoked-key rejection, and tag the exact commit as the V0.2 RC.

This task must not introduce new backend, frontend, API, database, Docker, CI, or smoke-script behavior.

## Scope Classification

Complex Task: release engineering and evidence consolidation across docs, Trellis metadata, Docker Compose runtime commands, gateway/admin API smoke evidence, key cleanup evidence, and git tag boundary. Complexity comes from release integrity and secret safety, not code implementation.

## Current Project State Summary

- Current branch: `main`.
- Start-of-task working tree: clean.
- Latest visible commit at task creation: `ea55a1c5 chore:记录v0.2 fresh demo key清理会话`.
- Recent journal records state that V0.2 is `READY FOR V0.2 RELEASE CANDIDATE`.
- Fresh demo key cleanup blocker is closed:
  - Admin API revoke for key ID 28 (`demo-acceptance-20260610`) returned `code=OK`, `status=REVOKED`, `revoked_at=2026-06-10T11:03:19`.
  - Public gateway rejected the revoked key with HTTP 401 and `error.code=invalid_api_key`.
- No backend/frontend/API/DB/infra behavior changes were made in the previous cleanup confirmation task.

## Requirements

- Confirm `main` is clean after Trellis record-session metadata has landed.
- Generate a V0.2 RC state summary from the latest commit, including:
  - commit hash and subject;
  - evidence file paths;
  - runtime prerequisites;
  - known limitations;
  - release boundary and rollback notes.
- Review existing release/runbook commands and make the final operator path explicit:
  - Docker Compose startup/config commands;
  - backend `/api/health`;
  - frontend `/api` proxy health;
  - frontend `/v1` proxy smoke via `scripts/demo-smoke.ps1`;
  - readiness and request-log assertions when `-AppId` and `-AdminUserId` are supplied;
  - revoked-key 401 check with `-VerifyRevokedKey`.
- Record tag-before-check results:
  - `git status --short`;
  - `git log --oneline -5`;
  - key evidence file paths;
  - manual smoke result placeholders or recorded safe metadata only;
  - forbidden-field/secret scan result.
- Recommend tag name `v0.2.0-rc.1`.
- If a tag is actually created later, it must point to the reviewed clean `main` commit and must not include uncommitted or unrecorded release evidence.
- Keep committed evidence safe: no plaintext app API keys, revoked keys, upstream keys, Authorization header values, provider raw bodies, full prompts/messages, full answer text, chunk content, embedding vectors, stack traces, `.env`, uploaded files, `dist`, `target`, or `node_modules`.

## Explicit Non-Goals / Forbidden Changes

- Do not modify backend implementation files under `backend/src/**`.
- Do not modify frontend implementation files under `frontend/src/**`.
- Do not change database migrations, entities, DTO/VO contracts, OpenAI-compatible API shapes, admin API payloads, Docker Compose service contracts, CI behavior, or smoke-script logic.
- Do not add new features such as real admin auth, rate limits, PDF/DOCX parser expansion, source citation, async ingestion, provider fallback, or new gateway endpoints.
- Do not commit, tag, or publish secrets.
- Do not create a release tag until the tag target, smoke evidence, and clean tree are explicitly recorded. If operator confirmation is required, stop before tagging and document the pending action.

## Command / Payload Contracts

### Git / Release Boundary Commands

```powershell
git status --short
git log --oneline -5
git rev-parse HEAD
git tag --list "v0.2.0-rc.*"
git tag -a v0.2.0-rc.1 <commit-hash> -m "v0.2.0-rc.1"
git show --stat --oneline v0.2.0-rc.1
```

Tag creation is a release operation. If not performed in the implementation pass, the final runbook must state the exact command and the required preconditions.

### Docker Compose Commands

```powershell
Copy-Item .env.example .env
docker compose --env-file .env -f deploy/docker-compose.yml config
docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
docker compose --env-file .env -f deploy/docker-compose.yml ps
```

Runtime `.env` may contain only local/operator secrets and must remain untracked.

### Health / Proxy Checks

```powershell
curl.exe -sS http://localhost:8080/api/health
curl.exe -sS http://localhost:3000/api/health
```

Expected health payload shape:

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

### Smoke Script Command

```powershell
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

Payload/secret rule: keys are operator-held runtime inputs only. They must never be written into committed files, terminal transcripts intended for commit, screenshots, or Trellis evidence except as `<redacted>` placeholders.

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Clean checkout at target commit | Working tree clean before smoke and before tag | `git status --short` has no output |
| Compose config renders | Compose validates services and env substitutions | `docker compose ... config` exits 0 |
| Backend health | Backend returns JSON health envelope | HTTP 200, `code=OK`, `data.status=UP` |
| Frontend `/api` proxy | Frontend proxy returns backend JSON, not SPA HTML | HTTP 200, `code=OK`, JSON content |
| Smoke with active key | Non-streaming and streaming chat pass without printing secrets | Script exits 0; safe fields only |
| Readiness enabled | App readiness returns `overall_status=READY` and required checks | Script readiness assertions pass |
| Request-log enabled | Latest matching success log and hit-chunk metadata are safe | Script request-log assertions pass |
| Revoked key supplied | Revoked key is rejected | HTTP 401, `error.code=invalid_api_key` |
| Revoked-key switch without key | Run fails visibly | `auth` boundary failure |
| Forbidden field in evidence | Release must not proceed | secret/forbidden scan fails |
| Dirty tree before tag | Release tag must not be created | Stop and document dirty files |
| Existing tag name | Do not overwrite | choose next RC tag or ask operator |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Fresh checkout at the reviewed commit, `.env` created from `.env.example`, Compose starts, backend and frontend proxy health pass, app readiness is `READY`, non-streaming and streaming smoke pass, request-log/hit-chunk safe evidence passes, revoked-key check returns 401 `invalid_api_key`, forbidden scan passes, and `v0.2.0-rc.1` points exactly to the reviewed commit. |
| Base | If operator-held providers, KB, active key, or revoked key are unavailable, the runbook still records all reproducible commands and safe placeholders, marks smoke/tag as pending, and does not overstate release completion. |
| Bad | A tag is created from a dirty tree, smoke evidence includes secrets/raw answers/chunk content/provider bodies, frontend proxy returns SPA HTML for API paths, revoked-key verification is skipped while claiming pass, or new behavior changes are mixed into the RC boundary. |

## Required Tests and Assertion Points

No backend/frontend implementation change is expected. If only release docs/Trellis metadata are changed, run:

```powershell
git status --short
git diff --check
git log --oneline -5
git rev-parse HEAD
git tag --list "v0.2.0-rc.*"
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
```

When runtime prerequisites are available, additionally run:

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

If any implementation file is unexpectedly changed, the implementer must stop and reroute through the normal backend/frontend test matrix before release work continues.

## Expected Files Likely To Modify

- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/prd.md`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/research.md`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/release-candidate-runbook.md`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/implement.jsonl`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/check.jsonl`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/debug.jsonl`
- `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/task.json`

Optional only if maintainers want durable top-level release documentation:

- `docs/v0.2-rc-runbook.md` or an equivalent release doc.

Do not modify implementation files unless the user explicitly changes scope.

## Open Questions

- None blocking. Default assumption: create a reproducible release/runbook record first; create `v0.2.0-rc.1` only after clean-tree and smoke evidence are recorded or after explicit operator approval if runtime secrets are not available to the implementer.
