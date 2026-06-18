# P1 Source Citations + Retrieval Evaluation

## Task Classification

Complex Task.

Reason: this crosses retrieval, prompt construction, OpenAI-compatible response shape, request-log evidence, admin/frontend observability, possible database migration, and test data. Codex planning scope only: do not implement business code in this pass.

## Current Project State

- Branch: `feature/source-citations-retrieval-evaluation`.
- Working tree was clean before task creation.
- Previous recorded task: Async Document Processing, completed on 2026-06-18 with commit `6e3c9fe8 feat:async-document-processing` and session record `8b154853`.
- Async ingestion is now durable: upload returns after file save + document row + processing task enqueue, worker/scheduler handles parse/chunk/embed, retry and frontend polling are in place.
- Existing RAG path already has vector retrieval, prompt augmentation, `hit_chunk_ids` request-log persistence, request-log admin APIs, hit-chunk summaries, and smoke/request-log evidence UI.

## Goal

Make RAG answers traceable to bounded source metadata and establish a small retrieval evaluation baseline, without breaking the existing OpenAI-compatible `/v1/chat/completions` contract or exposing private document content by default.

The outcome should let operators answer:

- Which retrieved chunks were injected for a gateway answer?
- Which document/source metadata is safe to show as citations?
- Did retrieval return the expected chunks for a small baseline set?
- Are request logs and admin evidence sufficient for debugging retrieval quality without leaking full prompts, messages, chunk content, keys, provider bodies, storage paths, or embeddings?

## Non-Goals

- Do not build a Dify/FastGPT-style workflow, agent, evaluation platform, or full analytics dashboard.
- Do not implement hybrid search, rerank, query rewrite, multi-query retrieval, BM25, or LLM-as-judge unless a later task explicitly defines them.
- Do not expose full chunk content, full augmented prompts, request messages, provider responses, embeddings, raw SSE payloads, storage paths, or secrets in normal responses/logs.
- Do not force citations into every answer by brittle text post-processing.
- Do not change the default `/v1/chat/completions` response shape for existing clients.
- Do not make streaming citation behavior a required P1 deliverable beyond request-log/admin evidence persistence.
- Do not change async ingestion state machines except for source metadata needed for citation labels.

## Proposed Contract

### Citation Object

Use one bounded citation metadata object across retrieval, prompt context, optional response extension, request-log evidence, and admin display.

Fields:

| Field | Type | Required | Boundary |
|---|---|---:|---|
| `citation_id` | string | yes | Stable per response/run, e.g. `S1`, `S2`. Not a database primary key. |
| `chunk_id` | number | yes | Existing `rag_document_chunk.id`. Safe ID. |
| `document_id` | number | yes | Existing `rag_document.id`. Safe ID. |
| `knowledge_base_id` | number | yes | App-bound KB only. Safe ID. |
| `source_filename` | string/null | yes | Safe original basename only; never storage path. |
| `chunk_index` | number/null | yes | Existing chunk order within document. |
| `similarity` | number/null | yes | Bounded score used for audit/eval; do not expose vector. |
| `metadata` | object/null | no | Only allow safe chunk metadata keys, initially `source` and `parser`; never raw content/storage/keys. |
| `content_chars` | number/null | no | Length metadata only. |
| `injected_chars` | number/null | no | Length after truncation/budgeting. |

Forbidden citation fields:

```text
content, chunk_content, summary by default, embedding, prompt, messages,
full_messages, augmented_prompt, api_key, key_hash, authorization,
upstream_api_key, api_key_encrypted, provider_response_body, stack_trace,
storage_path, raw_sse, environment
```

### Retrieval Evidence

Extend retrieval output from only `chunks + hitChunkIds` to include citation/evidence metadata for the final injected chunks.

Required invariants:

- Citation/evidence order must match final prompt injection order.
- Citation IDs are assigned only after threshold, deduplication, max chunk count, max total context chars, and per-chunk truncation are applied.
- `hit_chunk_ids` remains the lightweight compatibility field and must match citation `chunk_id` order.
- Retrieval SQL must remain scoped by `user_id` and `knowledge_base_id` before vector ordering.
- Source filename comes from safe document metadata, not `storage_path`.
- If document lookup for filename is unavailable, citation may use null `source_filename`, but must not fabricate a filename.

### Prompt Construction

Change prompt context from raw `[Chunk 42]` labels to source labels, for example:

```text
[S1] source="<filename or unknown>" document_id=10 chunk_index=3 similarity=0.842
<bounded chunk content>
```

Prompt rules:

- Preserve original messages and original user system prompt.
- Keep RAG context as a separate system message.
- Tell the model to use citation labels only when supported by provided context.
- Tell the model not to fabricate citation labels or sources.
- For no-hit `STRICT_RAG`, keep explicit insufficient-evidence instruction and do not create citations.
- Do not log the full augmented prompt.

Important: the model may or may not include inline `[S1]` labels in natural language. The reliable machine-readable trace is the citation metadata generated by retrieval, not post-hoc answer parsing.

### Public `/v1/chat/completions` Compatibility

Default behavior:

- Existing clients receive the same OpenAI-compatible response shape.
- No `sangui_citations` field is returned unless explicitly requested.

Opt-in behavior:

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
X-Sangui-Return-Citations: true
```

When the opt-in header is true and `stream` is absent or false, add a top-level extension field:

```json
{
  "id": "chatcmpl-test",
  "object": "chat.completion",
  "created": 1710000000,
  "model": "deepseek-v4-pro",
  "choices": [...],
  "usage": {...},
  "sangui_citations": [
    {
      "citation_id": "S1",
      "chunk_id": 8,
      "document_id": 4,
      "knowledge_base_id": 2,
      "source_filename": "handbook.md",
      "chunk_index": 0,
      "similarity": 0.842,
      "metadata": {"source": "handbook.md", "parser": "markdown"},
      "content_chars": 612,
      "injected_chars": 612
    }
  ]
}
```

Rules:

- Existing non-opt-in tests must assert `$.sangui_citations` does not exist.
- Opt-in response must not include chunk text/summary.
- For no hits, return `sangui_citations: []` only when opt-in is true.
- For `stream=true`, do not add a P1 streaming citation event. Persist request-log evidence and expose admin evidence after completion. A future task may define an explicit SSE extension.
- The request body remains compatible with the existing supported OpenAI subset; do not require non-standard body fields.

### Request Log / Evidence Fields

Add a bounded request-log evidence field while preserving `hit_chunk_ids`:

```sql
ALTER TABLE rag_request_log
  ADD COLUMN IF NOT EXISTS retrieval_evidence JSONB;
```

`retrieval_evidence` shape:

```json
{
  "version": 1,
  "no_hits": false,
  "retrieval_latency_ms": 42,
  "top_k": 5,
  "similarity_threshold": 0.3,
  "max_context_chunks": 5,
  "citations": [
    {
      "citation_id": "S1",
      "chunk_id": 8,
      "document_id": 4,
      "knowledge_base_id": 2,
      "source_filename": "handbook.md",
      "chunk_index": 0,
      "similarity": 0.842,
      "content_chars": 612,
      "injected_chars": 612
    }
  ]
}
```

Rules:

- Store metadata only; no content, summaries, prompts, messages, provider bodies, embeddings, keys, or storage paths.
- Malformed `retrieval_evidence` JSON must fail visibly in VO parsing/tests; do not silently fabricate evidence.
- Normal request-log list/detail may return retrieval evidence metadata if bounded. Hit-chunk summaries remain a separate endpoint and keep current tenant/app/KB checks.
- If `retrieval_evidence` is absent for old rows, VOs return `null` or an empty evidence object explicitly; do not treat old rows as retrieval success.

### Admin Evidence API / Frontend

Expected backend API options:

1. Reuse existing:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

2. Add focused evidence endpoint only if detail VO would become too noisy:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}/retrieval-evidence
```

