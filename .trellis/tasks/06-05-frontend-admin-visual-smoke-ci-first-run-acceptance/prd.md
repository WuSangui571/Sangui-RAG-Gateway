# Frontend Admin Visual Smoke CI First-Run Acceptance and Failure Artifact Verification

## Goal

Verify the real GitHub Actions behavior for the already-integrated frontend Admin visual smoke CI gate, especially Playwright browser cache restore/save, explicit Chromium installation, CI execution of `npm run test:visual:ci`, and failure artifact upload for Playwright reports/results.

This task is a CI acceptance and evidence task. It should not expand Admin UI coverage or change visual smoke assertions unless the real CI run exposes a concrete configuration defect.

## Scope Classification

Simple Task with infra-validation depth.

Reason: the implementation surface is intentionally narrow and the expected output is mostly CI evidence plus Trellis/spec recording. It still needs infra-style acceptance criteria because GitHub Actions cache and artifact behavior can only be proven in the hosted CI runtime.

## Current Project State

The previous task, archived at `.trellis/tasks/archive/2026-06/06-05-frontend-admin-visual-smoke-ci-artifact-policy/`, already implemented the CI gate and documented the contract.

Journal session 34 records commit `ace21d3 chore: add frontend Admin visual smoke CI gate` with local validation passing:

- `cd frontend; cmd /c npm ci --dry-run`
- `cd frontend; cmd /c npx playwright install chromium`
- `cd frontend; cmd /c npm run typecheck`
- `cd frontend; cmd /c npm run build`
- `cd frontend; cmd /c npm run test:visual:ci`
- `git diff --check`

Known remaining gap from the journal:

- GitHub Actions first real CI confirmation is still needed for Playwright cache miss/hit, Chromium install behavior, and failure artifact contents.

## Requirements

- Inspect the latest `main` GitHub Actions run that includes commit `ace21d3` or a later `main` commit containing the same frontend visual smoke CI configuration.
- Record evidence that the frontend job ran these commands from `frontend/`:
  - `npm ci`
  - `npx playwright install chromium`
  - `npm run typecheck`
  - `npm run build`
  - `npm run test:visual:ci`
- Record Playwright browser cache behavior:
  - first-run or cache-miss evidence for `~/.cache/ms-playwright`;
  - later cache-hit evidence if available or produced by a safe rerun;
  - cache key must remain OS plus `frontend/package-lock.json` hash.
- Verify the frontend job does not require backend services, Docker images, provider keys, upstream keys, or `.env`.
- If the successful run proves all success-path CI behavior, record the acceptance result in the Trellis task/session and avoid spec churn.
- If existing spec is missing real acceptance guidance, update only the narrow relevant spec section, expected first choice:
  - `.trellis/spec/frontend/quality-guidelines.md`
  - optionally `.trellis/spec/sangui-rag-gateway.md` only if the project-level CI contract is materially incomplete.
- If necessary, create one controlled visual smoke failure to verify artifact upload:
  - Prefer a temporary branch or pull request that intentionally changes only a visual smoke assertion or equivalent test-only failure trigger.
  - Do not merge the failure branch.
  - Do not leave the failure mutation in `main`.
  - Verify that `visual-smoke-results` artifact contains `frontend/playwright-report/` and/or `frontend/test-results/` when produced by Playwright.
- Record artifact evidence by file names, artifact names, job names, run IDs, and safe summaries only. Do not paste full report HTML or large traces into specs/journal.

## Non-Goals / Forbidden Scope

- Do not change Admin UI, routes, theme behavior, selectors, layout, copy, or visual design.
- Do not add visual smoke coverage for additional pages or authenticated workflows.
- Do not change backend Java, admin APIs, `/v1/*` gateway behavior, DTO/VO fields, database migrations, Docker Compose services, Redis, PostgreSQL, RAG retrieval, prompt behavior, or request-log APIs.
- Do not introduce secrets, provider API keys, `.env`, GitHub repository secrets, or external upstream dependencies into CI.
- Do not cache or upload `frontend/node_modules/`, `frontend/dist/`, `.env`, backend data, API keys, upstream keys, or manually generated smoke artifacts.
- Do not commit generated Playwright reports, traces, screenshots, videos, browser binaries, `node_modules`, or `dist`.
- Do not replace the existing visual smoke runner with a second implementation.

