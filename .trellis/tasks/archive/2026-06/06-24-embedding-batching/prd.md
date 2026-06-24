# Embedding batching

## Task Classification

Complex Task.

This task crosses the RAG document-ingestion pipeline, the OpenAI-compatible embedding client boundary, upstream error handling, runtime configuration, vector persistence, document/knowledge-base status transitions, tests, and spec contracts. It is not a system expansion because it should reuse the existing `embedding`, `document`, `model`, and config patterns instead of introducing a new processing subsystem.

## Current Project State

- Branch: `feature/embedding-batching`.
- Trellis current task before this PRD: none.
- Working directory before planning: clean.
- Previous recorded task: default admin bootstrap closeout. It is committed as `75308691` and merged by `188ba7ce`; journal says it is a completed independent loop with no RAG/gateway/document behavior changes.
- User-reported CI failure from the previous round: `OpenAiChatCompletionsRuntimeSmokeTest$ClientDisconnect.shouldRecordCancelledAndReleaseReservationOnce` expected request-log status `cancelled` but got `success`.
- Local CI reproduction status during planning: sandboxed Maven could not resolve `spring-boot-starter-parent:3.4.5` because network was denied; escalated Maven run hit the required 60 second backend test timeout. Treat the CI failure as not locally reproduced here, but root-cause evidence is available from code and CI output.

## Goal

Implement bounded embedding batching for document processing so large or long documents do not send all chunks in one upstream embedding request, while preserving chunk order, vector count, vector dimension validation, safe failure behavior, and document/knowledge-base consistency.

Before the embedding batching implementation, fix the blocking CI/runtime-smoke failure for streaming client disconnect so CI is green again. This fix must stay scoped to the streaming runtime smoke/client-disconnect lifecycle and must not reopen unrelated gateway behavior.

## User Intent / Priority

Embedding batching is higher priority than README cleanup, frontend i18n/a11y, and nginx header governance because it is closer to core RAG runtime stability. It affects large files, long documents, provider request-size limits, rate limiting, and the risk of marking documents `READY` after only partial embedding success.

## Non-Goals

- Do not add provider fallback, retry orchestration, circuit breaker, queue replacement, concurrent workers, or provider routing.
- Do not change retrieval SQL, prompt construction, chat completion response shape, admin frontend workflows, or API-key auth.
- Do not add a public `/v1/embeddings` endpoint.
- Do not persist partial successful embedding batches if any later batch fails.
- Do not silently downgrade strict RAG behavior to pass-through.
- Do not log chunk content, embedding vectors, provider raw response bodies, upstream API keys, encrypted keys, authorization headers, prompts, or stack traces in client/admin responses.
- Planning-side constraint already applied: do not edit business code, do not edit `plane.html`, and do not create business files in the planning session. The execution session may modify production/test/spec/config files listed below as needed.

## CI Failure Root-Cause Note

The failing test is likely a runtime smoke race, not an embedding issue.

Observed code pattern:

- `OpenAiChatCompletionsRuntimeSmokeTest.ClientDisconnect` closes the client response body after reading the first SSE line, then releases a latch.
- The mocked upstream then tries a second `emitter.send(...)` and returns `CANCELLED` only if that send throws `IOException`.
- On CI, the servlet/socket stack may accept the second write into a buffer even after the client body is closed, so the mock returns `SUCCESS`.
- `OpenAiChatCompletionsController` then records `status=success`, which matches the CI failure: expected `cancelled`, actual `success`.

Execution guidance:

- Fix this first, before embedding batching, so the branch has a reliable CI baseline.
- Prefer making the runtime smoke deterministic around the observable disconnect/cancellation boundary rather than weakening the production cancellation contract.
- Keep the expected runtime contract from `gateway/resilience.md`: client disconnect must record `cancelled/client_cancelled` and release reservation once.
- Do not replace the real `RANDOM_PORT` smoke with a pure MockMvc or unit-only check.
- If production code changes are required, keep them narrowly scoped to response-write disconnect classification and idempotent terminal handling.

## Requirements

1. Add a configurable embedding batch size for document embedding requests.
   - Preferred property: `rag.gateway.embedding.batch-size`.
   - Preferred env var: `RAG_GATEWAY_EMBEDDING_BATCH_SIZE`.
   - Default: `64`, unless execution research finds a stronger local convention.
   - Minimum: `1`.
   - Maximum: define explicitly in config validation; recommended upper bound `2048` to fail obviously on accidental huge values.
   - Invalid values must fail startup/config binding visibly, not silently clamp.

