# GitHub CI Remote Evidence Closeout Review

## Classification

Simple Task.

Reasoning: the goal is clear and low-complexity, and the expected work is a read-only GitHub remote/PR state review plus a lightweight evidence addendum. It does not require backend, frontend, API, DTO, database, Docker, RAG, provider, auth, or product behavior changes. Trellis planning is still required because the work closes a CI/evidence boundary and must preserve the project's safe-evidence rules.

## Current Project State

- Current branch: `main`.
- No active Trellis task existed before this task was created.
- The working tree already contains pre-existing Trellis archive/journal changes from the previous visual-smoke cleanup session. Do not revert or broaden those changes.
- Workspace journal Session 37 records completion of the frontend visual-smoke failure-artifact acceptance path, restoration of the `main` visual-smoke baseline, and successful local validation.
- Workspace journal Session 38 records completion of visual-smoke temporary branch cleanup through commit `93dc435`, with a documented Codex-side boundary: this shell could not independently refresh GitHub remote branch or PR state over SSH/HTTPS.
- Archived evidence at `.trellis/tasks/archive/2026-06/06-06-visual-smoke-temp-branch-pr-cleanup/acceptance-evidence.md` currently records local branch cleanup, a local merge-commit inference for PR #2, and the unresolved remote/PR refresh boundary.

## Goal

Close the final GitHub environment-network evidence gap for the visual-smoke CI acceptance chain by independently confirming remote GitHub branch and PR metadata in an environment with GitHub access, then recording only safe metadata.

## Requirements

- Read the existing archived evidence file:
  - `.trellis/tasks/archive/2026-06/06-06-visual-smoke-temp-branch-pr-cleanup/acceptance-evidence.md`
- In a GitHub-accessible environment, confirm all three remote conditions:
  - Remote branch `visual-smoke-failure-acceptance-test` does not exist.
  - PR `#2` is in `merged` state.
  - There is no open PR from head branch `WuSangui571:visual-smoke-failure-acceptance-test` to base branch `main`.
- Record only safe metadata:
  - UTC+8 check timestamp.
  - Query method used, such as GitHub UI, REST API metadata, or `gh` CLI metadata.
  - Branch name and absent/present result.
  - PR number, state, merged boolean, merge commit SHA if available.
  - Open PR count for that head/base query.
  - Explicit note that no artifacts, logs, screenshots, secrets, provider payloads, prompts, private document content, or large raw payloads were downloaded or stored.
- If GitHub access remains unavailable, do not claim the evidence loop is closed. Record the exact boundary and stop.
- If any remote condition fails, stop and return to Codex for check/decision instead of broadening scope.

## Non-Goals / Forbidden Scope

- Do not modify backend, frontend, API, DTO, database, migrations, Docker, RAG, provider config, auth, or product behavior files.
- Do not edit `.github/workflows/ci.yml`, `frontend/package.json`, or `frontend/tests/visual/admin-login-theme-smoke.spec.ts`.
- Do not create a new temporary failure trigger.
- Do not push branches, delete branches, open PRs, close PRs, merge PRs, rerun CI, or change GitHub repository settings unless the user explicitly changes the task scope.
- Do not download visual-smoke artifacts.
- Do not record artifact contents, Playwright report contents, screenshots, log bodies, stack traces, secrets, provider payloads, prompts, private document content, request/response bodies, or large raw JSON payloads.
- Do not revert unrelated Trellis archive/journal changes already present in the working tree.

## Command / API / Payload Metadata

No application API contract changes.

Allowed GitHub metadata checks:

| Purpose | Example command/API | Required fields only |
|---|---|---|
| Remote branch absence | `git ls-remote --heads origin visual-smoke-failure-acceptance-test` or GitHub branches UI/API | branch name, result empty/non-empty, optional ref SHA if unexpectedly present |
| PR #2 merged state | `gh pr view 2 --json number,state,mergedAt,mergeCommit,url,headRefName,baseRefName` or GitHub REST/UI equivalent | PR number, state, merged flag/time, merge commit SHA, head/base |
| Open PR from head to main | `gh pr list --state open --base main --head visual-smoke-failure-acceptance-test --json number,state,url,headRefName,baseRefName` or REST/UI equivalent | count, PR numbers/URLs if any |

Allowed REST metadata endpoints if using GitHub API:

```text
GET /repos/WuSangui571/Sangui-RAG-Gateway/branches/visual-smoke-failure-acceptance-test
GET /repos/WuSangui571/Sangui-RAG-Gateway/pulls/2
GET /repos/WuSangui571/Sangui-RAG-Gateway/pulls?state=open&base=main&head=WuSangui571:visual-smoke-failure-acceptance-test
```

