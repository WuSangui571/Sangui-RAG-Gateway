# Admin Console Configuration Workflow

## Task Classification

Complex Task.

This task spans multiple admin APIs, frontend routing/state/types, secret one-time display, file upload, document status polling, app binding, gateway smoke calls, and request-log verification. It is not a local hotfix. The implementation should be frontend-first and should reuse existing backend APIs unless a real contract gap is found.

## Goal

Build the admin-console configuration workflow that replaces the current manual PowerShell setup chain for RAG validation:

1. Create and inspect chat/embedding model configs.
2. Create a knowledge base and upload English/Chinese documents.
3. Create an app and bind its default chat model config and READY knowledge base.
4. Create an app API key and show the plaintext key only once.
5. Send a minimal `/v1/chat/completions` smoke request with the generated key, then verify the request log in the existing request-log UI.

The first screen should be the usable admin console, not a landing page. Because real admin login is not implemented yet, this task may keep a temporary Admin User ID input, but it should be centralized instead of repeated per page.

## Scope

### In Scope

- Frontend admin shell/navigation for:
  - Model Config
  - Knowledge Base
  - Apps
  - API Keys
  - Request Logs
  - End-to-end Smoke
- Typed frontend API clients and TypeScript contracts for apps, model configs, knowledge bases, documents, API keys, OpenAI smoke response/error, and request logs.
- Model Config page:
  - Create model config.
  - List model configs.
  - Disable model config.
  - Support chat-only config and embedding config.
  - Show only `api_key_masked`; never display plaintext upstream key after save.
- Knowledge Base page:
  - Create KB with `embedding_model` and `embedding_dimension`.
  - Upload `.txt`, `.md`, or `.markdown` files with English or Chinese content.
  - List documents and show `READY` / `FAILED` / intermediate statuses.
  - Poll visible documents only while status is non-terminal.
- App page:
  - Create app.
  - List apps.
  - Bind default model config.
  - Bind default knowledge base only after KB is READY.
- API Key page:
  - Create key for a selected app.
  - Show plaintext `key` exactly once in a modal/drawer/success state.
  - Clear plaintext key when the modal/drawer closes or app/page selection changes.
  - List existing keys by prefix/status without plaintext.
- Smoke workflow:
  - Use selected app key to call `POST /v1/chat/completions`.
  - Show success/error in OpenAI-compatible terms.
  - Navigate or switch to Request Logs for the same app/admin user and refresh.
- Request log page integration:
  - Reuse existing request-log table/detail/hit-chunks behavior.
  - Avoid forcing App ID/Admin User ID re-entry when the workflow already has selected context.

### Out of Scope / Forbidden

- Do not implement real admin login/auth in this task.
- Do not add new backend tables or migrations unless a verified API gap makes it unavoidable.
- Do not change RAG retrieval, prompt augmentation, embedding pipeline, upstream forwarding, or gateway auth behavior.
- Do not implement chat playground features beyond the minimal smoke request needed to generate a request log.
- Do not persist plaintext app API keys or upstream API keys in localStorage/sessionStorage/global stores.
- Do not render full prompts, full document content, provider raw bodies, API key hashes, encrypted upstream keys, or embeddings.
- Do not add low-code workflow/agent/plugin/product-platform UI.
- Do not auto-commit or run Trellis record-session from the implementation handoff.

## Existing Backend API Contracts To Reuse

All admin endpoints require:

```http
X-Admin-User-Id: <positive long>
```

All admin endpoints return:

```ts
interface ApiResponse<T> {
  code: string
  message: string
  data: T
}
```

### Model Config

```http
POST /api/admin/model-configs
GET /api/admin/model-configs?status=ENABLED|DISABLED
GET /api/admin/model-configs/{id}
PUT /api/admin/model-configs/{id}
POST /api/admin/model-configs/{id}/disable
```

Create payload:

```json
{
  "name": "chat config",
  "provider_name": "openai-compatible",
  "base_url": "https://api.example.com",
  "api_key": "upstream-secret",
  "chat_model": "deepseek-v4-pro",
  "embedding_model": null,
  "embedding_dimension": null
}
```

Embedding config payload uses the same endpoint with `embedding_model` and positive `embedding_dimension`. Current backend still requires `chat_model`; for embedding-only operational use, the UI should clearly accept a chat model value rather than inventing a second backend contract.

Response fields:

```ts
interface ModelConfigVO {
  id: number
  user_id: number
  name: string
  provider_name: string
  base_url: string
  api_key_masked: string | null
  chat_model: string
  embedding_model: string | null
  embedding_dimension: number | null
  status: 'ENABLED' | 'DISABLED'
  created_at: string
  updated_at: string
}
```

### Knowledge Base and Documents

```http
POST /api/admin/knowledge-bases
GET /api/admin/knowledge-bases?status=EMPTY|PROCESSING|READY|FAILED
GET /api/admin/knowledge-bases/{id}
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents?status=UPLOADED|PARSING|PARSED|EMBEDDING|READY|FAILED
GET /api/admin/documents/{documentId}
```

Create KB payload:

```json
{
  "name": "demo kb",
  "embedding_model": "text-embedding-v4",
  "embedding_dimension": 1024
}
```

Upload payload:

```http
Content-Type: multipart/form-data
file=<.txt|.md|.markdown>
```

Document terminal statuses: `READY`, `FAILED`.

### Apps

```http
POST /api/admin/apps
GET /api/admin/apps?status=ENABLED|DISABLED
GET /api/admin/apps/{id}
PUT /api/admin/apps/{appId}/default-model-config
PUT /api/admin/apps/{appId}/knowledge-base
```

Create app payload:

```json
{
  "name": "demo app"
}
```

Bind model config payload:

```json
{
  "model_config_id": 123
}
```

Bind knowledge base payload:

```json
{
  "knowledge_base_id": 456
}
```

### API Keys

```http
POST /api/admin/apps/{appId}/api-keys
GET /api/admin/apps/{appId}/api-keys
POST /api/admin/api-keys/{id}/disable
POST /api/admin/api-keys/{id}/revoke
```

Create key payload:

```json
{
  "name": "smoke key",
  "expires_at": null
}
```

Create key response includes plaintext only on create:

```ts
interface ApiKeyCreateVO extends ApiKeyVO {
  key: string
}
```

Normal key records must not include `key` or `key_hash`.

### Gateway Smoke

```http
POST /v1/chat/completions
Authorization: Bearer sk-sangui-...
Content-Type: application/json
```

Smoke payload:

```json
{
  "model": "ignored-by-gateway",
  "messages": [
    {
      "role": "user",
      "content": "Answer using the uploaded knowledge base."
    }
  ],
  "stream": false
}
```

Success uses OpenAI-compatible response shape without admin envelope. Failures use OpenAI-compatible `error` shape.

## Validation and Error Matrix

