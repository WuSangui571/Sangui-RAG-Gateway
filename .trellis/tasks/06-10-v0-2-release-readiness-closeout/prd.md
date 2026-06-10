# V0.2 Release Readiness Closeout

## Goal

Produce a release-readiness closeout for V0.2 after the core implementation and demo acceptance evidence pack are complete.

This task is a documentation, evidence, and release-boundary review. It must confirm whether the current `main` branch can be treated as a V0.2 release candidate, with any blocker stated explicitly. It must not introduce product features or modify backend/frontend business implementation.

## Task Classification

Complex Task.

Reason: the work does not require broad coding, but it crosses release, README/spec consistency, runtime evidence, API-key cleanup, and security evidence boundaries. The main risk is not implementation correctness; it is whether the release record is complete, reproducible, and secret-safe.

## Current Project Status From Workspace Journal

- `main` is clean at session start.
- No active Trellis task exists before this task.
- Recent work completed the V0.2 demo smoke readiness, runtime evidence checklist, and formal demo acceptance evidence pack.
- The committed evidence pack reports a successful metadata-only smoke run covering backend health, frontend proxy health, app readiness, non-streaming chat, streaming SSE, request-log/detail/hit-chunk validation, revoked-key auth, and static safety checks.
- The known unresolved release-risk item is the fresh demo key cleanup: the evidence pack records it as `PENDING MANUAL CONFIRMATION`, while the separate revoked-key fixture was already verified as revoked via `401 invalid_api_key`.

## Scope

### In Scope

- Confirm the fresh demo key final state:
  - either record that it has been revoked and verified;
  - or record the explicit reason it is intentionally retained for follow-up manual testing, including risk and owner.
- Review consistency between:
  - `README.md`
  - `docs/runtime-evidence-checklist.md`
  - `.trellis/spec/sangui-rag-gateway.md`
  - `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`
  - `.trellis/workspace/sangui/journal-2.md` recent V0.2 entries
- Run lightweight release-readiness static checks:
  - `git status --short`
  - sensitive field / generated key scan
  - documentation link/reference existence check for release-evidence paths
  - whitespace/diff sanity check where applicable
- Summarize V0.2 release candidate state:
  - completed capabilities
  - known limitations
  - manual deployment/demo prerequisites
  - blocker/non-blocker release risks
- Produce a task-local release readiness note.

### Out of Scope / Forbidden

- Do not modify backend implementation files.
- Do not modify frontend implementation files.
- Do not change API contracts, DTO/VO fields, frontend types, migrations, Docker Compose, CI, or smoke script behavior.
- Do not add new feature behavior.
- Do not silently edit release evidence to imply runtime facts that were not verified.
- Do not commit real API keys, upstream provider keys, `.env` values, raw assistant answers, raw SSE payloads, prompts/messages, chunk content, chunk summaries, provider bodies, stack traces, embeddings, upload artifacts, or terminal logs.
- Do not revoke a runtime key unless the operator provides the necessary key ID/admin context or explicitly instructs the executor to perform that runtime action.

## Expected Deliverables

- Task-local release readiness note:
  - `.trellis/tasks/06-10-v0-2-release-readiness-closeout/release-readiness.md`
- Optional task-local static-check notes if useful:
  - `.trellis/tasks/06-10-v0-2-release-readiness-closeout/static-checks.md`
- No business-code changes.

## Release Readiness Note Requirements

The note must include these sections:

1. `Decision`
   - State one of:
     - `READY FOR V0.2 RELEASE CANDIDATE`
     - `READY WITH OPERATOR-ACTION REQUIRED`
     - `NOT READY`
   - If the fresh demo key remains unconfirmed, the decision must not overstate release readiness. Prefer `READY WITH OPERATOR-ACTION REQUIRED`.

2. `Fresh Demo Key Cleanup`
   - Record final state:
     - `REVOKED`, with verification method and date/time; or
     - `INTENTIONALLY RETAINED`, with reason, owner, expected cleanup point, and risk; or
     - `UNCONFIRMED`, with exact missing evidence.
   - Do not record plaintext key values.

3. `Evidence Consistency`
   - Compare README, durable runtime checklist, project spec, and final evidence pack.
   - Record any mismatch as blocker/non-blocker.
   - Confirm whether the evidence pack references the same smoke contract and safe/forbidden field rules.

4. `Static Checks`
   - Record command, result, and important findings.
   - Scan hits that are rule text/placeholders must be distinguished from real secrets.

5. `Completed V0.2 Capabilities`
   - Summarize implemented capabilities only from existing README/spec/evidence.
   - Do not imply unsupported OpenAI APIs or non-MVP roadmap items are complete.

6. `Known Limitations`
   - Include at minimum:
     - temporary admin identity uses `X-Admin-User-Id`
     - only compatible subset of OpenAI Chat Completions is supported
     - PDF/DOCX parsing is roadmap unless implementation evidence says otherwise
     - rate limits/quotas are roadmap
     - formal smoke depends on configured local runtime providers and ready KB