## Command / CI Contract

### GitHub Actions workflow

File:

```text
.github/workflows/ci.yml
```

Frontend job command order:

```bash
npm ci
npx playwright install chromium
npm run typecheck
npm run build
npm run test:visual:ci
```

Working directory for each frontend command:

```text
frontend
```

Playwright cache path:

```text
~/.cache/ms-playwright
```

Expected cache key shape:

```text
playwright-${{ runner.os }}-${{ hashFiles('frontend/package-lock.json') }}
```

Failure artifact upload:

```text
name: visual-smoke-results
condition: failure() || cancelled()
paths:
  frontend/playwright-report/
  frontend/test-results/
if-no-files-found: ignore
```

### Local verification commands

Run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual:ci
```

Optional dependency sanity check:

```bash
cmd /c npm ci --dry-run
```

### GitHub CLI evidence commands

Use only if GitHub CLI is authenticated and available:

```bash
gh run list --branch main --workflow CI --limit 10
gh run view <run-id> --job <frontend-job-id> --log
gh run view <run-id> --json conclusion,createdAt,databaseId,event,headBranch,headSha,jobs,status,updatedAt,url
gh run download <failed-run-id> --name visual-smoke-results --dir <safe-temp-dir>
```

If `gh` is unavailable or unauthenticated, collect the same evidence from the GitHub Actions web UI and record exact run URL, job name, conclusion, and relevant log step names.

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Latest `main` CI run succeeds | Frontend job is green and includes visual smoke as a required step | GitHub run/job conclusion and log for `Visual smoke test` |
| Browser cache first run or miss | Cache step reports no exact cache found or a miss, then Chromium install succeeds | `Cache Playwright browsers` and `Install Playwright Chromium` logs |
| Browser cache hit on rerun/later run | Cache step restores `~/.cache/ms-playwright`; install step still succeeds | cache restore log plus explicit install log |
| Chromium install behavior | `npx playwright install chromium` runs in CI and exits 0 | install step log and job conclusion |
| Visual smoke command | `npm run test:visual:ci` delegates to `npm run test:visual` and Playwright Chromium tests pass | package script and CI log |
| Success artifact policy | Successful frontend job does not upload `visual-smoke-results` | no upload-artifact step execution on success or step skipped due condition |
| Controlled visual failure | Frontend job fails only at visual smoke/test boundary | failed run log and job conclusion |
| Failure artifact policy | Failed/cancelled visual run uploads `visual-smoke-results` with Playwright report/results when present | artifact list/download contents |
| Artifact safety | Artifacts contain Playwright debug outputs only; no `.env`, `node_modules`, `dist`, secrets, backend data, or manual smoke artifacts | artifact file list inspection |
| Spec sufficiency | Existing frontend quality/project spec already states command/cache/artifact policy | no spec edit needed; record acceptance result in task/session |
| Spec gap discovered | Missing or stale spec statement is updated narrowly | diff limited to `.trellis/spec/frontend/quality-guidelines.md` or project CI section |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | Latest or rerun `main` CI has frontend job green; logs prove Playwright cache behavior, explicit Chromium install, typecheck/build/visual smoke command order, and no success artifact upload. A controlled failure run, if needed, uploads `visual-smoke-results` with safe Playwright report/results files. |
| Base | Success-path CI evidence is complete, but no safe failure run is created because current GitHub permissions or branch policy make controlled failure impractical; record this as a remaining manual CI evidence gap rather than changing product code. |
| Bad | CI relies on cache without explicit Chromium install, skips `npm run test:visual:ci`, uploads artifacts on success, fails to upload artifacts on visual failure, requires backend/secrets, or the task broadens Admin UI/visual coverage without a CI configuration defect. |

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project-level CI baseline says frontend CI must run `npm ci`, `npx playwright install chromium`, typecheck, build, and `npm run test:visual:ci`.
- `.trellis/spec/frontend/quality-guidelines.md`: source of truth for visual smoke assertions, CI command contract, Playwright cache key, and artifact policy.
- `.trellis/spec/frontend/directory-structure.md`: generated artifacts and new frontend files must stay in expected frontend boundaries if any fix is needed.
- `.trellis/spec/frontend/type-safety.md`: no DTO/API/type changes are expected; use this as a guard against scope creep.
- `.trellis/spec/frontend/state-management.md`: no runtime evidence, request logs, secrets, or CI data should be persisted in frontend state.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: CI/runtime and artifact behavior should have explicit commands, matrix, and good/base/bad cases.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: do not introduce a duplicate visual smoke runner or parallel command implementation.

### Code Patterns Found

- `.github/workflows/ci.yml`: frontend job already uses `actions/setup-node@v4`, `actions/cache@v4` for `~/.cache/ms-playwright`, explicit `npx playwright install chromium`, then `npm run test:visual:ci`, and failure-only `actions/upload-artifact@v4`.
- `frontend/package.json`: `test:visual:ci` directly delegates to `npm run test:visual`; this is the intended single-runner pattern.
- `frontend/scripts/run-visual-smoke.mjs`: one existing Vite + Playwright wrapper starts Vite on `127.0.0.1:5173` and runs local Playwright binary; do not duplicate this runner.
- `frontend/playwright.config.ts`: Chromium-only project, `retries: process.env.CI ? 2 : 0`, `forbidOnly` in CI, list reporter, trace on first retry.
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`: current coverage is limited to unauthenticated Admin login theme background checks.
- `.gitignore`: `frontend/test-results/` and `frontend/playwright-report/` are already ignored.

