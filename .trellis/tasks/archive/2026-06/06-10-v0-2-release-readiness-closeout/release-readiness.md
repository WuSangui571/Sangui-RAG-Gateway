# V0.2 Release Readiness Closeout

Date: 2026-06-10 18:29 UTC+8
Task: V0.2 Release Readiness Closeout
Branch: `main`

---

## 1. Decision

**READY FOR V0.2 RELEASE CANDIDATE**

The V0.2 implementation, demo acceptance evidence, static safety checks, and fresh demo key cleanup confirmation all pass. The fresh demo API key (`demo-acceptance-20260610`, ID 28) has been revoked and the 401 `invalid_api_key` rejection has been verified via runtime Admin API and public gateway calls. No blockers remain.

Confirmation evidence: `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`.

---

## 2. Fresh Demo Key Cleanup

### Final State

**REVOKED** -confirmed 2026-06-10 19:03 UTC+8.

### Evidence

Fresh demo key cleanup confirmation completed:

| Key | Status | Verification |
|---|---|---|
| Fresh demo key (`demo-acceptance-20260610`, ID 28) | `REVOKED` | Revoked via `POST /api/admin/api-keys/28/revoke`. Verified - HTTP 401 with `error.code=invalid_api_key` via public gateway call. |
| Revoked demo key | `REVOKED` | Verified - HTTP 401 with `error.code=invalid_api_key` via `scripts/demo-smoke.ps1 -VerifyRevokedKey`. |

Full confirmation evidence recorded in `.trellis/tasks/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`. No plaintext key, Authorization header, or raw runtime responses committed.

### Operator Action Completed

1. Fresh demo key identified: name `demo-acceptance-20260610`, ID 28, app 5.
2. Key revoked via Admin API: `code=OK`, `status=REVOKED`, `revoked_at=2026-06-10T11:03:19`.
3. Revoked key rejected: HTTP 401 with `error.code=invalid_api_key`.
4. Safe metadata recorded; no plaintext key committed.

---

## 3. Evidence Consistency

### Sources Compared

| Source | Role |
|---|---|
| `README.md` | Canonical user-facing V0.2 beta status, smoke contract, safe/forbidden field lists, key cleanup runbook |
| `docs/runtime-evidence-checklist.md` | Durable metadata-only evidence recording template |
| `.trellis/spec/sangui-rag-gateway.md` | Project boundary, V0.2 scope, demo automation contract |
| `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md` | Latest formal V0.2 metadata-only acceptance evidence |
| `.trellis/workspace/sangui/journal-2.md` | Session history confirming completion of prerequisite tasks |

### Findings

| Check | Result | Notes |
|---|---|---|
| V0.2 status label consistency | PASS | README (`V0.2 beta`), spec (`V0.2 Usable Experience Version`), evidence pack (`V0.2 Demo Acceptance Evidence Pack`) all agree on V0.2 scope. |
| Smoke contract consistency | PASS | All sources prescribe the same 7-step smoke flow: health, proxy, readiness, non-streaming, streaming, request-log/hit-chunks, revoked-key 401. |
| Safe evidence fields | PASS | README Safe Evidence Fields list, spec `rag-security.md` safe fields, checklist template allowed fields, and evidence pack recorded fields are consistent. |
| Forbidden output fields | PASS | README Forbidden Output Fields list, spec forbidden fields contract, checklist recording rules, and evidence pack recordings agree. No real forbidden values were committed. |
| Key cleanup checklist | PASS | README "After Demo -Revocation Checklist" and checklist template "Key Cleanup" section prescribe the same revocation/verification flow. |
| Evidence pack references | PASS | Evidence pack references `scripts/demo-smoke.ps1` with consistent parameters; records metadata-only results matching the checklist template format. |
| Durable checklist path | PASS | README links to `docs/runtime-evidence-checklist.md` (durable path). Spec also references the same durable template. No unstable task-only paths remain in primary documentation. |

### Conclusion

No mismatches found. README, spec, checklist, and evidence pack agree on smoke contract, safe/forbidden evidence rules, key cleanup requirements, and V0.2 scope boundaries.

---

## 4. Static Checks

### Commands and Results

