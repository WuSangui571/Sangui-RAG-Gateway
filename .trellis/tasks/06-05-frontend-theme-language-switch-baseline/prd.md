# Frontend Theme and Language Switch Baseline

## Goal

Move the admin console from English-only/light-only usable UI to a maintainable frontend experience baseline:

- Default to dark mode with a visible light/dark switch.
- Default to Simplified Chinese with English preserved as a switchable language.
- Keep theme and language preferences local to the browser and independent from backend state, account state, and any secret state.
- Establish a lightweight i18n pattern while the frontend surface area is still small.

## Task Classification

Complex Task.

Reason: this is frontend-only and does not change backend/API/database/infra contracts, but it cuts horizontally across global providers, layout navigation, shared domain components, all admin pages, validation messages, status guidance, and manual visual regression checks.

## Scope

### In Scope

- Add an app-level frontend UI preference provider or equivalent near `App.tsx` / `AdminShell`.
- Configure Ant Design theme tokens and algorithms through `ConfigProvider`.
- Default theme mode to dark.
- Add a visible theme switch for `dark` / `light`.
- Persist theme preference in `localStorage`.
- Add a lightweight typed i18n dictionary for `zh-CN` and `en-US`.
- Default language to Simplified Chinese.
- Add a visible language switch for Simplified Chinese / English.
- Persist language preference in `localStorage`.
- Cover navigation, shell controls, primary buttons, form labels, validation messages, modal/drawer titles, status guidance, error headings, and Smoke/readiness copy.
- Preserve current safe-evidence behavior in Smoke and Request Logs pages.
- Preserve backend error codes in display where useful; localize surrounding frontend explanation only.
- Check dark-mode readability for Smoke, Apps, Model Config, Knowledge, API Keys, and Request Logs pages.

### Out of Scope / Forbidden

- Do not change backend Java code.
- Do not change `/v1/*` gateway behavior.
- Do not change admin API routes, DTOs, payload fields, response fields, or backend error codes.
- Do not change database schema or migrations.
- Do not introduce account-level or server-side user preference storage.
- Do not persist full API keys, upstream API keys, prompts, provider responses, chunk content, or other secret/sensitive values.
- Do not translate backend `error.code` values themselves; show code as-is with optional localized frontend context.
- Do not add a heavy i18n framework unless implementation research proves the current frontend already needs it.
- Do not broaden the UI into a chat playground, low-code workflow, agent, or marketplace surface.

## Requirements

### Theme Baseline

- Default mode is `dark` on first load.
- User can switch between `dark` and `light` from the admin shell.
- Preference survives page refresh through a non-secret localStorage key.
- Ant Design components receive the active theme via `ConfigProvider`.
- Shell, page content, tables, cards, tags, inputs, modals, drawers, descriptions, upload controls, and alerts remain readable in both modes.
- Existing custom CSS is updated only as needed for body/root/page background compatibility with theme tokens.

### Language Baseline

- Default locale is Simplified Chinese on first load.
- User can switch between Simplified Chinese and English from the admin shell.
- Preference survives page refresh through a non-secret localStorage key.
- Current English copy is preserved in the English dictionary.
- Chinese dictionary becomes the default display source.
- Dictionary access is typed enough that missing keys are caught during `npm run typecheck` or made obvious during review.
- Localize frontend-owned text: navigation, buttons, form labels, placeholders, validation messages, modal/drawer titles, alert titles, empty/error/loading states, status guidance, Smoke/readiness headings, and request-log safe-evidence labels.
- Keep domain status enum values and backend error codes stable; translated labels may be shown around them only if the original value remains available where useful for troubleshooting.

### Secret and Safety Requirements

- Full generated app API keys remain memory-only and are still cleared by existing modal/page lifecycle.
- No localStorage/sessionStorage use for generated API keys, upstream keys, prompts, request bodies, request logs, hit chunk summaries, or admin API responses.
- Smoke page must continue showing safe evidence only and must not render assistant answer text, plaintext keys, prompt content, provider bodies, embeddings, storage paths, stack traces, or full chunk content.
- Request Logs page and drawer must continue using backend safe fields only.

## Acceptance Criteria

- [ ] First fresh browser load shows Simplified Chinese text and dark mode.
- [ ] Theme switch changes Ant Design theme and custom page surfaces without requiring reload.
- [ ] Language switch changes visible frontend copy without requiring reload.
- [ ] Refresh keeps selected theme and language.
- [ ] English mode preserves the current English UI meaning.
- [ ] Smoke page readiness, non-streaming, streaming, request-log, and revoked-key sections remain safe-evidence-only.
- [ ] Apps, Model Config, Knowledge, API Keys, Request Logs, and Smoke pages are readable in dark mode.
- [ ] Tables, cards, tags, inputs, modals, drawers, descriptions, upload controls, and alerts are readable in dark and light modes.
- [ ] TypeScript check passes.
- [ ] Production build passes.

