# 清理视觉冒烟临时失败分支与 PR 防误合残留

## Classification

Simple Task.

Reasoning: the goal is clear and low-complexity, but it touches Git/GitHub workflow hygiene and remote branch/PR state. It does not require business-code changes, API changes, DB changes, frontend type changes, RAG changes, Docker changes, or product behavior changes.

## Current Project State

- Current branch: `main`.
- No active Trellis task before this task was created.
- Workspace journal Session 37 records that the frontend visual smoke CI artifact path and main baseline closeout were completed.
- Session 37 records `main` was restored so `frontend/tests/visual/admin-login-theme-smoke.spec.ts` uses `DARK_BG_RGB = 'rgb(20, 20, 20)'`.
- Session 37 records the remaining cleanup need: the temporary failure branch must not be merged again and can be deleted after evidence capture.
- Current working tree already contains pre-existing Trellis archive/journal changes from the previous closeout. Execution must not revert or broaden those changes.

## Planning-Time Read-Only Findings

- Local branch exists: `visual-smoke-failure-acceptance-test`.
- Remote branch exists: `origin/visual-smoke-failure-acceptance-test`.
- Fresh `git ls-remote --heads origin visual-smoke-failure-acceptance-test` confirmed the remote branch still exists at `c0f8e5daaf327942f0e5bac1d52395a06ab2fac1`.
- `gh` CLI is not installed in this environment.
- Read-only GitHub REST API query for open PRs with `base=main` and `head=WuSangui571:visual-smoke-failure-acceptance-test` returned an empty array at planning time.

## Goal

Remove the temporary visual-smoke failure branch and any open PR merge surface left from the controlled CI failure-artifact acceptance task, while preserving the restored passing `main` baseline.

## Requirements

- Confirm the local and remote status of `visual-smoke-failure-acceptance-test` before cleanup.
- Confirm whether any open PR points from `visual-smoke-failure-acceptance-test` to `main`.
- Delete the local temporary branch if it exists.
- Delete the remote temporary branch if it exists.
- If an open PR exists, close it and add a clear do-not-merge note explaining that the branch intentionally contains a failure trigger used only for artifact acceptance.
- Verify `main` still uses `DARK_BG_RGB = 'rgb(20, 20, 20)'` in `frontend/tests/visual/admin-login-theme-smoke.spec.ts`.
- Verify `git branch -a` no longer shows `visual-smoke-failure-acceptance-test`.
- Verify GitHub has no open PR from that head branch to `main`.
- Record only safe metadata: branch name, PR number/state/URL if present, branch deletion result, and verification result.

## Non-Goals / Forbidden Scope

- Do not modify backend, API, DB, RAG, Docker, provider config, or product behavior.
- Do not modify `frontend/tests/visual/admin-login-theme-smoke.spec.ts` unless it unexpectedly no longer contains the restored `rgb(20, 20, 20)` baseline; if that happens, stop and return to Codex check instead of broadening scope.
- Do not create a new temporary failure trigger.
- Do not rerun or download visual-smoke artifact contents.
- Do not record artifact contents, screenshots, logs with secrets, provider payloads, prompts, private document content, or large raw logs.
- Do not revert unrelated Trellis archive/journal changes already present in the working tree.
- Do not merge any PR associated with `visual-smoke-failure-acceptance-test`.

## Command / API Contract

No application API contract changes.

Operational command/API surface:

| Action | Command / API | Expected |
|---|---|---|
| Local branch check | `git branch --list visual-smoke-failure-acceptance-test` | Shows branch before cleanup if present. |
| Remote branch check | `git ls-remote --heads origin visual-smoke-failure-acceptance-test` | Shows branch SHA before cleanup if present. |
| PR check | GitHub UI, GitHub REST API, or available CLI equivalent | No open PR from `visual-smoke-failure-acceptance-test` to `main`; if one exists, close it. |
| Local delete | `git branch -d visual-smoke-failure-acceptance-test` or `git branch -D visual-smoke-failure-acceptance-test` | Branch removed locally. Prefer `-d`; use `-D` only if Git refuses because the branch contains the intentional failure commit and this is confirmed safe. |
| Remote delete | `git push origin --delete visual-smoke-failure-acceptance-test` | Remote branch removed. |
| Baseline assertion | `Select-String -Path frontend/tests/visual/admin-login-theme-smoke.spec.ts -Pattern "const DARK_BG_RGB = 'rgb\\(20, 20, 20\\)'"` | Match found. |
| Final branch verification | `git branch -a --list "*visual-smoke-failure-acceptance-test*"` plus fresh remote check | No local or remote branch remains. |

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Local branch exists and is safe to delete | Delete it locally. | `git branch --list visual-smoke-failure-acceptance-test` returns empty after cleanup. |
| Local branch absent | Treat as already-clean local state. | Record "local branch absent". |
| Remote branch exists | Delete remote branch. | Fresh `git ls-remote --heads origin visual-smoke-failure-acceptance-test` returns empty after cleanup. |
| Remote branch absent | Treat as already-clean remote state. | Record "remote branch absent". |
| Open PR exists | Close it, add do-not-merge note. | GitHub PR state is `closed`, not `open` or `merged`. |
| No open PR exists | No PR action required. | GitHub query returns empty open PR list. |
| `main` baseline is still restored | Proceed with cleanup. | `DARK_BG_RGB` is `rgb(20, 20, 20)`. |
| `main` baseline is regressed | Stop; do not delete evidence branch until Codex check decides next step. | Baseline assertion fails. |
| GitHub access unavailable | Stop after local-only findings; do not claim full cleanup. | Report exact failing boundary. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Local branch deleted, remote branch deleted, no open PR remains, `main` visual-smoke baseline remains `rgb(20, 20, 20)`, and only branch/PR metadata is recorded. |
| Base | Local branch was already absent; remote branch deleted or already absent; no open PR exists; baseline remains correct. |
| Bad | Remote branch remains, open PR remains, PR is merged, `main` baseline regresses, or cleanup records unsafe artifact/log/content data. |

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary, CI baseline, and secret-safe deployment/CI expectations.
- `.trellis/spec/frontend/quality-guidelines.md`: visual smoke command contract, CI visual smoke contract, and artifact policy.
- `.trellis/spec/frontend/directory-structure.md`: confirms no frontend implementation files should be added or reorganized for this task.
- `.trellis/spec/frontend/type-safety.md`: confirms no frontend API/type contract work is involved.
- `.trellis/spec/security/rag-security.md`: safe evidence boundary; do not expose secrets, full logs, prompts, provider bodies, or private content.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: boundary checklist; this task has no app API/DB/frontend type/DTO alignment work.

## Code Patterns Found

- `.github/workflows/ci.yml`: frontend job runs `npm ci`, Playwright Chromium install, `npm run typecheck`, `npm run build`, `npm run test:visual:ci`; uploads `visual-smoke-results` only on `failure() || cancelled()` and only from `frontend/playwright-report/` and `frontend/test-results/`.
- `frontend/package.json`: `test:visual:ci` delegates directly to `npm run test:visual`.
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`: restored baseline uses `const DARK_BG_RGB = 'rgb(20, 20, 20)'`.
- `.trellis/workspace/sangui/journal-2.md`: Session 37 records successful baseline restoration and identifies branch cleanup as the remaining hygiene step.

## Files Likely To Modify

- `.trellis/tasks/06-06-visual-smoke-temp-branch-pr-cleanup/prd.md`: planning and acceptance source of truth.
- `.trellis/tasks/06-06-visual-smoke-temp-branch-pr-cleanup/implement.jsonl`: implementation context for DeepSeek.
- `.trellis/tasks/06-06-visual-smoke-temp-branch-pr-cleanup/check.jsonl`: check context for Codex follow-up.
- `.trellis/tasks/06-06-visual-smoke-temp-branch-pr-cleanup/debug.jsonl`: debug context if cleanup fails.
- Optional after execution: `.trellis/tasks/06-06-visual-smoke-temp-branch-pr-cleanup/acceptance-evidence.md` or journal entry with branch/PR metadata only.

No business implementation file is expected to be modified.

## Required Tests / Verification

- `Select-String -Path .\frontend\tests\visual\admin-login-theme-smoke.spec.ts -Pattern "const DARK_BG_RGB = 'rgb\(20, 20, 20\)'"`
- `git branch --list visual-smoke-failure-acceptance-test`
- `git ls-remote --heads origin visual-smoke-failure-acceptance-test`
- GitHub PR query for open PRs where `base=main` and `head=visual-smoke-failure-acceptance-test`.
- After deletion: `git branch -a --list "*visual-smoke-failure-acceptance-test*"` should return empty.
- After deletion: fresh remote query should show no `refs/heads/visual-smoke-failure-acceptance-test`.
- `cmd /c npm run test:visual:ci` from `frontend/` is optional but recommended if the execution agent touches the visual smoke test file. It should not be necessary for branch-only cleanup if baseline assertion passes and no implementation file changes are made.

## Planning Self-Check

- Acceptance criteria are explicit: branch absent locally/remotely, no open PR, main baseline still correct, safe metadata only.
- Forbidden scope is explicit: no business-code edits, no new failure trigger, no artifact-content recording, no unrelated Trellis reverts.
- Expected modified files are limited to Trellis task/context/evidence files; business implementation files should remain unchanged.
- Required tests and assertion points are listed.
- Concrete guideline files were read: project spec, frontend directory structure, frontend quality, frontend type safety, RAG security, cross-layer guide.
- No unclear requirement currently blocks planning.
- No API, DB, frontend types, DTO, payload, migration, or RAG contract alignment is needed.

