# Frontend Quality Baseline

## Goal

Establish a reproducible frontend quality baseline for the Sangui-RAG-Gateway admin console before adding larger UI features such as Metrics Dashboard or Setup Wizard.

The task should make frontend regressions cheaper to catch by adding explicit lint and unit/component-test gates, covering the current shell/page quality contracts, and documenting the commands in `.trellis/spec/frontend/`.

## Scope Classification

Complex Task.

Reasons:

- Touches frontend build scripts, dev dependencies, test runner configuration, CI, component/page tests, and frontend specs.
- Covers multiple existing admin workflows: route/shell navigation, request logs, model configs, and app config.
- Establishes quality contracts for future frontend work rather than changing one isolated page.

## Current Project State

- Current branch: `feature/frontend-quality-baseline`.
- Working directory was clean at planning start.
- No active Trellis task existed before this task was created.
- The previous recorded workspace session closed out Streaming Robustness on `2026-06-18`.
- Pre-handoff Codex fix: legacy CI failure
  `OpenAiChatCompletionsControllerTest.shouldRecordFailureAndReleaseReservationOnceWhenStreamFailsAfterReady`
  was repaired by making streaming responses set `text/event-stream` after upstream readiness and before returning
  the `SseEmitter`. DeepSeek should preserve that backend fix and continue with the frontend quality baseline.
- Existing frontend checks:
  - `npm run typecheck`
  - `npm run build`
  - `npm run test:visual`
  - `npm run test:visual:ci`
- Existing gaps:
  - No `npm run lint`.
  - No unit/component test runner such as Vitest.
  - Visual smoke covers only the unauthenticated login theme baseline.
  - Route/shell guard, page empty/error/loading conventions, and sensitive request-log rendering are not covered by automated unit/component tests.

## Requirements

1. Add a frontend lint baseline.
   - Add an executable `npm run lint` command.
   - Prefer the Vite/React/TypeScript ecosystem default: ESLint with TypeScript and React hooks support.
   - Keep rules strict enough to prevent obvious regressions, but do not introduce broad style churn.
   - Lint must fail on unsafe or unused code patterns already covered by TypeScript where applicable.
   - Do not add Prettier or broad formatting rewrites in this task.

2. Add a frontend unit/component test baseline.
   - Add a reproducible `npm run test` command.
   - Prefer Vitest plus React Testing Library for component/page tests.
   - Test setup may include `jsdom` and a focused `test/setup` file if needed.
   - Tests should mock typed API client boundaries rather than backend servers.
   - Tests must not rely on live backend, Docker, provider keys, app API keys, or admin JWTs.

3. Cover shell/route quality.
   - Cover unauthenticated guard behavior: admin shell shows login screen before authentication.
   - Cover successful login path enough to prove authenticated shell navigation is available.
   - Cover navigation to at least the existing key pages through the `PageKey` menu path.
   - Do not introduce `react-router` or URL routing in this task unless a clear existing requirement is found. Current route contract is `AdminShell` + `PageKey` state.

4. Cover representative page contracts.
   - `RequestLogListPage`:
     - unauthenticated/no-app guard state.
     - persistent selected app path if `persistentAppId` is supplied.
     - loading/error/empty behavior through mocked `listRequestLogs`.
     - safe rendering expectations: request-log list must not render forbidden fields such as prompts, messages, raw provider bodies, keys, embeddings, stack traces, storage paths, raw SSE, or output preview content.
   - `ModelConfigPage`:
     - initial load uses typed API client.
     - empty/error/loading state is visible.
     - check buttons or check-result modal path have at least one baseline test if low-risk to implement.
     - capability contract remains `CHAT | EMBEDDING`; do not revive `CHAT_EMBEDDING`.
   - `AppConfigPage`:
     - initial load uses typed API client.
     - empty/error/loading state is visible.
     - enabling request-log output capture requires the existing explicit confirmation modal.
     - disabling output capture remains direct.