| Area | Case | Expected UI behavior | Backend/API expectation |
|---|---|---|---|
| Admin identity | Missing/non-positive Admin User ID | Block submit and show field error | No request sent |
| Model config create | Required fields blank | Block submit and preserve form values | No request sent or `400 INVALID_REQUEST` displayed |
| Model config create | Embedding model set without positive dimension | Block submit | Backend also rejects invalid dimension |
| Model config list | Disabled config present | Show disabled status and exclude from "recommended bind" selection unless user explicitly chooses otherwise | Backend returns `DISABLED` |
| Upstream key | Create succeeds | Clear plaintext input after save, show only `api_key_masked` | Backend never returns plaintext upstream key |
| KB create | Missing embedding model/dimension | Block submit | Backend rejects `INVALID_REQUEST` |
| Document upload | Unsupported extension/content type | Show backend error; no fake READY | Backend returns `400 INVALID_REQUEST` |
| Document processing | Status `EMBEDDING` or `PARSING` | Show intermediate state and poll | Poll stops on `READY` or `FAILED` |
| Document processing | Status `FAILED` | Show bounded `error_message` | Do not expose stack trace or provider body |
| App bind model | No enabled model config | Disable bind action or show actionable error | Backend may return `MODEL_CONFIG_NOT_READY` |
| App bind KB | KB not READY | Disable bind action or show actionable error | Backend returns `KNOWLEDGE_BASE_NOT_READY` |
| API key create | Success | Show plaintext key once; copy action allowed; clear on close | Backend returns `key` only from create endpoint |
| API key list | Existing keys | Show prefix/status only | No `key` or `key_hash` typed/rendered |
| Smoke call | Missing selected app/key | Block run and show prerequisites | No request sent |
| Smoke call | Gateway 200 | Show model/status summary; refresh request logs | `rag_request_log` should contain success row |
| Smoke call | Gateway 409/502/etc. | Show OpenAI error code/message and refresh logs when request reached gateway | No provider body or key displayed |
| Request logs | No rows yet | Empty state with refresh | Existing API returns empty page |
| Request logs | Detail/hit chunks | Reuse safe fields only | No forbidden fields typed/rendered |

## Good / Base / Bad Cases

### Good Cases

- User creates one chat model config and one embedding model config.
- User creates KB, uploads English `.md`, upload returns `READY`, KB becomes `READY`.
- User uploads Chinese `.md` or `.txt`, upload returns `READY` with readable filename/status.
- User creates app, binds default model config and READY KB.
- User creates API key, copies plaintext key once, closes modal, plaintext is no longer present in UI state.
- User runs smoke chat and sees a new request log with `question_summary` and non-empty `hit_chunk_ids` when retrieval hits.

### Base Cases

- Existing configs/apps/KBs/keys load correctly after page refresh.
- User can manually refresh lists.
- Request Log page remains usable if opened directly and no selected workflow context exists.
- `stream=false` smoke only; streaming smoke is out of scope.
- Admin User ID remains a temporary input because real login does not exist.

### Bad Cases

- Wrong Admin User ID cannot read another user's app/KB/config; UI shows 403 access denied without retry loops.
- Unsupported upload file fails visibly and does not show READY.
- Embedding provider/config failure produces `FAILED` document status and bounded error message.
- Binding a non-READY KB is blocked or shows backend `KNOWLEDGE_BASE_NOT_READY`.
- Disabled model config should not be silently used for the main happy path.
- API key plaintext must not remain after closing the one-time secret modal/drawer.
- Smoke gateway errors must not be hidden behind fake success or pass-through fallback.

## Expected Frontend Architecture

Prefer small typed modules following the current React + TypeScript + Vite + Ant Design baseline:

```text
frontend/src/api/http.ts
frontend/src/api/apps.ts
frontend/src/api/model-configs.ts
frontend/src/api/knowledge.ts
frontend/src/api/documents.ts
frontend/src/api/api-keys.ts
frontend/src/api/openai.ts
frontend/src/types/app.ts
frontend/src/types/model-config.ts
frontend/src/types/knowledge.ts
frontend/src/types/document.ts
frontend/src/types/api-key.ts
frontend/src/types/openai.ts
frontend/src/pages/model-configs/ModelConfigPage.tsx
frontend/src/pages/knowledge/KnowledgeBasePage.tsx
frontend/src/pages/apps/AppConfigPage.tsx
frontend/src/pages/api-keys/ApiKeyPage.tsx
frontend/src/pages/smoke/SmokeTestPage.tsx
frontend/src/components/layout/AdminShell.tsx
frontend/src/components/domain/*StatusTag.tsx
frontend/src/components/domain/ApiKeyOneTimeSecret.tsx
```

