# RAG Retrieval Quality

> Retrieval must provide stable, explainable, tenant-safe private-knowledge enhancement without changing the lightweight OpenAI-compatible gateway positioning.

## 1. Scope / Trigger

Use this spec before changing:

- query embedding generation for `/v1/chat/completions`
- vector retrieval SQL or mapper methods
- `topK`, `similarity_threshold`, context limits, deduplication, or ranking
- no-hit policy behavior
- request-log fields related to retrieval, including `hit_chunk_ids`
- future hybrid search, rerank, multi-query, metadata filtering, or query rewrite work

This task only records the spec. It does not implement new retrieval features.

## 2. Current Hard Specification

- Retrieval is not an agent system. It exists to add controlled private-knowledge context to an OpenAI-compatible gateway request.
- Retrieval must not cross `App`, `KnowledgeBase`, or `User` boundaries.
- Retrieval must preserve the low-friction client integration model: existing systems should keep using OpenAI-style `base_url`, `api_key`, and chat payloads.
- Retrieval must not expand the project into a heavy low-code or workflow platform.
- Retrieval results must be traceable through safe request-log fields, especially `hit_chunk_ids`.
- Retrieval context must be constrained by `topK`, `similarity_threshold`, `max_context_tokens`, and per-chunk limits before prompt injection.
- When the knowledge base has no valid hit, the gateway must not instruct the model to fabricate knowledge-base evidence.
- Default no-hit behavior is `STRICT_RAG`: still call upstream where the current gateway contract requires it, but the injected context must say there is not enough knowledge-base evidence.
- A retrieval change must never improve recall by removing tenant, app, or knowledge-base filters.
- Pure vector retrieval may under-recall proprietary names, abbreviations, code identifiers, version numbers, and long-tail expressions. This is a known quality limit, not permission to weaken access filters.

## 3. Signatures

Current retrieval data flow:

```text
POST /v1/chat/completions
  -> API key auth
  -> resolve app and bound knowledge base
  -> extract last user message
  -> generate query embedding
  -> vector retrieval scoped by user_id + knowledge_base_id + READY source document
  -> filter by similarity_threshold
  -> deduplicate and truncate
  -> produce hit_chunk_ids and safe context chunks
  -> prompt augmentation
  -> upstream chat
  -> request log
```

Required SQL boundary:

```sql
SELECT c.id AS chunk_id, c.document_id, c.content, c.metadata::text,
       1 - (e.embedding <=> ?::vector) AS similarity
FROM rag_document_chunk_embedding e
JOIN rag_document_chunk c
  ON c.id = e.chunk_id
 AND c.user_id = e.user_id
 AND c.knowledge_base_id = e.knowledge_base_id
 AND c.document_id = e.document_id
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

Forbidden global retrieval:

```sql
SELECT *
FROM rag_document_chunk_embedding
ORDER BY embedding <=> ?::vector
LIMIT 5;
```

Request-log retrieval fields:

```text
question_summary: bounded prefix of last user message
hit_chunk_ids: JSON array of final injected chunk IDs, or null/empty for no hits
retrieval_evidence: JSON metadata object for final injected chunks, or null for old/pre-retrieval rows
```

Source citation retrieval fields:

```text
citation_id: stable per response/run label such as S1, not a database ID
chunk_id: final injected rag_document_chunk.id
document_id: safe document ID
knowledge_base_id: app-bound KB ID
source_filename: safe original filename or null, never storage_path
chunk_index: chunk order within document or null
similarity: bounded score metadata
metadata: only source/parser when available
content_chars: original chunk length metadata
injected_chars: final injected length metadata
```

## 4. Contracts

| Contract | Required behavior |
|----------|-------------------|
| Tenant scope | Retrieval SQL includes `user_id` and `knowledge_base_id` before vector ordering. |
| App scope | Public gateway retrieval only uses the knowledge base bound to the authenticated app. |
| Document readiness | Retrieval SQL joins `rag_document` and returns only chunks whose source document has `status = READY`; non-READY document chunks are no valid retrieval hits. |
| Duplicated row consistency | Retrieval SQL requires embedding, chunk, and document rows to agree on `document_id`, `user_id`, and `knowledge_base_id`. |
| Similarity threshold | Chunks below threshold are excluded from final context and `hit_chunk_ids`. |
| `topK` | Limits candidates, but does not mean all candidates must be injected. |
| Context limit | Final injected chunks must fit the configured max context budget. |
| Deduplication | Duplicate or repeated chunks should be removed before final context construction. |
| Citation assignment | Citation IDs are assigned only after threshold, deduplication, max chunk count, total context budget, and per-chunk truncation are applied. |
| Citation ordering | Citation order matches final prompt injection order and `hit_chunk_ids`. |
| Source filename | Filename comes from same-user, same-KB document metadata. Missing filename stays null/unknown; do not fabricate. |
| Logging | Persist safe hit IDs and safe summaries only; never full prompt or full document content. |
| No-hit | Under `STRICT_RAG`, prompt context must state that no sufficient KB evidence was found. |

No-hit policies:

| Policy | Required behavior |
|--------|-------------------|
| `STRICT_RAG` | No valid context still calls upstream where the current flow requires it, but the prompt must say the KB has no sufficient evidence. |
| `PASS_THROUGH` | No valid context allows normal upstream model answering only when explicitly configured. |
| `ERROR` | No valid context returns a no-relevant-knowledge error instead of calling upstream. |

Similarity threshold guidance:

- Current implementation defaults may stay lower where the project has chosen recall-first behavior and tests document it.
- Quality-oriented deployments should consider `0.70 - 0.75` as the recommended stricter band.
- Any default change must update config, docs, tests, and acceptance scripts together.

## 5. Validation & Error Matrix

| Scenario | Expected behavior | Assertion point |
|----------|-------------------|-----------------|
| App has no bound KB | `409 knowledge_base_not_ready`; no embedding or upstream call | Gateway service/controller test |
| Bound KB is not `READY` | `409 knowledge_base_not_ready`; no retrieval | Gateway service/controller test |
| Query embedding config missing or invalid | `502 embedding_failed`; no upstream chat call | Gateway service test |
| Retrieval hits above threshold | Context contains only scoped chunks; `hit_chunk_ids` records injected IDs | `RetrievalServiceTest`, request-log test |
| Matching chunk belongs to non-READY document | It is excluded by SQL before thresholding, prompt injection, `hit_chunk_ids`, citations, or retrieval evidence | `RetrievalMapperTest`, `RetrievalServiceTest` |
| No chunks above threshold | `STRICT_RAG` no-hit context; no fabricated KB evidence | `RagPromptBuilderTest` |
| Cross-user or cross-KB chunk exists | It is not returned because SQL is scoped | Mapper/service test |
| Request log API returns hit chunks | Only safe fields and bounded summaries are exposed | Request-log admin API test |
| Request log detail returns retrieval evidence | Only bounded citation metadata is exposed; malformed JSON fails visibly | Request-log service/admin API test |
| Evaluation baseline run | Precision/recall/MRR and per-case hit/rank are computed from safe IDs only | Retrieval evaluation service/controller test |

## 6. Good/Base/Bad Cases

| Case | Expected result |
|------|-----------------|
| Good | Valid app key, ready KB, matching chunks: scoped chunks are retrieved, filtered, logged by ID, and injected within context limits. |
| Base | No chunk clears threshold: upstream may still be called under `STRICT_RAG`, but the model is instructed to say the KB lacks sufficient evidence. |
| Bad | Retrieval removes `user_id` or `knowledge_base_id`, logs full chunk content, injects all `topK` chunks without threshold/token limits, or silently passes through as normal chat under strict RAG. |
| Bad | Citation metadata is generated from model answer text, exposes chunk content/storage paths, or assigns labels before final filtering. |

## 7. Wrong vs Correct

### Wrong

```text
Retrieve globally, sort by vector distance, then filter user or knowledge_base in Java.
```

This can leak cross-tenant data and can let high-scoring unauthorized chunks affect ranking.

### Correct

```text
Filter by user_id and knowledge_base_id inside SQL before vector ordering, then apply threshold, deduplication, and context limits before prompt construction.
```

## 8. Future Enhancement Roadmap

The following are valid later enhancements, not V0.2 beta requirements:

- hybrid search: vector search plus keyword or BM25 retrieval
- rerank over candidate chunks
- multi-query retrieval for complex questions
- metadata filters by document, tag, source, or created time
- query rewrite before embedding
- query decomposition for multi-intent questions
- retrieval traces that record sub-query behavior

Enhancements must stay configurable, inherit the same app/knowledge-base/user boundary, preserve final `hit_chunk_ids`, and remain bounded by context limits.

For multi-intent questions, query decomposition must not be default-on in V0.2 beta. If implemented later, generated sub-queries must inherit the same app and knowledge-base boundary; merged results must be deduplicated, ranked, and truncated by `max_context_tokens`; logs should record final `hit_chunk_ids` and, where implemented, a safe retrieval trace.
