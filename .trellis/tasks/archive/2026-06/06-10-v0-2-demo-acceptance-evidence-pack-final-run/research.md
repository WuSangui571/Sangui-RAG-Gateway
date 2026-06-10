# Focused Code Research

## Current Project State From Journal

- Last completed task: Demo Smoke Runtime Evidence Checklist Finalization.
- Last commit recorded in journal: `d17b806` (`docs: demo smoke runtime evidence checklist`).
- Current evidence contract is stable:
  - Durable checklist: `docs/runtime-evidence-checklist.md`
  - Canonical safe/forbidden evidence rules: `README.md`
  - Automation contract: `.trellis/spec/sangui-rag-gateway.md`
- Current working tree already had unrelated Trellis archive metadata changes before this task started. Do not revert them.

## Task Classification

Complex Task.

This is an acceptance evidence task across backend health, frontend proxy, admin readiness, gateway non-streaming/streaming, request-log observability, RAG hit chunks, API key revocation, and secret-safe evidence recording. The expected path is evidence-only, but failures may need routing to a concrete backend/frontend/gateway/RAG/security boundary.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: project boundary, implemented demo smoke automation rule, acceptance checklist, safe/forbidden evidence fields.
- `docs/runtime-evidence-checklist.md`: durable metadata-only evidence pack template.
- `README.md`: canonical demo acceptance flow and safe/forbidden output fields.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: cross-layer validation, tenant/secret boundaries, Good/Base/Bad cases.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log and structured logging fields; no keys, prompts, full messages, chunk content, provider raw body, or stack trace.
- `.trellis/spec/backend/error-handling.md`: OpenAI-compatible gateway errors; admin envelope; readiness, request-log, auth, RAG error matrices.
- `.trellis/spec/backend/quality-guidelines.md`: targeted backend tests if a boundary fix becomes necessary.
- `.trellis/spec/gateway/resilience.md`: upstream timeout/error/streaming/request-log failure behavior.
- `.trellis/spec/rag/retrieval-quality.md`: tenant-safe retrieval, `hit_chunk_ids`, no-hit behavior, request-log hit chunk expectations.
- `.trellis/spec/rag/prompt-context-policy.md`: no raw prompt/messages/full context in logs or evidence.
- `.trellis/spec/security/rag-security.md`: secret-safe observability, tenant isolation, hit-chunk evidence boundaries.
- `.trellis/spec/frontend/type-safety.md`: frontend readiness/request-log DTO alignment and forbidden fields not to model as response contracts.

## Code Patterns Found

- `scripts/demo-smoke.ps1`
  - One executable smoke path covers backend health, frontend proxy health, readiness, non-streaming chat, streaming chat, request-log list/detail/hit-chunks, and revoked-key 401.
  - It never prints the fresh or revoked app key as a standalone value.
  - It prints non-streaming content length only, not answer text.
  - It records SSE evidence as `data:` count plus `[DONE]`, but the raw stream remains in process memory and should not be committed.
  - It recursively scans parsed JSON properties for forbidden fields in readiness, request-log list/detail, and hit-chunks.

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `GET /api/admin/apps/{appId}/readiness` validates `X-Admin-User-Id`, verifies app ownership, then returns `ApiResponse<AppReadinessVO>`.
  - Missing app returns 404; cross-user app returns 403.

- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - `assembleReadiness` produces checks for `app`, `default_model_config`, `default_knowledge_base`, `knowledge_base_status`, `active_api_key`, and `embedding_config`.
  - Metadata is intended to be safe IDs/status/provider/model fields only.
  - Overall readiness priority is `MISSING` > `DISABLED` > `NOT_READY` > `READY`.

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - Request-log list/detail/hit-chunks endpoints all validate user ID and app ownership before returning data.
  - Hit-chunks require the app's default knowledge base and request-log existence.

- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - Hit chunk summaries are bounded by `HIT_CHUNK_SUMMARY_MAX_CHARS = 200`.
  - Evidence may confirm summary count and metadata, but must not record summary text.

