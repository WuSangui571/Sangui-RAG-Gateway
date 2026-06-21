# Simplify Smoke Test Page Flow Architecture

## Background

The smoke/readiness page is a high-frequency entry for demos, acceptance, regression checks, and issue triage. It already combines app selection, readiness checks, gateway smoke calls, request-log evidence, streaming evidence, and revoked-key verification, but the current UI is vertically dense and reads like an information dump.

Recent work has already converged model-config check semantics, app readiness, request-log safe evidence, and Maven build stability. This task should turn the smoke page into an operator-oriented flow that helps users answer:

1. Which app am I testing?
2. Are prerequisites ready?
3. What should I run next?
4. What evidence is safe to record?
5. If it fails, which boundary should I inspect?

## Task Classification

Complex Task.

Reasons:

- Frontend information architecture and workflow states need to be redesigned, not just copy changed.
- The page touches app selection, readiness, gateway smoke calls, request-log evidence, streaming status, optional revoked-key validation, i18n, and tests.
- Safe metadata boundaries must be preserved while improving evidence visibility.
- Tests must cover empty/no-app, not-ready, ready-run, failure hints, and forbidden-field rendering.

## Goal

Refactor the Smoke Test page into a clear process interface with these primary regions:

1. Select App
2. Preconditions
3. Execute Smoke
4. Review Evidence
5. Failure Next Step

The implementation must reduce long explanatory copy and duplicate hints, make disabled/failure states actionable, and preserve the existing metadata-only evidence boundary.

## Non-Goals

- Do not change backend Java code.
- Do not change database schema or migrations.
- Do not change Admin API endpoint paths, request payloads, response DTO/VO fields, or auth behavior.
- Do not change `/v1/chat/completions` gateway behavior, streaming semantics, request-log persistence, retrieval behavior, or readiness computation.
- Do not introduce raw answer preview, prompt preview, raw request/response body display, chunk content display, API-key display from list APIs, upstream-key display, or output-preview access.
- Do not add a new chat playground, wizard platform, workflow/agent UI, or demo-specific route.
- Do not persist pasted smoke API keys outside in-memory page state.
- Do not modify unrelated pages unless needed for shared i18n/test helpers.

## Existing Contract Surface

This task is frontend-first and should reuse existing contracts.

### App Selection

API client:

```ts
listApps(status?: string): Promise<ApiResponse<AppVO[]>>
getAppReadiness(appId: number): Promise<ApiResponse<AppReadinessVO>>
listApiKeys(appId: number): Promise<ApiResponse<ApiKeyVO[]>>
```

Key fields:

- `AppVO.id`
- `AppVO.name`
- `AppVO.status`
- `AppVO.default_model_config_id`
- `AppVO.default_knowledge_base_id`
- `AppVO.request_log_output_capture_enabled`
- `ApiKeyVO.name`
- `ApiKeyVO.key_prefix`
- `ApiKeyVO.status`

The API-key list only provides metadata and prefixes. The plaintext app API key must still be pasted by the operator and must remain page-local transient state.

### Readiness

API:

```http
GET /api/admin/apps/{appId}/readiness
Authorization: Bearer <admin-jwt>
```

Frontend client:

```ts
getAppReadiness(appId: number): Promise<ApiResponse<AppReadinessVO>>
```

Fields:

- `app_id`
- `user_id`
- `overall_status`: `READY | MISSING | DISABLED | NOT_READY`
- `checks[]`
  - `key`
  - `label`
  - `status`
  - `message`
  - `metadata`

Allowed readiness metadata includes safe operational fields such as app status, model config id, provider name, chat model, KB id/status, embedding model/dimension, active key count, and embedding provider name.

### Smoke Execution

Non-streaming client:

```ts
smokeChatCompletions(
  request: {
    model: string
    messages: { role: 'user' | 'system' | 'assistant'; content: string }[]
    stream: false
  },
  apiKey: string,
): Promise<SmokeChatCompletionResponse>
```

