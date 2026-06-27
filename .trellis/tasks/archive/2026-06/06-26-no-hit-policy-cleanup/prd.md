# NoHitPolicy Dead-Code Cleanup / Contract Clarification

## Goal

Clarify the current `NoHitPolicy` contract without changing public behavior. The task starts from the question: is `NoHitPolicy` pure dead code, or is `no_hit_policy` already part of DB/API/frontend/spec contract?

Research shows this is **not** a pure dead-code deletion task. The Java enum `backend/src/main/java/com/sangui/raggateway/rag/prompt/NoHitPolicy.java` is unused dead code, but the `no_hit_policy` value is already persisted in `rag_app`, returned by Admin App APIs, typed in the frontend, and documented in RAG specs. Therefore this task must be treated as **contract clarification**, not schema/API removal.

## Task Classification

Simple Task with a structural decision gate.

- Clear target and limited file set.
- Structural trigger exists because `no_hit_policy` crosses DB, Admin API VO, frontend type, runtime prompt behavior, and spec.
- Do not optimize for deleting the smallest file if that leaves a persisted contract silently ignored.

## Requirements

- Confirm and preserve the current runtime behavior: MVP supports only `STRICT_RAG`; no-hit retrieval still calls upstream with explicit insufficient-evidence prompt.
- Do not remove `rag_app.no_hit_policy`, `AppEntity.noHitPolicy`, `AppVO.no_hit_policy`, frontend `AppVO.no_hit_policy`, or migration defaults in this task.
- Remove or replace the unused `rag.prompt.NoHitPolicy` enum only if the persisted `no_hit_policy` field is handled by a single runtime owner.
- Add explicit validation for persisted `no_hit_policy` so unsupported values such as `PASS_THROUGH` or `ERROR` do not silently execute as `STRICT_RAG`.
- Keep unsupported future policies documented as future/configurable policy values only, not implemented behavior.
- Do not add PASS_THROUGH or ERROR behavior in this task.
- Do not add Admin API fields, frontend editing controls, DB migrations, environment variables, fallback behavior, retry behavior, provider routing, or request-log schema changes.

## Current Research Decision

This task should follow the **persisted contract clarification** path:

- `NoHitPolicy.java` is production dead code: only defines `STRICT_RAG` and is not imported by runtime or tests.
- `no_hit_policy` is not dead contract: `V7__add_app_default_knowledge_base.sql`, `AppEntity`, `AppVO`, frontend `AppVO`, backend tests, and specs all reference it.
- Runtime owner gap: `AppRetrievalConfig.from(AppEntity)` validates topK/threshold/context limits but does not validate or expose `noHitPolicy`.
- Runtime behavior today is effectively always `STRICT_RAG` because `RagPromptBuilder` chooses fixed no-hit instructions from `RetrievalResult.isNoHits()`.
- Risk: a manually persisted `PASS_THROUGH`, `ERROR`, blank, or typo value would currently be exposed by API but ignored by runtime prompt behavior.

## API / Payload Fields

No new API endpoints or request payload fields are allowed.

Existing response field that must remain:

```json
{
  "no_hit_policy": "STRICT_RAG"
}
```

Current exposed surfaces:

- Admin App create/detail/list responses return `AppVO.no_hit_policy`.
- Frontend type `frontend/src/types/app.ts` includes `no_hit_policy: 'STRICT_RAG' | string | null`.
- DB column `rag_app.no_hit_policy VARCHAR(32) NOT NULL DEFAULT 'STRICT_RAG'`.

## Validation / Error Matrix

| Scenario | Expected behavior | Assertion point |
|---|---|---|
| App row has `no_hit_policy='STRICT_RAG'` | Retrieval config resolves successfully; no-hit prompt remains strict | `AppServiceTest`, `RagPromptBuilderTest`, `ChatCompletionGatewayServiceTest` |
| App row has `no_hit_policy=null` in unit-level entity or corrupted runtime object | Retrieval config resolution fails visibly | `AppServiceTest` |
| App row has blank `no_hit_policy` | Retrieval config resolution fails visibly | `AppServiceTest` |
| App row has `no_hit_policy='PASS_THROUGH'` | Runtime rejects unsupported persisted policy; no silent pass-through | `AppServiceTest`, optionally gateway service test |
| App row has `no_hit_policy='ERROR'` | Runtime rejects unsupported persisted policy; no no-hit error behavior is added | `AppServiceTest`, optionally gateway service test |
| No retrieval hits under valid `STRICT_RAG` | Upstream is still called with insufficient-evidence context | existing/new `RagPromptBuilderTest`, `ChatCompletionGatewayServiceTest` |
| Admin app responses | Still include `no_hit_policy` and safe retrieval config fields | `AppAdminControllerTest` |