| # | Check | Command | Result |
|---|---|---|---|
| 1 | Working tree status | `git status --short` | PASS - changed files are limited to Trellis task/evidence/archive/session files; no backend/frontend/API/DB/infra files changed. |
| 2 | Whitespace check | `git diff --check` | PASS - no whitespace errors. |
| 3 | Secret / forbidden-field scan | `rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts` | REVIEW PASS - all hits are rule text, placeholders, spec contracts, historical task rules, or script scanner arrays. No real generated `sk-sangui-*` keys, plaintext API keys, key hashes, encrypted keys, upstream provider keys, provider bodies, stack traces, chunk content, raw answers, or Authorization header values with concrete keys found. |
| 4 | Doc link / reference scan | `rg -n "docs/runtime-evidence-checklist.md|runtime-evidence-checklist.md|evidence-pack.md|demo-smoke.ps1|V0.2|V0.2 beta|release candidate" README.md docs .trellis/spec .trellis/tasks` | PASS - README, spec, checklist, task notes, and archived evidence pack reference consistent paths and V0.2 release terminology. |
### Secret Scan Hit Analysis

The core release surface still has the same 19 reviewed hits across `README.md`, `docs/runtime-evidence-checklist.md`, and `scripts/demo-smoke.ps1`:

| Category | Count | Examples |
|---|---|---|
| **Placeholder / template** | 1 | `README.md:116` -`sk-sangui-<your-key>` in curl example |
| **Rule / documentation text** | 14 | README lines 292, 301, 560-576, 594, 624, 701 -forbidden-field lists, safe/forbidden rules, expected behavior descriptions |
| **Script scanner array** | 4 | `scripts/demo-smoke.ps1:65-68` - `$forbidden` array used by the script's own `Test-ForbiddenFields` scanner |

No hit represents a real secret, generated key, or concrete provider credential.

The wider PRD-required scan over `README.md`, `docs/`, `.trellis/spec/`, `.trellis/tasks/`, and `scripts/` also produced only expected rule text, examples, placeholders, spec field names, historical task acceptance criteria, and scanner arrays. The archived evidence pack itself uses `<redacted>` for runtime keys.

### Not Run

| Check | Reason |
|---|---|
| PSParser syntax check on `scripts/demo-smoke.ps1` | Script was not edited in this task. Last PSParser check passed (evidence pack session). |
| Backend Maven tests | No backend implementation files changed. |
| Frontend typecheck / build | No frontend implementation files changed. |
| Full smoke via `demo-smoke.ps1` | Requires runtime environment with configured providers, KB, and app -not available in committed metadata. Operator must re-run smoke locally. |

---

## 5. Completed V0.2 Capabilities

Summarized from README and evidence pack (all confirmed by metadata-only demo acceptance evidence):

| Capability | Evidence |
|---|---|
| Spring Boot 3.4 backend with health check | Backend health PASS, HTTP 200, `code=OK`, `data.status=UP` |
| PostgreSQL + pgvector + Redis Docker Compose | Full-stack Compose deployment verified |
| Flyway database migrations | App, API key, model config, KB, document, chunk, embedding, request log tables created |
| App API key authentication (Bearer `sk-sangui-*`) | Non-streaming and streaming chat authenticated successfully; revoked key rejected with 401 |
| `GET /v1/models` | OpenAI-compatible model list for authenticated apps |
| `POST /v1/chat/completions` -non-streaming | PASS, HTTP 200, valid completion |
| `POST /v1/chat/completions` -streaming (`stream=true`) | PASS, SSE data chunks received, `[DONE]` present |
| Admin console -app, API key, model config, KB, document, request log management | App readiness PASS with 6 checks present |
| Upstream API key encryption (AES-256-GCM) | Admin model config responses show `api_key_masked` only |
| Tenant isolation on admin and retrieval operations | Cross-user access rejected with 403 |
| Safe structured logging | Request-log list/detail/hit-chunks return only safe metadata; forbidden fields absent |
| Hit chunk evidence | `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index` present; no full chunk content |
| Revoked-key 401 | PASS, HTTP 401, `error.code=invalid_api_key` |
| Full-stack Docker Compose one-command deployment | `docker compose --env-file .env -f deploy/docker-compose.yml up -d --build` |
| Split-provider runtime (Sanguicode chat + DashScope embedding) | Model config presence confirmed in admin setup |
| Frontend smoke test page (`/smoke`) | Admin UI smoke page implements the same 4-step acceptance check |
| PowerShell automated smoke script | `scripts/demo-smoke.ps1` with health/proxy/readiness/chat/stream/request-log/revoked-key validation |
| GitHub Actions CI | Backend Maven + Frontend typecheck/build + Docker image build on push/PR |

