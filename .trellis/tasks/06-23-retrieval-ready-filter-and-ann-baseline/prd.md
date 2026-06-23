# Retrieval READY Filter and ANN Baseline

## Goal

Fix the retrieval correctness boundary so vector retrieval only returns chunks whose source document is truly retrievable: `rag_document.status = READY`, with SQL-level `user_id` and `knowledge_base_id` scope preserved. Treat ANN indexing as an evaluation/baseline decision in this task, not an automatic implementation requirement.

## Task Classification

Complex Task.

Reason: the correctness change crosses retrieval SQL, document status semantics, prompt context inputs, request-log `hit_chunk_ids`, `retrieval_evidence`, citations/evaluation behavior, and database indexing assessment. It must be planned as a structural retrieval boundary fix, not a local Java-side filter.

## Scope

### In Scope

- Confirm the current retrieval mapper SQL and service flow.
- Ensure final retrieval candidates come only from:
  - same `user_id`
  - same `knowledge_base_id`
  - source `rag_document.status = READY`
- Apply READY filtering at the SQL/mapper layer by joining `rag_document`; do not add Java post-filtering as the primary protection.
- Preserve existing ranking, similarity computation, threshold filtering, deduplication, truncation, `hit_chunk_ids`, citation order, and retrieval evidence order.
- Add focused tests for READY and non-READY document status behavior.
- Assess ANN readiness from existing schema/migrations and specs.

### Out of Scope

- Do not change public `/v1/chat/completions` request or response shape.
- Do not change Admin API DTO/VO fields.
- Do not change retrieval config defaults such as `top_k`, similarity threshold, or context limits.
- Do not introduce hybrid retrieval, reranking, multi-query retrieval, query rewrite, metadata filters, or prompt format changes.
- Do not add ANN/HNSW/IVFFlat migration unless the code research finds clear existing scale evidence, operator-class choice, and an executable validation plan approved for this task.
- Do not change ingestion retry semantics; the previous task already handled stale chunk cleanup on retry.
- Do not add silent fallbacks, pass-through behavior, or Java-side filtering that masks a missing SQL boundary.

## API / Command / Payload Contract

### Public Gateway API

No API contract changes.

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Supported payload fields remain unchanged:

```text
model
messages
temperature
max_tokens
top_p
stream
```

Expected behavioral contract:

- If an app has a READY KB and READY document chunks above threshold, retrieval proceeds and the OpenAI-compatible response behavior remains unchanged.
- If matching chunks exist only under non-READY documents, they must be treated as no valid retrieval hits.
- Request logs and citation/evidence metadata must not include chunks from non-READY documents.

### Admin APIs

No Admin API DTO/VO changes.

Relevant existing observable surfaces:

```http
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

Expected behavioral contract:

- `hit_chunk_ids` and `retrieval_evidence.citations[].chunk_id` must contain only chunks whose joined source `rag_document.status = READY`.
- Hit chunk summaries must remain tenant-scoped and safe metadata only.

### Database Contract

Primary retrieval SQL must keep the existing vector-search boundary and add document readiness:

```sql
FROM rag_document_chunk_embedding e
JOIN rag_document_chunk c ON c.id = e.chunk_id
JOIN rag_document d
  ON d.id = e.document_id
 AND d.user_id = e.user_id
 AND d.knowledge_base_id = e.knowledge_base_id
 AND d.status = 'READY'
WHERE e.user_id = ?
  AND e.knowledge_base_id = ?
