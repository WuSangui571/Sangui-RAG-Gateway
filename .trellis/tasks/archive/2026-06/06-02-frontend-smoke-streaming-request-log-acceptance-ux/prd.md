# Frontend Smoke Page Streaming and Request-Log Acceptance UX

## Task Classification

Complex Task.

Reason: this spans frontend smoke UX, `/v1/chat/completions` streaming behavior, admin request-log typed clients, hit-chunk evidence display, security-safe field boundaries, README/spec synchronization, and targeted backend contract verification. It must be planned before implementation and must not expand into new RAG quality, ingestion, backend schema, or gateway behavior work.

## Current Project State Summary

The previous recorded work completed RAG demo acceptance hardening:

- `scripts/demo-smoke.ps1` validates backend/frontend health, non-streaming chat, streaming SSE with `[DONE]`, request-log list/detail/hit-chunks, forbidden fields, and revoked-key `401 invalid_api_key`.
- README and `.trellis/spec/sangui-rag-gateway.md` document the demo acceptance matrix, safe evidence fields, forbidden output fields, split-provider setup, and PowerShell 5.1 smoke flow.
- Manual acceptance passed with live backend/frontend/demo app/READY KB/fresh key/revoked key.
- The current frontend smoke page still mainly exercises non-streaming chat and currently displays the assistant answer body, which conflicts with the stricter demo acceptance rule that smoke output should be safe evidence only.
- Request-log typed client/types and a Request Logs page already exist; this task should reuse them instead of creating duplicate API surfaces.

## Goal

Enhance the frontend Admin Console Smoke Test page so it can perform the demo acceptance checks that currently require manual PowerShell steps:

- Non-streaming chat smoke with safe evidence only.
- Streaming chat smoke with SSE data chunk count and `[DONE]` validation.
- Request-log list/detail/hit-chunks validation from the smoke workflow.
- Optional revoked-key negative auth check with `401 invalid_api_key`.

Keep the PowerShell smoke script as the repeatable CLI/CI-style validation path.

## Non-Goals

- Do not rewrite backend gateway streaming behavior unless an existing API contract is demonstrably inconsistent with the spec.
- Do not add database migrations or new request-log fields.
- Do not change retrieval quality, prompt construction, no-hit policy, ingestion pipeline, document parsing, embedding behavior, provider routing, retry, fallback, or infra.
- Do not turn the Smoke Test page into a chat playground. It is an acceptance/operations surface.
- Do not show assistant answer text, full chunk summaries, private document content, full prompts, API keys, key hashes, upstream keys, provider raw bodies, embeddings, storage paths, or stack traces in the smoke evidence UI.
- Do not remove or weaken `scripts/demo-smoke.ps1`.

## User-Facing Requirements

- Smoke page allows selecting an app and active key reference as it does today.
- Smoke page accepts the full plaintext app API key only in transient in-memory state; it must be clearable and must not be persisted.
- Non-streaming smoke sends `POST /v1/chat/completions` with `stream=false`.
- Non-streaming success displays safe evidence: completion ID, object, model, finish reason, token counts if present, and response content length only.
- Non-streaming success must not render the assistant answer body.
- Streaming smoke sends `POST /v1/chat/completions` with `stream=true`.
- Streaming success displays safe evidence: data line/chunk count, `[DONE]` present/missing, final status, and safe boundary status.
- Streaming failure displays safe OpenAI-compatible error code/status when available, without provider body or raw SSE content beyond bounded diagnostics.
- Smoke page can query request-log list for the selected app/admin user after a successful non-streaming request.
- Request-log validation checks the latest matching success row by `question_summary` prefix and shows only safe evidence: request ID, model, provider_name, latency_ms, messages_count, hit_chunk_ids count/IDs.
- Request-log detail validation checks `request_id` matches and required safe detail fields are present.
- Hit-chunks validation checks the endpoint returns safe metadata: `chunk_id`, `document_id`, `knowledge_base_id`, `source_filename`, `chunk_index`.
- Smoke evidence UI must not display hit chunk `summary` text, even though the general Request Logs detail drawer may continue showing bounded summaries under the existing request-log page contract.
- Optional revoked-key check accepts a revoked key in transient in-memory state and validates HTTP 401 with `error.code=invalid_api_key`; the key value must never be printed or persisted.
- Add clear statuses for pass/fail/skip per step: non-streaming, streaming, request-log list, request-log detail, hit-chunks, revoked-key auth.

