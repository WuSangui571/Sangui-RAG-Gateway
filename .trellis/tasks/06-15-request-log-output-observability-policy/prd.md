# Request Log Output Observability Policy PRD

## Task Classification

Complex Task.

Reason: this touches request-log persistence, database migrations, gateway response capture, streaming behavior, admin API contract, frontend types/detail UI, access audit, retention cleanup, sensitive-data policy, and cross-layer tests. This task must be designed before implementation. Codex must not write business implementation code in this round.

## Current Project State

- Branch: `feature/request-log-output-observability-policy`.
- Working directory was clean before task setup.
- No active Trellis task existed before this task.
- Recent completed work:
  - `feature/kb-chinese-filename-display` was completed and recorded in Session 52.
  - `feature/smoke-module-clarity` was completed, manually accepted, committed as `e625f0ab`, and recorded/archived in Session 53.
- Current request-log baseline:
  - `rag_request_log` stores safe operational metadata, `question_summary`, and `hit_chunk_ids`.
  - Admin request-log APIs expose list/detail/hit-chunk metadata with tenant checks.
  - Frontend has Request Logs list page and detail drawer.
  - Smoke page verifies metadata-only request-log observability.
- Current gap:
  - Admin cannot inspect any controlled output snippet when a request succeeds but the answer quality, truncation, or upstream output behavior needs troubleshooting.

## Goal

Allow an app owner/admin to view a bounded, redacted, explicitly enabled output preview for troubleshooting, without turning request logs into full answer storage.

The feature is for operational diagnosis only:

- Show whether the gateway received useful output from upstream.
- Show a short safe preview when output capture is explicitly enabled.
- Show output length and capture status so failures are diagnosable even when preview is disabled or unavailable.
- Keep full prompts, full messages, full answers, chunk content, raw SSE payloads, provider raw bodies, API keys, and stack traces out of request-log persistence and default API responses.

## Non-Goals

- Do not store full assistant answers.
- Do not store full request messages, full prompts, augmented prompts, or RAG context.
- Do not store chunk content or unbounded document excerpts in request logs.
- Do not expose output preview in request-log list rows.
- Do not expose output preview in normal detail responses by default.
- Do not implement a role/permission system beyond the current temporary `X-Admin-User-Id` owner boundary.
- Do not add LLM-based semantic summarization in this task. `answer_summary` is a possible future field, but V1 should avoid an extra model call and avoid presenting generated summaries as evidence.
- Do not silently degrade security by saving output when redaction/capture validation fails.

## Product Boundary

This belongs in the lightweight RAG gateway because it improves safe observability for existing API integrations. It must remain an admin troubleshooting surface, not a chat transcript or analytics product.

## Policy Decisions

### Enablement

Output content capture requires both switches:

| Switch | Location | Default | Effect |
|---|---|---:|---|
| Global switch | backend configuration, e.g. `rag.request-log.output-capture.enabled` | `false` | If false, no output preview is persisted for any app. |
| App switch | `rag_app.request_log_output_capture_enabled` | `false` | If false, no output preview is persisted for that app. |

Effective rule:

```text
capture_output_preview = global_enabled && app.request_log_output_capture_enabled
```

`completion_length` is safe numeric metadata and may be recorded when technically available even if preview capture is disabled. It must not require content retention after the value is computed.

### Access Permission

V1 uses the existing temporary admin identity contract:

- Admin caller must provide `X-Admin-User-Id`.
- App ownership is verified through `AppService.findByIdAndUserId(appId, userId)`.
- Cross-user app access returns `403 FORBIDDEN`.
- Missing app returns `404 NOT_FOUND`.
- No output preview API may query request logs before app ownership is validated.

Future role hooks can add a permission such as `REQUEST_LOG_OUTPUT_VIEW`, but this task must not invent a partial role system.

### Explicit Confirmation And Audit

Output preview is a higher-sensitivity field. It must be retrieved through a separate explicit access endpoint, not through default list/detail.

Required endpoint:

```http
POST /api/admin/apps/{appId}/request-logs/{requestId}/output-preview/access
X-Admin-User-Id: <userId>
Content-Type: application/json

{
  "confirm_access": true,
  "reason": "Investigating upstream answer truncation"
}
```

Required behavior:

- `confirm_access=true` is required.
- `reason` is optional but, if present, must be bounded and trimmed.
- Successful and denied access attempts must be auditable.
- Audit records must not store preview content.

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "request_id": "req-id",
    "output_capture_status": "CAPTURED",
    "completion_length": 1832,
    "output_preview": "bounded redacted preview...",
    "output_preview_truncated": true,
    "output_redacted": true,
    "output_retention_expires_at": "2026-06-22T10:00:00"
  }
}
```

Normal detail endpoint may expose only metadata:

```json
{
  "output_capture_status": "CAPTURED",
  "completion_length": 1832,
  "output_preview_available": true,
  "output_preview_truncated": true,
  "output_redacted": true,
  "output_retention_expires_at": "2026-06-22T10:00:00"
}
```

Normal detail endpoint must not include `output_preview`.

### Capture Status

Use explicit statuses; do not infer behavior from nulls alone.

| Status | Meaning |
|---|---|
| `DISABLED` | Global or app capture disabled. |
| `CAPTURED` | Preview captured and available until retention expiry. |
| `EMPTY` | Upstream succeeded but assistant output was empty. |
| `TRUNCATED_ONLY` | Output existed but only a truncated preview was stored. Use with `output_preview_truncated=true` if this distinction is useful. |
| `REDACTED` | Preview captured after redaction changed the original preview. |
| `REDACTION_BLOCKED` | Sensitive pattern was detected and policy chose not to persist preview. |
| `STREAMING_UNSUPPORTED` | Streaming output preview could not be captured safely. |
| `FAILED` | Capture failed for an implementation reason; gateway response must remain unchanged. |
| `EXPIRED` | Preview was removed by retention cleanup. |

Implementation may consolidate `CAPTURED` + `output_redacted=true` instead of using a separate `REDACTED` status, but tests must assert the chosen contract.

### Fields

#### Database Fields On `rag_request_log`

Migration target: next migration after `V10`, expected name:

```text
backend/src/main/resources/db/migration/V11__add_request_log_output_observability.sql
```

Add columns:

| Column | Type | Default | Notes |
|---|---|---|---|
| `completion_length` | `INTEGER` | `NULL` | Character count of assistant output when available. |
| `output_capture_status` | `VARCHAR(32)` | `'DISABLED'` | Explicit status for old/new rows. |
| `output_preview` | `TEXT` | `NULL` | Bounded, redacted preview only. Never full output. |
| `output_preview_truncated` | `BOOLEAN` | `FALSE` | True when original output exceeded preview limit. |
| `output_redacted` | `BOOLEAN` | `FALSE` | True when redaction changed preview text. |
| `output_retention_expires_at` | `TIMESTAMP` | `NULL` | Used by cleanup; null when no preview is stored. |

Add app switch:

| Table | Column | Type | Default |
|---|---|---|---|
| `rag_app` | `request_log_output_capture_enabled` | `BOOLEAN` | `FALSE` |

Add audit table:

```text
rag_request_log_output_access_audit
```

Columns:

| Column | Type | Notes |
|---|---|---|
| `id` | `BIGSERIAL PRIMARY KEY` | Audit id. |
| `user_id` | `BIGINT NOT NULL` | Admin caller. |
| `app_id` | `BIGINT NOT NULL` | App boundary. |
| `request_log_id` | `BIGINT` | Nullable only if missing log access is audited before lookup. |
| `request_id` | `VARCHAR(64) NOT NULL` | Request id attempted. |
| `access_result` | `VARCHAR(32) NOT NULL` | `GRANTED`, `DENIED`, `NOT_FOUND`, `EXPIRED`, etc. |
| `reason` | `VARCHAR(256)` | Optional bounded reason. |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | Audit time. |

Recommended indexes:

```text
idx_rag_request_log_output_expiry on rag_request_log(output_retention_expires_at)
idx_rag_request_log_output_audit_user_created_at on rag_request_log_output_access_audit(user_id, created_at DESC)
idx_rag_request_log_output_audit_app_created_at on rag_request_log_output_access_audit(app_id, created_at DESC)
idx_rag_request_log_output_audit_request_id on rag_request_log_output_access_audit(request_id)
```

#### Backend DTO/VO

Add DTO:

```java
RequestLogOutputAccessDTO
- Boolean confirmAccess
- String reason
```

Add VO:

```java
RequestLogOutputPreviewVO
- String requestId
- String outputCaptureStatus
- Integer completionLength
- String outputPreview
- Boolean outputPreviewTruncated
- Boolean outputRedacted
- LocalDateTime outputRetentionExpiresAt
```

Extend detail VO with output metadata only:

```java
ApiRequestLogDetailVO
- String outputCaptureStatus
- Integer completionLength
- Boolean outputPreviewAvailable
- Boolean outputPreviewTruncated
- Boolean outputRedacted
- LocalDateTime outputRetentionExpiresAt
```

Do not add `output_preview` to list VO.

#### Frontend Types

Extend:

```ts
export type OutputCaptureStatus =
  | 'DISABLED'
  | 'CAPTURED'
  | 'EMPTY'
  | 'TRUNCATED_ONLY'
  | 'REDACTED'
  | 'REDACTION_BLOCKED'
  | 'STREAMING_UNSUPPORTED'
  | 'FAILED'
  | 'EXPIRED'

