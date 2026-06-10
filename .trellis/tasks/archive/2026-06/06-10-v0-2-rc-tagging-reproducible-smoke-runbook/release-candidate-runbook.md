# V0.2 Release Candidate Runbook

Date: 2026-06-10 19:40 UTC+8
Task: V0.2 Release Candidate Tagging and Reproducible Smoke Runbook
Executor: DeepSeek (implement phase)
Status: **RC RUNBOOK READY — TAG PENDING OPERATOR CONFIRMATION**

---

## 1. RC State Summary

| Assertion | Value |
|---|---|
| Release candidate commit | `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2` |
| Commit subject | `chore:记录v0.2 fresh demo key清理会话` |
| Branch | `main` |
| Working tree at runbook creation | Clean except untracked Trellis task directory; no implementation files modified |
| Previous release readiness | `READY FOR V0.2 RELEASE CANDIDATE` (confirmed 2026-06-10 18:29 UTC+8) |
| Fresh demo key blocker | Closed — key ID 28 `demo-acceptance-20260610` revoked, 401 `invalid_api_key` verified |
| Formal smoke evidence | Metadata-only full pass on 2026-06-10 17:57 UTC+8 — all 7 steps PASS, exit code 0 |
| Existing RC tags | None |
| Recommended tag name | `v0.2.0-rc.1` |

### Recent Commit History

```
ea55a1c5 chore:记录v0.2 fresh demo key清理会话
3be0282e docs:确认v0.2 fresh demo key清理
8a10655c docs:完善v0.2发布就绪收尾记录
89332c20 chore:记录v0.2 demo验收证据归档会话
1180ad88 docs:补充v0.2 demo验收证据包
```

All commits are documentation, evidence, and Trellis session records. The last five commits contain no backend, frontend, API, database, Docker, CI, or smoke-script behavior changes.

---

## 2. Tag Target

| Field | Value |
|---|---|
| Target commit (full hash) | `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2` |
| Target commit (short) | `ea55a1c5` |
| Intended tag name | `v0.2.0-rc.1` |
| Tag annotation | `v0.2.0-rc.1` |
| Tag type | Annotated (`git tag -a`) |

The tag must point exactly to `ea55a1c5`. If Trellis metadata-only files (this runbook) are committed before tagging, the operator must decide whether to bump the target commit forward or keep the tag on the last pre-runbook commit.

---

## 3. Evidence File Paths

### Formal Smoke Evidence

| File | Content |
|---|---|
| `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md` | Metadata-only V0.2 full smoke pass: 7 steps PASS, exit code 0, request_id `6a67c4a6-8a89-49eb-a678-5a37285d46e7` |
| `docs/runtime-evidence-checklist.md` | Durable evidence recording template |

### Release Readiness

| File | Content |
|---|---|
| `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md` | Final decision: `READY FOR V0.2 RELEASE CANDIDATE`, no blockers, static scans clean |

### Key Cleanup

| File | Content |
|---|---|
| `.trellis/tasks/archive/2026-06/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md` | Key ID 28 revoked, 401 `invalid_api_key` verified, no plaintext key committed |

### This Runbook

| File | Content |
|---|---|
| `.trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/release-candidate-runbook.md` | This file — RC state summary, reproducible commands, tag preflight, rollback |

---

## 4. Runtime Prerequisites

The following must be configured by the operator before running smoke, independent of this repository:

| Prerequisite | Details |
|---|---|
| Docker + Docker Compose | Docker 24+, Compose 2.x |
| Java / Maven / Node.js | Java 21+, Maven 3.9+, Node.js 20+ (for local dev only; Docker Compose builds containerized) |
| `.env` file | Copy from `.env.example`; set `RAG_GATEWAY_SECRET_KEY` to a non-default value |
| Sanguicode chat provider key | Model config with `base_url=https://api.sanguicode.com`, `chat_model=deepseek-v4-pro`, provider API key, `status=ENABLED` |
| DashScope embedding provider key | Model config with `base_url=https://dashscope.aliyuncs.com/compatible-mode/v1`, `embedding_model=text-embedding-v4`, `embedding_dimension=1024`, provider API key, `status=ENABLED` |
| Knowledge base + document | KB with embedding model `text-embedding-v4` / dimension `1024`, at least one `.txt`/`.md` document processed to `READY` status |
| App configured | App with Sanguicode chat as default model config and KB bound, app ID and admin user ID known |
| Active app API key | Fresh `sk-sangui-*` key generated for the app; plaintext held only in operator memory/terminal |
| Revoked key fixture | A separate API key revoked, for 401 verification |