## API / Command / Payload Contracts

### Public Gateway Chat API

Endpoint:

```http
POST /v1/chat/completions
Authorization: Bearer <plaintext-app-api-key>
Content-Type: application/json
```

Non-streaming payload:

```json
{
  "model": "ignored-by-gateway",
  "messages": [
    { "role": "user", "content": "<smoke user message>" }
  ],
  "stream": false
}
```

Streaming payload:

```json
{
  "model": "ignored-by-gateway",
  "messages": [
    { "role": "user", "content": "<smoke user message>" }
  ],
  "stream": true
}
```

Non-streaming success fields allowed in Smoke UI:

```text
id, object, model, finish_reason, usage.prompt_tokens,
usage.completion_tokens, usage.total_tokens, content length only
```

Streaming success evidence allowed in Smoke UI:

```text
HTTP status, SSE data line count, SSE chunk count, data: [DONE] present/absent
```

Gateway error shape:

```json
{
  "error": {
    "message": "Specific safe message",
    "type": "invalid_request_error",
    "code": "invalid_api_key"
  }
}
```

### Admin Request-Log APIs

All endpoints require:

```http
X-Admin-User-Id: <admin-user-id>
```

List endpoint:

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
```

List safe fields:

```text
id, request_id, app_id, api_key_id, model, provider_name, status, error_code,
latency_ms, upstream_latency_ms, usage, messages_count, question_summary,
hit_chunk_ids, created_at
```

Detail endpoint:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
```

Detail safe fields include list fields plus:

```text
user_id, updated_at
```

Hit-chunks endpoint:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

Hit-chunks safe metadata:

```text
chunk_id, document_id, knowledge_base_id, source_filename, chunk_index
```

The API may also return `summary` under the existing Admin request-log contract, but Smoke UI must not render or use the summary text as display evidence.

### Revoked-Key Negative Auth

Endpoint:

```http
POST /v1/chat/completions
Authorization: Bearer <revoked-app-api-key>
Content-Type: application/json
```

Expected result:

```text
HTTP 401
error.code = invalid_api_key
```

## Forbidden Fields

Forbidden in Smoke UI types/output/evidence:

```text
key_hash, api_key, api_key_encrypted, upstream_api_key, provider_response_body,
stack_trace, embedding, prompt, messages, full_messages, augmented_prompt,
authorization, storage_path, content, chunk_content, full answer text,
chunk summary text
```

Implementation guidance:

- Do not add these fields to smoke-specific frontend result types.
- Do not stringify raw full responses into the UI.
- Do not render `choices[0].message.content`; compute length and discard display of body.
- Do not render `HitChunkSummaryVO.summary` in Smoke UI.
- Do not log plaintext keys with `console.log` or store them in local/session storage.

## Validation / Error Matrix