If gateway-level invalid policy mapping is touched, use the existing `model_config_not_ready` conflict path for invalid app retrieval configuration unless a spec update explicitly defines a new public error code. Do not introduce a new public error code in this task.

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | `STRICT_RAG` remains the only supported runtime policy, app config resolution validates it, no-hit prompt still tells the model that KB evidence is insufficient, and public/Admin response fields remain unchanged. |
| Base | The unused `rag.prompt.NoHitPolicy` enum is deleted or replaced by an actually used owner without broad package churn. Existing rows with default `STRICT_RAG` continue working. |
| Bad | DB/API/frontend `no_hit_policy` is removed; `PASS_THROUGH` or `ERROR` is implemented without a new PRD; unsupported values silently behave like strict RAG; frontend types drift from backend; migration schema is changed unnecessarily. |

## Expected Implementation Approach

Preferred implementation path for DeepSeek:

1. Treat `no_hit_policy` as persisted app retrieval contract.
2. Add a single runtime validation owner for the field, most likely in `AppRetrievalConfig.from(AppEntity)` or a small app-domain enum/helper used by it.
3. Support only `STRICT_RAG`; reject null, blank, and unsupported values visibly.
4. Delete the existing unused `backend/src/main/java/com/sangui/raggateway/rag/prompt/NoHitPolicy.java` only if it remains unused after the new owner is in place.
5. Keep `RagPromptBuilder` behavior unchanged unless passing the validated policy is necessary to make ownership explicit. Do not implement `PASS_THROUGH` or `ERROR`.
6. Update specs only where wording currently implies all three policies are implemented today. Wording should say: current runtime supports `STRICT_RAG`; `PASS_THROUGH` and `ERROR` are future configurable policies requiring separate PRD/API/DB/frontend update.

## Files Likely To Modify

Likely business-code files for DeepSeek:

- `backend/src/main/java/com/sangui/raggateway/app/AppRetrievalConfig.java`: validate and expose current no-hit policy, or delegate to a small app-domain policy helper.
- `backend/src/main/java/com/sangui/raggateway/rag/prompt/NoHitPolicy.java`: delete if still unused after introducing the runtime owner, or move/replace with a used app-domain contract.
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`: add valid/invalid no-hit policy config resolution coverage.
- `backend/src/test/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayServiceTest.java`: add/adjust invalid retrieval-config/no-hit-policy gateway mapping only if app config failure mapping is touched.
- `backend/src/test/java/com/sangui/raggateway/rag/prompt/RagPromptBuilderTest.java`: keep or strengthen strict no-hit prompt assertion if builder signature changes.
- `.trellis/spec/sangui-rag-gateway.md`, `.trellis/spec/rag/retrieval-quality.md`, `.trellis/spec/rag/prompt-context-policy.md`, `.trellis/spec/backend/quality-guidelines.md`, `.trellis/spec/frontend/type-safety.md`: clarify current vs future no-hit policy contract if implementation changes wording.

Files that should not be modified without a new PRD:

- `backend/src/main/resources/db/migration/**`
- `frontend/src/**` implementation components or API clients
- `deploy/**`
- `.env.example`
- `README.md`, unless final implementation proves user-facing docs currently make a false runtime claim

## Required Tests

Run with a 60 second timeout per backend unit-test command when feasible:

```bash
cd backend
mvn -q "-Dtest=AppServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q "-Dtest=RetrievalServiceTest,RetrievalEvaluationServiceTest" test
mvn -q "-Dtest=AppAdminControllerTest" test
mvn -q -DskipTests compile
git diff --check
```

If only dead enum deletion plus AppRetrievalConfig validation changes are made and gateway code is not touched, minimum acceptable tests:

```bash
cd backend
mvn -q "-Dtest=AppServiceTest,RagPromptBuilderTest,ChatCompletionGatewayServiceTest" test
mvn -q -DskipTests compile
git diff --check
```

## Acceptance Criteria

- [ ] The task does not remove public DB/API/frontend `no_hit_policy` contract.
- [ ] Unsupported persisted policies fail visibly instead of silently becoming `STRICT_RAG`.
- [ ] Current `STRICT_RAG` no-hit behavior remains unchanged.
- [ ] Unused `NoHitPolicy` enum noise is removed or converted into a used single owner.
- [ ] Specs distinguish current supported runtime policy from future policy names.
- [ ] Targeted backend tests and compile pass.
- [ ] `git diff --check` passes.

## Prohibited Scope

- No DB schema migration.
- No public API response field removal.
- No frontend edit UI for no-hit policy.
- No implementation of `PASS_THROUGH` or `ERROR`.
- No retry/fallback/provider-routing changes.
- No retrieval SQL, embedding provider, request-log schema, or streaming behavior changes.
- No broad refactor outside app retrieval config, prompt contract, narrow tests, and directly relevant specs.