**Secret safety rule**: provider API keys, app API keys, and revoked keys are operator-held runtime inputs only. They must never be written into committed files, terminal transcripts intended for commit, screenshots, or this runbook.

---

## 5. Known Limitations

From the V0.2 scope — acknowledged, not unexpected:

| Limitation | Impact |
|---|---|
| Temporary admin identity via `X-Admin-User-Id` header | No real admin login/registration/authentication |
| Only OpenAI Chat Completions subset | `GET /v1/models` and `POST /v1/chat/completions` only |
| PDF / DOCX parsing not implemented | Only `.txt`, `.md`, `.markdown` supported |
| No API-key level rate limiting | No per-key or per-app rate/quota enforcement |
| No source citations in chat responses | Answers do not include inline source references |
| No async document processing | Large documents block the ingestion pipeline synchronously |
| No rerank or hybrid retrieval | Pure pgvector cosine distance |
| No production file storage (MinIO) | Local filesystem only |
| Formal smoke requires configured local runtime | Providers, KB, and app must be manually configured |
| Request-log misses auth failures and malformed JSON | `GatewayAuthFilter` 401 and `HttpMessageNotReadableException` 400 do not reach persistence |

---

## 6. Reproducible Smoke Commands

The following is an end-to-end operator flow from clean checkout to smoke pass. All commands are for PowerShell 5.1 on Windows. Use `curl.exe` (not PowerShell `curl` alias).

### 6.1 Clean Checkout

```powershell
git clone <repo-url> Sangui-RAG-Gateway-rc
cd Sangui-RAG-Gateway-rc
git checkout ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2
```

Verify clean state:

```powershell
git status --short
# Expected: no output (clean working tree)
```

### 6.2 Prepare Environment

```powershell
Copy-Item .env.example .env
# Edit .env: set RAG_GATEWAY_SECRET_KEY to a strong non-default value
```

### 6.3 Docker Compose Startup

```powershell
docker compose --env-file .env -f deploy/docker-compose.yml config
# Expected: exit 0, services rendered correctly

docker compose --env-file .env -f deploy/docker-compose.yml up -d --build
# Wait for all services healthy (backend has 40s start_period)

docker compose --env-file .env -f deploy/docker-compose.yml ps
# Expected: postgres (healthy), redis (healthy), backend (healthy), frontend (running)
```

### 6.4 Health Checks

```powershell
curl.exe -sS http://localhost:8080/api/health
# Expected: {"code":"OK","message":"success","data":{"status":"UP","service":"sangui-rag-gateway"}}

curl.exe -sS http://localhost:3000/api/health
# Expected: Same JSON envelope, NOT SPA HTML. Response must start with '{'.
```

### 6.5 Admin Configuration

The operator must configure the system through the Admin API or admin console UI:

1. Create Sanguicode chat model config (chat_provider)
2. Create DashScope embedding model config (embedding_provider)
3. Create knowledge base, upload document, wait for READY
4. Create app, bind chat model config and knowledge base
5. Create active API key (copy plaintext once)
6. Create and revoke a separate key for 401 verification

Refer to `README.md` sections "Manual Admin Configuration Smoke Flow" and "Admin API Setup Runbook" for complete payloads.

### 6.6 Smoke Test

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

Expected: all 7 steps PASS, exit code 0.

**Failure boundaries** — stop and investigate at the matching boundary:
- `health` → Backend `/api/health` down or unexpected
- `proxy` → Frontend proxy returns HTML, non-JSON, or wrong status
- `auth` → `401 invalid_api_key` (key invalid/disabled/revoked)
- `upstream` → `upstream_error`, `upstream_timeout`, or SSE truncation
- `embedding` → `embedding_failed`
- `retrieval` → `knowledge_base_not_ready`, `model_config_not_ready`, or no hits

### 6.7 Tag (If Smoke Passes)

**PRECONDITIONS — all must be met before tagging:**

