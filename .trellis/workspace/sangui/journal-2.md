# Journal - sangui (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-06-05

---



## Session 34: Frontend Admin visual smoke CI gate

**Date**: 2026-06-05
**Task**: Frontend Admin visual smoke CI gate
**Branch**: `main`

### Summary

(Add summary)

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
