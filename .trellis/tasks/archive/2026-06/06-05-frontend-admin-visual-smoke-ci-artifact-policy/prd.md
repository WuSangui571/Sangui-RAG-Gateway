# Frontend Admin Visual Smoke CI Integration and Artifact Policy

## Goal

Promote the existing local frontend Admin visual smoke command into the CI frontend gate, and document the browser runtime, cache, and artifact policy so future visual smoke expansion has a stable baseline.

## Scope Classification

Complex Task.

Reason: implementation is small, but it touches CI runtime behavior, Playwright browser installation, cache behavior, generated artifacts, and frontend quality specs. It is an engineering gate change, not a page-level UI change.

## Background

The previous task added the local visual smoke baseline:

- `frontend/package.json` exposes `npm run test:visual`.
- `frontend/scripts/run-visual-smoke.mjs` starts Vite on `127.0.0.1:5173` and runs Playwright tests.
- `frontend/playwright.config.ts` targets Chromium only.
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts` verifies Admin unauthenticated login theme backgrounds.
- `.gitignore` already ignores `frontend/test-results/` and `frontend/playwright-report/`.
- `.trellis/spec/frontend/quality-guidelines.md` documents the local visual smoke contract and one-time `npx playwright install chromium` setup.

Current gap: `.github/workflows/ci.yml` frontend job runs `npm ci`, `npm run typecheck`, and `npm run build`, but does not install Playwright Chromium or run `npm run test:visual`. Failure artifacts are ignored locally but not explicitly uploaded in CI.

## Requirements

- Add the frontend visual smoke command to GitHub Actions CI after dependencies are installed and before the frontend job is considered green.
- Install only Playwright Chromium for CI, matching the current `frontend/playwright.config.ts` project.
- Keep the existing local command usable: `cd frontend && cmd /c npm run test:visual`.
- If useful, add a CI-specific npm script such as `test:visual:ci`; do not duplicate visual smoke logic.
- Configure CI caching in a way that is explicit and maintainable:
  - keep existing npm cache through `actions/setup-node`;
  - add Playwright browser cache only if it is bounded to the browser cache directory and keyed by `frontend/package-lock.json` plus OS;
  - do not cache `frontend/node_modules/`, `frontend/dist/`, `frontend/test-results/`, or `frontend/playwright-report/`.
- Upload visual smoke failure artifacts from CI only:
  - `frontend/playwright-report/`
  - `frontend/test-results/`
- Upload artifacts only on failure or cancellation, not on successful runs.
- Keep generated Playwright artifacts ignored by git and local workspace.
- Update `.trellis/spec/frontend/quality-guidelines.md` with the CI command contract and artifact policy.
- Do not broaden visual smoke page coverage in this task.

## Non-Goals / Forbidden Scope

- Do not change Admin UI visual behavior, themes, selectors, or login page styling unless CI integration cannot run without a tiny mechanical fix.
- Do not add new E2E pages or authenticated admin workflows.
- Do not change backend Java, admin APIs, `/v1/*` gateway behavior, DTO/VO fields, database migrations, Docker images, Redis, PostgreSQL, or RAG behavior.
- Do not introduce secrets or provider API keys into CI.
- Do not make visual smoke depend on backend services.
- Do not commit Playwright generated reports, traces, screenshots, videos, browser binaries, `node_modules`, or `dist`.

## Command Contract

### Local command

```bash
cd frontend
cmd /c npm run test:visual
```

Prerequisite for a fresh local machine:

```bash
cd frontend
npx playwright install chromium
```

### CI command

The frontend CI job must run these commands from `frontend/`:

```bash
npm ci
npx playwright install chromium
npm run typecheck
npm run build
npm run test:visual
```

If a CI-specific script is added, the contract becomes:

```bash
npm run test:visual:ci
```

but it must delegate to the same smoke runner or the same Playwright test suite, not create a second implementation.

## CI Artifact Policy

### Ignored by git and never committed

```text
frontend/node_modules/
frontend/dist/
frontend/test-results/
frontend/playwright-report/
*.tsbuildinfo
```

### CI upload only on failure or cancellation

```text
frontend/playwright-report/
frontend/test-results/
```

### Do not upload on success

Successful visual smoke runs should keep logs concise and avoid artifact noise.

### Do not upload or cache

```text
.env
manual smoke artifacts
API keys or upstream keys
backend/data/
frontend/dist/
frontend/node_modules/
```

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Fresh CI frontend job | Installs npm deps, installs Chromium, runs typecheck, build, and visual smoke | `.github/workflows/ci.yml` frontend job contains all steps in a clear order |
| Playwright test passes | Frontend job succeeds without uploading report artifacts | upload-artifact step condition excludes normal success |
| Playwright test fails | Frontend job fails and uploads `playwright-report` and `test-results` if present | upload-artifact step uses failure/cancel condition and `if-no-files-found` does not hide the original failure |
| Browser cache miss | CI installs Chromium successfully | install step remains explicit and does not rely only on cache |
| Browser cache hit | CI can reuse browser cache but still validates/install checks Chromium | cache key includes OS and `frontend/package-lock.json` |
| Local developer run | Existing `npm run test:visual` remains valid | package scripts keep local command unchanged |
| Generated artifacts | Reports/results remain untracked | `.gitignore` retains artifact ignores |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | On GitHub Actions Ubuntu, frontend CI installs dependencies, installs Chromium, runs typecheck, build, then visual smoke. If visual smoke fails, CI uploads Playwright report and test results only for debugging. |
| Base | Local developer can still run `npm run test:visual` after `npx playwright install chromium`; no backend is required. |
| Bad | CI skips Chromium install, visual smoke is not a required gate, failure artifacts are committed instead of uploaded, success runs upload noisy artifacts, or CI caches broad generated directories such as `node_modules` or `dist`. |

## Expected Files To Modify

- `.github/workflows/ci.yml`: frontend job Playwright install, optional browser cache, visual smoke command, failure artifact upload.
- `.trellis/spec/frontend/quality-guidelines.md`: CI visual smoke command contract and artifact policy.
- `frontend/package.json`: optional only, if adding a CI-specific script improves clarity without duplicating logic.
- `frontend/package-lock.json`: only if `package.json` script changes require lock metadata changes; script-only changes normally should not alter dependency resolution.

## Required Tests and Checks

Run from the repository root unless noted:

```bash
cd frontend
cmd /c npm ci --dry-run
cmd /c npm run test:visual
cmd /c npm run typecheck
cmd /c npm run build
```

Check CI YAML structure:

```bash
Get-Content .github\workflows\ci.yml
```

Recommended static checks:

```bash
git diff --check
git status --short
```

If a YAML parser is available locally, validate `.github/workflows/ci.yml` syntax with it. Do not add a new dependency only for YAML validation.

## Planning Self-Check

- Acceptance criteria are explicit: CI must install Chromium, run visual smoke, and upload failure artifacts only.
- Forbidden scope is explicit: no backend/API/DB/Docker/RAG/UI behavior expansion.
- Expected modified files are listed.
- Required tests are listed.
- Specific frontend and shared guideline files were read before implementation planning.
- No user clarification is currently needed.
- No API, DB, frontend DTO/type, or backend payload fields are introduced or changed by this task.