ORDER BY e.embedding <=> ?::vector
LIMIT ?
```

The exact SQL may join via `c.document_id` if consistent with the mapper/entity model, but it must enforce:

- same document ID as the embedding/chunk row
- same user
- same knowledge base
- document status `READY`

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Points |
|---|---|---|
| READY document has matching embedding/chunk | Retrieval returns the chunk when it meets threshold and context limits | `RetrievalServiceTest`; returned chunk ID, order, similarity |
| FAILED document has matching embedding/chunk | Retrieval returns no hit from that document | `RetrievalServiceTest`; no returned chunk ID; no evidence citation |
| PROCESSING document has matching embedding/chunk | Retrieval returns no hit from that document | `RetrievalServiceTest` |
| PARSING document has matching embedding/chunk | Retrieval returns no hit from that document | `RetrievalServiceTest` |
| UPLOADED document has matching embedding/chunk | Retrieval returns no hit from that document | `RetrievalServiceTest` |
| EMBEDDING/PARSED document has matching embedding/chunk, if enum exists in code | Retrieval returns no hit from that document | `RetrievalServiceTest`, according to current `DocumentStatus` enum |
| Same KB/user has READY and non-READY chunks | Only READY chunks are ranked/injected/logged | `RetrievalServiceTest`; `hit_chunk_ids`/citation order |
| Cross-KB chunk has higher vector score | It is excluded by SQL scope | Existing or new retrieval test |
| Cross-user chunk has higher vector score | It is excluded by SQL scope | Existing or new retrieval test |
| No READY hits clear threshold | Normal no-hit behavior remains unchanged under current `STRICT_RAG` handling | `RetrievalServiceTest` and `RagPromptBuilderTest` if prompt effect is touched |
| Request log evidence generated from retrieval result | `hit_chunk_ids` order equals `retrieval_evidence.citations[].chunk_id`; no non-READY chunk appears | Gateway/request-log service tests if affected |
| ANN migration considered but not implemented | PRD/spec/task notes record why it is deferred or what explicit validation would be required | Task context / final handoff |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | A valid app key, READY KB, and READY document chunks produce scoped retrieval hits; final context, `hit_chunk_ids`, citations, and retrieval evidence all reference only READY source documents. |
| Base | Matching chunks exist only for FAILED/PROCESSING/PARSING/UPLOADED documents; retrieval behaves as no valid hit without leaking those chunks into prompt context or request-log evidence. |
| Bad | SQL retrieves vectors globally or from non-READY documents and tries to remove them later in Java; non-READY chunks influence ranking; `hit_chunk_ids`, citations, or evidence contain chunks from non-READY source documents. |
| Bad | ANN index migration is added without fixed distance metric/operator class, explain-plan verification, migration rollback consideration, or scale evidence. |

## Expected Implementation Plan

1. Inspect retrieval SQL and mapper model.
2. Add SQL-level join to `rag_document` with `status = READY`, preserving existing `user_id` and `knowledge_base_id` filters.
3. Keep Java service filtering/order behavior unchanged except where tests require adapting to the SQL result shape.
4. Add/extend focused retrieval tests for READY/non-READY status matrix and cross-scope isolation.
5. Review request-log/citation/evidence tests for order preservation and safe metadata.
6. Evaluate ANN prerequisites:
   - existing pgvector column type and metric
   - current indexes
   - migration naming/version sequence
   - explain-plan validation feasibility
   - current expected data scale
7. Defer ANN implementation unless evidence strongly supports including it in this task.

## Files Likely To Modify

To be confirmed by focused code research, but likely:

- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalMapper.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`
- `backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalMapperTest.java` or an equivalent SQL-contract test

Potentially affected only if tests reveal evidence/citation coupling:

- `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalEvaluationServiceTest.java`

Spec update only if a new durable contract is discovered:

- `.trellis/spec/rag/retrieval-quality.md`
- `.trellis/spec/backend/database-guidelines.md`

## Required Tests

Primary targeted tests:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

Evidence/citation regression tests if affected by implementation:

```bash
cd backend
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=RetrievalEvaluationServiceTest,RetrievalEvaluationAdminControllerTest" test
```

Compile and diff checks:

```bash
cd backend
mvn -q -DskipTests compile
git diff --check
```

Backend unit tests should be run with a 60-second command timeout where feasible.

## Acceptance Criteria

- [ ] Retrieval SQL joins `rag_document` and enforces `status = READY` before vector ordering.
- [ ] Retrieval SQL still enforces `user_id` and `knowledge_base_id`.
- [ ] Non-READY document chunks do not appear in retrieval results, prompt context, `hit_chunk_ids`, retrieval evidence, or citations.
- [ ] READY chunk order remains controlled by vector distance and existing final filtering/truncation logic.
- [ ] Cross-user and cross-KB isolation tests still pass.
- [ ] Focused retrieval status matrix tests cover READY and non-READY statuses present in code.
- [ ] ANN decision is explicitly recorded as implemented with validation evidence or deferred with rationale.
- [ ] No public API, Admin DTO/VO, frontend type, environment, or deployment contract changes are introduced by the correctness fix.

## Planning Notes for DeepSeek

- Prefer a single SQL boundary fix in the mapper over new service-side checks.
- Keep tests focused on observable retrieval outputs and final evidence ordering.
- Do not broaden the task into retrieval quality improvements.
- Do not add fallback behavior to make tests pass; failures should surface clearly.
