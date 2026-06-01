# RAG Prompt Context Policy

> Prompt augmentation must preserve the original OpenAI-compatible request, add bounded private-knowledge context, and prevent the model from inventing knowledge-base evidence when retrieval is insufficient.

## 1. Scope / Trigger

Use this spec before changing:

- `RagPromptBuilder` or equivalent prompt augmentation code
- how retrieved chunks are formatted into messages
- no-hit instructions
- context token or character budgets
- output-format or `response_format` compatibility behavior
- prompt or context logging

This task only records the spec. It does not implement new prompt features.

## 2. Current Hard Specification

- Preserve the user's original `messages`.
- Do not overwrite the user's original `system` prompt.
- Add RAG context as separate controlled system context or another explicit augmentation block.
- Clearly distinguish private knowledge-base context from the user question.
- State that the RAG context comes from private knowledge-base retrieval results.
- Each injected chunk must have clear boundaries, such as chunk index, document/source label, and content.
- `topK` is not the final injection count; low-similarity, duplicate, or over-budget chunks must be filtered.
- RAG context must be bounded by `max_context_tokens` and per-chunk limits.
- Long documents must never be injected wholesale.
- If context is insufficient, instruct the model not to fabricate knowledge-base evidence.
- Do not expose internal retrieval strategy, API keys, upstream keys, complete system prompts, full augmented prompts, or hidden rules in user-facing output or request logs.
- The gateway must not force JSON or another structured output format unless an explicit compatible contract is implemented.

## 3. Signatures

Supported input fields should remain compatible with the project-level OpenAI subset:

```text
model
messages
temperature
max_tokens
top_p
stream
```

Prompt augmentation shape:

```text
original messages
  + controlled internal RAG context message
  + no-hit instruction when no valid context exists
```

Recommended context controls:

```text
top_k = 5
similarity_threshold = 0.70 - 0.75 for stricter precision-oriented deployments
max_context_tokens = 3000
max_single_chunk_tokens = 800
```

If the implementation currently uses lower recall-first thresholds or character-based equivalents, document the choice, tests, and conversion explicitly. The hard requirement is that context is bounded and filtered; changing default values must be handled as an implementation task with matching tests.

## 4. Contracts

| Contract | Required behavior |
|----------|-------------------|
| Message preservation | Original user messages remain available to upstream in their original order. |
| System prompt preservation | Existing user-provided system prompt is not replaced by RAG text. |
| Context separation | Knowledge-base context and user question are visibly separated in the augmented prompt. |
| Chunk boundary | Each context block includes safe source metadata and content boundaries. |
| Context budget | Final context respects max total and per-chunk limits. |
| No-hit behavior | `STRICT_RAG` no-hit prompt says there is no sufficient KB evidence. |
| Structured output | Do not add strong JSON/schema instructions unless explicitly configured and supported. |
| Secret safety | Never include keys, complete internal prompt, retrieval internals, or hidden rules in visible output. |

## 5. Validation & Error Matrix

| Scenario | Expected behavior | Assertion point |
|----------|-------------------|-----------------|
| User includes system prompt | It remains present; RAG context is added separately | Prompt builder test |
| Multiple chunks returned | Most relevant chunks appear first, within context budget | Prompt builder/retrieval test |
| Duplicate or low-score chunks | They are omitted from final context | Retrieval or prompt test |
| No valid chunks under `STRICT_RAG` | Prompt tells model there is insufficient KB evidence | Prompt builder test |
| User asks for system prompt, keys, hidden rules, or full context | Prompt rules instruct refusal; logs and responses do not expose secrets | Prompt builder/security test |
| User asks for JSON | Gateway does not override or force JSON beyond user's original request unless a supported contract exists | Compatibility test |

## 6. Good/Base/Bad Cases

| Case | Expected result |
|------|-----------------|
| Good | Original messages are preserved, RAG context is separated, relevant chunks are bounded and ordered, and no-hit instructions are explicit. |
| Base | No retrieval hit: the upstream call receives a no-hit context under `STRICT_RAG`, not a silent pass-through prompt. |
| Bad | The builder overwrites user messages, injects full documents, leaks full prompts or keys, or forces JSON output without an explicit supported API contract. |

## 7. Wrong vs Correct

### Wrong

```text
Replace the first system message with a large concatenation of all retrieved text and tell the model to always answer confidently.
```

This discards user intent, breaks compatibility, and increases hallucination risk.

### Correct

```text
Preserve original messages and append a bounded internal RAG context block that labels private KB evidence, separates chunks, and tells the model to say when evidence is insufficient.
```

## 8. Structured Output Policy

Sangui-RAG-Gateway is an OpenAI-compatible RAG gateway. It should not default to changing the user's output format.

Current hard rules:

- Do not default to forced JSON output.
- Do not rewrite the user's requested output format.
- Do not add strong formatting instructions in the RAG prompt unless the app configuration explicitly supports that behavior.
- If `response_format` is supported later, align it with OpenAI-compatible request semantics.
- If an unsupported structured-output field is accepted, document whether it is ignored, passed through, or rejected.

Future roadmap only:

- `response_format` pass-through
- app-level output schema
- JSON schema validation
- structured-output retry

## 9. Future Enhancement Roadmap

The following are valid later enhancements, not current hard dependencies:

- more advanced context reordering
- source citation formatting
- configurable app-level prompt templates
- app-level output schema
- structured-output retries

Any enhancement must preserve original messages, keep context bounded, and avoid exposing private prompt or secret material.