7. `Manual Deployment / Demo Prerequisites`
   - Environment/runtime prerequisites needed to reproduce the evidence:
     - Docker Compose full stack or equivalent local backend/frontend
     - PostgreSQL/pgvector and Redis
     - `RAG_GATEWAY_SECRET_KEY`
     - Sanguicode chat provider and DashScope embedding provider credentials configured through Admin model configs
     - ready app, KB, active app API key, and revoked-key fixture if validating auth cleanup

8. `Release Boundary`
   - Confirm no backend/frontend/API/DB/infra behavior changed in this task unless explicitly scoped later.
   - Confirm this task is a release closeout, not a feature task.

## API / Command / Payload Fields

No API, DTO, database schema, frontend type, or command signature changes are planned.

Commands used for validation are read-only repository checks:

```powershell
git status --short
git diff --check
rg -n "<sensitive-patterns>" README.md docs .trellis/spec .trellis/tasks scripts
rg -n "docs/runtime-evidence-checklist.md|evidence-pack.md|demo-smoke.ps1|V0.2|release" README.md .trellis/spec docs .trellis/tasks
```

Runtime revocation verification, only if operator provides safe inputs, follows existing README contract:

```http
POST /api/admin/api-keys/{id}/revoke
X-Admin-User-Id: <admin-user-id>
```

Then:

```http
POST /v1/chat/completions
Authorization: Bearer <revoked-key>
```

Expected public error:

```json
{
  "error": {
    "code": "invalid_api_key"
  }
}
```

The plaintext key must never be written into task files.

## Validation / Error Matrix

| Scenario | Expected Result | Release Decision Impact |
|---|---|---|
| Working tree clean before release note | `git status --short` shows no unrelated changes before task-local docs are added | Supports RC readiness |
| Fresh demo key revoked and verified | Release note records `REVOKED` with safe metadata only | Supports `READY FOR V0.2 RELEASE CANDIDATE` |
| Fresh demo key intentionally retained | Release note records owner, reason, cleanup deadline, and risk | Supports `READY WITH OPERATOR-ACTION REQUIRED` unless user accepts retained key |
| Fresh demo key still unconfirmed | Release note records `UNCONFIRMED` and exact missing evidence | Blocks unconditional RC claim |
| Secret scan finds only placeholders/rule text | Findings reviewed and marked non-secret | Pass |
| Secret scan finds concrete generated key or provider key | Stop and report blocker; do not bury in note as acceptable | `NOT READY` until remediated |
| README/spec/checklist/evidence disagree on smoke contract | Record mismatch; fix only docs if in scope and safe | Blocker if user-facing release instructions are misleading |
| Link/reference path missing | Record as documentation blocker or fix task-local note only if no repo doc edit allowed | Depends on severity |
| Business implementation file modified by this task | Reject as scope violation unless separately approved | Stop |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Fresh demo key revoked and verified; README/spec/checklist/evidence agree; static scans show no real secrets; release note states `READY FOR V0.2 RELEASE CANDIDATE`. |
| Base | Evidence and docs agree, scans pass, but fresh demo key final state is operator-dependent or intentionally retained; release note states `READY WITH OPERATOR-ACTION REQUIRED` and names the exact action. |
| Bad | Release note claims readiness while key cleanup is unconfirmed, committed files contain real secrets/raw evidence, docs contradict the evidence pack, or implementation files are changed without a separate task. |

## Required Tests / Checks

Must run:

```powershell
git status --short
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
rg -n "docs/runtime-evidence-checklist.md|runtime-evidence-checklist.md|evidence-pack.md|demo-smoke.ps1|V0.2|V0.2 beta|release candidate" README.md docs .trellis/spec .trellis/tasks
```

Conditional:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

Run PSParser only if `scripts/demo-smoke.ps1` is edited, which is not expected for this task.

Do not run full Maven or frontend build by default unless implementation files are unexpectedly changed. If no backend/frontend source changes occur, record tests as skipped with reason.

## Files Likely To Modify

- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/prd.md`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/research.md`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/release-readiness.md`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/implement.jsonl`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/check.jsonl`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/debug.jsonl`
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/task.json`

## Files To Review, Not Modify Unless Explicitly Needed

- `README.md`
- `docs/runtime-evidence-checklist.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`
- `.trellis/workspace/sangui/journal-2.md`
- `scripts/demo-smoke.ps1`

## Planning Self-Check

- [x] Acceptance criteria are explicit.
- [x] Forbidden modification scope is explicit.
- [x] Expected modified files are listed.
- [x] Required checks are listed.
- [x] Specific guidelines were read, not only spec indexes.
- [x] No API / DB / frontend types / DTO field changes are planned.
- [x] Only unresolved requirement is external/operator confirmation of fresh demo key cleanup, which must be represented honestly in the release note.