- [ ] `git status --short` has no output (clean working tree)
- [ ] All 7 smoke steps PASS from the target commit
- [ ] Secret/forbidden-field scan passes (all hits are rule text/placeholders only)
- [ ] Operator has reviewed evidence and confirmed release intent
- [ ] No existing tag `v0.2.0-rc.1` (if exists, choose `v0.2.0-rc.2`)

**Tag command:**

```powershell
git tag -a v0.2.0-rc.1 ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2 -m "v0.2.0-rc.1"
```

Verify:

```powershell
git show --stat --oneline v0.2.0-rc.1
```

**DO NOT CREATE THE TAG if any precondition is unmet.** Stop and document the gap.

---

## 7. Tag-Before-Check Records

Recorded at runbook creation time (2026-06-10 19:40 UTC+8).

### 7.1 Working Tree Status

```
$ git status --short
?? .trellis/tasks/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/
```

Result: **PASS** — Only the untracked Trellis task directory exists. No backend, frontend, API, database, Docker, CI, or smoke-script files are modified.

### 7.2 Whitespace Check

```
$ git diff --check
(no output)
```

Result: **PASS** — No whitespace errors.

### 7.3 HEAD Commit

```
$ git rev-parse HEAD
ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2
```

### 7.4 Recent Commit Log

```
$ git log --oneline -5
ea55a1c5 chore:记录v0.2 fresh demo key清理会话
3be0282e docs:确认v0.2 fresh demo key清理
8a10655c docs:完善v0.2发布就绪收尾记录
89332c20 chore:记录v0.2 demo验收证据归档会话
1180ad88 docs:补充v0.2 demo验收证据包
```

### 7.5 Existing RC Tags

```
$ git tag --list "v0.2.0-rc.*"
(no output)
```

Result: **PASS** — No existing RC tags. `v0.2.0-rc.1` is available.

### 7.6 Key Evidence Files

| File | Exists | Content Summary |
|---|---|---|
| `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md` | Yes | 7-step smoke all PASS, request_id `6a67c4a6-...`, hit chunk [5], metadata-only |
| `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md` | Yes | `READY FOR V0.2 RELEASE CANDIDATE`, no blockers |
| `.trellis/tasks/archive/2026-06/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md` | Yes | Key 28 revoked, 401 verified, no plaintext committed |
| `docs/runtime-evidence-checklist.md` | Yes | Durable template |

### 7.7 Secret / Forbidden-Field Scan

Core release surface (`README.md`, `docs/runtime-evidence-checklist.md`, `scripts/demo-smoke.ps1`) scanned for:

```
sk-sangui-[A-Za-z0-9_-]{12,}
Authorization: Bearer sk-sangui-
api_key_encrypted
key_hash
upstream_api_key
provider_response_body
stack_trace
augmented_prompt
full_messages
chunk_content
raw SSE
raw answer
```

Result: **REVIEW PASS** — All hits are one of:

| Category | Count | Examples |
|---|---|---|
| Placeholder / template | Multiple | `README.md` `sk-sangui-<your-key>`, `sk-sangui-<fresh-demo-key>` |
| Rule / documentation text | Multiple | Forbidden field lists, safe evidence rules, expected behavior descriptions |
| Script scanner array | 4 lines | `scripts/demo-smoke.ps1:65-68` `$forbidden` array used by `Test-ForbiddenFields` |

**No real generated `sk-sangui-*` keys, plaintext API keys, key hashes, encrypted keys, upstream provider keys, provider bodies, stack traces, chunk content, raw answers, or Authorization header values with concrete keys found.**

### 7.8 Manual Smoke Result

| Field | Value |
|---|---|
| Status | **PENDING** — Runtime prerequisites (providers, KB, app, keys) not available in committed metadata |
| Last known full pass | 2026-06-10 17:57 UTC+8 (evidence-pack.md) |
| Operator action required | Re-run smoke from clean checkout per Section 6 above |

---

## 8. Tag Command (Deferred)

The tag command is **not executed** in this implementation pass. It is documented for the operator:

```powershell
git tag -a v0.2.0-rc.1 ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2 -m "v0.2.0-rc.1"
```

**Preconditions checklist for operator:**

