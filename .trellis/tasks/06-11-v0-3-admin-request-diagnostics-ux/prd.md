# V0.3 Admin Request Diagnostics UX

## Task Classification

Complex Task.

Reason: this work spans Admin frontend UX, request-log observability, readiness data, gateway/RAG error semantics, and security evidence boundaries. The first implementation path should be frontend-first and contract-preserving. Backend changes are allowed only if focused code research during implementation proves an existing safe field is insufficient.

## Goal

Productize request troubleshooting in the Admin console so operators can quickly distinguish auth, readiness, retrieval, embedding, upstream, streaming, and request-log boundaries without exposing prompts, full answers, chunk content, provider bodies, keys, stack traces, or hidden internal state.

The V0.3 scope builds on the V0.2 baseline: request logs, readiness checks, hit chunk metadata, safe evidence fields, and smoke validation are already available.

## Non-Goals

- Do not implement a chat playground, workflow platform, agent UI, or full observability suite.
- Do not expose raw prompts, request messages, answers, SSE payloads, chunk content, provider raw bodies, API keys, key hashes, encrypted keys, Authorization headers, stack traces, embeddings, storage paths, or filesystem paths.
- Do not add hidden fallbacks, fake successful diagnostics, or silent best-effort behavior.
- Do not duplicate backend authorization, readiness, or gateway behavior rules in frontend business logic.
- Do not add database tables or migrations unless a narrow backend diagnostic VO is explicitly chosen and proven insufficient without schema changes.
- Do not change public `/v1/*` compatibility behavior for this UX task.

## Product Requirements

1. Request log list and detail must present a clear diagnostics view using existing safe fields:
   - `request_id`
   - `status`
   - `error_code`
   - `latency_ms`
   - `upstream_latency_ms`
   - `model`
   - `provider_name`
   - `messages_count`
   - `question_summary`
   - `hit_chunk_ids`
   - token `usage`
   - hit chunk safe metadata from the existing hit-chunks endpoint
2. Failed requests must be classified into one of these diagnostic boundaries:
   - `auth`
   - `readiness`
   - `retrieval`
   - `embedding`
   - `upstream`
   - `streaming`
   - `request-log`
   - `unknown`
3. The Admin UI must show concise, actionable diagnostic suggestions derived from safe inputs only.
4. The preferred implementation must not require backend API changes:
   - Use existing request-log detail fields.
   - Use existing readiness endpoint fields where app/user context is already available.
   - Use existing hit-chunks endpoint for safe retrieval evidence.
5. If existing API fields are insufficient, add only a narrow safe diagnostic VO/API extension and update specs/tests in the same task.
6. Frontend diagnostic mapping must be display-only. Backend remains the source of truth for readiness checks, error codes, tenant access, and request-log data.
7. Loading, empty, error, and retry states must be explicit and must not mask failed API calls.
8. Display text must use the existing typed i18n dictionary and preserve zh-CN/en-US key parity.

## API / Payload Contract

### Preferred No-Backend-Change Contract

Existing endpoints:

```http
GET /api/admin/apps/{appId}/request-logs
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
GET /api/admin/apps/{appId}/readiness
```

Existing request-log safe fields:

| Field | Source | Diagnostic Use |
|---|---|---|
| `request_id` | request-log list/detail | Copyable correlation ID. |
| `status` | request-log list/detail | Success/failure state. |
| `error_code` | request-log list/detail | Primary failure-boundary signal. |
| `latency_ms` | request-log list/detail | Total gateway latency. |
| `upstream_latency_ms` | request-log list/detail | Upstream timing when available. |
| `model` | request-log list/detail | Resolved model on success paths. |
| `provider_name` | request-log list/detail | Resolved provider on success paths. |
| `messages_count` | request-log list/detail | Safe request size signal. |
| `question_summary` | request-log list/detail | Bounded prompt prefix only. |
| `hit_chunk_ids` | request-log list/detail | Retrieval evidence by ID only. |
| `usage` | request-log list/detail | Token metadata when upstream reports it. |
| `hit chunks` | hit-chunks endpoint | `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`, bounded `summary`. |

Existing readiness safe fields:

| Field | Source | Diagnostic Use |
|---|---|---|
| `overall_status` | readiness | Readiness boundary summary. |
| `checks[].key` | readiness | Map to app/model/KB/key/embedding prerequisites. |
| `checks[].status` | readiness | Suggest next setup action. |
| `checks[].message` | readiness | Backend-owned safe explanation. |
| `checks[].metadata` | readiness | Safe IDs/statuses/provider/model names only. |

### Optional Backend Safe Diagnostic VO

Only use this path if implementation proves the frontend cannot produce correct UX from existing fields.

Allowed shape:

```json
{
  "boundary": "auth|readiness|retrieval|embedding|upstream|streaming|request-log|unknown",
  "safe_summary": "Short operator-facing summary",
  "safe_next_steps": ["Short action 1", "Short action 2"],
  "related_request_id": "uuid-or-null",
  "related_error_code": "stable-error-code-or-null",
  "safe_signals": {
    "readiness_status": "READY|MISSING|DISABLED|NOT_READY|null",
    "hit_chunk_count": 0,
    "has_upstream_latency": false
  }
}
```

Forbidden in any new VO:

```text
prompt, messages, full_messages, augmented_prompt, answer, raw_sse,
api_key, key_hash, authorization, upstream_api_key, api_key_encrypted,
chunk_content, content, embedding, provider_response_body, stack_trace,
storage_path, internal filesystem path, environment variables
```

No DB schema change is expected for this task. A backend VO must be derived from existing request-log/readiness data.

## Diagnostic Mapping Matrix

| Input Signal | Boundary | UX Summary | Suggested Next Step |
|---|---|---|---|
| Gateway returns `401 invalid_api_key`; no request-log row exists | `auth` | The request did not pass gateway authentication. | Verify the app API key was copied once, not disabled/revoked/expired, and belongs to an enabled app. |
| Request log `error_code=invalid_request` | `request-log` or `unknown` depending on context | The gateway rejected the request before model execution. | Check OpenAI-compatible payload shape, especially non-empty `messages` and supported roles. |
| Request log `error_code=model_config_not_ready` | `readiness` | Chat model configuration is not ready. | Open app readiness and fix `default_model_config`. |
| Readiness check `default_model_config` is `MISSING`, `DISABLED`, or `NOT_READY` | `readiness` | The app lacks an enabled usable chat model config. | Bind or enable a model config with provider, base URL, chat model, and upstream key. |
| Request log `error_code=knowledge_base_not_ready` | `retrieval` | The app has no ready knowledge base for RAG retrieval. | Bind a ready KB or process documents until KB status is `READY`. |
| Readiness check `default_knowledge_base` or `knowledge_base_status` is not `READY` | `retrieval` | KB prerequisites are incomplete. | Bind a KB and complete document processing. |
| Request log `error_code=embedding_failed` | `embedding` | Query embedding failed before upstream chat. | Check matching enabled embedding config, model, dimension, and upstream key. |
| Readiness check `embedding_config` is not `READY` | `embedding` | Embedding provider/config is not usable. | Create or enable matching embedding config for KB model and dimension. |
| Success log with `hit_chunk_ids=[]` or null | `retrieval` | Request completed but no KB chunks were hit. | Verify the KB content, query wording, similarity threshold, and no-hit policy. |
| Failure `error_code=upstream_timeout` | `upstream` | Upstream chat provider timed out. | Check provider health, timeout settings, model availability, and network path. |
| Failure `error_code=upstream_error` | `upstream` | Upstream provider returned an error or unusable response. | Check provider config, base URL, model, upstream key, and provider status. |
| Streaming smoke has chunks but no `[DONE]` | `streaming` | Stream likely ended abnormally. | Inspect upstream availability and streaming proxy path; verify `/v1` proxy does not buffer SSE. |
| Request-log API detail/hit-chunks fails while chat succeeded | `request-log` | Observability lookup failed. | Check Admin `X-Admin-User-Id`, app ownership, request ID, and request-log persistence boundary. |
| Malformed `hit_chunk_ids` parsing error | `request-log` | Stored hit chunk IDs are invalid. | Treat as a visible persistence/data bug; do not fabricate hit evidence. |
| Unknown `error_code` | `unknown` | The request failed with an unclassified safe code. | Show the code and request ID; ask operator to check backend logs using the request ID. |

## Good / Base / Bad Cases

### Good

- Operator opens Request Logs, connects with valid app/admin user ID, and sees list rows with status, error code, model/provider, latency, token metadata, question summary, and hit count.
- Opening a failed detail shows a diagnostic boundary tag and safe next steps based on `error_code` and readiness checks.
- Opening a successful RAG detail with hits shows hit chunk IDs and safe chunk metadata/summaries only.
- Opening a successful no-hit detail classifies retrieval as no-hit without treating it as a gateway failure.
- Readiness checks appear as backend-owned prerequisite evidence, not duplicated frontend business truth.
- All forbidden fields are absent from frontend types, UI, tests, screenshots, and task evidence.

### Base

- App has no request logs: the page shows an empty state and does not imply diagnostics passed.
- Readiness endpoint cannot be loaded: diagnostics still show request-log-derived information and explicitly mark readiness evidence unavailable.
- Hit-chunks endpoint returns empty for empty `hit_chunk_ids`: the UI shows no hit chunks without error.
- Existing backend fields are enough: no API/DTO/backend test changes are made.

