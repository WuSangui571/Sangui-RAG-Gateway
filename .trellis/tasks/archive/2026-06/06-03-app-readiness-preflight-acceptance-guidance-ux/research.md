# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, app/model/KB/API key core model, admin/gateway split, safe evidence fields, demo smoke acceptance contract.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this task spans admin API, DTO/VO, frontend types, Smoke UX, tests, and safe metadata.
- `.trellis/spec/frontend/directory-structure.md`: API clients live under `frontend/src/api`, typed models under `frontend/src/types`, route pages under `frontend/src/pages`.
- `.trellis/spec/frontend/type-safety.md`: frontend response models must use explicit status unions and snake_case fields aligned with backend `@JsonProperty`.
- `.trellis/spec/frontend/state-management.md`: readiness should be server state/local page state, not global domain cache.
- `.trellis/spec/frontend/component-guidelines.md`: readiness UI should be compact admin status UI with explicit loading/error states.
- `.trellis/spec/frontend/quality-guidelines.md`: safe admin UX, no secret persistence, no full document/prompt rendering.
- `.trellis/spec/backend/directory-structure.md`: readiness belongs in app/admin domain unless it is extracted as a named app service/VO.
- `.trellis/spec/backend/error-handling.md`: admin endpoint should use `ApiResponse`, `BusinessException`, 400/403/404 admin matrix.
- `.trellis/spec/backend/logging-guidelines.md`: readiness logs/responses must avoid keys, prompts, provider bodies, chunks, embeddings, stack traces.
- `.trellis/spec/backend/database-guidelines.md`: readiness queries must be tenant-scoped by `user_id`/`app_id`; no migration expected.
- `.trellis/spec/backend/quality-guidelines.md`: tests should cover tenant isolation, safe responses, and failure paths.
- `.trellis/spec/rag/retrieval-quality.md`: readiness should not modify retrieval behavior; use KB/config readiness only.
- `.trellis/spec/rag/document-ingestion.md`: KB status meanings and ingestion states should be surfaced as metadata only.
- `.trellis/spec/security/rag-security.md`: readiness must expose safe metadata only and preserve tenant/secret boundaries.
- `.trellis/spec/gateway/resilience.md`: do not add fallback/retry/provider health behavior as part of readiness.

## Code Patterns Found

- Admin App endpoints are centralized in `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java` and return `ApiResponse<T>`.
- App tenant access pattern:
  - Same-user lookup: `appService.findByIdAndUserId(id, userId)`.
  - Missing vs cross-user distinction: if same-user lookup returns null, check `appService.findById(id)` and return 403 for different user, 404 for absent app.
- Existing App service resolution:
  - `AppService.resolveDefaultModelConfig(app)` returns same-user enabled model config only.
  - `AppService.resolveDefaultKnowledgeBase(app)` currently returns only READY KB, so readiness implementation should also fetch raw bound KB status when not READY.
- API key readiness source:
  - `ApiKeyService.listByAppIdAndUserId(appId, userId)` returns safe entities.
  - `ApiKeyService.isValid(apiKey)` already checks `ACTIVE` and expiry.
- Embedding config source:
  - `ModelConfigService.findEnabledEmbeddingConfig(userId, embeddingModel, embeddingDimension)` already models the operational embedding lookup used by retrieval/document ingestion.
  - To distinguish `MISSING` vs `DISABLED`, implementation may need a helper that finds matching embedding configs regardless of status.
- KB readiness source:
  - `KnowledgeBaseService.findByIdAndUserId(id, userId)` returns same-user KB including `status`, `embedding_model`, `embedding_dimension`.
- Frontend Smoke page:
  - `frontend/src/pages/smoke/SmokeTestPage.tsx` already loads apps and active keys, stores pasted plaintext key only in memory, and resets stale smoke evidence when inputs change.
  - Request-log validation already requires non-streaming pass before checking logs.
- Frontend API/type pattern:
  - `frontend/src/api/apps.ts` owns app admin API calls.
  - `frontend/src/types/app.ts` owns `AppVO`, binding DTOs/VOs, and `AppStatus`.
  - `frontend/src/components/domain/StatusTag.tsx` maps existing statuses and can be extended or a domain-specific readiness tag can be added.

## Files Likely To Modify

Backend if new endpoint is implemented:
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`: add `GET /api/admin/apps/{appId}/readiness`.
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`: add readiness assembly or delegate to an app readiness service.
- `backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessVO.java`: new response VO.
- `backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessCheckVO.java`: new check VO.
- `backend/src/main/java/com/sangui/raggateway/app/AppReadinessStatus.java`: optional enum for `READY|MISSING|DISABLED|NOT_READY`.
- `backend/src/main/java/com/sangui/raggateway/app/AppReadinessCheckKey.java`: optional enum for check keys.
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`: optional helper to find matching embedding config regardless of enabled status.
- `backend/src/test/java/com/sangui/raggateway/app/AppServiceTest.java`: readiness service rules.
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`: endpoint shape, 403/404, forbidden fields.

Frontend:
- `frontend/src/types/app.ts`: add `AppReadinessStatus`, `AppReadinessCheckKey`, `AppReadinessVO`, `AppReadinessCheckVO`.
- `frontend/src/api/apps.ts`: add `getAppReadiness(appId, adminUserId)`.
- `frontend/src/pages/smoke/SmokeTestPage.tsx`: load/render readiness before smoke steps; reset readiness/smoke state on app changes; avoid treating unknown status as ready.
- `frontend/src/components/domain/StatusTag.tsx`: optionally add readiness status support, or implement a local readiness tag to avoid overloading existing domain statuses.

Spec/docs:
- `.trellis/spec/sangui-rag-gateway.md`: update only if new endpoint is implemented.
- `.trellis/spec/frontend/type-safety.md`: optional, only if the new readiness contract should be recorded for future agents.

## Risk / Boundary Notes

- Readiness endpoint should not decrypt upstream keys just to prove availability. It can check `api_key_encrypted` presence unless implementation needs to distinguish corrupt encryption; decrypting risks new failure/logging paths.
- Existing `AppService.resolveDefaultKnowledgeBase` returns null for non-READY KB, which is insufficient for readiness because the UI needs `NOT_READY` with the current KB status. Use `knowledgeBaseService.findByIdAndUserId` for raw status.
- Existing `ModelConfigService.findEnabledEmbeddingConfig` cannot distinguish missing from disabled matching config. Add a targeted helper if the PRD's `DISABLED` status must be precise.
- Do not weaken `bindDefaultKnowledgeBase`; it currently rejects non-READY KB. Readiness can still diagnose a now-not-ready bound KB if status later changed after binding.
- Active key readiness can use `ApiKeyService.isValid` over same-user app keys. Do not expose counts by status beyond safe aggregate metadata needed by the UI.
- Frontend can continue requiring pasted plaintext key for actual smoke; readiness must not imply the plaintext key is recoverable.
- Avoid client-only readiness assembled from many endpoints if a backend readiness endpoint is added; that would create two sources of truth.

## Required Tests

Backend targeted:

```powershell
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ModelConfigServiceTest,ApiKeyServiceTest,KnowledgeBaseServiceTest" test
mvn -q -DskipTests compile
```

Frontend:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

Manual/browser:
- Open Smoke page with an app missing a default model config; assert preflight shows `MISSING` for default model config and smoke is not represented as ready.
- Use app with non-READY KB; assert KB status check shows `NOT_READY` and only safe status/IDs are shown.
- Use fully prepared app; assert all checks `READY`, then run existing non-streaming, streaming, request-log, and revoked-key checks unchanged.

