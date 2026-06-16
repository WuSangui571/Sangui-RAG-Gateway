# Output Capture Scheduled Cleanup

## Current Project State

- Branch: `feature/app-output-capture-management`.
- Working directory was clean before task setup.
- No active Trellis task existed before this task.
- Workspace journal records Session 55 `App Output Capture Switch Management` as completed and committed as `0895350d feat:app-output-capture-switch-management`.
- The completed switch task already added/validated:
  - backend `PUT /api/admin/apps/{appId}/request-log-output-capture`
  - `AppVO.request_log_output_capture_enabled`
  - frontend `AppConfigPage` switch with explicit enable warning
  - frontend `AppVO`/`UpdateAppOutputCaptureDTO` and `updateAppOutputCapture`
  - specs for backend/frontend/security output-capture switch boundaries
- Remaining related gap recorded in the journal: output preview cleanup scheduling is still a follow-up candidate.

## Task Classification

Complex Task.

Reason: the original request mentions backend API/DTO/frontend plus scheduled cleanup. Code and journal research show API/DTO/frontend switch management already exists, so implementation should be narrower than the request title. The remaining work still touches backend scheduling, configuration contract, request-log retention behavior, tests, and spec/context alignment. It must be planned before DeepSeek implementation.

## Goal

Complete the request-log output preview retention loop by scheduling the existing `ApiRequestLogService.cleanupExpiredOutputPreviews()` cleanup method behind explicit configuration.

The system should automatically clear expired output previews while preserving request-log rows and safe metadata.

## Requirements

- Keep the already implemented app-level output-capture switch API/UI as baseline; do not reimplement it.
- Add a backend scheduled cleanup trigger for expired output previews.
- Use the existing `rag.request-log.output-capture.cleanup-enabled` as the runtime enable/disable switch.
- Add a concrete schedule configuration field, recommended:
  - `rag.request-log.output-capture.cleanup-fixed-delay-ms`
  - default: `3600000` (1 hour)
  - environment override if following existing config style: `RAG_REQUEST_LOG_OUTPUT_CAPTURE_CLEANUP_FIXED_DELAY_MS`
- The scheduled trigger must call only `ApiRequestLogService.cleanupExpiredOutputPreviews()`.
- When cleanup is disabled, the scheduled method must return without querying/updating request logs.
- Cleanup behavior remains:
  - select rows where `output_retention_expires_at < now` and `output_preview IS NOT NULL`
  - set `output_preview = NULL`
  - set `output_capture_status = 'EXPIRED'`
  - update `updated_at`
  - preserve request-log rows and numeric metadata such as `completion_length`
- Add safe operational logging for scheduled runs using counts and error class only; never log preview content, prompts, messages, keys, provider bodies, or stack traces in client responses.
- Add focused backend tests for scheduler gating and invocation.
- Keep existing cleanup service tests intact.

## Non-Goals

- Do not add or alter database schema unless implementation discovers a real mismatch; current migration already has `output_retention_expires_at` and expiry index.
- Do not change `ApiRequestLogMapper.selectExpiredOutputPreviews()` or `expireOutputPreview()` semantics unless a focused bug is found.
- Do not delete request-log rows.
- Do not change output preview access API, audit API, redaction policy, capture statuses, or preview retention calculations.
- Do not add streaming output preview capture.
- Do not expose `output_preview` in app APIs, request-log list, or normal detail responses.
- Do not add a frontend global output-capture settings page.
- Do not rewrite the App management switch UI unless a type/build failure proves it is broken.
- Do not modify unrelated RAG retrieval, prompt construction, API-key rate limit, admin auth, Docker, Redis, or ingestion behavior.

## Existing API / Payload Baseline

The app-level switch is already implemented and must remain aligned with specs:

```http
PUT /api/admin/apps/{appId}/request-log-output-capture
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "request_log_output_capture_enabled": true
}
```

Response `data` is `AppVO` and includes:

```json
{
  "request_log_output_capture_enabled": true
}
```

Frontend maps this through:

```text
frontend/src/types/app.ts
frontend/src/api/apps.ts
frontend/src/pages/apps/AppConfigPage.tsx
```

This task should verify the baseline during research/check, but implementation should not duplicate the switch work.

## Scheduled Cleanup Contract

### Configuration Fields

Recommended config:

