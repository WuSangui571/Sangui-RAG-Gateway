# Vector Serialization Unification

## Goal

Unify pgvector string serialization for both document embedding persistence and retrieval query vectors behind one pure, reusable formatter.

This task removes duplicated vector formatting logic from:

- `RetrievalService.vectorToPgString(...)`
- `DocumentService.vectorToPgString(...)`

The implementation must preserve the existing retrieval-side output contract exactly enough to avoid query vector behavior drift, while moving document write-side formatting onto the same boundary.

## Classification

Complex Task.

Reason: the code change is small, but the boundary spans document ingestion, embedding vector persistence, retrieval query vector formatting, MyBatis `::vector` casts, and long-term pgvector/database contracts.

## Scope

In scope:

- Add one shared pure formatter, for example `PgVectorFormatter`, under a package consistent with existing structure, preferably `com.sangui.raggateway.common.util`.
- Format every float with `Locale.ROOT`.
- Preserve bracket/comma pgvector literal shape: `[0.10000000,0.20000000,-0.30000001]` style with no spaces.
- Preserve retrieval's current fixed 8-decimal formatting as the compatibility baseline.
- Replace document write-side vector serialization to use the same formatter.
- Replace retrieval query-side vector serialization to use the same formatter.
- Move or replace `RetrievalServiceTest.testVectorToPgString` with formatter-focused tests.
- Add or strengthen `DocumentServiceTest` assertion that persisted `DocumentChunkEmbeddingEntity.embedding` uses the shared fixed format.
- Update long-term spec if this formatter is accepted as the durable pgvector serialization boundary.

Out of scope:

- No DB schema or migration changes.
- No pgvector column type changes.
- No retrieval SQL, ranking, threshold, topK, ANN, HNSW, IVFFlat, or operator-class changes.
- No embedding provider behavior changes.
- No embedding batching changes.
- No document parser/chunker/status lifecycle changes.
- No public `/v1/*` API, Admin API, DTO/VO, frontend, Docker, CI, or deployment changes.
- No compatibility rewrite to a pgvector Java type or JDBC extension unless separately approved.

## API / Command / Payload Fields

No public API or command shape changes.

Internal Java contract to introduce:

```java
public final class PgVectorFormatter {
    public static String format(float[] vector)
}
```

Expected payload string shape:

```text
[<component_0>,<component_1>,...,<component_n>]
```

Component formatting:

```text
String.format(Locale.ROOT, "%.8f", value)
```

Call sites:

- Document write path passes formatted string to `DocumentChunkEmbeddingEntity.embedding`; mapper keeps `#{embedding}::vector`.
- Retrieval query path passes formatted string to `RetrievalMapper.retrieveChunks(...)`; mapper keeps `#{queryVector}::vector` in similarity and ordering expressions.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| Normal vector `{0.1f, 0.2f, -0.3f}` | Formatter returns bracketed comma-separated literal with fixed 8 decimal places and `Locale.ROOT` decimal separator | New `PgVectorFormatterTest` |
| Single-element vector | Formatter returns one bracketed component, no trailing comma | New `PgVectorFormatterTest` |
| Empty vector | Fail visibly with `IllegalArgumentException`; do not generate `[]` unless implementation proves current callers need it | New `PgVectorFormatterTest` |
| Null vector | Fail visibly with `IllegalArgumentException` or `NullPointerException`; prefer explicit `IllegalArgumentException` | New `PgVectorFormatterTest` |
| Document embedding persistence | Captured `DocumentChunkEmbeddingEntity.embedding` equals shared fixed-8-decimal output | `DocumentServiceTest` |
| Retrieval query vector | `RetrievalMapper.retrieveChunks(...)` receives shared fixed-8-decimal output | `RetrievalServiceTest` |
| Mapper SQL casts | `#{embedding}::vector` and `#{queryVector}::vector` remain unchanged | Existing mapper tests / focused diff review |
| Locale-sensitive runtime | Decimal separator remains `.` independent of JVM default locale | New formatter test may temporarily set default locale if practical |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Ingestion writes and retrieval queries both use the same pure formatter; both produce fixed 8-decimal pgvector literals with no spaces; existing mapper `::vector` casts and retrieval SQL behavior remain unchanged. |
| Base | Formatter is tested directly and service tests confirm the two important call sites pass its output through to persistence/query mapper boundaries. |
| Bad | DocumentService keeps Java's default `float.toString()` while retrieval uses fixed decimals; formatter silently accepts null/empty vectors and hides bad upstream data; retrieval SQL or DB schema changes are bundled into this cleanup; output formatting changes without compatibility tests. |