Preferred P1: add safe retrieval evidence to detail VO and keep hit-chunk summaries in existing endpoint, unless implementation shows a strong reason to split.

Frontend/admin updates should be compact:

- Extend request-log detail drawer with citation/evidence metadata.
- Keep hit chunk panel safe and bounded.
- Do not create a new dashboard unless needed for eval baseline.
- Use typed `request-log.ts` models; no `any`.

### Retrieval Evaluation Baseline

Implement a small, deterministic baseline that is useful for regression checks but not a full evaluation platform.

Baseline data:

- Add a small JSON/JSONL sample set under backend test resources or a repo-local evaluation resource.
- Each case includes:
  - `case_id`
  - `query`
  - `expected_chunk_ids` and/or `expected_document_ids`
  - optional `required_source_filename`
  - optional `min_expected_similarity`
  - expected `no_hits`

Evaluation result fields:

| Field | Type | Meaning |
|---|---|---|
| `case_id` | string | Stable eval case id. |
| `query` | string | May be bounded/truncated in admin output. |
| `expected_chunk_ids` | number[] | Ground truth chunk IDs for seeded/local test data. |
| `actual_chunk_ids` | number[] | Retrieval final hit IDs. |
| `expected_document_ids` | number[] | Optional looser ground truth. |
| `actual_document_ids` | number[] | Retrieved document IDs. |
| `hit` | boolean | True when expected chunk/doc match criteria pass. |
| `rank` | number/null | Rank of first expected hit. |
| `precision_at_k` | number | Baseline metric. |
| `recall_at_k` | number | Baseline metric. |
| `mrr` | number | Mean reciprocal rank contribution. |
| `no_hits` | boolean | Whether retrieval returned no hits. |
| `error_code` | string/null | Safe bounded failure code. |

Execution shape:

- P1 may choose either a backend admin endpoint or an offline command/test helper, but must implement only one primary path.
- Preferred if implementation effort is reasonable:

```http
POST /api/admin/apps/{appId}/retrieval-evaluations/runs
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{
  "case_ids": ["case-001"],
  "limit": 20
}
```

Response:

```json
{
  "code": "OK",
  "data": {
    "app_id": 1,
    "knowledge_base_id": 2,
    "case_count": 3,
    "hit_count": 2,
    "precision_at_k": 0.67,
    "recall_at_k": 0.67,
    "mrr": 0.5,
    "cases": [...]
  }
}
```

Validation:

- Missing/cross-user app: existing 404/403 pattern.
- App without READY KB: `400 KNOWLEDGE_BASE_NOT_READY` or admin-safe `INVALID_REQUEST` with clear message.
- Empty sample set: `400 INVALID_REQUEST`.
- Limit must be positive and bounded, e.g. `1..100`.
- Evaluation output must not include chunk content, prompts, keys, provider raw bodies, embeddings, or storage paths.

If the implementation chooses offline command/test helper instead:

- Document the command in README/spec/PRD follow-up notes.
- It must produce the same safe metrics fields.
- It must be runnable in CI/local tests without real provider keys by using mocked retrieval or seeded embeddings.

## API / Command / Payload Field Matrix

### Gateway Citation Opt-In

| Surface | Field/Header | Required | Expected |
|---|---|---:|---|
| `/v1/chat/completions` request | `X-Sangui-Return-Citations` | no | `true` enables response extension for non-streaming. Missing/false keeps default response unchanged. |
| non-streaming response | `sangui_citations` | opt-in only | Array of citation metadata, no content. |
| streaming response | n/a | n/a | No P1 SSE citation extension. Evidence goes to request log/admin. |

### Request Log

