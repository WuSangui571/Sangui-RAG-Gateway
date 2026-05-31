# RAG Request Log and Retrieval Observability Admin API

## Task Classification

Complex Task.

Reason: this introduces new Admin API contracts, tenant authorization boundaries, request-log query behavior, JSONB parsing, optional chunk-summary observability, tests, and spec updates across backend and future frontend type contracts.

## Current Project State

The previous RAG Retrieval and Prompt Augmentation Baseline is complete.

- `POST /v1/chat/completions` now performs API key auth, app/model/KB resolution, query embedding, tenant-scoped pgvector retrieval, RAG prompt augmentation, upstream forwarding, and request log persistence.
- `rag_request_log.question_summary` stores a bounded last-user-message prefix.
- `rag_request_log.hit_chunk_ids` stores retrieval hit chunk IDs as JSONB text such as `[9,8]`.
- Manual acceptance verified Chinese and English RAG queries returning grounded answers, and DB inspection confirmed non-empty `hit_chunk_ids`.
- The practical gap is observability: debugging currently requires `docker exec psql` queries instead of safe Admin APIs.

## Goal

Expose safe Admin APIs for request logs and retrieval hit observability so an admin user can inspect RAG calls for their own apps without exposing full prompts, user messages, API keys, upstream keys, provider raw bodies, embedding vectors, or full chunk content.

## Product Scope

In scope:

- Add request log list API under an app:
  - `GET /api/admin/apps/{appId}/request-logs`
  - Supports pagination, `status`, `error_code`, and time range filters.
- Add request log detail API:
  - Recommended route: `GET /api/admin/apps/{appId}/request-logs/{requestId}`
  - Alternative acceptable route only if justified by local patterns: `GET /api/admin/request-logs/{requestId}` with app ownership validation.
- Add optional safe hit chunk summary API:
  - Recommended route: `GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks`
  - Uses persisted `hit_chunk_ids`, validates the log belongs to the current user and app, validates the app's bound KB when resolving chunks, and returns only safe chunk metadata and short summaries.
- Add DTO/VO/query classes and mapper/service methods needed for these APIs.
- Add tests for tenant isolation, filtering, pagination, JSONB reading, and sensitive-field non-disclosure.
- Update Trellis specs for backend database/logging/error/quality and frontend type-safety contracts.

Out of scope:

- No frontend page implementation.
- No changes to retrieval ranking, thresholds, prompt building, no-hit policy, embeddings, ingestion, async queues, retries, rerank, hybrid search, citations, PDF/DOCX parsing, or gateway response behavior.
- No new database table.
- No persistence of complete prompt, complete user messages, chunk content in logs, app API key plaintext/hash, upstream key plaintext/encrypted, Authorization header, provider raw body, stack traces, or embedding vectors.
- No system-admin/global log API; all APIs are scoped by `X-Admin-User-Id` and app ownership.

## API Contract

All Admin endpoints:

- Use `ApiResponse<T>`.
- Require `X-Admin-User-Id: <positive long>`.
- Return safe admin errors through existing `BusinessException` and `GlobalExceptionHandler`.
- Use snake_case JSON fields via `@JsonProperty`, matching existing Admin VO style.

### List Request Logs

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=20&status=success&error_code=upstream_error&start_time=2026-05-31T00:00:00&end_time=2026-06-01T00:00:00
X-Admin-User-Id: 100
```

Query fields:

| Field | Required | Type | Rule |
|---|---:|---|---|
| `page` | no | integer | Default `1`; must be positive. |
| `page_size` | no | integer | Default `20`; must be `1..100`. |
| `status` | no | string | Only `success` or `failure`; case-insensitive input may be normalized. |
| `error_code` | no | string | Optional exact match; blank means no filter; do not echo arbitrary invalid values. |
| `start_time` | no | ISO local datetime | Inclusive lower bound on `created_at`. |
| `end_time` | no | ISO local datetime | Exclusive or inclusive upper bound must be documented consistently; recommended inclusive for UI simplicity. |

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "request_id": "req-001",
        "app_id": 11,
        "api_key_id": 30,
        "model": "deepseek-v4-pro",
        "provider_name": "sanguicode",
        "status": "success",
        "error_code": null,
        "latency_ms": 1234,
        "upstream_latency_ms": 1100,
        "usage": {
          "prompt_tokens": 10,
          "completion_tokens": 20,
          "total_tokens": 30
        },
        "messages_count": 2,
        "question_summary": "bounded prefix only",
        "hit_chunk_ids": [9, 8],
        "created_at": "2026-05-31T12:00:00"
      }
    ],
    "page": 1,
    "page_size": 20,
    "total": 1
  }
}
```

Notes:

- `hit_chunk_ids` must be returned as an array of numbers, not raw JSONB text.
- `usage` may be null or contain null token fields if upstream usage is unavailable.
- List items may omit `updated_at` unless detail needs it; keep contract documented.