2. Batch document chunk embedding calls.
   - `DocumentService.embedAndFinalize(...)` should split ordered chunk texts into batches.
   - Each batch calls the existing `EmbeddingClient.embed(baseUrl, apiKey, model, inputs, expectedDimension)`.
   - The returned vectors from all batches must be merged in the original chunk order.
   - The last batch may be smaller than the configured batch size.
   - Empty chunk lists still fail as they do today.

3. Preserve single-source validation.
   - Keep `OpenAiCompatibleEmbeddingClient` responsible for per-request response validation: response count, response index order after sorting, vector null checks, and expected dimension.
   - Keep `DocumentService.validateEmbeddingVectors(...)` or an equivalent final aggregate validation before persistence, because the merged output must match all chunks before any vector row is inserted.
   - Keep `PgVectorFormatter` as the only `float[] -> pgvector` literal formatter.

4. Preserve all-or-nothing vector persistence.
   - No `rag_document_chunk_embedding` rows may be inserted until every batch has succeeded and the merged vector list has passed aggregate validation.
   - If any batch fails, returns a wrong count, returns a wrong dimension, or throws provider/rate-limit/non-2xx/network/timeout errors, the document must become `FAILED` or retryable according to the existing worker path and no partial vector rows should be inserted.
   - Knowledge-base status after failure must follow the existing `updateKnowledgeBaseAfterFailure(...)` behavior: `READY` if prior ready docs remain, otherwise `FAILED`.

5. Keep upstream errors visible and safe.
   - Provider non-2xx, timeout, network failure, malformed body, count mismatch, and dimension mismatch remain `EmbeddingException` or a deliberately equivalent embedding failure boundary.
   - Do not include provider raw body, chunk content, vectors, API keys, or stack traces in persisted `error_message` / `last_error_message`.

6. Update executable spec after implementation.
   - Update `.trellis/spec/rag/document-ingestion.md` to move batch embedding from roadmap into current contract.
   - If a config validation class/property is added, update the relevant backend/gateway spec section describing the property name, default, min/max, and invalid-config failure behavior.

## Acceptance Criteria

- [ ] CI/runtime smoke client-disconnect failure is fixed first; `OpenAiChatCompletionsRuntimeSmokeTest` passes or the exact unrun reason is documented.
- [ ] `DocumentService` sends embedding requests in batches according to configured batch size.
- [ ] Multi-batch success persists one vector row per chunk and marks the document `READY`.
- [ ] Last partial batch is called with only the remaining inputs and final vector order still matches chunk order.
- [ ] Batch size `1` works and preserves ordering.
- [ ] A failure in the first, middle, or last batch marks the document failed/retryable through the existing path and inserts zero embedding rows.
- [ ] A batch response count mismatch prevents all persistence and leaves no partial vector rows.
- [ ] A batch vector dimension mismatch prevents all persistence and leaves no partial vector rows.
- [ ] Provider non-2xx/rate-limit, timeout, malformed response, or network failure remains visible and safe.
- [ ] Invalid configured batch size fails configuration binding/startup visibly.
- [ ] Spec is synchronized with the implemented embedding batch contract.
- [ ] No unrelated frontend, `plane.html`, README-only, nginx, retrieval SQL, prompt, API-key auth, or database schema changes are included.

## Technical Approach

### Preferred Implementation Shape

Use an injected validated properties object rather than a raw `@Value` in `DocumentService`.

Suggested option:

- Add or extend a configuration properties class under the embedding or gateway config boundary for `rag.gateway.embedding.timeout-seconds` and `rag.gateway.embedding.batch-size`.
- Annotate validation with `@Validated`, `@Min(1)`, and an explicit max.
- Keep constructor injection.
- Let invalid values fail at startup/config binding.

If execution chooses a smaller patch:

- A raw `@Value("${rag.gateway.embedding.batch-size:64}")` plus explicit constructor validation is acceptable only if it still fails visibly and is covered by tests.
- Do not silently clamp invalid values.

### DocumentService Batching Algorithm

1. Resolve KB and embedding model config exactly as today.
2. Decrypt upstream API key exactly as today.
3. Mark document `EMBEDDING` exactly as today.
4. Load chunks in deterministic chunk order.
5. Convert chunks to `chunkTexts` in that order.
6. Build `vectors = new ArrayList<>(chunks.size())`.
7. For `start = 0; start < chunkTexts.size(); start += batchSize`:
   - `end = Math.min(start + batchSize, chunkTexts.size())`.
   - Call `embeddingClient.embed(..., chunkTexts.subList(start, end), expectedDimension)`.
   - Append returned vectors to `vectors`.