```yaml
rag:
  request-log:
    output-capture:
      cleanup-enabled: true
      cleanup-fixed-delay-ms: 3600000
```

Rules:

| Field | Type | Default | Required behavior |
|---|---:|---:|---|
| `cleanup-enabled` | boolean | `true` | If false, scheduled trigger skips cleanup and does not call the service method. |
| `cleanup-fixed-delay-ms` | long | `3600000` | Fixed delay between scheduled cleanup runs. Must be positive. |

Implementation can bind this through `OutputCaptureProperties` or `@Scheduled(fixedDelayString = "...")`, but there must be one source of truth for the property name.

### Scheduler Shape

Preferred backend shape:

```text
backend/src/main/java/com/sangui/raggateway/log/RequestLogOutputCleanupScheduler.java
```

Responsibilities:

- Own only scheduling/orchestration.
- Inject `ApiRequestLogService` and `OutputCaptureProperties`.
- If cleanup disabled, skip.
- If enabled, call `cleanupExpiredOutputPreviews()`.
- Log safe count metadata on success.
- Let unexpected scheduler errors be visible in logs; do not swallow root causes silently behind fake success.

Scheduling should be enabled via a narrow configuration, for example:

```text
backend/src/main/java/com/sangui/raggateway/common/config/SchedulingConfig.java
```

or `@EnableScheduling` on an existing config/application class if it does not affect tests. Prefer `@Profile("!test")` for the scheduler/config if needed to avoid scheduled background work during unit tests.

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| `cleanup-enabled=true` | Scheduled method invokes `cleanupExpiredOutputPreviews()` once per trigger | Scheduler unit test with mocked service. |
| `cleanup-enabled=false` | Scheduled method returns without invoking service or mapper | Scheduler unit test with mocked service. |
| Expired rows exist | Existing service nulls previews, marks status `EXPIRED`, keeps rows | Existing `ApiRequestLogOutputServiceTest`; keep or strengthen. |
| No expired rows | Service returns `0`; scheduler logs safe count only | Scheduler/service test. |
| Mapper update for one row fails | Service continues attempting remaining rows and returns successful count only | Add/strengthen service test if current behavior is not covered. |
| Non-positive cleanup delay configured | Startup/property validation fails visibly, or the property is explicitly constrained in tests if validation is added | Property binding test if validation is implemented. |
| App switch API/list/detail | Existing behavior remains unchanged; only boolean metadata is exposed | Existing `AppServiceTest`/`AppAdminControllerTest`. |
| Request-log list/detail | Normal APIs still do not expose `output_preview` | Existing `ApiRequestLogAdminControllerTest`. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Global/app capture has produced previews with expired `output_retention_expires_at`; scheduled cleanup is enabled; trigger calls the cleanup service; previews are nulled, status becomes `EXPIRED`, request-log rows and metadata remain. |
| Base | Cleanup scheduling is disabled; expired preview rows remain untouched until manual/service cleanup is run; no background mapper query/update happens. |
| Base | No expired previews exist; scheduled trigger completes safely with count `0`. |
| Bad | Scheduler deletes request logs, clears `completion_length`, exposes preview content in logs, runs while disabled, or duplicates switch/API/frontend behavior unnecessarily. |

## Required Tests And Assertion Points

Backend targeted tests:

```bash
cd backend
mvn -q "-Dtest=ApiRequestLogOutputServiceTest,OutputCapturePolicyTest" test
mvn -q "-Dtest=RequestLogOutputCleanupSchedulerTest" test
mvn -q -DskipTests compile
```

If implementation touches App switch files despite the non-goal, also run:

```bash
cd backend
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest,OutputCapturePolicyTest" test
```

If implementation touches request-log controller/detail/access paths, also run:

```bash
cd backend
mvn -q "-Dtest=ApiRequestLogAdminControllerTest,OpenAiChatCompletionsControllerTest" test
```

Frontend checks are required only if frontend files change. If frontend files remain untouched, no frontend change is expected for this task.

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Required assertion points:

- Scheduler calls cleanup service when enabled.
- Scheduler skips cleanup service when disabled.
- Cleanup service preserves rows and metadata while clearing preview content.
- Cleanup service sets status to `EXPIRED`.
- Logs and responses do not include forbidden fields.
- Existing app switch API/UI tests remain valid if touched.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, request-log output observability contract.
- `.trellis/spec/backend/directory-structure.md`: scheduler/config/service placement and layering.
- `.trellis/spec/backend/database-guidelines.md`: request-log output cleanup contract, DB fields, expiry index, no row deletion.
- `.trellis/spec/backend/error-handling.md`: App switch API contract and output preview access boundaries.
- `.trellis/spec/backend/logging-guidelines.md`: safe output preview observability, config defaults, forbidden log fields.
- `.trellis/spec/backend/quality-guidelines.md`: backend test and review expectations.
- `.trellis/spec/frontend/type-safety.md`: existing App switch frontend contract; use as baseline only.
- `.trellis/spec/security/rag-security.md`: output preview boundary, tenant isolation, forbidden fields.
- `.trellis/spec/gateway/resilience.md`: request-log behavior must remain visible and safe.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: config/API/DTO/test contract alignment.

## Code Patterns Found

- Existing cleanup service method:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - `cleanupExpiredOutputPreviews()` selects expired preview rows and calls mapper update per row.
- Existing cleanup mapper:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java`
  - `selectExpiredOutputPreviews(now)` and `expireOutputPreview(id)`.
- Existing cleanup test:
  - `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogOutputServiceTest.java`
  - `shouldExpireOutputPreviewsWithoutDeletingRequestLogs()`.
- Existing output-capture config binding:
  - `backend/src/main/java/com/sangui/raggateway/log/OutputCaptureProperties.java`
  - currently has `enabled`, `previewMaxChars`, `retentionDays`, `cleanupEnabled`, `reasonMaxChars`.
- Existing config defaults:
  - `backend/src/main/resources/application.yml`
  - currently has `cleanup-enabled: true`, no schedule interval property.
- Existing app switch API/UI baseline:
  - `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - `backend/src/main/java/com/sangui/raggateway/app/vo/AppVO.java`
  - `frontend/src/types/app.ts`
  - `frontend/src/api/apps.ts`
  - `frontend/src/pages/apps/AppConfigPage.tsx`

## Files Likely To Modify

Expected backend files:

- `backend/src/main/java/com/sangui/raggateway/log/OutputCaptureProperties.java`
  - add schedule interval property if implementation chooses typed binding.
- `backend/src/main/resources/application.yml`
  - add `cleanup-fixed-delay-ms` default and optional env override.
- `backend/src/main/java/com/sangui/raggateway/log/RequestLogOutputCleanupScheduler.java`
  - new scheduler/orchestrator class.
- `backend/src/main/java/com/sangui/raggateway/common/config/SchedulingConfig.java`
  - new or existing scheduling enablement location, only if needed.
- `backend/src/test/java/com/sangui/raggateway/log/RequestLogOutputCleanupSchedulerTest.java`
  - new focused scheduler gating/invocation tests.
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogOutputServiceTest.java`
  - optional strengthening for partial mapper update failure.

Expected unchanged unless a concrete mismatch is found:

- `backend/src/main/java/com/sangui/raggateway/app/**`
- `frontend/src/types/app.ts`
- `frontend/src/api/apps.ts`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/app/i18n/dict.ts`
- database migrations

## Risk / Boundary Notes

- `output_preview` is sensitive even when bounded/redacted; cleanup logs must never print it.
- Scheduling must not create hidden fallback behavior. Disabled means no cleanup call.
- Avoid scheduled background work in tests unless a test explicitly invokes the scheduler method.
- Keep scheduler class thin. Cleanup semantics already belong in `ApiRequestLogService`.
- Do not broaden cleanup to delete audit rows or request-log rows.
- Existing `ApiRequestLogService` is `@Profile("!test")`; scheduler design must account for this so Spring tests do not fail due missing bean/profile interactions.
- The app-level switch endpoint now uses Admin JWT auth, not the older `X-Admin-User-Id` contract from the archived PRD.

## Planning Self-Check

- Acceptance criteria are defined: yes.
- Forbidden modification scope is defined: yes, do not duplicate already completed API/UI switch work; do not alter preview access/redaction/streaming.
- Expected files are listed: yes.
- Required tests are listed: yes.
- Specific guideline files were read, not only spec indexes: yes.
- Demand unclear questions: none blocking. The only adjustment is scope correction based on journal/code evidence.
- API / DB / frontend types / DTO fields are aligned: yes. App switch fields already align; scheduled cleanup needs only backend config/scheduler alignment.