Streaming client:

```ts
smokeStreamingChatCompletions(
  request: {
    model: string
    messages: { role: 'user' | 'system' | 'assistant'; content: string }[]
    stream: true
  },
  apiKey: string,
): Promise<SmokeStreamingEvidence>
```

Safe non-streaming evidence:

- response id
- object
- model
- finish reason
- content length only
- prompt/completion/total token counts

Safe streaming evidence:

- HTTP status
- `data:` line count
- chunk count
- `[DONE]` presence

### Request-Log Evidence

API clients:

```ts
listRequestLogs(appId, { page, page_size, status })
getRequestLogDetail(appId, requestId)
getHitChunks(appId, requestId)
```

Allowed request-log evidence:

- `request_id`
- `model`
- `provider_name`
- `status`
- `error_code`
- `latency_ms`
- `upstream_latency_ms`
- token usage
- `messages_count`
- `question_summary` as a bounded prefix only
- `hit_chunk_ids`
- detail safe fields such as `user_id` and `updated_at`
- hit chunk metadata: `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`

Forbidden fields:

```text
prompt, messages, full_messages, augmented_prompt, api_key, key_hash,
authorization, upstream_api_key, api_key_encrypted, chunk_content,
content, embedding, provider_response_body, stack_trace, storage_path,
raw_sse, environment, output_preview
```

`HitChunkSummaryVO.summary` exists in the frontend type but must not be rendered on the smoke page for this task. Use chunk metadata only.

## Information Architecture Requirements

### Select App

- Show one compact app selector using `listApps(undefined)`.
- Empty app list must present a clear next action hint: create/configure an app before smoke testing.
- Selecting an app should reset smoke execution/evidence state and reload readiness/API-key metadata.
- If an app is selected in the global shell, use it as the initial selected app.
- Keep the selected app synchronized with `AdminShell` via `setSelectedAppId`.

### Preconditions

Show prerequisites as an operator checklist, not a large descriptive table.

Required checks to surface:

- App enabled
- Default chat model config ready
- Default knowledge base bound
- Knowledge base status ready
- Active app API key exists
- Embedding config ready

For non-READY states, show a specific action hint using the readiness check key/status. Example intent:

| Readiness check | Hint boundary |
|---|---|
| `app` disabled | Enable the app before running smoke. |
| `default_model_config` missing/disabled/not ready | Bind or fix a saved chat model config. |
| `default_knowledge_base` missing | Bind a ready knowledge base to this app. |
| `knowledge_base_status` not ready | Wait for or fix document processing. |
| `active_api_key` missing/disabled | Create or enable an app API key, then paste the plaintext key. |
| `embedding_config` missing/disabled/not ready | Add or fix an enabled embedding config matching the KB model/dimension. |

Readiness overall status must gate the main smoke action visually. The UI may still let an operator override and run smoke only if this is explicit and does not hide the readiness warning. Prefer disabling primary "run all" until readiness is `READY`.

### Execute Smoke

- Provide one primary run path for the normal demo/regression flow.
- Preserve separate internal step states for:
  - non-streaming chat
  - streaming chat
  - request-log evidence validation
  - optional revoked-key check
- Avoid forcing the operator to read long text between steps.
- Disabled controls must say what is missing: selected app, pasted key, user message, successful non-streaming run, or readiness.
- Running state must show which step is currently active.
- Request-log validation remains dependent on a successful non-streaming smoke request.

### Review Evidence

Group safe evidence by purpose:

- Gateway response metadata
- Streaming metadata
- Request-log metadata
- Hit chunk metadata
- Optional revoked-key 401 evidence

The evidence view must be compact and copy-safe. It must not render raw answers, prompts, request bodies, provider bodies, chunk content, output previews, keys, hashes, encrypted secrets, stack traces, or storage paths.

### Failure Next Step

