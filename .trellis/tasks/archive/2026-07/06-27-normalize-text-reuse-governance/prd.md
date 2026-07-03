# normalizeText reuse governance

## Classification

Complex Task.

The implementation is low-to-medium complexity, but it needs a research-first boundary because several methods use similar "normalize" names for different domains. The task must distinguish duplicated document text normalization from domain-specific URL, filename, status, DTO-field, SQL-test, and output-redaction normalization.

## Goal

Consolidate duplicated document text normalization logic so document parsing and chunking use one production owner for the same semantics:

- normalize CRLF and CR to LF
- trim surrounding whitespace
- collapse three or more consecutive LF characters to exactly two LF characters
- keep normal Chinese and English text content unchanged
- keep the document/chunking call boundaries and visible behavior unchanged

## Scope

In scope:

- Backend document text cleaning used by document ingestion and chunking.
- A single shared owner for document text normalization, preferably under `common.util` if it is truly cross-module reusable, or under the document package if the final code research proves it is document-domain-only.
- Replacement of duplicated implementations in:
  - `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
  - `backend/src/main/java/com/sangui/raggateway/document/chunk/TextChunker.java`
- Focused unit tests for the shared owner and affected call sites.
- Spec update only if implementation creates a durable rule that future text cleaning must follow.

Out of scope:

- No public API, DTO, VO, frontend type, request payload, response payload, or OpenAI-compatible behavior change.
- No database schema, migration, mapper SQL, vector retrieval SQL, tenant boundary, or storage behavior change.
- No prompt content redesign, no retrieval ranking/threshold/topK/context-budget change, no no-hit policy change.
- No URL normalization consolidation unless a separate PRD explicitly covers upstream/model base URL ownership.
- No merging of filename sanitization, status normalization, model config field trimming, SQL-test whitespace normalization, output redaction, or error-message truncation into this helper.
- No broad try/catch, silent fallback, mock-success path, or defensive behavior added only to make tests pass.

## Current Research Summary

Duplicated same-semantics implementation found:

- `DocumentService.normalizeText(String)` normalizes line endings, trims, and collapses 3+ LF to 2 LF before empty-text detection and before `textChunker.chunk(cleanedText)`.
- `TextChunker.normalizeText(String)` repeats the same line-ending, trim, and 3+ LF collapse semantics before chunk splitting.

Similar names but different semantics found and should not be merged:

- `OpenAiCompatibleUpstreamClient.normalizeBaseUrl`, `OpenAiCompatibleEmbeddingClient.normalizeBaseUrl`, and `ModelConfigCheckService.normalizeBaseUrl`: URL base trimming and slash removal.
- `ModelConfigService.normalizeRequiredText` / `normalizeOptionalText`: DTO/model-field trim and optional blank-to-null behavior.
- `DocumentUploadRules.sanitizeFilename` / `extractDisplayBasename`: filename and path boundary cleanup.
- `ApiRequestLogAdminController.normalizeStatus`: status filter validation/normalization.
- `ChatCompletionGatewayService.truncateForSummary`: request-log bounded prefix only, not text cleaning.
- `RetrievalMapperTest.normalizeSql`: test-only SQL whitespace assertion helper.
- `OutputRedactionService`: sensitive-output redaction, not normalization.
- `truncateSafe` methods: bounded error message behavior.

## API / Command / Payload Contract

No public API, command, request payload, response payload, DTO, VO, frontend type, or database field changes are expected.

The only command contract for this task is validation:

```bash
cd backend
mvn -q "-Dtest=TextNormalizerTest,TextChunkerTest,DocumentServiceTest" test
mvn -q "-Dtest=PlainTextDocumentParserTest,MarkdownDocumentParserTest" test
mvn -q -DskipTests compile
git diff --check
```

If the implementer uses a different helper/test class name, adjust only the first `-Dtest=...` selector to the actual class name while keeping equivalent coverage.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| `null` passed to shared normalization owner | Returns `null` or preserves current caller behavior through documented owner contract; `TextChunker.chunk(null)` still returns empty list | New utility test and `TextChunkerTest` |
| Blank or whitespace-only text | Normalized value is blank; document parse still marks "Document has no readable text"; chunker still returns empty list | `DocumentServiceTest`, `TextChunkerTest` |
| Chinese text with CRLF/CR and extra blank lines | Line endings become LF, 3+ LF collapses to 2 LF, Chinese content remains unchanged | New utility test |
| English text with CRLF/CR and extra blank lines | Same normalization, English content remains unchanged | New utility test |
| Mixed Chinese/English with tabs/spaces inside lines | Internal content is preserved except documented outer trim and line-ending/blank-line normalization | New utility test |
| Leading/trailing whitespace and newlines | Trimmed according to existing behavior | New utility test |
| Extremely long text | No truncation, no hidden fallback, no data loss beyond documented normalization | New utility test or chunker test |
| DocumentService passes parser output to chunker | The chunker receives already-normalized text or behavior remains equivalent; no double-divergent custom implementation remains | `DocumentServiceTest` argument capture |
| TextChunker splits normalized input | Chunk splitting remains deterministic and overlap behavior unchanged | `TextChunkerTest` |
| Adjacent but different normalization helpers | URL, filename, status, DTO field trim, SQL-test, redaction, and truncation helpers are not merged | Diff review |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | A single production owner defines document text normalization; both document parsing and chunking call it or have one clear ownership boundary; focused tests cover Chinese, English, CRLF/CR, blank lines, null/blank, internal whitespace, and long text. |
| Base | Existing document ingestion and chunking behavior stays identical while duplicate private methods are removed. |
| Bad | A generic helper absorbs unrelated URL/status/filename/DTO/redaction/truncation semantics; tests only assert "compiles"; behavior changes request summaries, prompts, retrieval SQL, API payloads, or frontend types. |

## Acceptance Criteria

- [ ] Exactly one production implementation owns document text normalization semantics for line endings, trim, and 3+ LF collapse.
- [ ] `DocumentService` no longer has a private duplicate implementation with the same semantics.
- [ ] `TextChunker` no longer has a private duplicate implementation with the same semantics, or the implementation is moved into `TextChunker` only if the research proves no other production caller should normalize documents before chunking.
- [ ] Existing external behavior is preserved for document parse empty-text detection and chunk generation.
- [ ] New or updated tests cover Chinese, English, mixed whitespace, CRLF, CR, LF, repeated blank lines, null, blank, and long text.
- [ ] The implementation does not merge unrelated domain-specific normalization helpers.
- [ ] No API, DB, frontend, retrieval SQL, prompt/no-hit, auth, storage, or deployment behavior changes.
- [ ] Specs are updated only if a durable project rule is introduced.

## Likely Files To Modify

Expected production files:

- `backend/src/main/java/com/sangui/raggateway/common/util/TextNormalizer.java` or `backend/src/main/java/com/sangui/raggateway/document/TextNormalizer.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/chunk/TextChunker.java`

Expected test files:

- `backend/src/test/java/com/sangui/raggateway/common/util/TextNormalizerTest.java` or matching document-package test
- `backend/src/test/java/com/sangui/raggateway/document/chunk/TextChunkerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`

Possible spec file if implementation establishes a reusable rule:

- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/rag/document-ingestion.md`

## Required Specs

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/index.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/rag/index.md`
- `.trellis/spec/rag/document-ingestion.md`
- `.trellis/spec/rag/retrieval-quality.md`
- `.trellis/spec/rag/prompt-context-policy.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/gateway/resilience.md`
- `.trellis/spec/guides/index.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Required Validation

Run with a 60-second timeout per backend unit-test command when feasible:

```bash
cd backend
mvn -q "-Dtest=TextNormalizerTest,TextChunkerTest,DocumentServiceTest" test
mvn -q "-Dtest=PlainTextDocumentParserTest,MarkdownDocumentParserTest" test
mvn -q -DskipTests compile
git diff --check
```

Optional broader confidence if the implementation touches retrieval or prompt files despite the current boundary:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

## Planning Self-Check

- Acceptance criteria are defined.
- Forbidden modification scope is defined.
- Expected production and test files are listed.
- Required tests are listed.
- Specific guideline files were read, not only spec indexes.
- No user clarification is required before DeepSeek implementation.
- No API, DB, frontend type, DTO, command, or payload alignment is needed for the expected implementation.
