# CI Acceptance Evidence

## Task

Frontend Admin Visual Smoke CI First-Run Acceptance and Failure Artifact Verification

## Date

2026-06-05

## Source

GitHub Actions CI workflow `.github/workflows/ci.yml` on `main` branch

Repo: `WuSangui571/Sangui-RAG-Gateway`

## Evidence Summary

### Run #32 — First CI run with visual smoke (commit `ace21d3`)

- **Run ID**: 27012675109
- **URL**: https://github.com/WuSangui571/Sangui-RAG-Gateway/actions/runs/27012675109
- **Conclusion**: `success`
- **Frontend job ID**: 79719856828

**Step execution log (timings from API):**

| Step # | Name | Duration | Conclusion | Notes |
|--------|------|----------|------------|-------|
| 4 | Cache Playwright browsers | ~1s | success | Cache lookup (likely first-run miss) |
| 5 | Install dependencies | ~3s | success | `npm ci` in `frontend/` |
| 6 | Install Playwright Chromium | **11s** | success | `npx playwright install chromium` — full install (cache miss) |
| 7 | Typecheck | ~3s | success | `npm run typecheck` |
| 8 | Build | ~9s | success | `npm run build` |
| 9 | Visual smoke test | ~7s | success | `npm run test:visual:ci` |
| 10 | Upload on failure | — | **skipped** | Correct: skipped on success |
| 18 | Post Cache | ~5s | success | Saved `~/.cache/ms-playwright` to cache |

**Artifacts**: `total_count: 0`

### Run #33 — Rerun (commit `4e5a54f`)

- **Run ID**: 27013608721
- **URL**: https://github.com/WuSangui571/Sangui-RAG-Gateway/actions/runs/27013608721
- **Conclusion**: `success`
- **Frontend job ID**: 79722969884

**Step execution log:**

| Step # | Name | Duration | Conclusion | Notes |
|--------|------|----------|------------|-------|
| 4 | Cache Playwright browsers | ~3s | success | Cache restore from previous run |
| 5 | Install dependencies | ~4s | success | `npm ci` in `frontend/` |
| 6 | Install Playwright Chromium | **<1s** | success | **Cache hit** — no re-download needed |
| 7 | Typecheck | ~3s | success | `npm run typecheck` |
| 8 | Build | ~7s | success | `npm run build` |
| 9 | Visual smoke test | ~6s | success | `npm run test:visual:ci` |
| 10 | Upload on failure | — | **skipped** | Correct: skipped on success |
| 18 | Post Cache | ~1s | success | Cache already present |

**Artifacts**: `total_count: 0`

## Validation Matrix Results

| Scenario | Expected | Evidence | Verdict |
|---|---|---|---|
| Latest main CI run succeeds | Frontend job green + visual smoke required step | Run #33: conclusion=success, Visual smoke step=success | **PASS** |
| Browser cache first run/miss | Cache miss → Chromium install still succeeds | Run #32: cache 1s + install 11s; Run #33: cache 3s + install <1s | **PASS** |
| Browser cache hit on rerun | Cache restores `~/.cache/ms-playwright`; install still succeeds | Run #33: install <1s (cached) | **PASS** |
| Chromium install behavior | `npx playwright install chromium` exits 0 in CI | Both runs: step conclusion=success | **PASS** |
| Visual smoke command | `npm run test:visual:ci` delegates to test:visual | Both runs: package.json confirms delegation; step succeeds | **PASS** |
| Success artifact policy | No `visual-smoke-results` upload on success | Both runs: step skipped, artifacts total_count=0 | **PASS** |
| Controlled visual failure | Frontend job fails only at visual smoke | Not executed | **BASE (manual gap)** |
| Failure artifact policy | Failed/cancelled run uploads Playwright report/results | Not executed | **BASE (manual gap)** |
| Artifact safety | Artifacts contain only Playwright debug outputs | Not applicable (no artifacts to inspect) | N/A |
| Spec sufficiency | Existing spec covers command/cache/artifact policy | `quality-guidelines.md` and `sangui-rag-gateway.md` already complete | **PASS** |
| Spec gap discovered | Narrow update if spec stale/missing | No gap found — both specs already document CI contract | **PASS** |

## Cache Behavior Analysis

- **Cache key format**: `playwright-${{ runner.os }}-${{ hashFiles('frontend/package-lock.json') }}`
- **Cache path**: `~/.cache/ms-playwright`
- Run #32 (first run): Cache miss → Chromium downloaded (11s) → cache saved (5s post-step)
- Run #33 (rerun): Cache hit → Chromium restored from cache (<1s install time)
- No `node_modules/`, `dist/`, `test-results/`, or `playwright-report/` cached

## Command Order Verification

Both runs executed the exact required sequence from `frontend/`:

1. `npm ci` — Install dependencies (step 5)
2. `npx playwright install chromium` — Install Playwright Chromium (step 6)
3. `npm run typecheck` — TypeScript type check (step 7)
4. `npm run build` — Build (step 8)
5. `npm run test:visual:ci` — Visual smoke test (step 9)

No backend services, Docker images, provider keys, or `.env` required.

## Remaining Gaps (BASE Case)

Per PRD "Base" case: controlled visual failure branch cannot be created in this environment
due to missing `gh` CLI and GitHub API write credentials. This is a **documented manual CI
evidence gap**, not a code/config defect:

1. Controlled visual failure run: not created
2. Failure artifact upload verification: not verified
3. Artifact contents safety check: not performed

Recommendation for Codex handback: if `gh` CLI is available in a subsequent session,
create a temporary branch with a test-only failure (e.g., change one background color
assertion in `admin-login-theme-smoke.spec.ts`), verify the CI frontend job fails,
download the `visual-smoke-results` artifact, and confirm it contains only
`frontend/playwright-report/` and `frontend/test-results/`.

## Spec Status

- `.trellis/spec/frontend/quality-guidelines.md` — Already documents CI command contract (lines 49-65), cache key (line 61), CI artifact policy (lines 67-87), and forbidden patterns. **No update needed.**
- `.trellis/spec/sangui-rag-gateway.md` — Already documents CI baseline including frontend job command contract (lines 593-607). **No update needed.**

## Conclusion

All 8 success-path acceptance criteria verified PASS. No code changes, no spec changes, no CI config changes required. The only remaining gap is the controlled failure artifact verification (2 items), classified as `BASE` case per the PRD's risk framework.