Hooks are optional, but if repeated loading/mutation logic grows, use domain hooks such as `useModelConfigs`, `useKnowledgeBases`, `useApiKeys`, and `useSmokeTest`. Do not introduce a broad global entity store. Keep Admin User ID and selected App ID as small app-shell state or a narrowly scoped context.

## Required Tests and Assertion Points

### Frontend Checks

```bash
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Required assertions during code review/manual smoke:

- No `any`, `Record<string, any>`, or untyped API response plumbing for new contracts.
- No `console.log` containing secrets, plaintext keys, uploaded content, prompts, or Authorization headers.
- Generated app API key plaintext exists only in one-time display state and is cleared on close.
- Upstream API key input is cleared after successful model config create/update.
- Document polling stops when all visible statuses are `READY` or `FAILED`.
- Request logs still omit forbidden fields.
- Existing request-log page behavior is not regressed.

### Backend Regression Checks

No backend implementation changes are expected. If backend code is untouched, run only targeted existing tests that protect consumed contracts:

```bash
cd backend
mvn -q "-Dtest=ModelConfigAdminControllerTest,KnowledgeBaseAdminControllerTest,DocumentAdminControllerTest,AppAdminControllerTest,ApiKeyAdminControllerTest,ApiRequestLogAdminControllerTest" test
```

If any backend code changes become necessary, also run:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=ModelConfigServiceTest,KnowledgeBaseServiceTest,DocumentServiceTest,AppServiceTest,ApiKeyServiceTest,ApiRequestLogServiceTest" test
mvn test
```

### End-to-End Manual Smoke

Prerequisites: backend, frontend, database, and real upstream chat/embedding configs available.

1. Enter Admin User ID once.
2. Create chat model config with masked upstream key response.
3. Create embedding-capable model config matching planned KB embedding model/dimension.
4. Create KB.
5. Upload English document and verify `READY`.
6. Upload Chinese document and verify `READY`.
7. Create app.
8. Bind app default model config and READY KB.
9. Create app API key and copy plaintext once.
10. Close key modal and verify plaintext is not visible/recoverable in UI.
11. Run non-streaming `/v1/chat/completions` smoke request.
12. Open Request Logs for the same app and verify new row/detail/hit chunks.

## Risk and Boundary Notes

- The workflow is secret-sensitive. Treat app API keys and upstream API keys as separate contracts:
  - App API key plaintext is returned once by backend and must be short-lived in UI memory.
  - Upstream key is entered into a form and backend returns only `api_key_masked`.
- Existing backend uses snake_case JSON fields. Frontend types must match backend response fields rather than silently remapping partial objects.
- Existing `api/http.ts` supports GET only and `/api` prefix only. Implementation will likely need typed POST/PUT/upload helpers plus a separate `/v1` gateway smoke helper.
- Do not introduce silent fallbacks. API errors should remain visible and actionable.
- Existing request log page has local App ID/Admin User ID entry. New app shell should reuse it where possible without breaking direct access.
- Backend controllers are annotated `@Profile("!test")`; existing controller tests are already tailored to this baseline. Do not assume production profile behavior from test-profile missing routes.

## Acceptance Criteria

- [ ] A user can complete model config -> KB/document -> app binding -> API key -> smoke -> request-log verification from the frontend without manual PowerShell API calls.
- [ ] Model config UI supports chat and embedding config fields and never displays plaintext upstream keys after save.
- [ ] KB UI supports create/list/upload/list-documents and shows document/KB status including READY and FAILED.
- [ ] App UI supports create/list and binding default model config plus READY default KB.
- [ ] API key UI creates keys, displays plaintext once, and clears plaintext on close.
- [ ] Smoke UI can call `/v1/chat/completions` with generated app key and show OpenAI-compatible success/error.
- [ ] Request log UI can verify the generated smoke request for the selected app.
- [ ] Typecheck and build pass.
- [ ] Targeted backend contract tests pass, or any inability to run them is explicitly reported.
- [ ] No backend business implementation is changed unless a real API contract gap is found and documented.
