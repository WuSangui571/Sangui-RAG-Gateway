# DeepSeek 执行交接说明

## Current Project State

- Branch: `codex/request-log-page-usability`
- Working tree before implementation: only this Trellis task directory is untracked.
- Current Trellis task: `.trellis/tasks/06-18-request-log-page-app-selector-list-fix`
- PRD: `.trellis/tasks/06-18-request-log-page-app-selector-list-fix/prd.md`
- Context validation: `task.py validate` passed after removing stale `.claude/commands/trellis/*.md` default entries.
- Journal note: `.trellis/workspace/sangui/journal-2.md` is near the 2000-line limit; do not record-session until user confirms manual testing and commit presence.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary; request logs are safe operational metadata, not prompt/output storage by default.
- `.trellis/spec/frontend/type-safety.md`: request-log VO/detail/output preview types; app output capture switch contract; forbidden fields.
- `.trellis/spec/frontend/state-management.md`: selected app/request logs should stay local/server state; do not persist logs/previews/secrets globally.
- `.trellis/spec/frontend/component-guidelines.md`: tables must have loading/empty/error states; request log tables show safe summaries only.
- `.trellis/spec/frontend/quality-guidelines.md`: required frontend lint/test/typecheck/build; RequestLogListPage test coverage expectations.
- `.trellis/spec/backend/database-guidelines.md`: request-log admin list/count query is scoped by `user_id` and `app_id`; malformed JSON evidence should fail visibly.
- `.trellis/spec/backend/logging-guidelines.md`: basic request-log metadata is safe; output preview observability is a higher-sensitivity explicit access surface.
- `.trellis/spec/backend/error-handling.md`: Admin request-log API error matrix; output preview access matrix; app output capture switch matrix.
- `.trellis/spec/gateway/resilience.md`: request-log persistence status/error semantics for gateway requests.
- `.trellis/spec/security/rag-security.md`: tenant/secret/evidence/output preview boundaries.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: cross-layer flow and validation checklist.

## Code Patterns Found

- API key page app selector pattern:
  - `frontend/src/pages/api-keys/ApiKeyPage.tsx`
  - Uses `listApps(undefined)` to load `AppVO[]`.
  - Renders Ant Design `Select` with options `#${app.id} ${app.name}`.
  - Keeps `activeAppId` locally and syncs from `selectedAppId`.
  - Fetches server state only when `activeAppId !== null`.

- Current request-log page issue:
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - No-app state uses manual numeric `Input` and `Connect` button.
  - Uses separate `appId: string` and `submittedAppId: number | null`.
  - `persistentAppId` auto-connect path exists via `App.tsx`, but if no selected app is present the page does not offer app dropdown discovery.
  - `canQuery = submittedAppId !== null && adminUserId !== null`; fetch guard is correct but UX blocks users who do not know the ID.

- Request-log API helper:
  - `frontend/src/api/request-logs.ts`
  - Path is `/admin/apps/${appId}/request-logs`, mapped through central HTTP helper to `/api/admin/...`.
  - Query params: page, page_size, status, error_code, start_time, end_time.
  - No output capture query param exists.

- Backend list query pattern:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - Validates AdminAuthContext, app ownership, pagination/status/time range.
  - Calls `apiRequestLogService.listRequestLogs(userId, appId, query)`.
  - No output-capture gating in controller.
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - `buildFilterWrapper()` filters only `userId`, `appId`, optional `status`, `errorCode`, `startTime`, `endTime`.
  - No `output_capture_status` condition found.

- Detail/output preview boundary:
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
  - Detail renders output metadata (`output_capture_status`, `completion_length`, preview availability/truncation/redaction/expiry).
  - Opens `OutputPreviewModal` only when `output_preview_available`.
  - Normal detail does not fetch/render `output_preview`.

## Files Likely To Modify

Primary frontend:

- `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - Replace manual app ID input/connect flow with app list loading + Ant Design Select aligned with API key page.
  - Prefer `selectedAppId` from `useShell()` and keep selected app in shell context when user selects from request-log page.
  - Do not call `listRequestLogs` until a valid app is selected.
  - Keep filters/pagination reset on app change.
  - Keep basic metadata list independent of `output_capture_status`.

- `frontend/src/__tests__/pages/RequestLogListPage.test.tsx`
  - Mock `../../api/apps` and cover app dropdown loading, app selection, no-app empty state, logs rendering while output capture disabled, API error state, forbidden field absence.

- `frontend/src/app/i18n/dict.ts`
  - Add request-log app selector/no-app/app loading/app load error text in both `zh-CN` and `en-US` if reusing API key strings is not sufficient.

Conditional:

- `frontend/src/api/request-logs.ts`
  - Only if investigation finds parameter mapping mismatch. Do not add output capture filters.

- `frontend/src/types/request-log.ts`
  - Only if existing type mismatch is proven. Do not type forbidden fields.

Backend only if frontend query and mapping are proven correct but logs still fail:

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`

## Risk / Boundary Notes

- Do not treat `output_capture_status = DISABLED` as a reason to hide list rows.
- Do not expose `output_preview` through list or normal detail.
- Do not persist selected app, request logs, output previews, prompts, raw answers, or hit chunk data in localStorage/sessionStorage.
- Do not introduce `X-Admin-User-Id`; Admin APIs use `Authorization: Bearer <admin-jwt>` through `frontend/src/api/http.ts`.
- Do not change gateway request-log persistence unless there is direct evidence that rows are not being persisted.
- Do not add a DB migration or new request-log field for this task without explicit user approval.
- Do not broaden into smoke page simplification, diagnostics redesign, analytics, retention cleanup, or output capture policy changes.

## Required Tests

Always run after frontend implementation:

```powershell
cd frontend
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Run if backend code changes:

```powershell
cd backend
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest,OutputCapturePolicyTest" test
mvn -q -DskipTests compile
```

Always run:

```powershell
git diff --check
python .\.trellis\scripts\task.py validate .trellis\tasks\06-18-request-log-page-app-selector-list-fix
```

Browser smoke after frontend change:

```text
Login -> Request Logs -> app dropdown shown -> select app -> list or empty state -> open detail for metadata-only output status.
```

## Planning Self-Check

- Acceptance criteria are explicit in `prd.md`.
- Forbidden scope is explicit in `prd.md` and this handoff.
- Expected modify files are listed.
- Required tests are listed.
- Specific guideline files were read, not only indexes.
- No open requirement ambiguity found.
- API/DB/frontend DTO alignment: current plan reuses existing APIs and fields; no DB/API field additions planned.