8. After all batches return, call aggregate validation against the full chunk list.
9. Only then run the existing transaction that persists embeddings and marks document/KB ready.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, document ingestion flow, embedding dimension rules, safe errors.
- `.trellis/spec/backend/directory-structure.md`: `embedding` owns embedding clients/results; `document` owns ingestion/status/persistence.
- `.trellis/spec/backend/database-guidelines.md`: embedding vector table, tenant duplicated columns, dimension safety, transaction boundaries.
- `.trellis/spec/backend/error-handling.md`: embedding failures must not mark documents `READY`; bounded safe error messages.
- `.trellis/spec/backend/logging-guidelines.md`: embedding logs may include counts/dimensions/latency only, never vectors/chunk content/keys.
- `.trellis/spec/backend/quality-guidelines.md`: embedding/vector baseline tests and streaming runtime smoke commands.
- `.trellis/spec/rag/document-ingestion.md`: document status machine, async processing, retry cleanup, future batch embedding contract to update.
- `.trellis/spec/gateway/resilience.md`: embedding upstream error normalization and streaming client-disconnect runtime smoke contract.
- `.trellis/spec/security/rag-security.md`: tenant/secret/content exposure boundaries.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: document ingestion and config flow across service/config/spec/tests.

## Current Game State

Not applicable. The repository is `Sangui-RAG-Gateway`; no `plane.html` or plane-game project file was found during planning. Game-specific checks such as mobile single-finger control, auto-shooting, and game main flow are not applicable to this task and must not be imported into the execution scope.

## Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java`: current embedding client contract accepts a list of inputs and expected dimension.
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`: builds `/v1/embeddings`, logs safe counts, normalizes non-2xx/timeout/network/malformed responses into `EmbeddingException`, sorts response data by provider index, validates exact count and dimension.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`: current `embedAndFinalize(...)` sends all chunk texts in one `embeddingClient.embed(...)` call, validates aggregate vectors, then persists embeddings and marks `READY` in one transaction.
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentProcessingProperties.java`: existing simple properties class pattern for document worker knobs, currently lacks validation annotations.
- `backend/src/main/java/com/sangui/raggateway/common/config/ApiKeyLimitProperties.java` and `backend/src/main/java/com/sangui/raggateway/log/OutputCaptureProperties.java`: validated `@ConfigurationProperties` patterns with `@Min(1)`.
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`: existing client tests for URL variants, count mismatch, dimension mismatch, non-2xx, malformed body, and out-of-order indexes.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`: existing tests assert happy path, one vector row per chunk, embedding exception failure, wrong vector count failure, KB status preservation when prior ready docs exist.
- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`: client-disconnect smoke currently depends on the second `SseEmitter.send(...)` throwing after the response body is closed, which is race-prone in CI.

## Config Tables Involved

No database schema or migration is expected for embedding batching.

Runtime config/property surface:

- Existing: `rag.gateway.embedding.timeout-seconds`.
- New or reused: `rag.gateway.embedding.batch-size`.
- Existing env examples should be synchronized if the project convention requires committed env documentation: `RAG_GATEWAY_EMBEDDING_BATCH_SIZE`.

Database tables whose rows are affected by runtime behavior, without schema changes:

- `rag_document`: `status`, `error_message`, timestamps.
- `rag_knowledge_base`: `status`.
- `rag_document_chunk`: ordered input rows.
- `rag_document_chunk_embedding`: vector rows must be inserted only after all batches succeed.
- `rag_document_processing_task`: worker terminal/retryable/failed state and bounded last error.
- `rag_model_config`: embedding config lookup and upstream encrypted key remain unchanged.

## Files Likely To Modify

CI failure first:

- `backend/src/test/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsRuntimeSmokeTest.java`
- Possibly `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
- Possibly `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`

Embedding batching:

- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java` only if the batching contract belongs in client rather than service, but prefer keeping provider single-request validation as-is.
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java` only if execution chooses to expose a batch-aware helper; avoid changing the public interface unless clearly justified.
- A config properties class for `rag.gateway.embedding.*`, if execution chooses validated binding.
- `backend/src/main/resources/application.yml`
- `.env.example` and `deploy/docker-compose.yml` only if env passthrough/documentation is needed by existing conventions.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClientTest.java`
- Config validation tests for the new/extended properties class.
- `.trellis/spec/rag/document-ingestion.md`
- Possibly `.trellis/spec/gateway/resilience.md` or `.trellis/spec/backend/quality-guidelines.md` if the validation matrix or test commands change.