| Surface | Field | Required | Expected |
|---|---|---:|---|
| `rag_request_log` | `hit_chunk_ids` | existing | JSONB array of final hit chunk IDs. |
| `rag_request_log` | `retrieval_evidence` | new | JSONB metadata object, no content. |
| detail VO | `retrieval_evidence` | new | Parsed safe metadata or null for old rows. |
| hit-chunks endpoint | existing fields | existing | Safe IDs, filename, chunk_index, bounded summary only. |

### Evaluation

| Surface | Field | Required | Expected |
|---|---|---:|---|
| run request | `case_ids` | no | Optional subset. |
| run request | `limit` | no | Positive bounded default. |
| run response | `case_count`, `hit_count`, `precision_at_k`, `recall_at_k`, `mrr` | yes | Numeric metadata. |
| case result | `expected_*`, `actual_*`, `rank`, `hit`, `error_code` | yes | Safe metadata only. |

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Non-opt-in gateway request with hits | Response shape remains existing OpenAI-compatible shape; `sangui_citations` absent | `OpenAiChatCompletionsControllerTest` |
| Opt-in non-streaming request with hits | Response includes ordered `sangui_citations` metadata only | controller/service test |
| Opt-in no-hit request | Response includes empty citation array; prompt no-hit policy remains strict | service/prompt test |
| `stream=true` opt-in | No SSE citation event in P1; request log still records retrieval evidence | stream controller test |
| Retrieval hit above threshold | Citation order matches final injected chunks and `hit_chunk_ids` | `RetrievalServiceTest` |
| Low-score/duplicate/over-budget chunk | No citation assigned for excluded chunks | retrieval test |
| Cross-user/cross-KB chunk exists | SQL-scoped retrieval prevents it from being returned/cited | mapper/service test |
| Prompt context with citations | Original messages/system prompt preserved; context labels are `[S1]`; no fabricated citations instruction present | `RagPromptBuilderTest` |
| Request-log evidence persisted | `hit_chunk_ids` and `retrieval_evidence` persisted without content/secrets | `ApiRequestLogServiceTest` |
| Malformed evidence JSON | VO parsing fails visibly; no silent fallback | VO/service test |
| Admin request-log detail | Returns safe metadata only; forbidden fields absent | `ApiRequestLogAdminControllerTest` |
| Evaluation valid baseline | Metrics computed from expected vs actual IDs/ranks | eval service/controller test |
| Evaluation cross-user app | 403 before retrieval/eval work | controller test |
| Evaluation no READY KB | safe admin error; no embedding/retrieval loop | controller/service test |
| Provider/embedding failure during eval | Case records safe `error_code`; no provider body or keys | eval service test |

## Good / Base / Bad Cases

Good:

- Ready app + KB + matching chunks + non-streaming citation opt-in returns normal chat completion plus `sangui_citations` metadata. Request log stores `hit_chunk_ids` and `retrieval_evidence`; admin detail shows safe citation metadata; hit-chunks endpoint can show bounded summaries.
- Evaluation baseline runs on a small sample and reports precision/recall/MRR without content or secrets.

Base:

- Non-opt-in client sees no response-shape change.
- No retrieval hits under `STRICT_RAG` still calls upstream with insufficient-evidence prompt, stores empty/no-hit evidence, and optional citation response is empty.
- Old request logs without `retrieval_evidence` render as missing evidence, not as successful citations.

Bad:

- Returning full chunk text in `/v1/chat/completions` citation metadata.
- Logging or persisting full augmented prompt/messages/provider body.
- Assigning citations before threshold/dedup/context-budget filtering.
- Letting the model invent source labels not present in retrieved context.
- Emitting non-standard SSE citation events without an explicit tested streaming contract.
- Evaluation silently passes when retrieval errors, expected sample data is empty, or cross-user app access is attempted.