export interface RequestLogOutputPreviewVO {
  request_id: string
  output_capture_status: OutputCaptureStatus
  completion_length: number | null
  output_preview: string | null
  output_preview_truncated: boolean
  output_redacted: boolean
  output_retention_expires_at: string | null
}
```

Extend `ApiRequestLogDetailVO` with metadata only.

### Redaction And Bounds

V1 redaction should be deterministic, local, testable, and conservative.

Required bounds:

| Setting | Default | Notes |
|---|---:|---|
| `rag.request-log.output-capture.preview-max-chars` | `1000` | Hard upper bound. |
| `rag.request-log.output-capture.retention-days` | `7` | Hard default retention. |
| `rag.request-log.output-capture.reason-max-chars` | `256` | Audit reason bound. |

Preview creation order:

1. Extract assistant output text only.
2. Compute `completion_length`.
3. Truncate to `preview-max-chars`.
4. Redact known sensitive patterns.
5. If blocking patterns remain, set `REDACTION_BLOCKED` and do not persist preview.
6. Set `output_retention_expires_at`.

Minimum redaction patterns:

- `sk-sangui-...` app keys.
- Bearer token values.
- Obvious upstream/provider keys (`sk-...` style tokens).
- Authorization header fragments.
- `api_key`, `apiKey`, `api_key_encrypted`, `key_hash` key/value style fragments.

Do not attempt broad PII detection in V1 unless it is explicit, deterministic, and tested. Do not add an LLM safety model.

### Capture Points

Non-streaming success:

- Extract assistant output from parsed `OpenAiChatCompletionResponse`.
- Capture only after upstream response parse succeeds.
- Persist preview metadata in the same request-log row via `CreateRequestLogCommand`.

Non-streaming failure:

- Do not persist provider raw body or exception message.
- `completion_length=null`, `output_capture_status` should be `FAILED`, `DISABLED`, or not applicable according to the implemented status contract.

Streaming success:

- Do not store raw SSE payload.
- If implemented in V1, collect only assistant text deltas in memory with a bounded collector, then persist the bounded/redacted preview at stream completion.
- If safe delta extraction is not implemented in this task, set `output_capture_status=STREAMING_UNSUPPORTED`, keep `completion_length=null`, and document/test the limitation.

Streaming failure after response commit:

- Do not persist raw SSE or partial provider bodies.
- If a bounded text collector exists, partial preview may be stored only when the same redaction/retention policy passes; otherwise record status only.

### Retention Cleanup

Implement cleanup as an explicit scheduled service or service method covered by unit tests.

Cleanup behavior:

- Find rows where `output_retention_expires_at < now` and `output_preview IS NOT NULL`.
- Set `output_preview=NULL`.
- Set `output_capture_status='EXPIRED'`.
- Preserve `completion_length`, `output_preview_truncated`, `output_redacted`, `created_at`, and base request metadata.
- Never delete the whole request-log row as part of this task.

Config:

```yaml
rag:
  request-log:
    output-capture:
      enabled: false
      preview-max-chars: 1000
      retention-days: 7
      cleanup-enabled: true
```

### API / Payload Contract

#### Existing List

```http
GET /api/admin/apps/{appId}/request-logs
```

No output preview is returned. List may optionally include `completion_length` and `output_capture_status` only if the UI needs a small status indicator. Prefer keeping list minimal unless needed.

#### Existing Detail

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
```

Returns existing safe fields plus output metadata only. No `output_preview`.

#### New Explicit Access Endpoint

```http
POST /api/admin/apps/{appId}/request-logs/{requestId}/output-preview/access
```