## Forbidden Modification Scope

- Do not modify `plane.html`; it is not part of this repository/task.
- Do not add game-specific code, controls, rendering, or manual game tests.
- Do not modify frontend UI unless a later explicit user request changes scope.
- Do not modify retrieval SQL, prompt builder, citations, request-log evidence, API-key auth, model-config CRUD, default admin bootstrap, storage delete lifecycle, or Docker/nginx unless directly required by the CI unblock or batch config contract.
- Do not create duplicate vector formatters or alternate embedding validation paths.
- Do not add silent fallbacks, fake success, broad catch-and-ignore logic, or partial success semantics.
- Do not change DB schema unless execution finds a hard evidence-backed reason; current plan expects no migration.

## Risk / Boundary Notes

- Main consistency risk: batch 1 succeeds, batch 2 fails, and partial vectors are inserted or the document is marked `READY`. Prevent by collecting all vectors before the persistence transaction.
- Ordering risk: merging batch results incorrectly can shift vectors to the wrong `chunk_id`. Preserve ordered chunks and append vectors exactly in batch order after each client call has validated in-batch indexes.
- Error safety risk: provider raw bodies or chunk contents can leak into `error_message` or logs. Keep bounded generic messages.
- Config risk: silent clamping of invalid batch size hides deployment mistakes. Fail visibly.
- CI risk: the streaming disconnect smoke is real HTTP and inherently timing-sensitive. Fix determinism without downgrading the contract to a mock-only unit test.
- Transaction risk: do not hold DB transactions open across upstream embedding calls.
- Retry risk: failed attempts must remain compatible with existing worker retry/backoff and cleanup behavior.

## Required Automated Checks

Run with the backend unit-test hard timeout of 60 seconds per command when feasible.

CI unblock:

```bash
cd backend
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest" test
mvn -q "-Dtest=OpenAiChatCompletionsRuntimeSmokeTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
```

Embedding batching:

```bash
cd backend
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=DocumentProcessingTaskServiceTest,DocumentProcessingWorkerTest" test
mvn -q "-Dtest=ModelConfigServiceTest" test
mvn -q -DskipTests compile
git diff --check
```

If config properties are added or changed:

```bash
cd backend
mvn -q "-Dtest=*ConfigTest,*PropertiesTest" test
```

If time allows after targeted checks:

```bash
cd backend
mvn -q test
```

## Required Manual Tests

Manual tests are required after implementation because provider limits and long-document behavior are runtime concerns.

- Upload a document that produces more chunks than the configured batch size; verify processing reaches `READY`.
- Set a small batch size such as `2`, upload a document with `5` chunks, and verify provider logs or mock/provider evidence show calls of `2 + 2 + 1`.
- Verify request/log output never prints chunk content, vectors, provider raw body, or upstream keys.
- Simulate or use a bad embedding config/provider key; verify document becomes failed/retryable and no vector rows are left for the failed document.
- If using a real provider with rate limits, verify non-2xx/429 remains visible and safe.
- Re-run a normal RAG chat against a ready KB to ensure existing retrieval still sees only `READY` documents with complete embeddings.

## Planning Self-Check

- Acceptance criteria are explicit: yes.
- Planning session avoided business code and `plane.html`: yes.
- "No new files" applies to this planning session only: yes. Execution may add a config properties/test file if needed for validated config binding.
- "Default only modify `plane.html`" is not applicable: current repo has no `plane.html`; the expected execution scope is backend Java tests/config/spec.
- Config surfaces are explicit: `rag.gateway.embedding.batch-size`, `RAG_GATEWAY_EMBEDDING_BATCH_SIZE`, existing timeout property, affected runtime DB tables without schema changes.
- Mobile single-finger control: not applicable to Sangui RAG.
- Auto-shooting: not applicable to Sangui RAG.
- Game main flow manual test: not applicable to Sangui RAG.
- Concrete guidelines were read, not only indexes: yes; see Relevant Specs.
- Open question: none that blocks execution. The only deliberate execution choice is whether to introduce a validated embedding properties class or perform explicit constructor validation; prefer the properties class if the diff remains contained.