## API / Command / Payload Contract

No backend API, `/v1/*`, admin route, DTO, payload field, response field, database, infra, or command contract changes are allowed.

Frontend-only command contract:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Manual browser check:

```text
Open the frontend app, connect with an Admin User ID, switch theme/language, refresh, and inspect all main pages.
```

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Fresh browser with no preference keys | Dark mode + Simplified Chinese | UI after first load |
| Theme changed to light | Light Ant Design tokens and shell/page backgrounds apply immediately | Admin shell and page content |
| Theme changed back to dark | Dark tokens apply immediately; tables/cards/tags/inputs/modals/drawers readable | All main pages |
| Language changed to English | Existing English meaning appears without layout breakage | Navigation/pages/modals |
| Language changed to Chinese | Chinese copy appears without layout breakage | Navigation/pages/modals |
| Browser refresh after changes | Last selected theme/language restored | localStorage-backed preferences |
| Backend returns an API error | Frontend may localize heading/context, backend code remains visible where displayed | Alert/error sections |
| Smoke safe evidence shown | Counts/IDs/statuses shown only; no answer text or secrets introduced | Smoke page |
| API key one-time dialog closes | Plaintext key is still cleared from React state; not persisted | API Keys page/modal |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | A user opens the admin console, sees Chinese dark-mode UI, connects with an Admin User ID, switches to English/light, refreshes, and sees the same preferences. All configured admin pages remain readable and smoke evidence stays safe. |
| Base | A developer runs only the automated frontend checks after implementation: `cmd /c npm run typecheck` and `cmd /c npm run build`; both pass and no backend code changes exist. |
| Bad | Theme/language preferences persist secrets or server data; backend error codes are replaced; `/v1/*` or admin API contracts change; dark mode leaves tables/modals unreadable; Smoke starts rendering answer text, prompts, chunk content, or API keys. |

## Required Tests and Assertion Points

Automated:

- `cd frontend; cmd /c npm run typecheck`
- `cd frontend; cmd /c npm run build`

Manual browser:

- Fresh load defaults: Chinese + dark.
- Theme persists after refresh.
- Language persists after refresh.
- Switch Chinese/English on:
  - Model Config
  - Knowledge
  - Apps
  - API Keys
  - Smoke
  - Request Logs
- Verify modal/drawer readability:
  - API key creation one-time secret modal
  - App bind modals
  - Model Config create/edit modals
  - Knowledge create/upload surfaces
  - Request Log detail drawer
- Verify safe evidence boundaries on Smoke and Request Logs pages.

## Files Likely To Modify

- `frontend/src/main.tsx` or `frontend/src/App.tsx`: app-level provider wiring.
- `frontend/src/components/layout/AdminShell.tsx`: shell controls, navigation labels, theme/language switch UI.
- `frontend/src/styles/index.css`: theme-compatible base background/body styling.
- New lightweight i18n/preference files under one of:
  - `frontend/src/app/providers/`
  - `frontend/src/app/i18n/`
  - `frontend/src/hooks/`
  - `frontend/src/types/`
- Shared domain components:
  - `frontend/src/components/domain/StatusTag.tsx`
  - `frontend/src/components/domain/RequestLogStatusTag.tsx`
  - `frontend/src/components/domain/ApiKeyOneTimeSecret.tsx`
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
  - `frontend/src/components/domain/HitChunksPanel.tsx`
- Main pages:
  - `frontend/src/pages/model-configs/ModelConfigPage.tsx`
  - `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
  - `frontend/src/pages/apps/AppConfigPage.tsx`
  - `frontend/src/pages/api-keys/ApiKeyPage.tsx`
  - `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`

## Implementation Notes For DeepSeek

- Prefer a small typed dictionary and `useI18n()` hook/context over introducing a full i18n library.
- Keep dictionary keys stable and domain-oriented; avoid inline ad-hoc translations spread across pages.
- Consider a single UI preferences provider that owns `themeMode`, `locale`, setters, and localStorage hydration.
- Use Ant Design 5 `theme.defaultAlgorithm` / `theme.darkAlgorithm` via `ConfigProvider`.
- Keep preference localStorage keys explicit and non-secret, for example `sangui-admin-theme` and `sangui-admin-locale`.
- Keep the first render deterministic enough to avoid broken UI if localStorage contains invalid values; invalid preference values should reset to defaults explicitly.
- Do not swallow API errors. Continue showing safe error messages and codes.