Request body:

```json
{
  "confirm_access": true,
  "reason": "optional bounded reason"
}
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "request_id": "req-id",
    "output_capture_status": "CAPTURED",
    "completion_length": 120,
    "output_preview": "redacted bounded preview",
    "output_preview_truncated": false,
    "output_redacted": false,
    "output_retention_expires_at": "2026-06-22T10:00:00"
  }
}
```

### Validation / Error Matrix

| Scenario | HTTP | Code | Assertion |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing admin header handling. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | No log query. |
| App missing | 404 | `NOT_FOUND` | No output access query. |
| App belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`; audit denied if implemented after ownership decision. |
| Request log missing under owned app | 404 | `NOT_FOUND` | No preview returned. |
| `confirm_access` missing/false | 400 | `INVALID_REQUEST` | Audit denied; no preview returned. |
| Reason too long | 400 | `INVALID_REQUEST` | No preview returned; no raw reason echo if unsafe. |
| Capture disabled | 200 | `OK` | Status `DISABLED`, `output_preview=null`. |
| Preview expired | 200 | `OK` | Status `EXPIRED`, `output_preview=null`. |
| Redaction blocked | 200 | `OK` | Status `REDACTION_BLOCKED`, `output_preview=null`. |
| Captured preview | 200 | `OK` | Bounded preview returned only from explicit endpoint. |
| Cross-user request ID guessed | 403 or 404 by app boundary | `FORBIDDEN` or `NOT_FOUND` | No request-log row leaked. |

### Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Global and app switches enabled; non-streaming request succeeds; `completion_length` is stored; `output_preview` is redacted and truncated to max chars; normal detail shows metadata only; explicit preview access with confirmation returns preview and writes an audit row; no forbidden fields appear. |
| Base | Global or app switch disabled; request logs continue recording existing metadata; `output_capture_status=DISABLED`; explicit preview access returns no preview and does not break existing list/detail/smoke behavior. |
| Bad | Full answer, prompt, messages, augmented prompt, raw SSE payload, chunk content, API keys, key hashes, upstream key fields, provider raw body, stack trace, or environment values are persisted or returned; default detail exposes preview without confirmation; cross-user access can infer preview content. |

### Required Tests And Assertion Points

Backend targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
```

Backend regression:

```bash
cd backend
mvn -q test
```

Frontend checks:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
cmd /c npm run test:visual
```

Required backend assertions:

- Migration adds fields with safe defaults; old rows remain readable.
- `CreateRequestLogCommand` maps completion length/status/preview metadata.
- Preview is not persisted when global switch is disabled.
- Preview is not persisted when app switch is disabled.
- Preview is persisted when both switches are enabled.
- Preview is truncated at configured max.
- Sensitive tokens are redacted or blocked.
- Detail endpoint omits `output_preview`.
- Explicit access endpoint requires `confirm_access=true`.
- Explicit access endpoint writes audit without preview content.
- Cross-user app access cannot retrieve preview and should not query request-log output.
- Cleanup nulls expired preview and marks status `EXPIRED`.
- Streaming behavior is explicitly tested as captured or `STREAMING_UNSUPPORTED`.

Required frontend assertions:

- Type definitions match backend snake_case VO fields.
- Detail drawer shows output metadata status without rendering preview by default.
- Preview action requires explicit user confirmation.
- Preview modal/drawer handles unavailable/disabled/expired/redaction-blocked statuses.
- Preview text uses bounded container with copy disabled unless explicitly approved in UI design.
- i18n dictionary parity remains intact.

Required forbidden-field assertions:

Scan list/detail/preview responses for absence of:

```text
prompt, messages, full_messages, augmented_prompt, api_key, key_hash, authorization,
upstream_api_key, api_key_encrypted, chunk_content, content, embedding,
provider_response_body, stack_trace, storage_path, raw_sse, environment
```

Note: `output_preview` is allowed only in the explicit access endpoint.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary, request-log domain, OpenAI-compatible gateway scope.
- `.trellis/spec/backend/database-guidelines.md`: migration rules, request-log schema, tenant-safe persistence.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields, forbidden log/persistence content, current admin observability fields.
- `.trellis/spec/backend/error-handling.md`: admin error envelope, gateway error shape, request-log API error matrix.
- `.trellis/spec/backend/quality-guidelines.md`: request-log observability tests and security review checklist.
- `.trellis/spec/frontend/type-safety.md`: request-log TS contract and forbidden fields.
- `.trellis/spec/frontend/state-management.md`: request logs are server state; no persistent frontend secret/content storage.
- `.trellis/spec/frontend/component-guidelines.md`: request-log tables/detail drawers show summaries only.
- `.trellis/spec/frontend/quality-guidelines.md`: request-log UI must be debuggable without exposing sensitive data.
- `.trellis/spec/gateway/resilience.md`: upstream errors must be safe and observable; request-log insert failure must not change gateway response.
- `.trellis/spec/rag/retrieval-quality.md`: request logs may store `hit_chunk_ids`, not full chunks.
- `.trellis/spec/rag/prompt-context-policy.md`: prompts/context/full hidden rules must not be exposed.
- `.trellis/spec/security/rag-security.md`: request-log and evidence boundaries; full prompt/chunk/provider body exposure is forbidden.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: DB/API/frontend/test contract alignment.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search/extend existing request-log patterns before adding parallel mechanisms.

### Code Patterns Found

- Request-log persistence command pattern:
  - `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java`
- Tenant-scoped admin API pattern:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - Uses `validateAppOwnership`, `X-Admin-User-Id`, 403 for cross-user, 404 for missing app.
- Safe VO conversion pattern:
  - `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java`
  - `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java`
  - Forbidden fields are simply not modeled.
- Gateway capture points:
  - `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
  - Non-streaming and streaming both call `apiRequestLogService.record(...)`.
  - `ChatCompletionResult` and `ChatCompletionStreamPreparation` already carry question summary and hit chunk IDs.
