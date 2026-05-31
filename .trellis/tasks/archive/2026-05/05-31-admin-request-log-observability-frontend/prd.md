# Admin 请求日志观测前端页面

## Task Classification

Complex Task.

Reason: this is primarily frontend implementation, but it consumes a backend Admin API contract with sensitive observability fields, tenant-aware error behavior, pagination/filtering, and browser smoke-test requirements. The current `frontend/` directory is empty except for `.gitkeep`, so implementation must create the minimal admin console frontend baseline needed for this workflow instead of assuming existing routes or components.

## Goal

Add an Admin Console request-log observability page for app-level RAG request logs so operators can inspect request outcomes, latency, token usage, safe question summaries, hit chunk IDs, and bounded hit chunk summaries from the browser without PowerShell/curl.

The page must help debug gateway usage while preserving the existing security boundary: never display prompt/full messages/full document chunk content/API keys/key hashes/upstream keys/provider bodies/stack traces.

## Product Boundary

This feature serves the lightweight OpenAI-compatible RAG gateway by making the already-implemented request log Admin API usable from an operational frontend.

This task must not expand the product into a chat playground, low-code workflow builder, analytics platform, or document-content browser.

## Current State

- Backend request-log Admin API is implemented and stable.
- Project spec documents the API contract and safe field boundary.
- `frontend/` currently contains only `.gitkeep`; there is no existing Vite app, router, API client, or UI library setup.
- This task may create a minimal frontend app structure under `frontend/` for the request-log workflow.
- Existing untracked local data under `backend/data/uploads/knowledge/7/` is manual test/upload state and must not be touched by this task.

## Scope

In scope:

- Create or extend the frontend admin console baseline only as needed for request-log observability.
- Add typed request log API client and TypeScript types matching backend snake_case VO fields.
- Add an App-scoped request-log list page.
- Support pagination, `status`, `error_code`, `start_time`, and `end_time` filters.
- Add a request-log detail drawer or detail page.
- Display usage, latency, `question_summary`, and `hit_chunk_ids`.
- Add hit chunks panel using the hit-chunks endpoint.
- Cover loading, empty, error, and retry states.
- Display 400/403/404 Admin API errors clearly using the backend `ApiResponse` envelope.
- Run frontend validation checks and a browser smoke test.

Out of scope:

- No backend API, DTO/VO, mapper, DB, migration, RAG, retrieval, prompt, storage, or auth changes.
- No changes to request-log persistence behavior.
- No full admin auth implementation; keep the temporary `X-Admin-User-Id` contract expected by existing Admin APIs.
- No document preview/full chunk content page.
- No prompt/messages viewer.
- No charts/analytics dashboard beyond the requested table/detail workflow.
- No new API fields beyond the implemented backend contract.
- No secret display or persistence.

## API Contract

All endpoints use the Admin `ApiResponse<T>` envelope and require:

```http
X-Admin-User-Id: <positive numeric user id>
```

### List Request Logs

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=20&status=success&error_code=upstream_error&start_time=2026-05-31T00:00:00&end_time=2026-06-01T00:00:00
```

Query fields:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `page` | number | no | Default `1`; backend rejects `< 1`. |
| `page_size` | number | no | Default `20`; allowed `1..100`. |
| `status` | `success \| failure` | no | Backend accepts case-insensitive input and normalizes. |
| `error_code` | string | no | Exact match; blank omitted. |
| `start_time` | ISO local datetime string | no | Inclusive; format like `2026-05-31T00:00:00`. |
| `end_time` | ISO local datetime string | no | Inclusive; must not be before `start_time`. |

Response data:

```ts
interface ApiRequestLogPageVO<T> {
  items: T[];
  page: number;
  page_size: number;
  total: number;
}

interface ApiRequestLogVO {
  id: number;
  request_id: string;
  app_id: number;
  api_key_id: number;
  model: string | null;
  provider_name: string | null;
  status: 'success' | 'failure';
  error_code: string | null;
  latency_ms: number | null;
  upstream_latency_ms: number | null;
  usage: RequestLogUsageVO | null;
  messages_count: number | null;
  question_summary: string | null;
  hit_chunk_ids: number[];
  created_at: string;
}

interface RequestLogUsageVO {
  prompt_tokens: number | null;
  completion_tokens: number | null;
  total_tokens: number | null;
}
```

### Detail Request Log

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
```

Response data:

```ts
interface ApiRequestLogDetailVO extends ApiRequestLogVO {
  user_id: number;
  updated_at: string;
}
```

### Hit Chunk Summaries

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

Response data:

```ts
interface HitChunkSummaryVO {
  chunk_id: number;
  document_id: number;
  knowledge_base_id: number;
  source_filename: string | null;
  chunk_index: number;
  summary: string | null;
}
```

## Forbidden Frontend Fields

Do not type, request, render, log, or create UI placeholders for these fields:

```text
prompt, messages, full_messages, augmented_prompt, api_key, key_hash, authorization,
upstream_api_key, api_key_encrypted, chunk_content, embedding, provider_response_body,
stack_trace, storage_path
```

The hit chunks panel may show `summary` only. It must not imply this is full chunk content.

## Validation / Error Matrix

