# App Output Capture Switch Management

## Goal

Expose the existing app-level request-log output capture switch to app owners through the Admin API and Admin frontend, completing the V1 operability loop for bounded output preview capture.

The previous request-log output observability task already added:

- `rag_app.request_log_output_capture_enabled`
- `AppEntity.requestLogOutputCaptureEnabled`
- effective capture policy: global `rag.request-log.output-capture.enabled` AND app-level switch
- explicit audited preview access endpoint

This task must not redesign output capture. It only makes the existing app-level switch manageable by the app owner.

## Classification

Complex Task.

Reason: the change spans backend Admin API, DTO/VO contracts, service ownership checks, frontend API/types/UI/i18n, security boundaries, tests, and spec updates. It does not require a new database table or a new capture policy.

## Requirements

- Backend exposes `request_log_output_capture_enabled` in app Admin API responses.
- Backend provides an owner-only mutation path for the app-level output capture switch.
- New apps remain default-off unless the request explicitly opts in.
- Frontend App management page displays the current switch state.
- Frontend App management page allows changing the switch with an explicit risk warning before enabling.
- Frontend types and API client match backend snake_case payloads.
- Security boundaries remain unchanged:
  - output preview content is never returned by app list/detail/update responses
  - normal request-log list/detail still returns metadata only
  - preview content remains available only through explicit audited preview access
  - cross-user app mutation returns 403 and must not update the row
- Specs are updated with the app switch API payload, default, errors, and frontend type/UI contract.

## Non-Goals

- Do not add streaming delta output capture.
- Do not change `OutputCapturePolicy` effective rule except tests may assert the app flag still participates in it.
- Do not add new DB tables or migrations.
- Do not store, return, or display full prompts, raw answers outside the explicit preview endpoint, raw SSE, provider bodies, keys, hashes, chunk content, embeddings, stack traces, or environment values.
- Do not introduce role systems or real admin auth; keep the current temporary `X-Admin-User-Id` owner boundary.
- Do not redesign the App page or move unrelated workflows.
- Do not add broad request-log settings, per-key settings, or global output-capture config UI.

## Backend API Contract

### App Response Field

`AppVO` must include:

```json
{
  "request_log_output_capture_enabled": false
}
```

Type: boolean.

Default: `false` for newly created apps and existing rows when the database default applies.

### Preferred Mutation Endpoint

Use a focused endpoint to match the existing app management style:

```http
PUT /api/admin/apps/{appId}/request-log-output-capture
X-Admin-User-Id: <userId>
Content-Type: application/json

{
  "request_log_output_capture_enabled": true
}
```

Success response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 1,
    "user_id": 100,
    "name": "Demo App",
    "status": "ENABLED",
    "default_model_config_id": 10,
    "default_knowledge_base_id": 20,
    "request_log_output_capture_enabled": true,
    "created_at": "...",
    "updated_at": "..."
  }
}
```

If implementation chooses a DTO/VO name, prefer:

```text
UpdateAppOutputCaptureDTO
```

with Java property:

```java
Boolean requestLogOutputCaptureEnabled
```

annotated as needed with:

```java
@JsonProperty("request_log_output_capture_enabled")
```

### Validation / Error Matrix

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global/header validation behavior. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing Spring conversion handling. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | No app lookup or mutation. |
| Malformed JSON body | 400 | `INVALID_REQUEST` | Do not echo request body. |
| Null body | 400 | `INVALID_REQUEST` | No mutation. |
| Missing `request_log_output_capture_enabled` | 400 | `INVALID_REQUEST` | No mutation. |
| Non-boolean value | 400 | `INVALID_REQUEST` | No mutation; handled by JSON conversion or explicit validation. |
| App does not exist | 404 | `NOT_FOUND` | No mutation. |
| App belongs to another user | 403 | `FORBIDDEN` | Generic access denied; no mutation. |
| Owned app, switch `true` | 200 | `OK` | Persist true, update `updated_at`, return AppVO. |
| Owned app, switch `false` | 200 | `OK` | Persist false, update `updated_at`, return AppVO. |

## Frontend Contract

### Types

Update `frontend/src/types/app.ts`:

```ts
export interface AppVO {
  request_log_output_capture_enabled: boolean
}