### Bad

- UI exposes prompt/messages/answers/chunk content/provider body/secrets for easier debugging.
- UI silently maps unknown errors to a known boundary or hides the error.
- Frontend reimplements tenant or readiness rules as if it were the backend source of truth.
- Backend adds a broad diagnostic endpoint returning raw logs or provider payloads.
- Request-log insert failures are hidden as successful diagnostics.
- Streaming failures are represented as normal success because some chunks arrived.

## Acceptance Criteria

- [ ] Existing request log list remains usable with pagination, filters, loading/error/empty states.
- [ ] Request log detail includes a diagnostics section with boundary classification, safe summary, and next steps.
- [ ] Failure classifications cover auth, readiness, retrieval, embedding, upstream, streaming, request-log, and unknown.
- [ ] Successful RAG rows distinguish hit count greater than zero from no-hit.
- [ ] Diagnostics can optionally incorporate current readiness checks without blocking detail rendering if readiness load fails.
- [ ] Hit chunk metadata remains bounded and tenant-scoped through the existing endpoint.
- [ ] No forbidden fields are added to TypeScript request-log/readiness types.
- [ ] If backend API changes are introduced, `.trellis/spec/` is updated with exact fields and forbidden-field rules.
- [ ] Required focused tests and build/typecheck commands pass.

## Expected Implementation Approach

### Preferred Frontend-Only Approach

1. Add a small domain-level diagnostic mapper near request-log UI, for example:
   - `frontend/src/types/request-log.ts`: explicit diagnostic boundary union if needed.
   - `frontend/src/components/domain/RequestDiagnosticsPanel.tsx`: display component.
   - Optional `frontend/src/components/domain/requestDiagnostics.ts`: pure mapping helper.
2. Extend `RequestLogDetailDrawer` to:
   - show the diagnostics panel,
   - load readiness for the app when detail opens or when a failure needs readiness evidence,
   - keep readiness loading/error separate from request-log detail loading.
3. Optionally add list-level boundary tag/filter only if it is low-risk and does not require backend changes.
4. Add i18n keys to both `zh-CN` and `en-US`.
5. Add tests only if current frontend test harness supports them; otherwise rely on typecheck/build and browser smoke.

### Backend Optional Path

If a safe backend diagnostic VO is required:

1. Add a narrow VO under `backend/src/main/java/com/sangui/raggateway/log/vo/`.
2. Derive it from existing request-log/readiness-safe data only.
3. Keep tenant validation identical to existing request-log endpoints.
4. Update backend spec and tests:
   - `ApiRequestLogServiceTest`
   - `ApiRequestLogAdminControllerTest`
   - forbidden-field assertions

## Files Likely To Modify

Expected frontend-first files:

```text
frontend/src/types/request-log.ts
frontend/src/components/domain/RequestLogDetailDrawer.tsx
frontend/src/components/domain/RequestDiagnosticsPanel.tsx
frontend/src/components/domain/requestDiagnostics.ts
frontend/src/app/i18n/dict.ts
```

Possible supporting frontend files:

```text
frontend/src/pages/request-logs/RequestLogListPage.tsx
frontend/src/api/apps.ts
frontend/src/types/app.ts
```

Only if backend contract is insufficient:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/log/vo/*Diagnostic*.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/security/rag-security.md
```

## Required Tests and Assertion Points

### Frontend

Run from `frontend/`:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

Assertion points:

- Diagnostic boundary union is typed, not arbitrary string state.
- Unknown errors have explicit fallback display.
- i18n key parity still compiles.
- No forbidden fields are typed or rendered.
- Detail drawer handles readiness unavailable separately from request-log detail failure.

Optional browser smoke when frontend changes are done:

```bash
cmd /c npm run test:visual
```

If a dev server is used for manual browser smoke, verify the Request Logs page and detail drawer through the Browser plugin or Playwright with a real page open.

### Backend If Changed

Run from `backend/` with a 60 second timeout for targeted unit tests:

```bash
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
```

Regression set if gateway error mapping or request-log persistence changes:

```bash
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
```

Assertion points:

- No new response includes forbidden fields.
- Tenant ownership checks stay identical to existing request-log/readiness endpoints.
- Unknown/malformed `hit_chunk_ids` fails visibly.
- Request-log insert failure still does not affect gateway response.

## Planning Self-Check

- Acceptance standards are explicit.
- Prohibited modification scope is explicit.
- Expected files are listed.
- Required tests are listed.
- Concrete spec/guideline files were read before handoff.
- Current best judgment: no clarification is required before DeepSeek implementation.
- Current best judgment: existing request-log/readiness fields are sufficient for frontend-first V0.3 UX; API/DB changes should be avoided unless implementation proves otherwise.