---

## 6. Known Limitations

From README roadmap and project spec -these are acknowledged gaps, not unexpected missing features:

| Limitation | Impact |
|---|---|
| Temporary admin identity via `X-Admin-User-Id` header | No real admin login/registration/authentication. Admin endpoints are open to anyone who knows the header convention. |
| Only compatible subset of OpenAI Chat Completions supported | `GET /v1/models` and `POST /v1/chat/completions` are the only public endpoints. Tools, functions, vision, audio, response_format are unsupported. |
| PDF / DOCX parsing not implemented | Only `.txt`, `.md`, `.markdown` files are supported for document upload. |
| API-key level rate limiting not implemented | No per-key or per-app rate/quota enforcement. |
| Source citations not in chat responses | Answers do not include inline source references. |
| No async document processing | Large documents block the ingestion pipeline synchronously. |
| No rerank or hybrid retrieval | Retrieval is pure pgvector cosine distance. |
| No production file storage | MinIO is roadmap; local filesystem used currently. |
| Formal smoke depends on configured local runtime | Providers, KB, and app must be manually configured before smoke. |
| Request-log misses auth failures and malformed JSON | `GatewayAuthFilter` 401 and `HttpMessageNotReadableException` 400 do not reach the persistence boundary. |

---

## 7. Manual Deployment / Demo Prerequisites

To reproduce the evidence, the following must be configured:

| Prerequisite | Details |
|---|---|
| Docker Compose | `docker compose --env-file .env -f deploy/docker-compose.yml up -d --build` starts PostgreSQL/pgvector, Redis, backend, frontend. |
| `.env` file | Copy from `.env.example`; set `RAG_GATEWAY_SECRET_KEY`. |
| Sanguicode chat provider | Model config with `base_url=https://api.sanguicode.com`, `chat_model=deepseek-v4-pro`, provider API key, `status=ENABLED`. |
| DashScope embedding provider | Model config with `base_url=https://dashscope.aliyuncs.com/compatible-mode/v1`, `embedding_model=text-embedding-v4`, `embedding_dimension=1024`, provider API key, `status=ENABLED`. |
| Knowledge base | Created via admin UI with embedding model `text-embedding-v4` and dimension `1024`. At least one `.txt` or `.md` document uploaded and processed to `READY`. |
| App | Created, with default model config (Sanguicode chat) and default knowledge base bound. |
| Active app API key | Generated via admin UI/API; plaintext copied once. |
| Revoked key fixture (for auth validation) | A separate API key revoked and verified via the smoke script. |

---

## 8. Release Boundary

| Assertion | Status |
|---|---|
| No backend implementation files modified | Confirmed - git status shows no changes to `backend/src/`. |
| No frontend implementation files modified | Confirmed - git status shows no changes to `frontend/src/`. |
| No API contracts, DTO/VO fields changed | Confirmed - no code changes made. |
| No database migrations changed | Confirmed - no migration files touched. |
| No Docker Compose, CI, or smoke script behavior changed | Confirmed - `deploy/`, `.github/`, `scripts/` unchanged. |
| No new feature behavior added | Confirmed - this remains documentation/evidence-only release closeout work. |
| No real secrets committed | Confirmed - static scan found only rule text and placeholders. |
| Trellis-only changes | Confirmed - updates are limited to Trellis task/evidence/archive/session files. |

This task is a release closeout, not a feature task. It does not alter the release candidate itself; it evaluates whether the current `main` branch is fit for release.

---

## Summary

V0.2 implementation is complete and evidence-backed. All 7 smoke steps pass with metadata-only evidence. Static scans are clean. Documentation is internally consistent. Fresh demo key has been revoked and verified.

**No blockers remain.** Release readiness is unconditional -`READY FOR V0.2 RELEASE CANDIDATE`.
