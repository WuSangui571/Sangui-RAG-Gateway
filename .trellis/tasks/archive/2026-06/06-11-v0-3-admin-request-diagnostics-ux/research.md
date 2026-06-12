# Focused Code Research

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: Product boundary, implemented request-log observability API, readiness preflight baseline, safe evidence fields, V0.2 smoke evidence contract.
- `.trellis/spec/frontend/directory-structure.md`: Request-log page and domain component placement under `frontend/src/pages/request-logs` and `frontend/src/components/domain`.
- `.trellis/spec/frontend/component-guidelines.md`: Use dense admin UI patterns, explicit loading/error/empty states, request-log summaries only.
- `.trellis/spec/frontend/type-safety.md`: Request-log VO types, explicit status unions, no forbidden fields in frontend types.
- `.trellis/spec/frontend/state-management.md`: Server state remains page/hook level; readiness/request-log data must not move into global state.
- `.trellis/spec/frontend/hook-guidelines.md`: API calls go through typed clients; hooks/components expose safe error codes without swallowing errors.
- `.trellis/spec/frontend/quality-guidelines.md`: Request logs must help debug usage without exposing sensitive data.
- `.trellis/spec/backend/error-handling.md`: Gateway/admin error code families and existing request-log/readiness error matrices.
- `.trellis/spec/backend/logging-guidelines.md`: Safe observability fields, request-log persistence contract, forbidden request-log fields.
- `.trellis/spec/backend/database-guidelines.md`: `rag_request_log` fields, tenant-scoped request-log/hit chunk queries, no schema change expected.
- `.trellis/spec/backend/quality-guidelines.md`: Required request-log tests and regression expectations if backend changes.
- `.trellis/spec/gateway/resilience.md`: Upstream error/timeout classification and request-log failure metadata.
- `.trellis/spec/rag/retrieval-quality.md`: Retrieval/no-hit/hit_chunk_ids contract and safe hit chunk evidence.
- `.trellis/spec/rag/prompt-context-policy.md`: Prompt/context secrecy and no-hit behavior.
- `.trellis/spec/rag/document-ingestion.md`: KB/document status boundaries relevant to readiness diagnostics.
- `.trellis/spec/security/rag-security.md`: Safe request-log and hit-chunk evidence boundary.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Required contract, validation, and Good/Base/Bad matrix for cross-layer observability work.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: Search and reuse existing request-log/readiness UI before adding new abstractions.

## Code Patterns Found

- Typed request-log API client:
  - `frontend/src/api/request-logs.ts`
  - Existing functions: `listRequestLogs`, `getRequestLogDetail`, `getHitChunks`.
  - Pattern: `page/component -> typed API client -> ApiResponse<T>`.
- Request-log safe types:
  - `frontend/src/types/request-log.ts`
  - Existing safe fields match V0.3 requirements: `status`, `error_code`, `latency_ms`, `upstream_latency_ms`, `model`, `provider_name`, `messages_count`, `question_summary`, `hit_chunk_ids`, `usage`.
- Request-log list UI:
  - `frontend/src/pages/request-logs/RequestLogListPage.tsx`
  - Existing filters: `status`, `error_code`, `start_time`, `end_time`; existing detail drawer open flow by `request_id`.
- Request-log detail UI:
  - `frontend/src/components/domain/RequestLogDetailDrawer.tsx`
  - Existing display already includes request ID, status, error code, model/provider, latency, upstream latency, message count, usage, question summary, hit chunk IDs, and `HitChunksPanel`.
- Hit chunk safe evidence:
  - `frontend/src/components/domain/HitChunksPanel.tsx`
  - Shows bounded `summary` and safe metadata only; no full chunk content type exists.
- App readiness frontend contract:
  - `frontend/src/api/apps.ts`
  - `frontend/src/types/app.ts`
  - Existing `getAppReadiness(appId, adminUserId)` returns `overall_status` and `checks[]`.