- Upstream streaming forwarding:
  - `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
  - Currently forwards SSE `data` lines without parsing/storing content.
- Frontend typed API/detail pattern:
  - `frontend/src/types/request-log.ts`
  - `frontend/src/api/request-logs.ts`
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`

### Files Likely To Modify

Backend:

- `backend/src/main/resources/db/migration/V11__add_request_log_output_observability.sql`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/sangui/raggateway/app/AppEntity.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/dto/*` and `backend/src/main/java/com/sangui/raggateway/app/vo/*` if adding app switch API fields.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java`
- `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- New log policy/config classes under `backend/src/main/java/com/sangui/raggateway/log/`.
- New audit entity/mapper/service under `backend/src/main/java/com/sangui/raggateway/log/`.
- `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java`
- New `RequestLogOutputPreviewVO`.
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java` only if streaming safe delta capture is implemented.

Frontend:

- `frontend/src/types/request-log.ts`
- `frontend/src/api/request-logs.ts`
- `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
- New domain component for explicit output preview access/confirmation.
- `frontend/src/app/i18n/dict.ts`
- Possibly `frontend/src/pages/request-logs/RequestLogListPage.tsx` if list includes output status/length.

Tests:

- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClientTest.java` if streaming capture is implemented.
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`

Docs/specs:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `README.md` only if public/operator behavior changes need documentation.

### Risk / Boundary Notes

- Output preview may contain user/private business data even when bounded. This is why default must be off and access must be explicit/audited.
- The current request-log API intentionally avoids full content. Do not weaken that by adding preview to list/default detail.
- Existing smoke/evidence policy currently forbids answer previews in committed evidence. That policy should remain for smoke evidence unless the user explicitly changes it.
- Streaming preview is riskier than non-streaming because current code forwards raw SSE lines. Do not persist raw SSE. Either parse bounded assistant deltas safely or mark streaming preview unsupported.
- Insert failure behavior should remain non-blocking for gateway responses, but capture policy failures should be visible in `output_capture_status`.
- Do not add silent fallback that stores unredacted output when redaction fails.
- Do not implement broad role/permission scaffolding beyond current owner check unless the user opens that scope.

## Planning Self-Check

- Acceptance criteria are defined: yes.
- Forbidden modification scope is defined: yes, no business implementation during this Codex round; no full answer/prompt/chunk/provider body storage in implementation.
- Expected files are listed: yes.
- Required tests are listed: yes.
- Concrete guidelines were read, not only indexes: yes.
- Demand unclear questions: none blocking. V1 chooses explicit opt-in preview, separate confirmation endpoint, audit, bounded deterministic redaction, and no LLM-generated summaries.
- API/DB/frontend DTO fields are aligned: yes, see sections above.

