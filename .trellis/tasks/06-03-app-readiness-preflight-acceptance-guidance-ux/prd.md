# App Readiness Preflight and Acceptance Guidance UX

## Goal

Add an app readiness preflight surface before Smoke acceptance so users can see why an app cannot pass smoke yet. The UI should diagnose configuration prerequisites for an app without exposing secrets, prompts, chunk content, provider response bodies, or other sensitive material.

This task is the next step after the frontend Smoke streaming/request-log acceptance UX. It keeps the work on the same acceptance chain and reduces demo/debug cost before expanding deeper RAG capability.

## Scope Classification

Complex Task.

Reasons:
- Crosses frontend Smoke/App UX, typed API contracts, backend admin app readiness service/controller, and tests.
- May introduce a new lightweight admin readiness endpoint if existing APIs cannot safely provide all readiness facts.
- Touches sensitive app/API key/model/knowledge-base metadata and must preserve tenant and secret boundaries.

## Current State Summary

Previous recorded workspace sessions show:
- RAG demo smoke automation and runbook hardening are complete and archived.
- Frontend Smoke Test page now covers non-streaming chat, streaming SSE, request-log evidence, and revoked-key auth.
- Manual acceptance passed with safe evidence only.
- Correct Compose command is `docker compose --env-file .env -f deploy/docker-compose.yml up -d --build`.
- Current gap: Smoke UX assumes app, default chat model config, default KB, READY KB, active key, and embedding config are already prepared.

## Requirements

- Provide a preflight status section on the frontend Smoke/App acceptance workflow.
- Show readiness checks using only safe metadata.
- Each check must display a stable status from this set:
  - `READY`
  - `MISSING`
  - `DISABLED`
  - `NOT_READY`
- Cover at least these readiness dimensions:
  - App exists and is enabled.
  - App has an enabled default chat model config.
  - App has a bound default knowledge base.
  - Bound knowledge base status is `READY`.
  - App has at least one active app API key available for smoke.
  - An enabled embedding config exists for the bound KB's `embedding_model` and `embedding_dimension`.
- Include short actionable guidance for failed checks, such as "bind a default model config", "upload and process documents until KB is READY", or "create an active app API key".
- Keep the preflight display operational and compact, consistent with existing admin console style.
- Reuse existing APIs where they are sufficient and safe.
- If existing APIs cannot answer embedding config availability or active key status without awkward client-side inference, add one lightweight admin endpoint.

## Preferred Backend Contract If New API Is Needed

Endpoint:

```http
GET /api/admin/apps/{appId}/readiness
X-Admin-User-Id: <userId>
```

Response envelope:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "app_id": 1,
    "user_id": 1,
    "overall_status": "READY",
    "checks": [
      {
        "key": "app",
        "label": "App",
        "status": "READY",
        "message": "App is enabled.",
        "metadata": {
          "app_status": "ENABLED"
        }
      }
    ]
  }
}
```

Suggested VO fields:

```text
AppReadinessVO
- app_id: number
- user_id: number
- overall_status: READY | MISSING | DISABLED | NOT_READY
- checks: AppReadinessCheckVO[]