| Scenario | Expected UI Result | Boundary | Assertion Point |
|---|---|---|---|
| Active key, READY KB, upstream success, non-streaming | PASS; safe evidence only; content length shown, answer body hidden | upstream | Smoke UI state and typecheck |
| Active key, streaming success | PASS; data chunks counted and `[DONE]` present | upstream | stream parser state |
| Streaming starts but `[DONE]` missing | FAIL with upstream/proxy boundary | upstream/proxy | stream parser state |
| Gateway returns OpenAI-compatible error before stream | FAIL with HTTP status and error code | auth/retrieval/upstream | `SmokeApiError` handling |
| Request-log automation prerequisites missing | SKIP or disabled state; no false pass | request-log | UI control state |
| Request-log list returns no matching success row | FAIL | request-log | list validation |
| Matching list row missing model/provider/latency/hit IDs | FAIL | request-log | field validation |
| Detail request ID differs from list row | FAIL | request-log | detail validation |
| Detail missing `user_id`, `updated_at`, or required safe fields | FAIL | request-log | detail validation |
| Hit-chunks empty for retrieval-hit demo path | FAIL | request-log | hit-chunks validation |
| Hit-chunks include forbidden fields if raw scan is implemented | FAIL | request-log | forbidden-field scanner |
| Revoked key check disabled | SKIP with neutral state | auth | UI state |
| Revoked key check enabled but no revoked key | disabled or FAIL before network call | auth | form validation |
| Revoked key returns 401 `invalid_api_key` | PASS | auth | error parser |
| Revoked key succeeds or returns another code | FAIL | auth | error parser |
| Backend request-log API contract unexpectedly differs from spec | Do not work around silently; fix backend only if contract is wrong | request-log | targeted backend test |

## Good / Base / Bad Cases

Good:

- Prepared demo app with READY KB, enabled chat model config, enabled embedding config, fresh active key, and revoked key.
- User runs Smoke UI full flow.
- Non-streaming passes with content length only.
- Streaming passes with SSE chunk count and `[DONE]`.
- Request-log list/detail/hit-chunks pass and display only safe evidence.
- Revoked-key check passes with `401 invalid_api_key`.

Base:

- User only has an active key and wants quick smoke.
- Non-streaming and streaming can be run without request-log/revoked-key checks.
- Request-log validation is disabled/skipped when app/admin identity is unavailable.
- Revoked-key check is skipped unless user explicitly provides a revoked key and enables the check.

Bad:

- Smoke UI prints the assistant answer body or chunk summary text.
- Smoke UI stores plaintext keys persistently.
- UI accepts streaming success without `[DONE]`.
- UI reports request-log success without verifying a matching recent row and required safe fields.
- UI creates duplicate request-log types/API clients instead of reusing existing ones.
- Backend is changed broadly despite existing API contract being sufficient.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, streaming requirements, Admin request-log API contract, demo acceptance automation rule, safe/forbidden evidence matrix.
- `.trellis/spec/frontend/directory-structure.md`: page/API/type organization and direct fetch prohibition outside typed clients.
- `.trellis/spec/frontend/type-safety.md`: explicit request-log/openai types; forbidden request-log fields must not be modeled.
- `.trellis/spec/frontend/state-management.md`: full keys only in transient local state; server state via typed clients.
- `.trellis/spec/frontend/component-guidelines.md`: operational admin UI, explicit loading/error states, no chat playground drift.
- `.trellis/spec/frontend/quality-guidelines.md`: secret safety, request-log safety, build/typecheck expectations.
- `.trellis/spec/backend/error-handling.md`: OpenAI-compatible gateway errors, request-log Admin API error matrix, streaming pre/post commit rules.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields, forbidden log/response fields, request-log retrieval fields.
- `.trellis/spec/backend/database-guidelines.md`: request-log schema, tenant-scoped request-log and hit-chunk queries.
- `.trellis/spec/backend/quality-guidelines.md`: request-log observability targeted tests.
- `.trellis/spec/gateway/resilience.md`: streaming and upstream error normalization contracts.
- `.trellis/spec/rag/retrieval-quality.md`: hit_chunk_ids and safe retrieval observability boundary.
- `.trellis/spec/security/rag-security.md`: evidence boundary and forbidden sensitive fields.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: cross-layer payload and validation matrix requirements.

## Code Patterns Found