## Acceptance Criteria

- [ ] There is exactly one production implementation for formatting `float[]` into a pgvector string literal.
- [ ] `RetrievalService` no longer owns a vector serialization helper.
- [ ] `DocumentService` no longer owns a vector serialization helper.
- [ ] The shared formatter is pure and has no Spring/database/provider dependencies.
- [ ] Normal formatting uses `Locale.ROOT` and fixed 8 decimal places.
- [ ] Null/empty input failure behavior is explicit and tested.
- [ ] `DocumentServiceTest` proves the persisted embedding string uses fixed 8-decimal formatting.
- [ ] `RetrievalServiceTest` or formatter tests preserve the prior retrieval vector format contract.
- [ ] Mapper SQL `::vector` casts are unchanged.
- [ ] No DB schema, API, DTO/VO, frontend, provider, ranking, ANN, or deployment behavior changes are introduced.
- [ ] If the formatter becomes the long-term boundary, update `.trellis/spec/backend/database-guidelines.md` and/or `.trellis/spec/rag/retrieval-quality.md` with the pgvector serialization contract.

## Expected Files To Modify

Likely production files:

- `backend/src/main/java/com/sangui/raggateway/common/util/PgVectorFormatter.java`
- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`

Likely test files:

- `backend/src/test/java/com/sangui/raggateway/common/util/PgVectorFormatterTest.java`
- `backend/src/test/java/com/sangui/raggateway/retrieval/RetrievalServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`

Possible spec files:

- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/rag/retrieval-quality.md`

## Required Tests And Assertion Points

Run from repo root unless noted:

```powershell
mvn -q "-Dtest=PgVectorFormatterTest,RetrievalServiceTest,DocumentServiceTest" test
mvn -q "-Dtest=RetrievalMapperTest,RagPromptBuilderTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest,DocumentAdminControllerTest,ModelConfigServiceTest" test
mvn -q -DskipTests compile
git diff --check
```

Assertion points:

- Formatter normal output is exact, fixed width, no spaces, Locale.ROOT-compatible.
- Formatter null/empty behavior fails visibly.
- Retrieval still passes the formatted vector to `RetrievalMapper.retrieveChunks(...)`.
- Document ingestion persistence captures the same formatted output in `DocumentChunkEmbeddingEntity.embedding`.
- Existing SQL casts and READY/source-boundary retrieval tests remain intact.
- Compile catches stale imports from removed private helper methods.

## Implementation Notes For DeepSeek

- Prefer a final utility class with a private constructor and one public static `format(float[] vector)` method.
- Keep the method small and deterministic; do not add provider-specific behavior, logging, Spring injection, fallback parsing, or database calls.
- Use `StringBuilder` plus `String.format(Locale.ROOT, "%.8f", vector[i])` to preserve the existing retrieval-side contract.
- Remove `java.util.Locale` imports from services when no longer needed.
- Preserve existing test style: JUnit 5 + AssertJ + Mockito.
- Keep implementation changes tightly scoped; do not refactor surrounding ingestion/retrieval logic.

## Spec Update Decision

This should become a long-term boundary if implemented as planned because both persisted vectors and query vectors rely on the same pgvector literal contract.

Recommended spec addition:

- In `.trellis/spec/backend/database-guidelines.md`, under the vector/embedding area, record that Java code must use the shared pgvector formatter for all `float[] -> VECTOR` literals and must not duplicate formatter logic in services.
- Optionally in `.trellis/spec/rag/retrieval-quality.md`, note retrieval query vectors use the same formatter as document embedding persistence before `?::vector`.

Do not update spec if implementation takes a different route that does not establish a reusable boundary.