Failures should be transformed into a clear boundary and next action.

Reuse or extend existing diagnostic patterns where practical:

- `frontend/src/components/domain/requestDiagnostics.ts`
- `frontend/src/components/domain/RequestDiagnosticsPanel.tsx`

Expected boundary mapping:

| Source | Boundary |
|---|---|
| Invalid/missing app API key, revoked-key mismatch | `auth` |
| Readiness non-READY or readiness API failure | `readiness`, `retrieval`, or `embedding` based on failed check |
| `knowledge_base_not_ready` | `retrieval` |
| `embedding_failed` | `embedding` |
| `upstream_error` / `upstream_timeout` | `upstream` |
| Missing `[DONE]` or SSE truncation | `streaming` |
| Request-log list/detail/hit-chunks mismatch | `request-log` |
| Unknown error | `unknown` |

Next-step hints should be short and action-oriented. Do not add broad help text or doc-like paragraphs in the page.

## UX Requirements

- Use dense admin patterns: rows, checklist, status tags, alerts, segmented/step-like layout where useful.
- Do not use a marketing hero, decorative cards, nested cards, or a chat-playground-first layout.
- Keep text readable on desktop and mobile widths.
- Prefer status tags, checklists, and concise action hints over long secondary paragraphs.
- Use Ant Design components consistently.
- Keep cards only where they frame a tool or evidence group; avoid card-in-card layout.
- Use typed i18n keys in zh-CN and en-US.

## Validation / Error Matrix

| Scenario | Expected UI behavior | Assertion points |
|---|---|---|
| No apps returned | Shows empty app state and app-creation/configuration hint; smoke controls disabled | `listApps(undefined)` called; no crash; no smoke API call |
| App selected but readiness `MISSING` | Preconditions show failed/missing check and action hint; primary run disabled or visibly warned | readiness status rendered; boundary hint present |
| App selected and readiness `READY` | Preconditions show ready status; key/message controls enabled according to available data | readiness checks rendered compactly |
| Active API key metadata exists but plaintext key absent | Shows prefix/name metadata but requires pasted key; run disabled with clear reason | no plaintext inferred from key list |
| Pasted key + READY app + valid message | Non-streaming can run; successful response records metadata only | no answer text rendered; content length shown |
| Non-streaming pass | Request-log validation action becomes available | request-log button enabled only after pass |
| Non-streaming failure with `knowledge_base_not_ready` | Failure panel shows retrieval/readiness next action | status/code and safe message only |
| Streaming returns chunks without `[DONE]` | Streaming step fails with streaming next action | `[DONE]` absence visible |
| Request log list has no matching success row | Request-log evidence step fails with request-log next action | clear "recent matching log" failure |
| Hit-chunks return metadata | Render chunk IDs/document IDs/KB IDs/source filename/chunk index only | `summary` and `content` not rendered |
| Revoked-key check disabled | Optional step remains skipped/neutral | no validation required |
| Revoked-key check enabled and returns 401 `invalid_api_key` | Optional step passes and records safe 401 evidence | status/code rendered only |
| Any API error | No silent fallback; visible failure/action hint | no mock success path |
| Test fixture includes forbidden strings/fields | DOM must not contain forbidden field names or secret-like values | forbidden-field DOM scan |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Operator selects a READY app, sees all prerequisites green, pastes a valid app key, runs the main smoke flow, sees non-streaming/streaming/request-log evidence grouped as safe metadata, and can identify the final pass state without reading long instructions. |
| Base | Operator has an app but readiness is not READY. The page does not pretend the smoke is valid; it points to the exact missing prerequisite and keeps the run path clearly blocked or warned. |
| Base | Operator has no pasted plaintext key. The page can show active key prefixes from metadata but requires the operator to paste the one-time secret manually. |
| Bad | The page renders raw assistant answer, prompt/messages, chunk content/summary, output preview, provider body, keys, hashes, encrypted upstream keys, storage paths, stack traces, or raw SSE payloads. |
| Bad | The page shows a pass overview when readiness is not READY or when request-log evidence was not checked after a non-streaming run. |
| Bad | The refactor introduces a second source of truth for readiness, API types, auth identity, or request-log evidence validation. |