- `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
  - `/v1/*` auth returns OpenAI-compatible `401 invalid_api_key` for missing, malformed, unknown, disabled, revoked, expired keys, or disabled app.
  - Revoked-key verification should record only HTTP status and error code.

- `frontend/src/pages/smoke/SmokeTestPage.tsx`
  - Browser smoke UI mirrors the CLI evidence surface and intentionally displays content length/SSE counts/request-log metadata, not answer body/raw SSE/key values.

- `frontend/src/types/app.ts` and `frontend/src/types/request-log.ts`
  - Readiness and request-log DTOs use snake_case backend fields.
  - `HitChunkSummaryVO.summary` exists for API/UI metadata but task evidence must not copy its text.

## Files Likely To Modify

Expected evidence-only path:

- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`
  - Create metadata-only evidence pack from `docs/runtime-evidence-checklist.md`.

Already prepared by Codex:

- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/prd.md`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/research.md`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/implement.jsonl`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/check.jsonl`
- `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/debug.jsonl`

Only if runtime evidence exposes a concrete defect:

- Readiness defect:
  - `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `frontend/src/types/app.ts`
  - `frontend/src/api/apps.ts`
  - `frontend/src/pages/smoke/SmokeTestPage.tsx`
- Request-log or hit-chunk defect:
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogService.java`
  - `backend/src/main/java/com/sangui/raggateway/log/vo/*`
  - `frontend/src/types/request-log.ts`
  - `frontend/src/api/request-logs.ts`
  - `frontend/src/pages/smoke/SmokeTestPage.tsx`
- Auth/revoked-key defect:
  - `backend/src/main/java/com/sangui/raggateway/common/security/GatewayAuthFilter.java`
  - `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyService.java`
- Gateway non-streaming/streaming/upstream defect:
  - `backend/src/main/java/com/sangui/raggateway/gateway/openai/OpenAiChatCompletionsController.java`
  - `backend/src/main/java/com/sangui/raggateway/gateway/completion/ChatCompletionGatewayService.java`
  - `backend/src/main/java/com/sangui/raggateway/gateway/upstream/OpenAiCompatibleUpstreamClient.java`
  - `scripts/demo-smoke.ps1` only if the executable validation contract is wrong.
- Proxy defect:
  - `frontend/nginx.conf`
  - `frontend/vite.config.ts`
  - `deploy/docker-compose.yml`

Do not modify these implementation files for evidence formatting only.

## Risk / Boundary Notes

- Evidence must be metadata-only. Never paste full script transcript if it contains raw SSE, answer text, key values, prompts, messages, chunk summaries, provider bodies, or unreviewed JSON.
- `scripts/demo-smoke.ps1` may print gateway error messages and bounded HTTP body previews on failures. Review any captured output before copying into evidence.
- `HitChunkSummaryVO.summary` is an API field, but committed evidence must not include the summary text.
- `README.md` contains placeholder strings like `sk-sangui-<...>` and rule text with forbidden names. Forbidden-field scans may intentionally hit rule text/placeholders; review hits rather than treating every textual match as a leak.
- A successful non-streaming run is not enough for this task. The final pack must also cover readiness, streaming, request-log list/detail, hit-chunks, revoked-key 401, and static forbidden-field review.
- If readiness is non-ready, do not bypass it or downgrade to a partial pass. Record the boundary and route to the underlying config/auth/retrieval/embedding issue.
- If request-log no matching row occurs, do not accept stale request rows. Match the smoke `Message` prefix as the script does.
- Fresh demo key and revoked demo key must be created/managed outside committed files. Record key IDs/statuses only if available and safe.
- Current task should not update API contracts, DTOs, database schema, frontend types, README, docs, or script behavior unless the formal run proves a defect.

## Required Tests

Evidence-only path:

```powershell
$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content .\scripts\demo-smoke.ps1 -Raw), [ref]$null)
```

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\demo-smoke.ps1 `
  -ApiKey "<fresh-demo-key>" `
  -AppId <app-id> `
  -AdminUserId <admin-user-id> `
  -BackendBaseUrl "http://localhost:8080" `
  -FrontendBaseUrl "http://localhost:3000" `
  -Message "<known-demo-message>" `
  -RevokedApiKey "<revoked-demo-key>" `
  -VerifyRevokedKey
```

```powershell
rg -n --hidden --glob "!frontend/node_modules/**" --glob "!backend/target/**" --glob "!frontend/dist/**" --glob "!frontend/playwright-report/**" --glob "!frontend/test-results/**" `
  "sk-sangui-|Authorization: Bearer sk-sangui-|api_key_encrypted|key_hash|upstream_api_key|provider_response_body|stack_trace|augmented_prompt|full_messages|chunk_content|raw SSE|raw answer" `
  .trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run docs README.md scripts
```

If backend implementation changes:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ApiRequestLogServiceTest,ApiRequestLogAdminControllerTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest" test
mvn -q "-Dtest=RetrievalServiceTest,RagPromptBuilderTest" test
```

If frontend implementation changes:

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

If script/docs only change:

```bash
git diff --check
```

## Planning Self-Check

- Acceptance criteria are explicit: yes.
- Prohibited evidence fields are explicit: yes.
- Prohibited modification range is explicit: yes, no business code in the evidence-only path.
- Expected evidence file is explicit: `.trellis/tasks/06-10-v0-2-demo-acceptance-evidence-pack-final-run/evidence-pack.md`.
- Must-run checks are listed: yes.
- Specific guideline files were read, not only index files: yes.
- API / DB / frontend DTO changes expected: no, unless a concrete runtime defect is found.
- Requirement uncertainty: none blocking. Runtime secrets and local demo state must be supplied outside committed files by the executor.

