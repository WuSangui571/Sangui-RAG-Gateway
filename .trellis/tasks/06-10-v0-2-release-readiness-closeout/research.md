# Focused Research - V0.2 Release Readiness Closeout

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product source of truth for V0.2 beta scope, implemented full-stack deployment baseline, demo acceptance automation rule, safe/forbidden evidence fields, and release-relevant limitations.
- `.trellis/spec/security/rag-security.md`: defines forbidden response/log/evidence fields, safe request-log fields, and hit-chunk evidence boundaries.
- `.trellis/spec/backend/logging-guidelines.md`: defines safe structured logging and request-log fields; forbids keys, provider bodies, full prompts, messages, chunk content, embeddings, and stack traces.
- `.trellis/spec/backend/error-handling.md`: defines app API key revoke behavior and public `/v1/*` `401 invalid_api_key` contract for disabled/revoked/expired keys.
- `.trellis/spec/backend/quality-guidelines.md`: defines release-quality expectations and confirms full tests are required when backend behavior changes; for this task, backend tests can be skipped only if no backend implementation files change.
- `.trellis/spec/gateway/resilience.md`: defines upstream/streaming/request-log failure boundaries and safe evidence expectations.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: applies because release evidence crosses API keys, request logs, Docker/runtime prerequisites, and secret boundaries.
- `README.md`: canonical user-facing V0.2 beta status, demo setup, smoke script contract, safe evidence fields, forbidden output fields, key revocation checklist, and deployment prerequisites.
- `docs/runtime-evidence-checklist.md`: durable metadata-only evidence recording template and static validation checklist.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`: latest formal V0.2 acceptance evidence pack and current key-cleanup status.
- `.trellis/workspace/sangui/journal-2.md`: recent session summaries and known unresolved release-risk note.

## Code / Document Patterns Found

- Task-local deliverables are acceptable for release/evidence work:
  - Previous evidence task kept `evidence-pack.md` under `.trellis/tasks/...` and archived it after user validation.
  - This task should follow that pattern with `release-readiness.md`.
- README is currently the durable evidence contract:
  - It contains canonical Safe Evidence Fields and Forbidden Output Fields.
  - It also contains the key revocation and after-demo cleanup runbook.
- `docs/runtime-evidence-checklist.md` is the durable reusable template:
  - It explicitly allows only safe metadata and requires key cleanup rows.
  - It says all temporary evidence-session keys must be revoked.
- The final evidence pack is metadata-only and records the formal smoke as passing:
  - Backend health, frontend proxy health, app readiness, non-streaming chat, streaming chat, request-log validation, hit-chunk evidence, revoked-key 401, and static safety checks all passed.
  - Fresh demo key cleanup remains `PENDING MANUAL CONFIRMATION`.
- Revoked-key behavior is contractually clear:
  - Admin revoke returns `code=OK` and omits `key`/`key_hash`.
  - Public `/v1/*` calls with revoked keys return HTTP `401` with OpenAI-compatible `invalid_api_key`.

## Files Likely To Modify

- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/release-readiness.md`: primary deliverable with release decision, key cleanup status, evidence consistency, static checks, completed capabilities, limitations, and prerequisites.
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/static-checks.md`: optional supporting record if the release note becomes too dense.
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/task.json`: may be updated automatically by `task.py start`.
- `.trellis/tasks/06-10-v0-2-release-readiness-closeout/implement.jsonl`, `check.jsonl`, `debug.jsonl`: task context files.

## Files To Review, Not Modify Unless Explicitly Approved

- `README.md`
- `docs/runtime-evidence-checklist.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/tasks/archive/2026-06/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`
- `.trellis/workspace/sangui/journal-2.md`
- `scripts/demo-smoke.ps1`

## Risk / Boundary Notes

- Main blocker candidate: fresh demo key final status is not proven from committed metadata. A release note must not claim unconditional readiness unless the operator confirms revocation or intentionally accepts retention.
- Release evidence must stay metadata-only. Static scans can find placeholder/rule-text hits; the executor must review hits and distinguish them from concrete secrets.
- Do not rewrite historical evidence to make unverified runtime facts look verified.
- Do not run runtime revoke commands unless the operator provides the key ID/admin context and explicitly asks the executor to perform that action.
- Do not expand scope into code fixes, CI changes, Docker changes, README rewrites, or test automation changes. If a real inconsistency is found, record it as a release blocker or ask for explicit scope expansion.
- If only task-local Trellis files change, backend Maven tests and frontend checks are not required. Record them as skipped with reason.

## Required Tests / Checks

Must run during execution:

```powershell
git status --short
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
rg -n "docs/runtime-evidence-checklist.md|runtime-evidence-checklist.md|evidence-pack.md|demo-smoke.ps1|V0.2|V0.2 beta|release candidate" README.md docs .trellis/spec .trellis/tasks
```

Recommended review-only checks:

```powershell
rg -n "PENDING MANUAL CONFIRMATION|Key Cleanup|After Demo - Revocation Checklist|Safe Evidence Fields|Forbidden Output Fields" README.md docs .trellis/spec .trellis/tasks .trellis/workspace/sangui/journal-2.md
git show --stat --oneline --name-only HEAD
```

Conditional:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

Run PSParser only if `scripts/demo-smoke.ps1` is edited. It should not be edited in this task.

Do not run full backend/frontend tests unless non-task-local implementation files are changed.