### Request Log Detail

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
X-Admin-User-Id: 100
```

Response fields:

| Field | Required | Notes |
|---|---:|---|
| `id` | yes | Internal numeric row ID is safe. |
| `request_id` | yes | Stable request UUID/string. |
| `user_id` | yes | Same current admin user only. |
| `app_id` | yes | Must equal path appId. |
| `api_key_id` | yes | Safe key metadata ID only. |
| `model` | no | Resolved chat model if available. |
| `provider_name` | no | Provider display name if available. |
| `status` | yes | `success` or `failure`. |
| `error_code` | no | Stable gateway error code. |
| `latency_ms` | no | Total elapsed time. |
| `upstream_latency_ms` | no | Upstream elapsed time. |
| `usage` | no | prompt/completion/total tokens. |
| `messages_count` | no | Count only. |
| `question_summary` | no | Bounded prefix only. |
| `hit_chunk_ids` | no | Array of long IDs. |
| `created_at` | yes | Timestamp. |
| `updated_at` | yes | Timestamp. |

Explicitly forbidden response fields:

- `prompt`
- `messages`
- `full_messages`
- `augmented_prompt`
- `api_key`
- `key_hash`
- `authorization`
- `upstream_api_key`
- `api_key_encrypted`
- `chunk_content`
- `embedding`
- `provider_response_body`
- `stack_trace`

### Hit Chunk Safe Summary

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
X-Admin-User-Id: 100
```

Response:

```json
{
  "code": "OK",
  "message": "success",
  "data": [
    {
      "chunk_id": 9,
      "document_id": 6,
      "knowledge_base_id": 6,
      "source_filename": "manual-kb-unique.md",
      "chunk_index": 0,
      "summary": "first N safe characters only"
    }
  ]
}
```

Rules:

- This endpoint is optional per original requirement, but recommended because it closes the "which source was hit" debugging loop.
- It must first load the request log by `user_id + app_id + request_id`.
- It must parse `hit_chunk_ids`; null/empty returns an empty list.
- It must only fetch chunks by IDs with SQL or mapper constraints that include `user_id`.
- It must validate chunks are reachable through the app's current/default KB or the log's same user/app boundary. Recommended for current schema: app must have `default_knowledge_base_id`, and fetched chunks must match `user_id` and that KB. If future log rows need historical KB IDs, add a spec note, not a schema change in this task unless unavoidable.
- It may use `rag_document.original_filename` as `source_filename`, or safe JSON metadata if already reliable.
- It must return a short `summary`, not full chunk content. Recommended bound: first 120 or 200 characters. The chosen bound must be a named constant and covered by a test.
- Preserve the original `hit_chunk_ids` order where practical.

## Validation and Error Matrix

| Scenario | HTTP | Code | Required behavior |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler. |
| Non-numeric `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Existing global handler. |
| Non-positive `X-Admin-User-Id` | 400 | `INVALID_REQUEST` | Controller/service validation before DB query. |
| `appId` does not exist | 404 | `NOT_FOUND` | `App not found`. |
| `appId` belongs to another user | 403 | `FORBIDDEN` | Generic `Access denied`; do not return any log data. |
| Invalid `page` | 400 | `INVALID_REQUEST` | No mapper query. |
| Invalid `page_size` | 400 | `INVALID_REQUEST` | No mapper query. |
| Invalid `status` | 400 | `INVALID_REQUEST` | Only `success`/`failure`. |
| Invalid time range parse | 400 | `INVALID_REQUEST` | Safe message; do not echo raw arbitrary string. |
| `start_time` after `end_time` | 400 | `INVALID_REQUEST` | No mapper query. |
| Valid filters, no results | 200 | `OK` | Empty `items`, correct pagination metadata. |
| Log request ID missing under owned app | 404 | `NOT_FOUND` | `Request log not found`. |
| Log request ID exists under another app/user | 404 or 403 | Prefer 404 for request ID enumeration safety unless existing app ownership already failed as 403. |
| `hit_chunk_ids` null/empty | 200 | `OK` | Empty chunk summary list. |
| `hit_chunk_ids` malformed despite DB contract | 500 or 400 | Prefer fail-visible internal error in service test; do not silently return fake success. |
| Chunk ID belongs to another user/KB | 200 | `OK` | Omit unauthorized chunk; test must ensure it is not returned. Consider logging a WARN with safe IDs. |

## Good / Base / Bad Cases

Good cases:

- Same user lists logs for own app with default pagination.
- Same user filters by `success`, `failure`, `error_code`, and time range.
- Same user opens detail and sees safe fields, usage, `question_summary`, and numeric `hit_chunk_ids`.
- Same user fetches hit chunk summaries and sees chunk ID, document ID, source filename, chunk index, and bounded summary.

Base cases:

- Owned app with no logs returns empty page.
- No-hit request log (`hit_chunk_ids = null`) returns detail with empty/null hit array and hit-chunks endpoint returns `[]`.
- Streaming request logs may have null usage and still display safely.
- Failure logs may have null model/provider/usage and still display safely.

Bad cases:

- Cross-user app access returns 403 and never calls log/chunk query service.
- Guessing another app's request ID does not return log detail.
- Malformed/invalid pagination and filters return 400.
- Responses do not include full prompt/messages/chunk content/keys/provider bodies.
- JSONB `hit_chunk_ids` is read and parsed as numeric IDs, not leaked as a raw string.

## Expected Backend Design

Recommended files to add:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogQuery.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogPageVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/RequestLogUsageVO.java
backend/src/main/java/com/sangui/raggateway/log/vo/HitChunkSummaryVO.java
backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java
```