Payload recording rule: record only the selected metadata fields above, never raw full API payloads.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Remote branch is absent | Evidence loop branch condition passes. | Branch query returns empty, 404 branch API, or GitHub UI shows branch absent. |
| Remote branch exists | Stop and report unresolved cleanup. Do not delete it unless scope is explicitly changed. | Branch query returns a ref/SHA. |
| PR #2 is merged | Evidence loop PR state condition passes. | PR metadata shows `MERGED`/`merged`, `merged=true`, or equivalent UI state. |
| PR #2 is open or closed-unmerged | Stop and report mismatch. | PR metadata state is not merged. |
| No open PR from head to main | Evidence loop open-PR condition passes. | Open PR query count is `0`. |
| One or more open PRs exist | Stop and report PR numbers/URLs as safe metadata. Do not close without explicit scope change. | Open PR query count is greater than `0`. |
| GitHub access unavailable | Stop without claiming closure. | Record tool/API/network/auth boundary only. |
| Artifact/log content encountered | Do not store it. | Evidence contains metadata only. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Remote branch is absent, PR #2 is merged, no open PR exists from the temporary head branch to `main`, and the evidence file or addendum records only safe metadata. |
| Base | One or more checks were already known locally, but the final addendum refreshes GitHub remote state through a GitHub-accessible environment and explicitly closes the prior Codex network boundary. |
| Bad | Evidence claims closure while GitHub access failed, records artifact/log/payload contents, modifies business code, reruns/changes CI, or leaves a remote branch/open PR unreported. |

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product and CI boundary; CI must avoid provider secrets and unsafe artifact handling.
- `.trellis/spec/frontend/quality-guidelines.md`: visual-smoke CI command contract and artifact policy; upload is failure/cancel only and limited to Playwright report/test-result directories.
- `.trellis/spec/frontend/directory-structure.md`: confirms no frontend implementation file or structure changes are needed.
- `.trellis/spec/frontend/type-safety.md`: confirms no frontend API/type contract work is involved.
- `.trellis/spec/security/rag-security.md`: safe observability/evidence boundary; do not expose secrets, prompts, provider bodies, full logs, document content, or internal payloads.
- `.trellis/spec/backend/logging-guidelines.md`: safe metadata principle for operational evidence; avoid sensitive data and large content.
- `.trellis/spec/backend/error-handling.md`: safe error boundary if documenting access/API failures.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: confirms no API/DB/frontend DTO alignment is needed, while still requiring explicit boundary and validation thinking.

## Code Patterns Found

- `.github/workflows/ci.yml`: frontend CI runs `npm ci`, Playwright Chromium install, `npm run typecheck`, `npm run build`, and `npm run test:visual:ci`; uploads `visual-smoke-results` only on `failure() || cancelled()` from `frontend/playwright-report/` and `frontend/test-results/`.
- `frontend/package.json`: `test:visual:ci` delegates directly to `npm run test:visual`.
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`: current `main` baseline uses `const DARK_BG_RGB = 'rgb(20, 20, 20)'`.
- `.trellis/tasks/archive/2026-06/06-06-visual-smoke-temp-branch-pr-cleanup/acceptance-evidence.md`: existing evidence documents the exact unresolved Codex GitHub access boundary to close.
- `.trellis/workspace/sangui/journal-2.md`: Sessions 37 and 38 summarize the completed visual-smoke acceptance chain and the remaining remote/PR verification gap.

## Files Likely To Modify

- `.trellis/tasks/06-06-github-ci-remote-evidence-closeout-review/prd.md`: planning source of truth.
- `.trellis/tasks/06-06-github-ci-remote-evidence-closeout-review/implement.jsonl`: context for DeepSeek execution.
- `.trellis/tasks/06-06-github-ci-remote-evidence-closeout-review/check.jsonl`: context for Codex follow-up check.
- `.trellis/tasks/06-06-github-ci-remote-evidence-closeout-review/debug.jsonl`: debug context if GitHub access or metadata conflicts occur.
- Optional execution output: append a short section to `.trellis/tasks/archive/2026-06/06-06-visual-smoke-temp-branch-pr-cleanup/acceptance-evidence.md` or create a lightweight addendum/evidence file under the current task directory with safe metadata only.
- Optional journal update only after execution is accepted by the user and normal Trellis closeout is requested.

No business implementation file is expected to be modified.

## Required Tests / Verification

No backend/frontend build is required if no implementation file changes are made.

Mandatory verification:

- Remote branch check for `visual-smoke-failure-acceptance-test`.
- PR `#2` merged-state check.
- Open PR query for head `WuSangui571:visual-smoke-failure-acceptance-test` and base `main`.
- `git status --short` after evidence edit to confirm the diff is limited to Trellis task/evidence metadata.

Optional local guard if the execution agent wants to reassert the visual-smoke baseline without changing files:

```powershell
Select-String -Path .\frontend\tests\visual\admin-login-theme-smoke.spec.ts -Pattern "const DARK_BG_RGB = 'rgb\(20, 20, 20\)'"
```

Only if a business/frontend file is unexpectedly modified:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual:ci
```

## Planning Self-Check

- Acceptance criteria are explicit: remote branch absent, PR #2 merged, no open PR from the temporary head branch to `main`, safe metadata only.
- Forbidden scope is explicit: no business-code edits, no CI rerun/change, no branch/PR mutation, no artifact download, no unsafe payload/log/content recording.
- Expected modified files are limited to Trellis task/context/evidence files; business implementation files should remain unchanged.
- Required tests and assertion points are listed.
- Concrete guideline files were read, not just indexes: project spec, frontend directory structure, frontend type safety, frontend quality, RAG security, backend logging, backend error handling, backend quality, and cross-layer guide.
- No unclear requirement currently blocks planning.
- No API, DB, frontend type, DTO, payload, migration, RAG, provider, or auth contract alignment is needed.
