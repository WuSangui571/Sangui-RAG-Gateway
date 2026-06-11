# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: Product boundary, model config baseline, app readiness baseline, safe evidence and request-log forbidden fields.
- `.trellis/spec/backend/directory-structure.md`: Model, app, embedding, document, retrieval, gateway module responsibilities.
- `.trellis/spec/backend/database-guidelines.md`: `rag_model_config` schema, app default model binding, embedding dimension rules, migration requirements.
- `.trellis/spec/backend/error-handling.md`: Admin model config error matrix, app binding errors, gateway model/readiness errors.
- `.trellis/spec/backend/logging-guidelines.md`: Safe logging for model, embedding, request IDs, and forbidden key/vector/provider body fields.
- `.trellis/spec/backend/quality-guidelines.md`: Required backend tests for model config, embedding/vector storage, app/readiness, gateway regressions.
- `.trellis/spec/frontend/directory-structure.md`: API clients under `src/api`, route pages under `src/pages`, types under `src/types`.
- `.trellis/spec/frontend/component-guidelines.md`: Model config form behavior, explicit loading/error states, safe secret handling.
- `.trellis/spec/frontend/hook-guidelines.md`: Server-state calls must go through typed API clients; errors must not be swallowed.
- `.trellis/spec/frontend/state-management.md`: Provider check state should stay local/page-level; full keys must not persist.
- `.trellis/spec/frontend/type-safety.md`: Add explicit unions for capability/check status; avoid `any` and untyped API responses.
- `.trellis/spec/frontend/quality-guidelines.md`: Form validation, actionable errors, status fallback, typecheck/build requirements.
- `.trellis/spec/gateway/resilience.md`: Upstream checks need timeout/error classification and safe normalized messages.
- `.trellis/spec/rag/retrieval-quality.md`: Embedding config lookup is part of retrieval and must not fall back silently.
- `.trellis/spec/rag/document-ingestion.md`: Upload must fail clearly if embedding config/dimension/provider fails; no fake READY state.
- `.trellis/spec/security/rag-security.md`: Check/readiness/log responses must not expose keys, vectors, provider bodies, prompts, chunk content, or stack traces.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: This task crosses API, DB, DTO, frontend types, embedding, readiness, and security boundaries.

## Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
  - Current create/update requires `chatModel` through `normalizeRequiredText` and `validateRequiredFields`.
  - Current embedding validation requires model and dimension as a pair.
  - `findEnabledEmbeddingConfig` and `findMatchingEmbeddingConfig` already centralize embedding config lookup and should be extended for capability instead of duplicated elsewhere.
  - Existing preserve-key update semantics must be retained.

- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - `resolveDefaultModelConfig` and `bindDefaultModelConfig` currently only require enabled same-user config.
  - `assembleReadiness` already has separate `default_model_config` and `embedding_config` checks.
  - Readiness metadata is safe IDs/provider/model/dimension only; keep that boundary.

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - Binding endpoint performs 404/403 distinction before calling service.
  - It should reject non-chat-capable configs before mutation.

- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
  - Upload calls `findEnabledEmbeddingConfig(userId, kb.embeddingModel, kb.embeddingDimension)`.
  - If config/key/dimension/provider fails, document becomes `FAILED`; this visible failure behavior must remain.

- `backend/src/main/java/com/sangui/raggateway/retrieval/RetrievalService.java`
  - Query embedding uses same embedding config lookup and `EmbeddingClient.embed`.
  - No silent fallback to chat/provider config should be introduced.

- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
  - Existing `embed` validates expected dimension and never returns vector values to callers except as `float[]`.
  - A probe method can reuse URL normalization and response parsing but must expose only dimension and safe status.

- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
  - Form currently always requires `chat_model`.
  - Embedding fields are optional but coupled by manual validation.
  - Add capability segmented/select control, conditional fields, and check action/result state here.

- `frontend/src/pages/apps/AppConfigPage.tsx`
  - Binding modal currently calls `listModelConfigs('ENABLED')` and maps all rows as `${name} (${chat_model})`.
  - It should request/filter chat-capable configs only.

- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
  - KB creation still requires manual embedding model/dimension.
  - It can consume enabled embedding-capable configs to auto-fill those fields after model config checks have populated dimensions.

## Files Likely To Modify

Backend:

- `backend/src/main/resources/db/migration/V8__model_config_capability_split.sql`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/CreateModelConfigDTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/dto/UpdateModelConfigDTO.java`
- `backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java`
- New `ModelConfigCapability` enum and check DTO/VO/service classes under `backend/src/main/java/com/sangui/raggateway/model/`.
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/EmbeddingClient.java`
- `backend/src/main/java/com/sangui/raggateway/embedding/OpenAiCompatibleEmbeddingClient.java`
- Related tests under `backend/src/test/java/com/sangui/raggateway/model/`, `app/`, `embedding/`, `document/`, `retrieval/`, and gateway regressions as needed.

Frontend:

- `frontend/src/types/model-config.ts`
- `frontend/src/api/model-configs.ts`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/pages/apps/AppConfigPage.tsx`
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
- `frontend/src/app/i18n/dict.ts`
- Possibly shared status/check display components under `frontend/src/components/domain/`.

Specs/docs:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- README/runbook only if setup instructions change.

## Risk / Boundary Notes

- Database migration is likely required because `chat_model` is currently `NOT NULL`; embedding-only configs need it nullable.
- Capability must be one backend invariant, not frontend-only filtering. App binding and readiness must enforce it server-side.
- Existing configs must be backfilled deterministically to avoid breaking V0.2 rows.
- Check APIs will call external providers. They must use explicit timeouts and return safe diagnostic summaries only.
- Chat check should not return assistant text and should not persist request-log rows as if it were public `/v1/chat/completions`.
- Embedding dimension probe must not expose vector values.
- `ModelConfigService.updateAdminConfig` currently cannot clear `chat_model` because blank values are ignored. Embedding-only conversion needs explicit null/clear semantics or a full replacement path.
- Frontend must not persist upstream API keys or check payloads outside local form state.
- Knowledge base remains fixed to one embedding model and dimension; changing KB dimension after upload/retrieval remains out of scope.

## Required Tests

Backend:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,ModelConfigAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q "-Dtest=DocumentServiceTest,RetrievalServiceTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
```

Frontend:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Optional manual smoke only when runtime secrets and local provider state are available:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 -ApiKey "<fresh-key>" -AppId <app-id> -AdminUserId <admin-user-id> -VerifyRevokedKey -RevokedApiKey "<revoked-key>"
```