### Files Likely To Modify

- `.trellis/tasks/06-05-frontend-admin-visual-smoke-ci-first-run-acceptance/prd.md`: already created for task requirements and handoff.
- `.trellis/spec/frontend/quality-guidelines.md`: only if CI first-run/failure verification reveals a missing or stale executable acceptance rule.
- `.trellis/spec/sangui-rag-gateway.md`: only if project-level CI baseline is materially incomplete after real CI evidence.
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-ci-first-run-acceptance/*.jsonl`: Trellis context files.
- `.github/workflows/ci.yml`: only if real CI exposes a clear configuration defect in cache, Chromium install, command order, or artifact upload.
- `frontend/package.json`: only if real CI exposes a script contract defect; do not change for coverage expansion.

## Required Tests and Assertion Points

If no implementation/config change is needed:

- GitHub Actions evidence:
  - latest `main` CI run URL;
  - frontend job conclusion;
  - cache restore/save log snippet summary;
  - Chromium install step conclusion;
  - visual smoke command step conclusion;
  - success artifact absence;
  - controlled failure artifact presence if performed.
- Local static checks before handback:
  - `git status --short`
  - `git diff --check`

If CI/spec/package changes are made:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual:ci
```

Recommended CI evidence commands after changes:

```bash
gh run list --branch main --workflow CI --limit 10
gh run view <run-id> --job <frontend-job-id> --log
gh run download <failed-run-id> --name visual-smoke-results --dir <safe-temp-dir>
```

## Planning Self-Check

- Acceptance criteria are explicit: CI success logs, cache miss/hit evidence, Chromium install, visual smoke command, success artifact absence, and failure artifact upload behavior.
- Forbidden scope is explicit: no Admin UI changes, no coverage expansion, no backend/API/DB/RAG/Docker changes, no secrets.
- Expected modified files are listed and constrained.
- Required tests and GitHub Actions assertion points are listed.
- Specific guideline files were read: project spec, frontend quality, directory structure, type safety, state management, cross-layer thinking, and code reuse thinking.
- No user clarification is currently needed; the requested scope is clear.
- No API, DB, frontend DTO/type, or backend payload fields are introduced or changed.