| Scenario | Expected UI behavior |
|---|---|
| Missing/invalid local Admin user ID input | Prevent request or show visible validation error before calling backend. |
| Backend `400 INVALID_REQUEST` for invalid page/page_size/status/time format/time range | Show backend error message and keep current filter values editable. |
| Backend `403 FORBIDDEN` for cross-user app | Show access denied state; do not retry automatically. |
| Backend `404 NOT_FOUND` for missing app or request log | Show not found state; list page should remain usable. |
| Valid filters with no logs | Show explicit empty state, not a spinner or error. |
| Hit chunks with empty/null `hit_chunk_ids` | Show empty hit chunks state. |
| App has no default KB and hit chunks endpoint returns `400 INVALID_REQUEST` | Show clear hit-chunks panel error; detail itself should remain visible. |
| Network/server failure | Show error state with retry action; do not fake success or silently clear data. |

## Good / Base / Bad Cases

Good cases:

- Given an app ID and admin user ID with logs, the list loads with default pagination and safe columns.
- Applying `status=failure`, `error_code`, and date range refreshes the list and preserves pagination metadata.
- Opening a row loads detail and hit chunks; usage, latency, question summary, hit chunk IDs, and bounded summaries are visible.

Base cases:

- Empty log list returns `items=[]` and `total=0`; UI shows an empty state.
- `usage` is null; UI displays an unobtrusive empty value rather than crashing.
- `model`, `provider_name`, `error_code`, latency fields, and `question_summary` can be null.
- `hit_chunk_ids=[]`; hit chunks panel shows no hit chunks.

Bad cases:

- Invalid filters return 400 and visible error.
- Cross-user app returns 403 and visible access denied.
- Missing app or request log returns 404 and visible not found.
- Backend response must not expose forbidden fields; frontend must not render them even if future responses accidentally include extra keys.

## UX Requirements

- The first meaningful screen should be the request log workflow, not a marketing landing page.
- Use dense admin patterns: filters, table, pagination, drawer/detail panel, status tags, and compact description sections.
- Columns should prioritize scanability: created time, status, error code, model/provider, latency, usage total, question summary, hit count, action.
- Status must not rely on color alone.
- Long `question_summary` and `summary` text must wrap or clamp without overlapping table/detail controls.
- Detail drawer/page should clearly label safe summaries and avoid wording that implies full prompt or full document content is available.
- Error messages should be visible and actionable.

## Expected File Areas

Because the frontend is empty, implementation will likely add:

```text
frontend/package.json
frontend/index.html
frontend/vite.config.ts
frontend/tsconfig.json
frontend/src/main.tsx or frontend/src/main.ts
frontend/src/app/router/*
frontend/src/api/http.ts
frontend/src/api/request-logs.ts
frontend/src/types/common.ts
frontend/src/types/request-log.ts
frontend/src/pages/request-logs/*
frontend/src/components/domain/*
frontend/src/styles/*
```

The exact framework may be Vue 3 or React, but keep it consistent. Prefer the smallest practical Vite + TypeScript admin baseline. Do not mix frameworks.

## Required Tests and Assertion Points

Automated checks:

- Frontend typecheck must pass.
- Frontend build must pass.
- If tests are added, cover request log API client URL/query construction and/or UI states where practical.

Browser smoke test:

- Start backend/frontend as needed.
- Open the request log page in a real browser.
- Verify list page can load or show a controlled empty/error state.
- Verify filters are usable.
- Verify opening detail triggers detail and hit chunks UI.
- Verify safe fields are displayed and forbidden fields are not present in the UI.

Suggested manual backend data path if sample data is needed:

- Use existing backend manual/test data or create a normal RAG request through `/v1/chat/completions` to generate logs.
- Do not insert fake frontend-only success paths.

## Implementation Notes For DeepSeek

- Match backend snake_case payload fields exactly. Do not convert API response types to camelCase unless the existing frontend baseline establishes a mapper pattern; there is currently no baseline.
- Keep all request-log server state local to the page/hook. Do not introduce a global store for logs.
- Use the existing temporary Admin identity header. If adding a simple local input for `appId` and `adminUserId`, keep it explicit and easy to change.
- The frontend API client should unwrap `ApiResponse<T>` consistently and preserve `code`/`message` for error display.
- Do not swallow backend errors or replace them with silent defaults.
- Do not log API payloads to console because request logs contain user question summaries and operational IDs.

## Acceptance Criteria

- [ ] Request log TypeScript types match the implemented backend VO fields and forbidden fields are absent.
- [ ] Typed API client supports list/detail/hit-chunks endpoints with `X-Admin-User-Id`.
- [ ] App-scoped request log list page supports pagination and filters for `status`, `error_code`, `start_time`, `end_time`.
- [ ] Detail drawer/page displays usage, latency, `question_summary`, and `hit_chunk_ids`.
- [ ] Hit chunks panel displays `source_filename`, `chunk_index`, and bounded `summary`.
- [ ] Loading, empty, error, 400, 403, and 404 states are handled visibly.
- [ ] UI does not display prompt/full messages/chunk_content/API keys/key hashes/upstream keys/provider bodies/stack traces.
- [ ] Frontend typecheck/build pass.
- [ ] Browser smoke test verifies list, detail, and hit chunks behavior.
