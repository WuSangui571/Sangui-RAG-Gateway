# CI Visual Smoke Failure Artifact Acceptance Evidence

## Local Verification (Completed)

| Check | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run build` | PASS |
| `npm run test:visual:ci` | 2 failed, 1 passed (dark theme assertions fail as intended) |
| Failure boundary | Visual smoke test assertion at `admin-login-theme-smoke.spec.ts:16`; `DARK_BG_RGB` changed from `rgb(20, 20, 20)` to `rgb(30, 20, 20)` |
| Unaffected jobs | Typecheck and build pass; light theme test passes |

## Temporary Branch

- Branch: `visual-smoke-failure-acceptance-test`
- Base: `main`
- Change: `frontend/tests/visual/admin-login-theme-smoke.spec.ts`; `DARK_BG_RGB` constant only
- Intent: NOT to be merged to `main`

## CI Evidence (to be filled after push)

| Field | Value |
|---|---|
| GitHub Run ID | [TBD] |
| Frontend Job ID | [TBD] |
| Frontend Job Status | [TBD - expected: failure] |
| Artifact Name | `visual-smoke-results` |
| Artifact Present | [TBD - expected: yes] |

## Artifact Content Allowlist

Expected allowed paths:

- `frontend/playwright-report/` (may be empty with `list` reporter)
- `frontend/test-results/`

## Artifact Content Denylist

Files/paths that MUST NOT appear:

- `.env`
- `node_modules/`
- `dist/`
- `backend/` or backend data
- `knowledge/`, `uploads/`
- `*.tsbuildinfo`
- `Dockerfile*`
- `docker-compose*`
- Secret-like files (keys, tokens, credentials)

## Safety Conclusion

[TBD - after artifact download and inspection]