- `frontend/src/pages/smoke/SmokeTestPage.tsx`: current smoke workflow, app/key selection, transient key input, non-streaming call, error rendering.
- `frontend/src/api/openai.ts`: `/v1/chat/completions` typed fetch client and `SmokeApiError` parser for non-streaming.
- `frontend/src/types/openai.ts`: smoke request/response types currently limited to `stream: false`; should be extended or split for streaming.
- `frontend/src/api/request-logs.ts`: existing typed request-log list/detail/hit-chunks client; should be reused.
- `frontend/src/types/request-log.ts`: existing request-log/hit-chunk types; forbidden fields are not modeled, but `summary` exists for general hit-chunk API.
- `frontend/src/pages/request-logs/RequestLogListPage.tsx`: request-log list pattern, filters, detail drawer integration.
- `frontend/src/components/domain/RequestLogDetailDrawer.tsx` and `HitChunksPanel.tsx`: general request-log detail display; note this path currently renders question summary and chunk summary, while Smoke UI must be stricter.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`: admin request-log endpoints and validation.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`: hit chunk summaries and request-log parsing.
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`: safe-field and error-matrix test coverage.
- `scripts/demo-smoke.ps1`: executable acceptance logic to mirror in frontend UX.
- `README.md`: current CLI/manual smoke runbook to synchronize with frontend smoke coverage.

## Files Likely To Modify

Expected implementation files:

- `frontend/src/pages/smoke/SmokeTestPage.tsx`: add streaming step, request-log validation step, revoked-key step, per-step evidence/status UI; remove answer body rendering from smoke result.
- `frontend/src/api/openai.ts`: add streaming chat smoke client using `fetch` + stream reader, with `[DONE]` validation and safe error parsing.
- `frontend/src/types/openai.ts`: add streaming request/evidence result types and allow `stream: true` without weakening non-streaming response typing.
- `README.md`: update frontend smoke page acceptance matrix and keep PowerShell script as CLI/CI repeatable path.
- `.trellis/spec/sangui-rag-gateway.md`: update spec only if frontend smoke page coverage becomes part of executable acceptance contract.

Maybe modify only if implementation needs a small helper:

- `frontend/src/types/request-log.ts`: add smoke-specific safe evidence helper type only if useful; do not add forbidden fields.
- `frontend/src/api/request-logs.ts`: reuse existing functions; change only if a type/client gap is found.

Backend files should not be modified unless contract drift is discovered:

- `backend/src/main/java/com/sangui/raggateway/log/**`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`

## Prohibited Modification Scope

- No database migration.
- No schema changes to `rag_request_log`, `rag_document_chunk`, or API key/model config tables.
- No retrieval SQL changes.
- No prompt construction changes.
- No upstream provider client behavior changes unless streaming contract is broken by an existing bug.
- No changes to API key hashing/encryption/storage.
- No new auth model beyond existing temporary `X-Admin-User-Id`.
- No removal of existing PowerShell smoke script.

## Required Tests And Assertion Points

Frontend:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Backend targeted contract tests:

```bash
cd backend
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
```

Browser/manual smoke:

- Start backend and frontend with prepared demo environment.
- Open Smoke Test page.
- Run non-streaming smoke and verify content length only, no answer body.
- Run streaming smoke and verify `[DONE]` PASS.
- Run request-log validation and verify list/detail/hit-chunks PASS with safe metadata only.
- Run revoked-key check with a revoked key and verify `401 invalid_api_key`.
- Inspect UI text/output for forbidden fields.

Optional Playwright/browser smoke:

- Navigate to the Smoke Test page.
- Verify the page renders controls and does not expose answer/chunk summary placeholders.
- If a live demo key is supplied manually by the user, run full browser smoke; do not hardcode keys in tests or files.

## Planning Self-Check

- Acceptance criteria are explicit for non-streaming, streaming, request-log list/detail/hit-chunks, and revoked-key auth.
- Forbidden modification scope is explicit.
- Expected modified files are listed.
- Required frontend/backend/manual tests are listed.
- Concrete guidelines were read, not only spec indexes.
- No open user clarification is required before Qwen implementation.
- Existing API/types are mostly aligned; key frontend gap is streaming client/types and smoke-specific safe evidence display.
- Existing request-log API returns `summary` for hit chunks, but Smoke UI must not render it.