## Focused Code Research

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, OpenAI-compatible subset, request-log safe metadata.
- `.trellis/spec/backend/directory-structure.md`: retrieval/rag/gateway/log module ownership.
- `.trellis/spec/backend/database-guidelines.md`: tenant-safe vector retrieval, request-log JSONB parsing, migration rules.
- `.trellis/spec/backend/error-handling.md`: public `/v1` OpenAI-compatible errors, admin 403/404/400 patterns, RAG retrieval error codes.
- `.trellis/spec/backend/logging-guidelines.md`: retrieval/request-log safe fields and forbidden content.
- `.trellis/spec/backend/quality-guidelines.md`: required retrieval/prompt/gateway/request-log tests.
- `.trellis/spec/rag/retrieval-quality.md`: scoped SQL, thresholds, no-hit, `hit_chunk_ids`.
- `.trellis/spec/rag/prompt-context-policy.md`: preserve messages, bounded context, no fabricated evidence.
- `.trellis/spec/rag/document-ingestion.md`: chunk identity, safe metadata, async ingestion baseline.
- `.trellis/spec/gateway/resilience.md`: upstream/embedding failure normalization and safe logging.
- `.trellis/spec/security/rag-security.md`: tenant, prompt, evidence, output preview boundaries.
- `.trellis/spec/frontend/type-safety.md`: request-log and hit-chunk frontend DTO rules.
- `.trellis/spec/frontend/state-management.md`: server state and secret-state boundaries.
- `.trellis/spec/frontend/component-guidelines.md`: `SourceCitationList` expected domain component and admin table/detail patterns.
- `.trellis/spec/frontend/quality-guidelines.md`: request-log UX, safe display, typecheck/build expectations.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required API/DB/frontend/test alignment.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: search/reuse before adding parallel concepts.

### Code Patterns Found

- Retrieval SQL and filtering:
  - `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalMapper.java`
  - `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`
  - Current SQL scopes by `e.user_id` + `e.knowledge_base_id`; service applies threshold, dedup, max chunks, total chars, single chunk chars.
- Retrieval result:
  - `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalResult.java`
  - Current `RetrievedChunk` has `chunkId`, `documentId`, `content`, `metadata`, `similarity`; no filename/chunk_index/citation ID yet.
- Prompt builder:
  - `backend/src/main/java/com/sangui/raggateway/rag/prompt/RagPromptBuilder.java`
  - Current prompt labels `[Chunk <id>]` and explicitly tells model not to mention chunk IDs. This must change for citation-aware prompts.
- Gateway orchestration:
  - `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
  - Current service extracts last user message, retrieves, builds prompt, forwards upstream, returns `ChatCompletionResult` with `questionSummary` and `hitChunkIds`.
  - `prepareStreamCompletion` already carries `questionSummary` and `hitChunkIds`.
- OpenAI response model:
  - `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionResponse.java`
  - Unknown upstream fields are ignored; response currently has no gateway extension fields.
- Request log:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java`
  - `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - Current persistence supports `question_summary`, `hit_chunk_ids`, output preview metadata, but no structured retrieval evidence.
- Admin hit chunks:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/log/vo/HitChunkSummaryVO.java`
  - Existing endpoint verifies app ownership, default KB, request-log existence, then returns safe fields plus 200-char summary.
- Frontend request logs:
  - `frontend/src/types/request-log.ts`
  - `frontend/src/api/request-logs.ts`
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
  - `frontend/src/components/domain/HitChunksPanel.tsx`
  - Types and UI already model hit chunk IDs and hit chunk summaries.

### Files Likely To Modify

Backend:

