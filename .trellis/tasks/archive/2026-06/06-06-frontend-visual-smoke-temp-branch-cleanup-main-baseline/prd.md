# Frontend Visual Smoke Temporary Branch Cleanup and Main Baseline Confirmation

## Goal

Prevent the temporary visual-smoke failure assertion from being merged, and confirm that `main` keeps the passing Admin login visual-smoke baseline.

## Background

The previous task intentionally changed `frontend/tests/visual/admin-login-theme-smoke.spec.ts` on branch `visual-smoke-failure-acceptance-test` so `DARK_BG_RGB` became `rgb(30, 20, 20)`. That branch exists only to prove CI failure-artifact upload behavior. The follow-up risk is accidental merge or reuse of that temporary failing assertion.

## Scope Classification

Simple Task.

This is a frontend verification and branch hygiene task with a narrow, known target. It does not change API contracts, database schema, backend behavior, RAG retrieval, storage, AI provider behavior, secrets, or permissions.

## Requirements

- Verify `main` does not contain `DARK_BG_RGB = 'rgb(30, 20, 20)'`.
- Verify `main` has `DARK_BG_RGB = 'rgb(20, 20, 20)'` in `frontend/tests/visual/admin-login-theme-smoke.spec.ts`.
- Run frontend validation from `main`:
  - `cmd /c npm run typecheck`
  - `cmd /c npm run build`
  - `cmd /c npm run test:visual:ci`
- Confirm all three commands pass on `main`.
- Ensure the temporary branch `visual-smoke-failure-acceptance-test` is not proposed for merge.
- Either delete the temporary branch after evidence is no longer needed, or keep it clearly documented as a do-not-merge evidence branch.
- If writing Trellis/session notes, keep them lightweight and safe: branch names, command names, pass/fail status, and no artifact contents or secrets.

## Acceptance Criteria

- [ ] On `main`, `frontend/tests/visual/admin-login-theme-smoke.spec.ts` contains `const DARK_BG_RGB = 'rgb(20, 20, 20)'`.
- [ ] On `main`, no source file contains the intentional failing value `DARK_BG_RGB = 'rgb(30, 20, 20)'`.
- [ ] `cmd /c npm run typecheck` passes from `frontend/` on `main`.
- [ ] `cmd /c npm run build` passes from `frontend/` on `main`.
- [ ] `cmd /c npm run test:visual:ci` passes from `frontend/` on `main`.
- [ ] The temporary branch is deleted, or explicitly documented as do-not-merge.
- [ ] No backend/API/DB/RAG/Docker/product implementation files are modified.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | `main` has `rgb(20, 20, 20)`, all frontend validation commands pass, and the temporary branch is deleted or clearly marked do-not-merge. |
| Base | `main` is clean and passing, but the temporary branch is retained for traceability with a clear do-not-merge note. |
| Bad | `main` contains `rgb(30, 20, 20)`, visual smoke fails unexpectedly, or the temporary branch is prepared for merge. |

## Validation / Error Matrix

| Check | Expected | If it fails |
|---|---|---|
| Search `main` for `DARK_BG_RGB = 'rgb(30, 20, 20)'` | No match | Stop; inspect how the temporary assertion reached `main`; restore `rgb(20, 20, 20)` only in the visual smoke test if needed. |
| Search `main` for `DARK_BG_RGB = 'rgb(20, 20, 20)'` | Match in `frontend/tests/visual/admin-login-theme-smoke.spec.ts` | Stop; compare against frontend quality spec and restore the baseline. |
| `cmd /c npm run typecheck` | Exit 0 | Fix only directly related frontend type issues caused by the cleanup, if any. |
| `cmd /c npm run build` | Exit 0 | Fix only directly related frontend build issues caused by the cleanup, if any. |
| `cmd /c npm run test:visual:ci` | Exit 0 with 3 Chromium tests passing | Investigate visual-smoke baseline root cause; do not weaken assertions or add silent fallbacks. |
| Branch hygiene | Temporary branch is deleted or marked do-not-merge | Do not open PR or merge the temporary branch. |

## Expected Files To Modify

- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`: only if `main` unexpectedly contains the temporary failing value.
- Trellis task/context files under `.trellis/tasks/06-06-frontend-visual-smoke-temp-branch-cleanup-main-baseline/`.
- Optional lightweight journal/session note only after validation evidence exists.

## Explicitly Out Of Scope

- No backend changes.
- No API, DTO, command payload, migration, storage, AI provider, or permission changes.
- No CI workflow changes unless the existing contract is proven broken independently of the temporary assertion.
- No broad frontend redesign, style refactor, new visual tests, or assertion weakening.
- No artifact content capture; evidence must stay to command status and safe file/path summaries.

## Required Tests

Run from `frontend/` on `main`:

```bat
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual:ci
```

Optional hygiene checks:

```powershell
git grep "DARK_BG_RGB = 'rgb(30, 20, 20)'" main -- frontend/tests/visual/admin-login-theme-smoke.spec.ts
git grep "DARK_BG_RGB = 'rgb(20, 20, 20)'" main -- frontend/tests/visual/admin-login-theme-smoke.spec.ts
```

## Handoff Notes

DeepSeek should start by preserving or safely parking the current temporary branch state, then switch to `main` for all baseline checks. If any implementation edit is needed, keep it limited to restoring the visual-smoke constant in `frontend/tests/visual/admin-login-theme-smoke.spec.ts`; otherwise, this task should be validation and branch hygiene only.