## Files Likely To Modify

Expected implementation files:

- `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - Main IA and flow-state refactor.
- `frontend/src/app/i18n/dict.ts`
  - Add/update concise smoke flow, precondition, evidence, and failure next-step keys in zh-CN/en-US.
- `frontend/src/__tests__/pages/SmokeTestPage.test.tsx`
  - New page tests covering core flow and forbidden fields.

Possible supporting files:

- `frontend/src/components/domain/requestDiagnostics.ts`
  - Extend boundary mapping or reusable next-step keys if the page reuses diagnostics.
- `frontend/src/components/domain/RequestDiagnosticsPanel.tsx`
  - Optional only if the existing panel is reused and needs small presentation adjustments.
- `frontend/src/types/app.ts`, `frontend/src/types/request-log.ts`, `frontend/src/types/openai.ts`
  - Only if current types are inconsistent with existing API clients/tests. Do not change backend DTO semantics.

Files that should not change unless a blocking mismatch is found:

- Backend Java source/tests.
- Database migrations.
- `scripts/demo-smoke.ps1`.
- Request-log output-preview API/client behavior.
- Admin auth / shell auth identity logic.

## Required Tests

Targeted first:

```bash
cd frontend
cmd /c npx vitest run src/__tests__/pages/SmokeTestPage.test.tsx
```

Then frontend quality gates:

```bash
cd frontend
cmd /c npm run lint
cmd /c npm run typecheck
cmd /c npm run test
cmd /c npm run build
```

Visual/browser validation:

```bash
cd frontend
cmd /c npm run test:visual
```

Use `test:visual` if the smoke page refactor changes shared layout/theme assumptions or if manual browser review finds likely responsive/overflow risk. The current visual baseline covers login theme only, so it is useful as a regression guard but not sufficient proof of smoke-page layout by itself.

Backend Maven tests are not required if the implementation remains frontend-only and does not change API contracts, backend DTOs, Java code, migrations, deployment, auth, gateway, request-log persistence, retrieval, or readiness computation.

Also run:

```bash
git diff --check
```

## Test Assertion Points

Smoke page tests should mock typed API clients and assert:

- `listApps(undefined)` is used for app selection.
- No-app/empty-app state renders a clear next action and disables smoke execution.
- Readiness not ready renders failed prerequisite and action hint.
- READY readiness plus pasted key enables the main non-streaming smoke action.
- Successful non-streaming smoke renders response metadata and content length, not answer text.
- Request-log validation is unavailable before non-streaming success and available afterward.
- Request-log evidence renders request id, model, provider, latency, message count, hit chunk ids, and hit chunk metadata.
- Hit chunk `summary`, chunk content, prompt, messages, output preview, keys, hashes, provider body, storage path, and stack trace strings are absent from DOM.
- Streaming missing `[DONE]` produces a failure/action hint.
- Revoked-key optional check handles disabled/skipped and 401 `invalid_api_key`.
- i18n dictionary parity remains intact.

## Planning Self-Check

- Acceptance criteria are explicit in the Information Architecture, Validation Matrix, and Test Assertion Points sections.
- Forbidden scope is explicit in Non-Goals and Files Likely To Modify.
- Expected files are listed.
- Required tests are listed.
- Concrete guidelines read before this PRD: project spec, frontend directory/component/hook/state/type/quality, RAG security, backend logging/error handling, retrieval quality, cross-layer thinking, and code reuse.
- No API/DB/backend contract change is planned.
- No user clarification is currently needed; the task scope is sufficiently bounded as frontend IA/flow refactor with existing API contracts.