Likely files to modify:

```text
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogMapper.java
backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java
backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java
.trellis/spec/sangui-rag-gateway.md
.trellis/spec/backend/database-guidelines.md
.trellis/spec/backend/error-handling.md
.trellis/spec/backend/logging-guidelines.md
.trellis/spec/backend/quality-guidelines.md
.trellis/spec/frontend/type-safety.md
```

Possible but not required:

```text
backend/src/main/java/com/sangui/raggateway/common/response/PageVO.java
```

Use a common page VO only if it stays generic and does not disturb existing APIs.

## Required Tests and Assertion Points

Targeted controller tests:

- `GET /api/admin/apps/{appId}/request-logs` returns paged logs for the same user.
- List supports `status`, `error_code`, `start_time`, `end_time`, `page`, `page_size`.
- Invalid status/page/page_size/time range returns `400 INVALID_REQUEST`.
- Missing app returns `404 NOT_FOUND`.
- Cross-user app returns `403 FORBIDDEN` and does not query logs.
- List response does not contain forbidden sensitive field names or values.

Targeted service/mapper tests:

- Query scopes by `user_id` and `app_id`.
- Pagination applies `limit` and `offset` correctly.
- Count query uses same filters as list query.
- JSONB `hit_chunk_ids` like `[8,9]` maps to `List<Long>` in VO.
- Null `hit_chunk_ids` maps to empty or null according to documented VO contract.
- Malformed `hit_chunk_ids` fails visibly and is covered by a unit test if parser logic exists.

Hit chunk tests:

- Same-user hit chunks return only chunk ID, document ID, KB ID, filename, index, and bounded summary.
- Summary length is bounded by a named constant.
- Full chunk `content`, `storage_path`, embeddings, and metadata internals are not returned.
- Cross-user/chunk mismatch cannot leak chunk details.
- Original hit order is preserved when chunks are returned.

Regression tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppAdminControllerTest,DocumentAdminControllerTest,RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn test
```

Backend unit tests must complete within the project rule of 60 seconds for targeted runs where practical.

## Spec Update Requirements

Update these specs after implementation:

- `.trellis/spec/sangui-rag-gateway.md`: document new Admin APIs, safe fields, limitations, and observability use case.
- `.trellis/spec/backend/database-guidelines.md`: document request-log Admin query rules and `hit_chunk_ids` JSONB read contract; note no new table expected.
- `.trellis/spec/backend/error-handling.md`: add Admin request-log API error matrix.
- `.trellis/spec/backend/logging-guidelines.md`: document exposed safe observability fields and forbidden fields.
- `.trellis/spec/backend/quality-guidelines.md`: add required request-log Admin API tests.
- `.trellis/spec/frontend/type-safety.md`: add future `ApiRequestLogVO`, `ApiRequestLogDetailVO`, `RequestLogUsageVO`, `HitChunkSummaryVO`, and page response contracts.

## Implementation Notes

- Reuse the existing temporary Admin identity contract: `X-Admin-User-Id`.
- Validate app ownership before querying logs or chunks.
- Prefer SQL/mapper-level tenant constraints for log and chunk queries; do not rely on Java-only tenant filtering.
- Keep service responsibilities: controller validates HTTP basics and app ownership, service performs query construction/parsing/VO conversion, mapper performs scoped DB access.
- Since existing services are annotated `@Profile("!test")` but controller tests use standalone MockMvc, mirror the current test style.
- Do not add hidden fallback behavior. If parsing `hit_chunk_ids` fails, surface a clear failure in code/tests rather than pretending there are no hits.

## Acceptance Criteria

- [ ] `GET /api/admin/apps/{appId}/request-logs` exists and returns paginated, filtered, safe request-log summaries for owned apps only.
- [ ] `GET /api/admin/apps/{appId}/request-logs/{requestId}` exists and returns safe detail for an owned app log only.
- [ ] Optional but recommended hit chunk summary endpoint exists and returns safe bounded summaries without full content.
- [ ] Cross-user app access is rejected with `403 FORBIDDEN`.
- [ ] Log request ID from another app/user cannot leak detail.
- [ ] `hit_chunk_ids` JSONB is parsed into numeric IDs in API responses.
- [ ] Sensitive fields are absent from list/detail/hit chunk responses.
- [ ] Required targeted tests pass.
- [ ] Relevant specs are updated.