- [ ] Clean checkout at `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2`
- [ ] `git status --short` returns no output
- [ ] Secret/forbidden-field scan reviewed and PASS
- [ ] Docker Compose starts; health checks pass
- [ ] `demo-smoke.ps1` completes with all 7 steps PASS, exit code 0
- [ ] No existing tag named `v0.2.0-rc.1`
- [ ] Operator explicitly confirms release intent

If tag `v0.2.0-rc.1` already exists, use the next sequential name:

```powershell
git tag -a v0.2.0-rc.2 ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2 -m "v0.2.0-rc.2"
```

Verify the tag after creation:

```powershell
git show --stat --oneline v0.2.0-rc.1
```

---

## 9. Rollback Notes

### If tag was created and needs removal

```powershell
# Delete local tag only (safe, does not affect commits)
git tag -d v0.2.0-rc.1

# If tag was pushed to remote (requires explicit operator decision)
git push --delete origin v0.2.0-rc.1
```

### If smoke fails at the clean checkout

1. Record the failure boundary (health, proxy, auth, upstream, embedding, retrieval) and error details.
2. Do not patch backend/frontend behavior in the release task — file a separate bug.
3. If the failure is environmental (missing `.env`, provider key expired, Docker not running), fix the environment and re-run.
4. If the failure is a code regression, the RC cannot proceed — investigate with a new task.

### If the tag target needs to change

If Trellis metadata-only files are committed before tagging and the operator wants the tag to include them, bump the target:

```powershell
git rev-parse HEAD
# Use the new HEAD hash as tag target

git tag -a v0.2.0-rc.1 <new-commit-hash> -m "v0.2.0-rc.1"
```

The original target `ea55a1c5` remains a valid fallback point.

---

## 10. Static Validation Results

Performed at runbook creation time.

| # | Check | Command | Result |
|---|---|---|---|
| 1 | Working tree status | `git status --short` | PASS — only untracked Trellis task dir |
| 2 | Whitespace check | `git diff --check` | PASS — no whitespace errors |
| 3 | HEAD verification | `git rev-parse HEAD` | `ea55a1c5ea7242e4d8a1cc38679bb5e2d4b48cf2` |
| 4 | Recent commits | `git log --oneline -5` | PASS — 5 docs/chore commits, no code changes |
| 5 | Existing RC tags | `git tag --list "v0.2.0-rc.*"` | PASS — none exist |
| 6 | Secret / forbidden-field scan | PowerShell `Select-String` on `README.md`, `docs/runtime-evidence-checklist.md`, `scripts/demo-smoke.ps1` | REVIEW PASS — all hits are placeholders, rules, or scanner arrays; no real secrets |
| 7 | Backend tests | `mvn test` | SKIP — no backend implementation files changed |
| 8 | Frontend typecheck | `npm run typecheck` | SKIP — no frontend implementation files changed |
| 9 | Frontend build | `npm run build` | SKIP — no frontend implementation files changed |
| 10 | Runtime smoke | `demo-smoke.ps1` | SKIP — runtime prerequisites not available in metadata context; operator must re-run |

### Not Run (with reasons)

| Check | Reason |
|---|---|
| Backend Maven tests | No backend implementation files changed |
| Frontend typecheck / build | No frontend implementation files changed |
| PSParser syntax check on `demo-smoke.ps1` | Script was not edited; last PSParser check passed (evidence pack session) |
| Runtime smoke via `demo-smoke.ps1` | Requires operator-held secrets and configured local runtime |
| Docker Compose validation | Requires `.env` with operator secrets; `.env.example` uses safe defaults |
| Docker image builds | Requires full Docker environment; CI already validates this |

---

## 11. Release Boundary Assertions

| Assertion | Status |
|---|---|
| No backend implementation files modified | Confirmed — `backend/src/` unchanged |
| No frontend implementation files modified | Confirmed — `frontend/src/` unchanged |
| No API contracts, DTO/VO fields changed | Confirmed — no code changes |
| No database migrations changed | Confirmed — no migration files touched |
| No Docker Compose, CI, or smoke-script behavior changed | Confirmed — `deploy/`, `.github/`, `scripts/` unchanged |
| No new feature behavior added | Confirmed — metadata/release-docs only |
| No real secrets committed | Confirmed — static scan found only rule text and placeholders |
| RC runbook scope is metadata-only | Confirmed — this is a release engineering record, not a code change |
