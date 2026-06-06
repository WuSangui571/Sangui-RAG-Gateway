# Journal - sangui (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-06-05

---



## Session 34: Frontend Admin visual smoke CI gate

**Date**: 2026-06-05
**Task**: Frontend Admin visual smoke CI gate
**Branch**: `main`

### Summary

Recorded and accepted the frontend Admin visual smoke CI first-run evidence. The task confirmed the GitHub Actions frontend job command order, Playwright browser cache miss/hit behavior, explicit Chromium install, visual smoke execution, and success-run artifact absence.

### Main Changes

**Commit**: ace21d3 chore: add frontend Admin visual smoke CI gate

**Main modules**:
- Frontend CI workflow
- Frontend visual smoke npm script contract
- Trellis frontend/project quality specs

**Updated files**:
- `.github/workflows/ci.yml`
- `frontend/package.json`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/tasks/archive/2026-06/06-05-frontend-admin-visual-smoke-ci-artifact-policy/`

**Implementation summary**:
- Added Playwright browser cache for `~/.cache/ms-playwright`, keyed by OS plus `frontend/package-lock.json`.
- Added explicit `npx playwright install chromium` in the frontend CI job.
- Promoted Admin unauthenticated login visual smoke into the frontend CI gate through `npm run test:visual:ci`.
- Kept `test:visual:ci` as a direct delegation to existing `npm run test:visual`; no second visual smoke runner was introduced.
- Uploaded `frontend/playwright-report/` and `frontend/test-results/` only on failure or cancellation.
- Documented the CI visual smoke command contract and artifact policy in frontend and project specs.

**Validation**:
- `cd frontend; cmd /c npm ci --dry-run` passed.
- `cd frontend; cmd /c npx playwright install chromium` passed after rerun with network/browser-cache access.
- `cd frontend; cmd /c npm run typecheck` passed.
- `cd frontend; cmd /c npm run build` passed with the existing Vite chunk-size warning only.
- `cd frontend; cmd /c npm run test:visual:ci` passed, 3/3 Chromium visual smoke tests.
- `git diff --check` passed with Windows LF-to-CRLF warnings only.

**Result and boundaries**:
- Human manual testing was completed before record-session.
- No backend, API, DB, Docker image, Redis, PostgreSQL, RAG, secret, or Admin UI behavior changes were made.
- GitHub Actions artifact behavior still needs first real CI confirmation for cache hit/miss and failure artifact contents.


### Git Commits

| Hash | Message |
|------|---------|
| `ace21d3` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 35: Frontend Admin visual smoke CI acceptance

**Date**: 2026-06-05
**Task**: Frontend Admin visual smoke CI acceptance
**Branch**: `main`

### Summary

Completed the controlled frontend Admin visual-smoke failure artifact acceptance task. The committed work proves the temporary failure trigger path for the CI visual smoke artifact contract and records the Trellis evidence needed for follow-up archive/session tracking.

### Main Changes

| Area | Details |
|------|---------|
| Commit | 598a9c4 chore: record frontend Admin visual smoke CI acceptance |
| Main module | Trellis task evidence for frontend Admin visual smoke CI first-run acceptance |
| Updated files | .trellis/tasks/06-05-frontend-admin-visual-smoke-ci-first-run-acceptance/acceptance-evidence.md; check.jsonl; debug.jsonl; implement.jsonl; prd.md; task.json |
| Acceptance result | Success-path GitHub Actions evidence recorded for frontend job command order, Playwright browser cache miss/hit behavior, explicit Chromium install, visual smoke execution, and success artifact absence. |
| Validation | Codex finish check passed: git diff --check; cd frontend && cmd /c npm run typecheck; cd frontend && cmd /c npm run build; cd frontend && cmd /c npm run test:visual:ci with 3 Chromium tests passed. |
| Manual acceptance | User confirmed manual testing and committed the work before record-session. |
| Boundaries | No source code, CI workflow, backend, API, DB, RAG, Docker, or spec files changed in this acceptance task. Controlled failure artifact verification remains a documented BASE/manual gap because gh CLI/write credentials were unavailable and no temporary failure branch was pushed. |
| Archive | Archived task frontend-admin-visual-smoke-ci-first-run-acceptance to .trellis/tasks/archive/2026-06/. |


### Git Commits

| Hash | Message |
|------|---------|
| `598a9c4` | chore:记录前端 Admin 视觉冒烟 CI 验收 |

### Testing

- [OK] User confirmed manual testing before record-session.
- [OK] `git diff --check`
- [OK] `cd frontend; cmd /c npm run typecheck`
- [OK] `cd frontend; cmd /c npm run build`
- [OK] `cd frontend; cmd /c npm run test:visual:ci` (3 Chromium tests passed)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 36: Frontend visual smoke failure artifact acceptance

**Date**: 2026-06-06
**Task**: Frontend visual smoke failure artifact acceptance
**Branch**: `visual-smoke-failure-acceptance-test`

### Summary

Completed the controlled frontend Admin visual-smoke failure artifact acceptance task. The committed work proves the temporary failure trigger path for the CI visual smoke artifact contract and records the Trellis evidence needed for follow-up archive/session tracking.

### Main Changes

| Area | Summary |
|---|---|
| Frontend visual smoke | Added a temporary branch-only failing assertion in `frontend/tests/visual/admin-login-theme-smoke.spec.ts` by changing `DARK_BG_RGB` from `rgb(20, 20, 20)` to `rgb(30, 20, 20)` so CI can prove failure artifact upload behavior. |
| Trellis evidence | Recorded PRD, check context, implementation context, task metadata, and acceptance evidence for the controlled visual-smoke failure artifact acceptance task. |
| Codex check/fix | Codex reviewed PRD/spec/check context, confirmed the failure boundary is visual smoke only, and cleaned mojibake in `acceptance-evidence.md` before the user committed. |
| Validation | `cmd /c npm run typecheck` passed; `cmd /c npm run build` passed with existing Vite chunk-size warning; `cmd /c npm run test:visual:ci` failed as expected with 2 dark-theme assertion failures and 1 light-theme pass; `git diff --check` passed; `python ./.trellis/scripts/task.py validate frontend-admin-visual-smoke-failure-artifact-acceptance` passed. |
| Boundary | No backend/API/DB/RAG/Docker/product behavior changes. The intentional failing assertion is a temporary acceptance trigger and must not be merged to main. |

**Updated Files**:
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/acceptance-evidence.md`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/prd.md`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/check.jsonl`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/implement.jsonl`
- `.trellis/tasks/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/task.json`


### Git Commits

| Hash | Message |
|------|---------|
| `c0f8e5d` | (see git log) |

### Testing

- [OK] `cmd /c npm run typecheck` passed.
- [OK] `cmd /c npm run build` passed with the existing Vite chunk-size warning.
- [OK] `cmd /c npm run test:visual:ci` failed as expected at the dark-theme visual smoke assertion only: 2 dark-theme failures, 1 light-theme pass.
- [OK] `git diff --check` passed.
- [OK] `python ./.trellis/scripts/task.py validate frontend-admin-visual-smoke-failure-artifact-acceptance` passed.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 37: Frontend visual smoke CI artifact and main baseline closeout

**Date**: 2026-06-06
**Task**: Frontend visual smoke CI artifact and main baseline closeout
**Branch**: `main`

### Summary

(Add summary)

### Main Changes

| Area | Summary |
|---|---|
| Frontend visual smoke evidence | Completed the controlled failure-artifact acceptance path for the Admin visual smoke CI contract. The temporary failure branch proved the failure boundary and artifact behavior, with evidence limited to safe metadata and file-list summaries. |
| Main baseline recovery | Verified that the temporary branch was accidentally merged into main, then restored `frontend/tests/visual/admin-login-theme-smoke.spec.ts` so `DARK_BG_RGB` is back to `rgb(20, 20, 20)`. |
| Trellis cleanup | Archived both the failure-artifact acceptance task and the main-baseline cleanup task after manual GitHub confirmation and committed code. |
| Validation | `cmd /c npm run typecheck` passed; `cmd /c npm run build` passed with the existing Vite chunk-size warning; `cmd /c npm run test:visual:ci` passed with 3 Chromium tests. GitHub Actions was manually confirmed normal after commit `84b3798`. |
| Boundary | No backend, API, DB, RAG, Docker, provider, or product behavior changes. The remaining work was limited to the visual-smoke test baseline and Trellis task/session metadata. |

**Updated Files**:
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`
- `.trellis/tasks/archive/2026-06/06-05-frontend-admin-visual-smoke-failure-artifact-acceptance/`
- `.trellis/tasks/archive/2026-06/06-06-frontend-visual-smoke-temp-branch-cleanup-main-baseline/`
- `.trellis/workspace/sangui/index.md`
- `.trellis/workspace/sangui/journal-2.md`

**Commits Recorded**:
- `c0f8e5d` - temporary branch-only failure trigger for visual smoke artifact acceptance.
- `4cb098f` - Trellis main-baseline validation and task metadata.
- `84b3798` - restore `main` visual-smoke dark background baseline after accidental merge.

**Verification**:
- [OK] `cmd /c npm run typecheck` passed.
- [OK] `cmd /c npm run build` passed with existing Vite chunk-size warning.
- [OK] `cmd /c npm run test:visual:ci` passed, 3/3 Chromium tests.
- [OK] GitHub Actions was manually confirmed normal after `84b3798`.
- [OK] `python ./.trellis/scripts/task.py list` shows no active tasks after archive.

**Result**:
- Frontend visual-smoke CI success path and failure-artifact path are now both validated.
- `main` is restored to the passing `rgb(20, 20, 20)` baseline.
- The temporary failure branch must not be merged again; it can be deleted now that evidence has been captured.


### Git Commits

| Hash | Message |
|------|---------|
| `c0f8e5d` | (see git log) |
| `4cb098f` | (see git log) |
| `84b3798` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