export interface UpdateAppOutputCaptureDTO {
  request_log_output_capture_enabled: boolean
}
```

### API Client

Add a typed API function in `frontend/src/api/apps.ts`:

```ts
updateAppOutputCapture(
  appId: number,
  dto: UpdateAppOutputCaptureDTO,
  adminUserId: number,
): Promise<ApiResponse<AppVO>>
```

It should call:

```text
PUT /admin/apps/{appId}/request-log-output-capture
```

through existing `apiPut`.

### UI

In `frontend/src/pages/apps/AppConfigPage.tsx`:

- Add a compact column or row action showing the current output capture switch state.
- Use an Ant Design `Switch` or equivalent binary setting control.
- When enabling from false to true, show a confirmation modal with a risk warning.
- When disabling from true to false, allow a direct or lighter confirmation update; disabling should be easy.
- Refresh the app list after success.
- Show API errors through the existing page error surface.
- Keep text in `frontend/src/app/i18n/dict.ts` for both zh-CN and en-US.

Risk warning content must communicate:

- enabling allows bounded, redacted output previews to be captured for this app only when global capture is also enabled
- preview access is still explicit and audited
- this is for operational diagnosis, not full answer storage

Do not render actual output preview content on the App page.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Owner enables the switch for an owned app; AppVO returns `request_log_output_capture_enabled=true`; capture policy can capture only when global config is also enabled; frontend list reflects enabled state with risk warning shown before enabling. |
| Good | Owner disables the switch; AppVO returns false; future requests for that app have effective capture disabled unless re-enabled. |
| Base | New app is created with `request_log_output_capture_enabled=false`; app list/detail show false; request-log output preview remains unavailable unless both global and app switches are enabled. |
| Bad | Cross-user user guesses app ID and changes the switch; must return 403 and not update. |
| Bad | App list/detail/update returns `output_preview`, prompts, messages, raw answers, provider body, keys, hashes, chunk content, embeddings, stack traces, or raw SSE. |
| Bad | Frontend stores output preview content or app switch state in localStorage/sessionStorage/global store. |

## Required Tests And Assertion Points

### Backend

Add/update `AppServiceTest`:

- create app defaults `requestLogOutputCaptureEnabled` to `false` or null-safe false per implementation; prefer setting explicit false in service for deterministic VO.
- owner can set output capture true and false.
- cross-user app returns null/no update from service method.
- missing app returns null/no update.
- update sets `updated_at`.

Add/update `AppAdminControllerTest`:

- App list/detail/create/update include `request_log_output_capture_enabled`.
- `PUT /api/admin/apps/{appId}/request-log-output-capture` accepts true and false.
- null body / missing field returns `400 INVALID_REQUEST`.
- missing app returns `404 NOT_FOUND`.
- cross-user app returns `403 FORBIDDEN`.
- response does not contain forbidden fields: `output_preview`, `prompt`, `messages`, `api_key`, `key_hash`, `authorization`, `upstream_api_key`, `api_key_encrypted`, `chunk_content`, `content`, `embedding`, `provider_response_body`, `stack_trace`, `raw_sse`.

Keep/verify `OutputCapturePolicyTest`:

- effective capture requires global enabled and app switch true.
- default app switch false disables capture.

### Frontend

- `cmd /c npm run typecheck`
- `cmd /c npm run build`

Manual/visual notes if time allows:

- App page renders switch state.
- Enabling shows risk confirmation.
- API failure displays existing error alert.

## Required Commands

Backend commands run from `backend/`:

```bash
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest,OutputCapturePolicyTest" test
mvn -q -DskipTests compile
```

Frontend commands run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

If implementation touches request-log output access code beyond App management, also run:

```bash
mvn -q "-Dtest=ApiRequestLogAdminControllerTest,ApiRequestLogOutputServiceTest,OpenAiChatCompletionsControllerTest" test
```

## Expected Files To Modify

Backend:

- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/vo/AppVO.java`
- `backend/src/main/java/com/sangui/raggateway/app/dto/UpdateAppOutputCaptureDTO.java` (new, or equivalent)
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/OutputCapturePolicyTest.java` (only if assertion strengthening is needed)

Frontend:

- `frontend/src/types/app.ts`
- `frontend/src/api/apps.ts`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/app/i18n/dict.ts`

Specs:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/security/rag-security.md`

## Planning Self-Check

- Acceptance criteria are defined.
- Prohibited scope is defined.
- Expected files are listed.
- Required tests are listed.
- Specific guidelines were read, not only spec indexes.
- No unresolved product question blocks implementation.
- API payload, DB/entity field, frontend types, DTO, VO, and tests are aligned around `request_log_output_capture_enabled`.