5. Update frontend quality spec.
   - Update `.trellis/spec/frontend/quality-guidelines.md` with the new local and CI command contract.
   - If test folder/config conventions are added, update `.trellis/spec/frontend/directory-structure.md` or another frontend spec file only where useful.
   - Document what the unit/component baseline covers and what remains visual/e2e/manual.

6. Update CI.
   - Add lint and unit/component test steps to the frontend CI job.
   - Keep existing typecheck, build, visual smoke, Playwright cache, and failure artifact policy.
   - CI must not require backend services for frontend unit/component tests.

## Non-Goals / Forbidden Scope

- Do not change backend Java code.
- Do not change backend API routes, payload fields, DTO/VO names, DB schema, migrations, auth, permissions, RAG retrieval, gateway streaming, rate limits, or storage behavior.
- Do not add metrics dashboard, setup wizard, product redesign, new admin workflows, or URL routing.
- Do not rewrite existing pages into hooks or a new state-management architecture unless a very small extraction is required to make tests possible.
- Do not weaken TypeScript strictness to make tests pass.
- Do not snapshot-test large Ant Design DOM trees as the primary assertion strategy.
- Do not persist or render secrets, prompts, document content, raw provider bodies, stack traces, embeddings, storage paths, raw SSE payloads, or output preview content outside the explicit output preview access surface.

## Command / Script Contract

Expected frontend scripts after implementation:

```json
{
  "lint": "<eslint command>",
  "test": "<vitest command>",
  "typecheck": "tsc -b --noEmit",
  "build": "tsc -b && vite build",
  "test:visual": "node ./scripts/run-visual-smoke.mjs",
  "test:visual:ci": "npm run test:visual"
}
```

Recommended local verification order:

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual
```

Repository-level whitespace check:

```bash
git diff --check
```

If new packages are added, update and commit `frontend/package-lock.json`.

## API / Payload / DTO Contract

No backend API or DTO changes are expected.

Frontend tests may mock these existing typed API client functions:

```text
frontend/src/api/auth.ts
frontend/src/api/apps.ts
frontend/src/api/model-configs.ts
frontend/src/api/request-logs.ts
frontend/src/api/http.ts
```

Mocked payloads must match existing frontend types:

```text
AdminLoginVO
AdminUserVO
AppVO
ModelConfigVO
ApiRequestLogVO
ApiRequestLogPageVO
ApiRequestLogDetailVO
```

Tests must keep status literals aligned with existing unions:

```text
AppStatus = ENABLED | DISABLED
ModelConfigCapability = CHAT | EMBEDDING
ModelConfigStatus = ENABLED | DISABLED
RequestLog status = success | failure | cancelled
OutputCaptureStatus = DISABLED | CAPTURED | EMPTY | TRUNCATED_ONLY | REDACTED | REDACTION_BLOCKED | STREAMING_UNSUPPORTED | FAILED | EXPIRED
```

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| `npm run lint` on clean implementation | exits 0 | frontend lint command |
| `npm run test` on clean implementation | exits 0 and runs deterministic unit/component tests | frontend test command |
| Unit tests without backend service | pass with mocked typed API clients | no live HTTP dependency |
| Unauthenticated shell | login screen is shown; admin pages are not visible | `AdminShell`/`App` test |
| Login API returns 401 | visible login error; auth token cleared | `AdminShell` test |
| Authenticated navigation | menu can switch to apps, model configs, request logs, API keys, knowledge, smoke pages | shell route baseline test |
| Request-log list API returns empty page | table empty state shown, no unsafe fields rendered | request-log page test |
| Request-log list API throws safe error | alert/retry state shown; stale rows cleared | request-log page test |
| Request-log mock accidentally contains forbidden secret-like fields | test asserts forbidden strings are absent from rendered DOM | request-log security rendering test |
| Model-config list API returns empty list | empty state shown | model-config page test |
| Model-config list API throws | error alert shown; stale rows cleared | model-config page test |
| App output capture switch false -> true | confirmation modal appears before API mutation | app page test |
| App output capture switch true -> false | direct API mutation path remains available | app page test |
| CI frontend job | installs deps, runs lint, test, typecheck, build, visual smoke | `.github/workflows/ci.yml` |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Fresh checkout with Node 20 runs `npm ci`, `npm run lint`, `npm run test`, `npm run typecheck`, `npm run build`, and `npm run test:visual:ci` from `frontend/`; unit tests mock API clients and cover shell navigation plus request-log/model-config/app representative states; specs document the new command contract. |
| Base | Developer without Playwright browsers can still run `lint`, `test`, `typecheck`, and `build`; `test:visual` remains the only command requiring the one-time Chromium install. Unit/component tests do not require backend services. |
| Bad | Lint is added but not wired to CI; tests require a live backend or secrets; tests assert implementation details instead of user-visible states; quality spec still mentions only typecheck/build/visual; forbidden request-log fields or output preview content are rendered by normal list/detail surfaces without a failing test. |

## Acceptance Criteria

- [ ] `frontend/package.json` exposes `lint` and `test` scripts.
- [ ] Frontend dependencies and lockfile include only the tooling needed for lint and unit/component tests.
- [ ] ESLint config is scoped to the frontend and compatible with TypeScript/React/Vite.
- [ ] Unit/component test config is scoped to the frontend and uses jsdom/browser-like DOM only where needed.
- [ ] At least one shell/navigation test covers unauthenticated guard and authenticated navigation.
- [ ] `RequestLogListPage` has tests for no-app/empty/error/loading or equivalent representative states, including safe-rendering assertions.
- [ ] `ModelConfigPage` has tests for empty/error/loading or equivalent representative states.
- [ ] `AppConfigPage` has tests for empty/error/loading and output-capture confirmation behavior.
- [ ] `.github/workflows/ci.yml` runs frontend lint and unit/component tests before typecheck/build/visual smoke.
- [ ] `.trellis/spec/frontend/quality-guidelines.md` documents lint and unit/component test commands and coverage boundaries.
- [ ] No backend code, DB migrations, API contracts, or auth/permission logic changed.
- [ ] All required verification commands pass or any environment-only failure is documented with exact reason.

## Required Tests

Run after implementation:

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual
cd ..
git diff --check
```

