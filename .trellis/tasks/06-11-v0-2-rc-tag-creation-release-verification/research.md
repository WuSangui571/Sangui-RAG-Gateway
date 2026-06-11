# Focused Research: V0.2 RC Tag Creation and Release Verification

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, V0.2 deployment/release scope, smoke evidence safety rules, Docker/health/smoke command contracts, known limitations.
- `.trellis/spec/security/rag-security.md`: safe request-log/evidence fields and forbidden fields; applies to release evidence and journal recording.
- `.trellis/spec/backend/logging-guidelines.md`: secrets, prompts, documents, provider bodies, and Authorization headers must not appear in logs or release records.
- `.trellis/spec/backend/quality-guidelines.md`: defines when backend tests are required and confirms that metadata-only release work does not need backend test runs unless implementation files change.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: relevant because release verification references Docker/env, gateway/admin smoke, request logs, and secret boundaries even though this task must not alter those contracts.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-rc-tagging-reproducible-smoke-runbook/release-candidate-runbook.md`: direct predecessor runbook; records original tag target `ea55a1c5`, tag name `v0.2.0-rc.1`, preconditions, smoke command, rollback notes, and tag-deferred state.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-release-readiness-closeout/release-readiness.md`: release decision is `READY FOR V0.2 RELEASE CANDIDATE`; no blockers remain.
- `.trellis/tasks/archive/2026-06/06-10-v0-2-fresh-demo-key-cleanup-confirmation/fresh-demo-key-cleanup-confirmation.md`: fresh demo key ID 28 revoked and public gateway 401 `invalid_api_key` verified without committing plaintext keys.
- `.trellis/workspace/sangui/journal-2.md`: latest recorded sessions show fresh key cleanup, RC runbook/smoke/tag runbook, and task archival are completed.

## Code / Repo Patterns Found

- Existing release tasks keep release evidence under `.trellis/tasks/archive/2026-06/...` and record only safe metadata.
- Previous RC runbook deferred tag creation rather than performing it from an uncommitted Trellis task state.
- Previous release evidence distinguishes safe metadata (`request_id`, model, provider, latency, hit chunk IDs, key ID/prefix) from forbidden data (plaintext keys, Authorization values, provider raw bodies, prompts/messages, chunk content, raw answers).
- `scripts/demo-smoke.ps1` is treated as an already-validated smoke script and must not be edited in this task.
- `docs/runtime-evidence-checklist.md` is the durable evidence recording template, while task-local records capture specific release decisions.

## Current Git / Tag State At Planning Time

- Branch: `main`.
- `git status --short` at `$start`: clean.
- After task creation, the only expected dirty files are new Trellis task metadata in `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/`.
- Recent commits:
  - `1efaa8a9 chore:归档v0.2 rc runbook会话`
  - `5aec32fe docs:补充v0.2 rc复现smoke与tag runbook`
  - `ea55a1c5 chore:记录v0.2 fresh demo key清理会话`
  - `3be0282e docs:确认v0.2 fresh demo key清理`
  - `8a10655c docs:完善v0.2发布就绪收尾记录`
- `git tag --list "v0.2.0-rc.*"`: no output at planning time, so no RC tag conflict was present.
- `git show --stat --oneline --decorate 5aec32fe`: commit adds the archived RC reproducible smoke/tag runbook task files, 861 insertions.
- `git show --stat --oneline --decorate ea55a1c5`: commit records the fresh demo key cleanup session metadata, 74 insertions.

## Files Likely To Modify

- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/prd.md`: requirements and command contract.
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/research.md`: focused research handoff.
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/implement.jsonl`: implementation context.
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/check.jsonl`: check context.
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/debug.jsonl`: default Trellis context.
- `.trellis/tasks/06-11-v0-2-rc-tag-creation-release-verification/task.json`: task activation/status metadata.
- `.trellis/workspace/sangui/journal-2.md`: expected only after release tag evidence is recorded.

No expected changes:

- `backend/src/**`
- `frontend/src/**`
- `backend/src/main/resources/db/migration/**`
- `deploy/docker-compose.yml`
- `.github/workflows/**`
- `scripts/demo-smoke.ps1`
- `frontend/nginx.conf`
- API DTO/VO/type files

## Risk / Boundary Notes

- Main release risk is tag target ambiguity, not code complexity.
- Previous runbook target was `ea55a1c5`; current task should explicitly decide whether `5aec32fe` is the better target because it includes the committed runbook.
- Tag creation must not use a dirty working tree as implicit context. The selected target hash must be passed explicitly to `git tag -a`.
- Creating a local tag is reversible before push; deleting a pushed tag is a release coordination action and must not be done silently.
- Push may fail due credentials, network, or remote tag conflict. If push fails, record local verification separately and do not claim remote release completion.
- Forbidden-field scans will have expected hits from rule text/placeholders. The executor must classify hits, not blindly fail on all matches.
- Runtime smoke is not required in this task unless operator-held secrets/runtime are available; existing evidence already shows full V0.2 smoke pass and key cleanup closure.

## Required Tests

Metadata/tagging preflight:

```powershell
git status --short
git log --oneline -8
git rev-parse 5aec32fe
git rev-parse ea55a1c5
git tag --list "v0.2.0-rc.*"
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{12,}|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" README.md docs .trellis/spec .trellis/tasks scripts
```

Tag verification after creation:

```powershell
git show --stat --oneline v0.2.0-rc.1
git rev-list -n 1 v0.2.0-rc.1
git status --short
```

Push:

```powershell
git push origin v0.2.0-rc.1
```

Unexpected implementation changes require:

```powershell
cd backend
mvn -q -DskipTests compile
mvn test
cd ..\frontend
cmd /c npm run typecheck
cmd /c npm run build
```
