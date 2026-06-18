# Frontend Quality Guidelines

> The admin console should be reliable, clear, and safe for configuration workflows. Visual polish matters, but operational clarity and secret safety matter more.

## Testing Expectations

### Lint Baseline

Run ESLint with TypeScript and React hooks rules:

```bash
cd frontend
cmd /c npm run lint
```

The lint command (`eslint . --ext .ts,.tsx`) covers TypeScript, React hooks, and unused variable detection. Rules are strict enough to catch regressions without broad style churn. Lint must be run and pass before committing frontend changes.

### Unit/Component Test Baseline

Run unit and component tests with Vitest and React Testing Library:

```bash
cd frontend
cmd /c npm run test
```

Test configuration:
- Runner: Vitest with `jsdom` environment.
- Test location: `frontend/src/**/*.test.{ts,tsx}`.
- Setup file: `frontend/src/test/setup.ts` (imports `@testing-library/jest-dom/vitest` matchers).
- Tests mock typed API client boundaries; no live backend, Docker, provider keys, app API keys, or admin JWTs required.

Coverage scope:
- AdminShell: unauthenticated guard, login success/failure, authenticated navigation across all PageKey menu items.
- RequestLogListPage: unauthenticated guard, no-app-guard, loading/empty/error states, safe-rendering assertions (forbidden fields absent from DOM).
- ModelConfigPage: initial load with typed API client, loading/empty/error states.
- AppConfigPage: initial load with typed API client, loading/empty/error states, output-capture enable confirmation modal, output-capture disable direct path.

What remains visual/e2e/manual:
- Visual theme baseline: `npm run test:visual` (Playwright Chromium).
- Full end-to-end admin workflows (login → create → configure → verify).
- Cross-page workflows such as secret → smoke test.
- Browser-specific layout and responsive behavior.

### Visual Smoke (Automated Browser Baseline)

Run the automated visual smoke baseline for the Admin unauthenticated login page:

```bash
cd frontend
cmd /c npm run test:visual
```

One-time Playwright browser setup (required before first run):

```bash
cd frontend
npx playwright install chromium
```

The visual smoke command starts the Vite dev server through `frontend/scripts/run-visual-smoke.mjs`, opens the unauthenticated Admin login page, and verifies:

- Default dark theme: `body`, `#root`, app frame, and login wrapper have dark-compatible computed backgrounds; viewport edges are not white.
- Light theme after localStorage toggle: all four targets switch to light-compatible backgrounds.
- Dark theme after second localStorage toggle: all targets return to dark-compatible backgrounds with non-white viewport edges.

The test does not depend on backend services. It uses frontend-only runtime and covers the unauthenticated login screen only.

Assertion contract:

| Target | Selector | Dark expected | Light expected |
|---|---|---|---|
| `body` | `body` | `rgb(20, 20, 20)` | `rgb(245, 245, 245)` |
| `#root` | `#root` | `rgb(20, 20, 20)` | `rgb(245, 245, 245)` |
| App frame | `[data-testid="app-frame"]` | `rgb(20, 20, 20)` | `rgb(245, 245, 245)` |
| Login wrapper | `[data-testid="login-wrapper"]` | `rgb(0, 0, 0)` | `rgb(245, 245, 245)` |
| Viewport edges (dark) | `elementFromPoint` at four corners | Not `rgb(255, 255, 255)` | — |

Note: the unauthenticated login page does not expose a theme toggle button. The visual smoke test uses `localStorage` manipulation and page reload to simulate theme switching, which exercises the same `UIPreferenceProvider` mount path that reads persisted theme and applies CSS variables.

Implementation files:

- `frontend/scripts/run-visual-smoke.mjs`
- `frontend/playwright.config.ts`
- `frontend/tests/visual/admin-login-theme-smoke.spec.ts`

### CI Frontend Job Contract

The frontend CI job in `.github/workflows/ci.yml` must run these commands from `frontend/`:

```bash
npm ci
npm run lint
npm run test
npx playwright install chromium
npm run typecheck
npm run build
npm run test:visual:ci
```

- The Playwright browser cache is keyed by `runner.os` and `hashFiles('frontend/package-lock.json')`.
- `frontend/package.json` keeps `test:visual:ci` as a direct delegation to `npm run test:visual`; CI must not introduce a second visual smoke runner.
- `frontend/playwright.config.ts` enables CI retries (`retries: 2`) and disables `only` when `CI` is set.
- The visual smoke test runs against the Chromium project only, matching the local baseline.
- No backend services, Docker images, provider keys, or secrets are required.

### CI Artifact Policy

Git-ignored (never committed, never cached):

```text
frontend/node_modules/
frontend/dist/
frontend/test-results/
frontend/playwright-report/
*.tsbuildinfo
```

CI upload on failure or cancellation only:

```text
frontend/playwright-report/
frontend/test-results/
```

Do not upload on success. Do not upload or cache `.env`, `frontend/dist/`, `frontend/node_modules/`, or secrets.

### Local Command Contract

```bash
cd frontend
cmd /c npm run lint               # ESLint with TypeScript + React hooks
cmd /c npm run test               # Vitest unit/component tests
cmd /c npm run typecheck          # TypeScript check
cmd /c npm run build              # Production build
npx playwright install chromium   # one-time setup for visual smoke
cmd /c npm run test:visual        # start Vite and run Playwright
```

The local command is unchanged and remains independent of backend services.

---

When frontend implementation starts, cover:

```text
login flow
app create/edit flow
knowledge base create flow
document upload and status display
model config form validation
API key one-time display and clearing
API key revoke/disable flow
request log list filters
```

Component-level tests should focus on:

```text
status tags
forms
API key one-time display
document upload panel
request log table
```

End-to-end tests should cover the MVP admin path:

```text
login -> create knowledge base -> upload document -> create app -> create API key
```

## UX Requirements

- Users must always know whether a document is uploaded, parsing, embedding, ready, or failed.
- API key creation must clearly show that the full key is visible only once.
- Disabled/revoked/expired API keys must be visually distinct.
- Model configuration errors must be actionable.
- Retrieval settings should show defaults and bounds.
- Request logs should be filterable enough to debug app usage without exposing sensitive data.

## Security Requirements

- Do not log secrets to console.
- Do not persist full generated API keys.
- Do not show upstream API key plaintext after save.
- Do not render full private document content in logs unless the feature explicitly requires a document preview and the backend authorizes it.
- Mask or omit sensitive fields by default.

## Accessibility

- Form fields need labels.
- Icon-only controls need tooltips/accessible labels.
- Error messages must be visible and associated with relevant fields.
- Status cannot rely on color alone.
- Keyboard navigation should work for dialogs and forms.

## Performance

- Paginate large lists.
- Avoid loading full request logs or full document contents into list pages.
- Poll document status only while needed.
- Avoid repeated API calls caused by unstable dependencies in hooks/composables.
- Keep admin pages responsive during uploads and long-running processing.

## Visual Design

- Build the actual admin experience as the first screen after login.
- Avoid landing-page or marketing-page composition.
- Use dense but organized tables and forms.
- Use cards only for repeated items, modals, and genuinely framed tools.
- Do not nest cards inside cards.
- Keep text within containers on desktop and mobile.

## Completion Checklist

- [ ] Loading, empty, error, and success states are handled.
- [ ] Forms validate before submit and show backend errors.
- [ ] Secret fields are masked or cleared correctly.
- [ ] Status values have explicit UI mappings and fallbacks.
- [ ] API calls use typed client functions.
- [ ] Page behavior matches the lightweight RAG gateway product boundary.

## Forbidden Patterns

- Storing generated full API keys in local storage/session storage.
- Using the admin console as a chat playground-first product instead of a configuration console.
- Building workflow/agent/plugin-marketplace UI for MVP.
- Copying provider-specific assumptions into generic model config UI.
- Rendering complete augmented prompts in request log pages by default.