If dependencies need installation or browser setup:

```bash
cd frontend
cmd /c npm install
npx playwright install chromium
```

Do not run backend Maven tests for this task unless implementation unexpectedly touches backend files. If backend files are touched, stop and re-scope before continuing.

## Expected Files To Modify

Likely:

```text
frontend/package.json
frontend/package-lock.json
frontend/eslint.config.js or frontend/eslint.config.mjs
frontend/vitest.config.ts or frontend/vite.config.ts
frontend/src/test/setup.ts or frontend/tests/setup.ts
frontend/src/**/*.test.tsx or frontend/tests/**/*.test.tsx
.github/workflows/ci.yml
.trellis/spec/frontend/quality-guidelines.md
```

Representative test targets:

```text
frontend/src/App.tsx
frontend/src/components/layout/AdminShell.tsx
frontend/src/pages/request-logs/RequestLogListPage.tsx
frontend/src/pages/model-configs/ModelConfigPage.tsx
frontend/src/pages/apps/AppConfigPage.tsx
frontend/src/api/http.ts
frontend/src/app/i18n/dict.ts
```

Do not modify unless a testability issue is found and the change is small:

```text
backend/**
deploy/**
database migrations
frontend production page behavior beyond small testability fixes
```

## Planning Self-Check

- Acceptance criteria: defined above.
- Forbidden scope: backend/API/DB/auth/RAG/storage/new UI features are out of scope.
- Expected files: listed above.
- Required tests: listed above.
- Concrete guidelines read: project spec, frontend index and specific frontend guideline files, shared guides, security/logging/error/gateway specs relevant to request-log safety.
- Open questions: none requiring user confirmation before handoff. The implementation should use conservative defaults and existing React/Vite patterns.
- API/DB/frontend DTO alignment: no API/DB changes expected; frontend tests must use existing type unions and API client shapes.