- `backend/src/main/resources/db/migration/V15__add_request_log_retrieval_evidence.sql`: add `retrieval_evidence JSONB` if chosen.
- `backend/src/main/java/com/sangui/raggateway/retrieval/ChunkRow.java`: include `knowledgeBaseId`, `chunkIndex`, safe filename/source fields if loaded in retrieval SQL.
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalMapper.java`: join document or select chunk fields needed for citation metadata while preserving SQL tenant/KB scope.
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalResult.java`: add citation/evidence object(s).
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`: assign citation IDs after final filtering and build metadata.
- `backend/src/main/java/com/sangui/raggateway/rag/prompt/RagPromptBuilder.java`: source labels and no-fabrication citation instructions.
- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionResult.java`: carry citation/evidence metadata.
- `backend/src/main/java/com/sangui/raggateway/gateway/stream/ChatCompletionStreamPreparation.java`: carry retrieval evidence for request log if not already available.
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionResponse.java`: optional `sangui_citations` extension when opt-in.
- `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`: parse opt-in header, add optional citations, persist evidence.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogEntity.java`: `retrievalEvidence`.
- `backend/src/main/java/com/sangui/raggateway/log/CreateRequestLogCommand.java`: builder field for retrieval evidence.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`: entity mapping and safe parsing.
- `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java` and/or `ApiRequestLogDetailVO.java`: safe retrieval evidence VO.
- Optional new eval package under `backend/src/main/java/com/sangui/raggateway/evaluation/` or `retrieval/evaluation/`.
- Optional admin eval controller under `backend/src/main/java/com/sangui/raggateway/retrieval/` or `log/` depending on ownership.

Frontend:

- `frontend/src/types/request-log.ts`: retrieval evidence/citation types.
- `frontend/src/api/request-logs.ts`: request-log detail/evidence API if endpoint changes.
- `frontend/src/components/domain/RequestLogDetailDrawer.tsx`: display safe citation metadata.
- `frontend/src/components/domain/HitChunksPanel.tsx`: optionally align labels with citation IDs.
- Optional `frontend/src/components/domain/SourceCitationList.tsx`: if reused in detail/eval.
- Optional eval UI/API/types if admin endpoint is implemented and user wants visible admin result in this task.
- `frontend/src/app/i18n/dict.ts`: labels for citations/evaluation.

Tests:

- `backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/rag/prompt/RagPromptBuilderTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`
- New retrieval evaluation tests if evaluation service/controller is added.

### Risk / Boundary Notes

- Citation metadata must be generated from actual final retrieval hits, not inferred from model answer text.
- Returning citation metadata by default may break strict OpenAI SDK/client models; keep response extension opt-in only.
- Streaming citation extensions are intentionally out of P1 scope unless a separate tested SSE contract is defined.
- Request-log detail can expose metadata, but content/summary must remain bounded and behind existing hit-chunks behavior; do not make detail return full chunks.
- `source_filename` must come from safe basename (`original_filename`) or chunk metadata, never `storage_path`.
- Evaluation must not depend on live provider credentials in unit tests; mock retrieval or use deterministic seeded data.
- If DB migration is added, update `.trellis/spec/sangui-rag-gateway.md`, backend DB/logging/security/RAG specs, and frontend types/specs accordingly.

## Required Tests And Assertion Points

Backend targeted tests:

```bash
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
```

Add evaluation tests when implemented:

```bash
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
```

Regression compile:

```bash
mvn -q -DskipTests compile
```

Frontend checks if frontend files change:

```bash
cmd /c npm run typecheck
cmd /c npm run build
```

Diff hygiene:

```bash
git diff --check
```

Assertion points:

- Default gateway response has no `sangui_citations`.
- Opt-in non-streaming response has citation metadata and no forbidden fields.
- Citation IDs/order match final retrieval injection and `hit_chunk_ids`.
- Prompt builder preserves original messages/system prompt and uses `[S1]` labels.
- Request logs persist `retrieval_evidence` metadata only.
- Admin request-log APIs remain tenant-scoped and forbidden fields absent.
- Evaluation metrics compute expected hit/rank/precision/recall/MRR and expose safe metadata only.

## Planning Self-Check

- Acceptance criteria defined: yes.
- Forbidden modification scope defined: yes.
- Expected files listed: yes.
- Required tests listed: yes.
- Specific guidelines read, not only indexes: yes.
- Requirement questions needing user confirmation: none blocking. The plan chooses an opt-in citation response extension to avoid default compatibility risk.
- API / DB / frontend types / DTO fields aligned: planned above; implementation must keep them synced.