- Existing readiness UI pattern:
  - `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - Shows readiness check rows and status tags; this can be reused conceptually, not copy-pasted wholesale.
- I18n key parity:
  - `frontend/src/app/i18n/dict.ts`
  - Adds keys to both `zh-CN` and `en-US`; `DictionaryKeyParity` type catches missing keys.
- Backend request-log controller/service:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - Existing tenant validation and safe VO conversion should remain source of truth.
- Backend request-log VO:
  - `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogVO.java`
  - `backend/src/main/java/com/sangui/raggateway/log/vo/ApiRequestLogDetailVO.java`
  - Existing fields are sufficient for frontend-first diagnostics.
- Backend readiness VO/service:
  - `backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessVO.java`
  - `backend/src/main/java/com/sangui/raggateway/app/vo/AppReadinessCheckVO.java`
  - `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - Existing check keys: `app`, `default_model_config`, `default_knowledge_base`, `knowledge_base_status`, `active_api_key`, `embedding_config`.

## Files Likely To Modify

Preferred frontend-only path:

- `frontend/src/types/request-log.ts`: add diagnostic boundary union and possibly pure display model types.
- `frontend/src/components/domain/requestDiagnostics.ts`: new pure mapping helper from request log detail + readiness + hit count to diagnostics.
- `frontend/src/components/domain/RequestDiagnosticsPanel.tsx`: new compact panel with boundary tag, safe summary, next steps, readiness evidence.
- `frontend/src/components/domain/RequestLogDetailDrawer.tsx`: load readiness and render diagnostics panel without blocking detail display.
- `frontend/src/app/i18n/dict.ts`: add zh-CN/en-US text keys for diagnostics.

Possible frontend support:

- `frontend/src/pages/request-logs/RequestLogListPage.tsx`: optionally show boundary column/tag if low-risk; not required for first acceptance.
- `frontend/src/api/apps.ts` and `frontend/src/types/app.ts`: likely no changes; existing readiness client/types suffice.

Backend only if current fields prove insufficient:

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
- `backend/src/main/java/com/sangui/raggateway/log/vo/*Diagnostic*.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogAdminControllerTest.java`
- Relevant spec files under `.trellis/spec/`.

## Risk / Boundary Notes

- Auth failures from `GatewayAuthFilter` are known not to reach request-log persistence. The UI must not require a request-log row for `invalid_api_key`; auth diagnostics may appear in smoke errors or operator guidance, not detail rows.
- Malformed JSON can also be unpersisted because request deserialization fails before controller request ID/log boundary.
- Frontend classification must remain display-only. It can map known safe `error_code` values to human guidance, but backend remains source of truth for readiness/tenant/gateway behavior.
- Existing request-log detail does not include readiness; if diagnostics load readiness, failure to load readiness must be shown as unavailable evidence, not as request failure.
- Hit chunks endpoint requires app default KB. For logs with empty hits or no default KB, diagnostics should show retrieval/request-log state safely.
- Do not add raw answer/SSE inspection. Streaming diagnostics should be limited to existing smoke metadata or known request-log `error_code` when present.
- `HitChunksPanel` currently renders bounded summaries. This is allowed by current spec, but any new diagnostic panel should prefer IDs/counts and avoid expanding sensitive text exposure.
- `dict.ts` currently includes mojibake for zh-CN in the working tree; do not broad-rewrite existing dictionary text. Add minimal keys consistently if coding later.

## Required Tests

Frontend:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Optional frontend smoke:

```bash
cd frontend
cmd /c npm run test:visual
```

Backend only if changed:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
```

Gateway/RAG regression only if backend gateway/readiness/retrieval behavior changes:

```bash
cd backend
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
mvn -q "-Dtest=ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest,OpenAiCompatibleUpstreamClientTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
```

## Current Recommendation

Implement V0.3 Admin Request Diagnostics UX as a frontend-first feature using existing request-log, readiness, and hit-chunk APIs. Avoid backend/API/DB changes unless implementation exposes a concrete missing safe field.
