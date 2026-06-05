# Research: Frontend Theme and Language Switch Baseline

## Current Project State

- Current branch: `main`.
- No active Trellis task before this task was created.
- Previous recorded session in journal: App readiness preflight was manually accepted and committed as `eae2d6e feat:app-readiness-preflight`.
- Existing uncommitted changes are Trellis archival/workspace updates from the prior task, including the moved archived task directory and updated workspace journal/index.
- This planning task must not clean up, stage, commit, or rewrite the prior archival changes.

## Task Scope Judgment

Complex Task.

This is frontend-only and avoids backend/API/DB/infra/RAG changes, but it crosses the app shell, global UI preference state, Ant Design theming, shared domain components, every main admin page, and a large amount of user-facing copy.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, lightweight admin console scope, secret handling, safe evidence, request-log/readiness contracts.
- `.trellis/spec/frontend/index.md`: frontend pre-development checklist and admin-console product direction.
- `.trellis/spec/frontend/directory-structure.md`: provider, hooks, components, pages, types, and style placement.
- `.trellis/spec/frontend/component-guidelines.md`: admin UI composition, explicit states, modal/table/status component rules, accessibility.
- `.trellis/spec/frontend/state-management.md`: local/global/server state boundaries and secret-state rules.
- `.trellis/spec/frontend/type-safety.md`: explicit TypeScript contracts and status unions.
- `.trellis/spec/frontend/hook-guidelines.md`: hook/provider responsibilities and side-effect boundaries.
- `.trellis/spec/frontend/quality-guidelines.md`: frontend validation, secret safety, visual design, and completion checklist.
- `.trellis/spec/guides/index.md`: confirms project spec is always required and cross-layer specs are triggered only for API/RAG/security/data boundary changes.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: avoid duplicated text/status/theme logic.

## Code Patterns Found

- React + Ant Design 5 + Vite frontend; scripts are `dev`, `typecheck`, `build`, `preview`.
- No existing `ConfigProvider`, theme provider, locale provider, i18n dictionary, or localStorage preference helper.
- `frontend/src/main.tsx` renders `<App />` directly under `StrictMode`.
- `frontend/src/App.tsx` delegates page selection to `AdminShell`.
- `frontend/src/components/layout/AdminShell.tsx` owns:
  - `adminUserId` in memory.
  - `selectedAppId` in memory.
  - current page state.
  - menu labels in `PAGE_LABELS`.
  - current light-only shell styling through inline `#fff` and `Sider theme="light"`.
- `frontend/src/styles/index.css` hardcodes `body` background `#f5f5f5`.
- Page text is currently inline English across all pages.
- Shared domain components currently show raw status enum text through Ant Design `Tag`.
- `ApiKeyOneTimeSecret` intentionally keeps plaintext key in component props/state only and disables accidental modal close paths; this must remain memory-only.
- Smoke page already enforces safe evidence by showing content length/counts/IDs/statuses instead of full answer text or secrets.
- Request Log drawer and hit-chunks panel display safe backend fields only; `HitChunksPanel` currently shows bounded summary from backend, not full chunk content.

## Files Likely To Modify

- `frontend/src/main.tsx` or `frontend/src/App.tsx`
- `frontend/src/components/layout/AdminShell.tsx`
- `frontend/src/styles/index.css`
- New provider/i18n files under `frontend/src/app/`, `frontend/src/hooks/`, or `frontend/src/types/`
- `frontend/src/components/domain/StatusTag.tsx`
- `frontend/src/components/domain/RequestLogStatusTag.tsx`
- `frontend/src/components/domain/ApiKeyOneTimeSecret.tsx`
- `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
- `frontend/src/components/domain/HitChunksPanel.tsx`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/pages/api-keys/ApiKeyPage.tsx`
- `frontend/src/pages/smoke/SmokeTestPage.tsx`
- `frontend/src/pages/request-logs/RequestLogListPage.tsx`

## Risk / Boundary Notes

- Do not let UI preference persistence become a generic global store for domain data.
- Do not store full generated API keys, upstream keys, prompts, request logs, hit chunk data, or admin API responses in localStorage.
- Do not translate or remap backend status enum values in API types.
- Do not hide backend error codes that are used for troubleshooting.
- Avoid duplicated dictionary lookups or local hardcoded translations in multiple files.
- Dark mode must account for existing inline white backgrounds in `AdminShell` and page/card custom styles.
- The frontend has no `lint` script; do not claim lint coverage.
- Backend and Maven tests are not required unless implementation unexpectedly changes backend/contracts.

## Required Tests

- `cd frontend; cmd /c npm run typecheck`
- `cd frontend; cmd /c npm run build`
- Browser manual checks:
  - Fresh load defaults to Simplified Chinese + dark.
  - Theme and language persist after refresh.
  - Chinese/English switching does not break layout.
  - Dark mode readability on Model Config, Knowledge, Apps, API Keys, Smoke, and Request Logs.
  - Tables, cards, tags, inputs, modals, drawers, descriptions, upload controls, and alerts remain readable.
  - Smoke and Request Logs remain safe-evidence-only.

## Planning Self-Check

- Acceptance criteria explicit: yes.
- Forbidden modification scope explicit: yes.
- Expected modification files listed: yes.
- Required tests listed: yes.
- Concrete guidelines read, not just indexes: yes.
- Need user confirmation before coding: no; user explicitly requested planning-only handoff.
- API / DB / frontend types / DTO alignment risk: no backend/API/DB/DTO changes allowed; frontend status/API types must remain aligned and unchanged except i18n display wrappers if needed.
