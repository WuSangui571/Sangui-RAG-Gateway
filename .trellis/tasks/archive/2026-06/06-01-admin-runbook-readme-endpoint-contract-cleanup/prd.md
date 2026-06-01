# Admin Runbook and README Endpoint Contract Cleanup

## Goal

Make the demo/admin runbook endpoint contract explicit and repeatable so manual acceptance commands do not drift from the implemented Admin API routes.

The immediate trigger is the previously miswritten app default model config binding command. The implemented route is:

```http
PUT /api/admin/apps/{appId}/default-model-config
```

not:

```http
PUT /api/admin/apps/{appId}/model-config
```

This task is documentation and contract cleanup. It should not change backend behavior unless research finds a real implementation/test/spec contract mismatch.

## Classification

Complex Task.

Reason: the expected implementation is mostly README/spec documentation, but the contract spans README manual commands, Trellis specs, backend controller routes, frontend typed API clients, API key lifecycle, request-log verification, and PowerShell acceptance rules.

## Scope

### In Scope

- README/manual runbook cleanup for Admin API endpoint contracts.
- Add or improve commands for:
  - app default model config binding
  - app default knowledge base binding
  - model config creation
  - API key creation
  - API key revoke/disable validation where already supported
  - request-log list/detail/hit-chunk verification
- Clearly distinguish:
  - formal acceptance commands
  - quick manual checks
- Scan and, only if needed, synchronize `.trellis/spec/sangui-rag-gateway.md` or guideline docs when they disagree with implemented route contracts.
- Scan backend controllers/tests and frontend typed API clients to confirm the implemented contract.

### Out of Scope

- No Java business-code changes unless a real route/test/spec inconsistency is found.
- No frontend TypeScript implementation changes unless an actual API client/type mismatch is found.
- No database migrations.
- No RAG retrieval, prompt, embedding, auth, streaming, or request-log behavior changes.
- No smoke script behavior changes unless README and script contract are proven inconsistent.
- No provider key, app key, or `.env` changes.
- No auto-commit, auto-push, archive, or record-session in this planning handoff.

## API / Command / Payload Contract

All Admin API examples use:

```http
X-Admin-User-Id: <admin-user-id>
```

Admin responses use `ApiResponse<T>` with top-level `code`, `message`, and `data`.

### App Default Model Config Binding

```http
PUT /api/admin/apps/{appId}/default-model-config
Content-Type: application/json

{"model_config_id": 123}
```

Expected success data:

```json
{
  "app_id": 1,
  "user_id": 1,
  "default_model_config_id": 123
}
```

Forbidden stale route:

```http
PUT /api/admin/apps/{appId}/model-config
```

### App Default Knowledge Base Binding

```http
PUT /api/admin/apps/{appId}/knowledge-base
Content-Type: application/json

{"knowledge_base_id": 123}
```

Expected success data:

```json
{
  "app_id": 1,
  "user_id": 1,
  "default_knowledge_base_id": 123
}
```

### Model Config Creation

```http
POST /api/admin/model-configs
Content-Type: application/json

{
  "name": "demo-chat",
  "provider_name": "openai-compatible",
  "base_url": "https://example.com/v1",
  "api_key": "<upstream-provider-key>",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024,
  "status": "ENABLED"
}
```

Rules:

- Upstream provider key must be a placeholder in docs, never a real key.
- Response must show masked key only; never document `api_key_encrypted` or plaintext response fields.
- Formal PowerShell JSON examples should use UTF-8 no-BOM temp files and `curl.exe --data-binary`.

### API Key Create / Disable / Revoke

```http
POST /api/admin/apps/{appId}/api-keys
Content-Type: application/json

{"name":"demo-acceptance-YYYYMMDD","expires_at":null}
```

```http
POST /api/admin/api-keys/{id}/disable
POST /api/admin/api-keys/{id}/revoke
```

Rules:

- `key` appears only in create response.
- README must warn that plaintext app keys are one-time display and must not be committed.
- Revoked/disabled keys must fail public `/v1/*` checks with `401 invalid_api_key`.

### Request Log Acceptance

```http
GET /api/admin/apps/{appId}/request-logs?page=1&page_size=5&status=success
GET /api/admin/apps/{appId}/request-logs/{requestId}
GET /api/admin/apps/{appId}/request-logs/{requestId}/hit-chunks
```

Allowed evidence fields:

```text
request_id, model, provider_name, latency_ms, hit_chunk_ids,
chunk_id, document_id, knowledge_base_id, source_filename, chunk_index
```

Forbidden fields in README outputs/examples:

```text
full app API key, key_hash, upstream API key, api_key_encrypted,
full prompt, full messages, chunk content, embedding, provider raw body,
stack trace, storage path
```

## Formal Acceptance vs Quick Check

Formal acceptance:

- Use `curl.exe`, not PowerShell `curl` alias.
- For JSON POST/PUT, write request body to a temp file with `New-Object System.Text.UTF8Encoding($false)`.
- Submit with `curl.exe --data-binary "@$bodyPath"`.
- Remove temp files in cleanup.
- Check HTTP status and response JSON contract.

Quick manual checks:

- May use inline literal JSON with `curl.exe -d '{"key":"value"}'` when the command is clearly labeled non-formal.
- Must not use variable-based `curl.exe -d $body` in README because PowerShell 5.1 encoding can corrupt JSON.
- Must not count as regression evidence.

## Validation / Error Matrix

| Scenario | Expected result | Assertion point |
|---|---|---|
| README documents default model binding | Uses `PUT /api/admin/apps/{appId}/default-model-config` and payload `model_config_id` | `rg` over README and specs |
| Stale `/api/admin/apps/{appId}/model-config` remains in active docs/code | No active hit except historical journal/archive records if intentionally left untouched | `rg` with `frontend/dist` and `backend/target` excluded |
| Model config creation command | Uses `/api/admin/model-configs`, `api_key` placeholder, `chat_model`, optional embedding fields, safe formal JSON body | README review |
| App KB binding command | Uses `/api/admin/apps/{appId}/knowledge-base` and payload `knowledge_base_id` | README review |
| API key create command | Uses `/api/admin/apps/{appId}/api-keys`; warns `key` is one-time plaintext | README review |
| API key revoke/disable command | Uses `/api/admin/api-keys/{id}/revoke` or `/disable`; revoked public key returns `401 invalid_api_key` | README review |
| Request log commands | Use `/api/admin/apps/{appId}/request-logs/**`, safe evidence only | README review |
| Formal commands | Use UTF-8 no-BOM temp body plus `--data-binary` for JSON POST/PUT | README review and `rg` |
| Quick checks | Clearly labeled as non-formal if using inline `-d` | README review |
| Secret exposure | No committed `sk-sangui-*` long keys, provider keys, or plaintext examples | secret scan |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | README has a single coherent Admin API runbook covering model config, default model binding, KB binding, API key lifecycle, request-log acceptance, formal command rules, and safe evidence rules. |
| Base | `.trellis/spec/sangui-rag-gateway.md`, backend controllers/tests, and frontend API clients are already correct; only README needs edits. |
| Bad | README keeps stale `/model-config` route, formal commands use PowerShell 5.1 unsafe `curl.exe -d $body`, examples print real keys/chunk content, or docs imply unsupported API behavior. |

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: source of truth for implemented Admin APIs, request-log automation rule, API key safety, and gateway product boundary.
- `.trellis/spec/backend/error-handling.md`: Admin API envelope and error matrix for app/model config/API key/request-log endpoints.
- `.trellis/spec/backend/database-guidelines.md`: tenant ownership and model/app/KB/request-log contracts; relevant for docs that explain binding constraints.
- `.trellis/spec/backend/logging-guidelines.md`: safe request-log fields and forbidden sensitive fields.
- `.trellis/spec/backend/quality-guidelines.md`: validation order, API compatibility, and request-log safety requirements.
- `.trellis/spec/frontend/type-safety.md`: frontend API route/type alignment for `AppVO`, binding DTO/VO, request-log VO.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: required because this task checks route, payload, docs, frontend client, and tests together.
- `.trellis/spec/security/rag-security.md`: secret/evidence boundary for request-log and hit-chunk examples.

## Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`: implemented route is `@PutMapping("/{appId}/default-model-config")`; KB binding route is `@PutMapping("/{appId}/knowledge-base")`; API key create/list are nested under `/api/admin/apps/{appId}/api-keys`.
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`: model config CRUD routes live under `/api/admin/model-configs`.
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`: key disable/revoke routes live under `/api/admin/api-keys/{id}/disable|revoke`.
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`: request-log routes live under `/api/admin/apps/{appId}/request-logs`.
- `frontend/src/api/apps.ts`: typed client already calls `/admin/apps/${appId}/default-model-config` and `/knowledge-base`.
- `frontend/src/types/app.ts`: typed DTO fields are `model_config_id` and `knowledge_base_id`.
- `README.md`: request-log and key rotation sections exist; default model config binding and model config creation runbook coverage are incomplete.

## Files Likely To Modify

- `README.md`: primary expected change. Add or consolidate Admin runbook endpoint table and PowerShell 5.1-safe formal commands.
- `.trellis/spec/sangui-rag-gateway.md`: modify only if implementation research finds a spec inconsistency. Current scan suggests it already has the correct default-model endpoint.
- `.trellis/spec/backend/error-handling.md` or `.trellis/spec/frontend/type-safety.md`: modify only if their endpoint/type contracts disagree with code. Current scan suggests no change needed.

## Files To Inspect But Not Modify Unless Mismatch Found

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/apikey/ApiKeyAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/log/ApiRequestLogAdminController.java`
- `frontend/src/api/apps.ts`
- `frontend/src/api/model-configs.ts`
- `frontend/src/api/api-keys.ts`
- `frontend/src/api/request-logs.ts`
- `frontend/src/types/app.ts`
- `scripts/demo-smoke.ps1`

## Required Tests / Validation

Always run after documentation/spec edits:

```powershell
rg -n "/api/admin/apps/\{?appId\}?/model-config|/admin/apps/\$\{?appId\}?/model-config|/apps/<app-id>/model-config|/model-config" README.md .trellis backend frontend scripts -g "!frontend/dist/**" -g "!backend/target/**"
git diff --check
rg -n "sk-sangui-[A-Za-z0-9_-]{20,}|api_key_encrypted|key_hash|provider_response_body|stack_trace" README.md scripts .trellis/spec .trellis/tasks/06-01-admin-runbook-readme-endpoint-contract-cleanup -g "!frontend/dist/**" -g "!backend/target/**"
```

Run Maven only if implementation or controller tests are changed, or if research reveals a code/test contract mismatch:

```powershell
cd backend
mvn -q "-Dtest=AppAdminControllerTest,ModelConfigAdminControllerTest,ApiKeyAdminControllerTest,ApiRequestLogAdminControllerTest" test
```

Frontend checks are not required if no frontend TS files change. If frontend API clients or types change:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

## Planning Self-Check

- Acceptance criteria are explicit: endpoint contract table, formal/quick command distinction, stale route scan, diff check, secret scan.
- Forbidden modification scope is explicit: no Java/TS business code unless mismatch is found; no DB/RAG/auth/streaming behavior changes.
- Expected modified files are listed: README primary, spec docs only if mismatch found.
- Required validation commands are listed, with Maven gated on implementation/test changes.
- Specific guidelines were read, not only indexes.
- No unresolved requirement needs user confirmation before DeepSeek implementation.
- API / DB / frontend types / DTO fields are aligned by current research: `model_config_id`, `knowledge_base_id`, request-log safe fields, route prefixes.