AppReadinessCheckVO
- key: app | default_model_config | default_knowledge_base | knowledge_base_status | active_api_key | embedding_config
- label: string
- status: READY | MISSING | DISABLED | NOT_READY
- message: string
- metadata: object with safe scalar metadata only
```

Safe metadata examples:

```text
app_status
default_model_config_id
provider_name
chat_model
default_knowledge_base_id
knowledge_base_status
embedding_model
embedding_dimension
active_key_count
embedding_config_id
embedding_provider_name
```

Forbidden metadata/response fields:

```text
api_key
key_hash
api_key_encrypted
upstream_api_key
authorization
prompt
messages
full_messages
augmented_prompt
chunk_content
content
embedding
provider_response_body
stack_trace
storage_path
environment variables
```

Status rules:

| Check | READY | MISSING | DISABLED | NOT_READY |
|---|---|---|---|---|
| app | App exists and `status=ENABLED` | App not found should use 404, not check row | App exists but `status=DISABLED` | n/a |
| default_model_config | Bound config exists, same user, `ENABLED`, usable chat model present | `default_model_config_id` is null or config missing | Bound config status is `DISABLED` | Config exists but required chat fields are incomplete |
| default_knowledge_base | App has bound KB same user | `default_knowledge_base_id` is null or KB missing | n/a | n/a |
| knowledge_base_status | Bound KB status is `READY` | n/a | n/a | Bound KB status is `EMPTY`, `PROCESSING`, or `FAILED` |
| active_api_key | At least one app API key is `ACTIVE` and not expired | No API key exists | Only disabled/revoked/expired keys exist | n/a |
| embedding_config | Enabled model config exists for KB `embedding_model` + `embedding_dimension` with usable encrypted upstream key | No matching embedding config | Matching config exists but disabled | Matching enabled config is incomplete or key unavailable |

Overall status:
- `READY` only when all checks are `READY`.
- `DISABLED` if app or a blocking config is disabled and no higher-priority missing prerequisite blocks the check.
- `MISSING` if a required binding/resource does not exist.
- `NOT_READY` for resources that exist but are not operational yet, such as KB not READY or incomplete matching config.

## Validation / Error Matrix

| Scenario | HTTP | Envelope | Expected result |
|---|---:|---|---|
| Missing `X-Admin-User-Id` | 400 | `ApiResponse` | `INVALID_REQUEST`; no DB mutation |
| Non-numeric `X-Admin-User-Id` | 400 | `ApiResponse` | `INVALID_REQUEST` |
| Non-positive `X-Admin-User-Id` | 400 | `ApiResponse` | `INVALID_REQUEST` |
| App id does not exist | 404 | `ApiResponse` | `NOT_FOUND`; no readiness details |
| App belongs to another user | 403 | `ApiResponse` | `FORBIDDEN` with generic `Access denied` |
| App disabled | 200 | `ApiResponse` | app check `DISABLED`; overall not `READY` |
| Missing default model config | 200 | `ApiResponse` | default model check `MISSING` |
| Disabled default model config | 200 | `ApiResponse` | default model check `DISABLED` |
| Missing default KB | 200 | `ApiResponse` | default KB check `MISSING`; KB/embedding checks should not fabricate readiness |
| KB exists but not READY | 200 | `ApiResponse` | KB status check `NOT_READY` with safe status only |
| No active API key | 200 | `ApiResponse` | active key check `MISSING` or `DISABLED`, no key values |
| Missing matching embedding config | 200 | `ApiResponse` | embedding check `MISSING` |
| Matching embedding config disabled | 200 | `ApiResponse` | embedding check `DISABLED` |
| Fully prepared app | 200 | `ApiResponse` | all checks `READY`, overall `READY` |

## Good / Base / Bad Cases

Good:
- Enabled app with enabled default chat model config, bound READY KB, at least one active key, and enabled matching embedding config. Preflight shows all checks `READY`, and Smoke actions remain available.

Base:
- App exists but one or more prerequisites are absent or not ready. Preflight displays exact failed checks and actionable guidance, while preserving the current Smoke workflow. No secrets or content are shown.

Bad:
- UI lets users interpret a missing/disabled/not-ready prerequisite as smoke success.
- Backend readiness response includes plaintext keys, key hashes, encrypted keys, prompts, provider bodies, full document/chunk content, embeddings, stack traces, or storage paths.
- Frontend duplicates readiness logic from multiple APIs in a way that becomes a second source of truth after a backend readiness endpoint is introduced.
- Readiness endpoint silently falls back to `READY` when a dependency lookup fails.

## Files Likely To Modify

Backend, if a new endpoint is required:
- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- New VO types under `backend/src/main/java/com/sangui/raggateway/app/vo/`
- Possibly small mapper/service helpers in `ApiKeyService` and `ModelConfigService`
- Backend tests under `backend/src/test/java/com/sangui/raggateway/app/`

Frontend:
- `frontend/src/pages/smoke/SmokeTestPage.tsx`
- `frontend/src/api/apps.ts` or a domain-specific API client file if existing organization indicates one
- `frontend/src/types/app.ts` or related shared type file
- Possibly a small domain component under `frontend/src/components/domain/` if the status list becomes reusable

Spec/docs if API is added:
- `.trellis/spec/sangui-rag-gateway.md`
- Possibly `.trellis/spec/frontend/type-safety.md` if new frontend contract should be recorded

## Explicit Non-Scope

- Do not change public `/v1/*` gateway behavior.
- Do not change retrieval SQL, topK, thresholds, no-hit policy, prompt construction, streaming behavior, or request-log persistence.
- Do not add database migrations unless implementation proves an existing table cannot answer readiness. Expected path should not require DB changes.
- Do not introduce provider fallback, model fallback, retry, health check, or circuit breaker behavior.
- Do not expose or recover plaintext app API keys or upstream keys.
- Do not render assistant answer text, chunk content, prompt content, provider body, embeddings, stack traces, or storage paths.
- Do not turn Smoke page into a chat playground or broad low-code workflow UI.

## Acceptance Criteria

- [ ] A user can select or identify an app and see preflight status before running Smoke checks.
- [ ] Preflight reports app, default model config, default KB, KB readiness, active key availability, and embedding config availability.
- [ ] Each readiness item uses `READY`, `MISSING`, `DISABLED`, or `NOT_READY`.
- [ ] Failed checks include concise actionable guidance.
- [ ] The UI and any API response expose only safe metadata.
- [ ] If a new admin readiness endpoint is added, it is tenant-scoped by `X-Admin-User-Id` and returns `ApiResponse`.
- [ ] If a new endpoint is added, spec is updated with endpoint, payload fields, validation/error matrix, and Good/Base/Bad cases.
- [ ] Backend tests cover successful readiness, missing config, disabled config/key, KB not ready, missing embedding config, cross-user access, and safe-field absence.
- [ ] Frontend types are explicit, no `any`, and align with backend snake_case fields.
- [ ] Frontend typecheck/build pass.
- [ ] Browser/manual smoke verifies the preflight section shows blocking prerequisites before running chat smoke.

## Required Tests

Backend targeted tests, if backend endpoint/service changes:

```powershell
mvn -q "-Dtest=AppServiceTest,AppAdminControllerTest" test
mvn -q "-Dtest=ModelConfigServiceTest,ApiKeyServiceTest,KnowledgeBaseServiceTest" test
```

Backend compile:

```powershell
mvn -q -DskipTests compile
```

Frontend checks:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

Manual/browser smoke:
- Open the Smoke page.
- Select or enter an app that is intentionally missing one prerequisite.
- Confirm preflight shows the exact blocking check and safe metadata only.
- Prepare all prerequisites.
- Confirm preflight changes to all `READY`.
- Run existing non-streaming/streaming/request-log/revoked-key smoke path unchanged.

## Assertion Points

- Backend service/controller tests assert `$.data.checks[*].status` values for each readiness condition.
- Tests assert cross-user app access returns 403 and does not return readiness details.
- Tests assert missing app returns 404.
- Tests assert serialized readiness response does not contain forbidden field names.
- Frontend type definitions model readiness status as a union type.
- Frontend rendering includes loading/error/empty states and explicit fallback for unknown status values without treating them as ready.
- No implementation uses broad try/catch to turn lookup failures into success.

## Planning Self-Check

- Acceptance criteria: defined.
- Prohibited modification scope: defined.
- Expected files: listed.
- Required tests: listed.
- Concrete guidelines read: project spec, frontend, backend, gateway, RAG, security, and cross-layer docs.
- Open questions: none blocking. The implementation should decide whether existing APIs are sufficient; if not, add the lightweight endpoint above.
- API/DTO alignment: preferred endpoint, fields, statuses, and safe metadata are defined.
