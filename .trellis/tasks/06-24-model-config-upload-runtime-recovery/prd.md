# Model Config Decrypt And Document Upload Runtime Recovery

## Goal

Restore the main runtime path:

```text
configure model -> saved model check -> create/upload knowledge -> async document processing -> RAG usable
```

The task must root-cause the current runtime failures:

- Saved model-config check reports `Failed to decrypt upstream API key`.
- Knowledge-base document upload reports an unexplained `500`.

This is a runtime recovery/debug task, not a broad secret-system redesign.

## Scope Classification

Complex Task.

Reason: the issue crosses runtime environment, encrypted historical data, Admin model-config APIs, document upload/storage, durable processing tasks, async embedding, frontend error display, and secret/error boundaries.

## Current Evidence From Journal

Session 83 recorded CI/runtime validation as complete and separated this issue as the next debug task. Runtime smoke passed for image build, health, runtime user, and upload-dir writability, but user-reported runtime failures remained:

- Saved model-config check: `Failed to decrypt upstream API key`.
- KB document upload: `500`.
- Preliminary candidate: existing `api_key_encrypted` rows no longer match current `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` after JWT/AES secret split/runtime config changes.

## Product Boundary

Keep Sangui-RAG-Gateway a lightweight OpenAI-compatible RAG gateway. The fix should restore the existing model config and ingestion workflows. Do not add provider fallback, secret fallback chains, a new migration framework, a new upload path, or a broader admin/debug console.

## Requirements

1. Reproduce and collect evidence before changing behavior:
   - Read how the runtime is started: local JVM, Docker Compose, or frontend proxy.
   - Capture backend logs around saved model check and KB upload.
   - Confirm effective runtime values are present for `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`, `RAG_ADMIN_AUTH_JWT_SECRET`, storage type/path, datasource, Redis, and document-processing worker settings.
   - Inspect current DB model-config rows only for safe metadata: `id`, `user_id`, `capability`, `status`, `provider_name`, model fields, `api_key_masked`, whether `api_key_encrypted` is null/blank, and ciphertext prefix/format. Do not print ciphertext or plaintext keys.
   - Reproduce `POST /api/admin/model-configs/{id}/check` with `{}`.
   - Reproduce `POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents` with a small `.md` or `.txt` file.

2. Freeze the secret recovery boundary:
   - Determine which historical key generated existing `api_key_encrypted` values.
   - If the old key is known and current runtime should preserve existing rows, set/copy the old AES value into `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` and document the operator recovery step.
   - If code support is required, implement only an explicit, operator-triggered one-time migration or clearer admin error response.
   - Do not add runtime silent fallback from `RAG_GATEWAY_ENCRYPTION_SECRET_KEY` to `RAG_GATEWAY_SECRET_KEY`.
   - Do not create a second permanent source of truth for upstream key encryption.
   - Do not log, return, or persist plaintext upstream keys.

3. Fix saved model-config check error clarity:
   - Trace frontend `checkSavedModelConfig(record.id, {})` through `ModelConfigAdminController.checkSaved()` to `ModelConfigCheckService.checkSavedConfig()`.
   - Decryption failure must not collapse into a vague generic `400`.
   - Admin API response must remain `ApiResponse` shape and secret-safe.
   - Preferred response boundary when saved key is undecryptable:
     - HTTP: `400` unless a more specific existing admin status is already used locally.
     - code: `MODEL_CONFIG_NOT_READY`.
     - message: actionable and secret-safe, e.g. tell the operator to restore the original AES encryption secret or re-enter the upstream key.
   - Frontend may display the backend `ApiError.message`; only change frontend if the current UI hides or over-generalizes that message.

4. Fix document upload/runtime boundary:
   - Confirm whether the `500` happens inside `uploadAndEnqueue()` before response, during `FileStorageService.save(...)`, during the short DB/task transaction, during `taskService.findByDocumentId(...)`, or after response in async worker processing.
   - Valid upload response must remain:
     - HTTP `200`
     - `ApiResponse<DocumentVO>`
     - `DocumentVO.status = UPLOADED`
     - `processing_task_status = PENDING`
     - no `storage_path`
   - Upload must not parse/chunk/embed before returning.
   - If embedding config key decryption fails later in the worker, persist bounded `document.error_message` and `rag_document_processing_task.last_error_message`; update document/task/KB state according to retry policy. Do not make the upload API report an unexplained immediate `500` for later embedding failure.
   - Storage save and DB/task transaction failures remain visible failures; no mock success or silent fallback.

5. Preserve security and observability:
   - Never log upstream plaintext keys, `api_key_encrypted`, Authorization headers, admin JWTs, uploaded file content, chunk content, storage absolute paths, provider bodies, stack traces in responses, or env var values.
   - Logs may include safe IDs, status, error code, class name, document ID, KB ID, model config ID, and bounded messages.

## API / Command / Payload Fields

### Saved Model Check

```http
POST /api/admin/model-configs/{id}/check
Authorization: Bearer <admin-jwt>
Content-Type: application/json

{}
```

Optional request fields from `ModelConfigCheckRequest`:

```json
{
  "capability": "CHAT | EMBEDDING",
  "provider_name": "optional override",
  "base_url": "optional override",
  "api_key": "optional plaintext override, request-only",
  "chat_model": "optional override for CHAT",
  "embedding_model": "optional override for EMBEDDING",
  "embedding_dimension": 1024
}
```

Response remains:

```json
{
  "code": "OK | INVALID_REQUEST | MODEL_CONFIG_NOT_READY | FORBIDDEN | NOT_FOUND | UNAUTHORIZED",
  "message": "...",
  "data": {
    "overall_status": "SUCCESS | FAILED | PARTIAL",
    "capability": "CHAT | EMBEDDING",
    "base_url_checked": true,
    "chat": null,
    "embedding": null
  }
}
```

`api_key`, `api_key_encrypted`, raw provider bodies, stack traces, and env values must never appear.

### Document Upload

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <admin-jwt>
Content-Type: multipart/form-data

file=<.txt|.md|.markdown>
```

Successful response:

```json
{
  "code": "OK",
  "message": "success",
  "data": {
    "id": 10,
    "user_id": 100,
    "knowledge_base_id": 1,
    "original_filename": "test.md",
    "content_type": "text/markdown",
    "file_size": 123,
    "status": "UPLOADED",
    "chunk_count": 0,
    "error_message": null,
    "processing_task_id": 20,
    "processing_task_status": "PENDING"
  }
}
```

Forbidden response fields:

```text
storage_path, api_key, api_key_encrypted, upstream_api_key, key_hash,
authorization, provider_response_body, stack_trace, content, chunk_content,
embedding, absolute local path, environment
```

### Runtime Evidence Commands

Use safe metadata-only commands. Do not print secrets or ciphertext.

Suggested evidence collection:

```powershell
git status --short
docker compose --env-file .env -f deploy/docker-compose.yml ps
docker compose --env-file .env -f deploy/docker-compose.yml logs --tail=300 backend
```

DB inspection should select safe metadata only, for example:

```sql
SELECT id, user_id, capability, status, provider_name, chat_model,
       embedding_model, embedding_dimension, api_key_masked,
       api_key_encrypted IS NULL AS encrypted_is_null,
       length(api_key_encrypted) AS encrypted_length,
       split_part(api_key_encrypted, ':', 1) AS encrypted_version
FROM rag_model_config
ORDER BY id;
```

Do not select full `api_key_encrypted` in shared logs or handoff output.

## Validation / Error Matrix

| Scenario | Expected boundary | Assertion point |
|---|---|---|
| Saved config owned by caller, key decrypts, upstream chat/embedding probe succeeds | `200 OK`, `code=OK`, result `SUCCESS` | `ModelConfigCheckServiceTest`, `ModelConfigAdminControllerTest` |
| Saved config owned by caller, encrypted key cannot decrypt with current AES secret | Admin envelope with specific code/message, no plaintext/ciphertext; no upstream call | check service/controller tests |
| Saved config owned by caller, no stored key and no request override | `400`, `MODEL_CONFIG_NOT_READY` or existing explicit invalid/not-ready code; actionable message | check service/controller tests |
| Saved config belongs to another user | `403 FORBIDDEN`, generic access denied; no decrypt attempt | controller test |
| Saved config missing | `404 NOT_FOUND`; no decrypt attempt | controller test |
| Unsaved check with request `api_key` | Uses request key only; no DB decrypt needed | check service test |
| Valid upload | `200 OK`, `DocumentVO.status=UPLOADED`, task `PENDING`, no parse/embed before response | document service/controller tests |
| Unsupported/empty/oversized upload | `400 INVALID_REQUEST`; no storage interaction | service/controller tests |
| Storage save fails | visible upload failure; no cleanup delete when no key exists | service/controller tests |
| Document insert/task creation/KB status update fails after storage save | transaction rolls back, storage delete called exactly once, original failure propagated | `DocumentServiceTest` |
| Async worker cannot decrypt embedding config key | upload already returned; worker marks doc/task retryable or failed with bounded safe message; no immediate upload 500 | worker/service tests |
| Frontend receives specific backend ApiError for saved check/upload | Displays actionable backend message without rendering forbidden fields | page tests only if frontend changes |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | Runtime env uses the AES secret that matches existing encrypted model configs. Saved check succeeds or returns upstream-specific `FAILED` result without secret leakage. Valid upload returns `UPLOADED/PENDING`; async processing later reaches `READY` when embedding config works. |
| Base | Existing DB rows were encrypted with an old key. Operator can restore/copy that old value into `RAG_GATEWAY_ENCRYPTION_SECRET_KEY`, or explicitly re-enter provider keys. Saved check reports a specific actionable not-ready error; upload still returns `UPLOADED/PENDING`, and worker records embedding config failure safely. |
| Bad | Runtime silently falls back to `RAG_GATEWAY_SECRET_KEY`, tries multiple secrets without operator action, logs ciphertext/plaintext, returns generic 500/400 without actionable boundary, marks failed embedding as READY, or creates an alternate upload implementation. |

## Files Likely To Modify

Expected backend files:

- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigCheckService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java` only if decrypt helper boundary needs a typed exception or specific not-ready classification.
- `backend/src/main/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptor.java` only if adding typed safe exception details without changing crypto format or adding fallback.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingWorker.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentProcessingTaskService.java`

Expected tests:

- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigCheckServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/common/security/UpstreamApiKeyEncryptorTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentProcessingTaskServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentProcessingWorkerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/storage/ObjectFileStorageServiceTest.java`

Frontend files only if backend-specific messages are still hidden or typed contract changes:

- `frontend/src/api/http.ts`
- `frontend/src/pages/model-configs/ModelConfigPage.tsx`
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
- `frontend/src/__tests__/pages/ModelConfigPage.test.tsx`
- `frontend/src/__tests__/pages/KnowledgeBasePage.test.tsx`

Docs/spec files only if implementation changes runtime/operations contract:

- `README.md`
- `.env.example`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/rag/document-ingestion.md`
- `.trellis/spec/security/rag-security.md`

## Explicit Non-Goals / Forbidden Changes

- No business-code changes by Codex in the planning session.
- No runtime dual-secret silent fallback.
- No permanent second source of truth for encryption.
- No schema migration unless the implementation proves it is required.
- No plaintext/ciphertext secret logging.
- No API response exposing `api_key_encrypted`, plaintext upstream keys, storage paths, provider bodies, stack traces, or env values.
- No synchronous parsing/embedding inside upload response path.
- No new document upload implementation.
- No provider routing/fallback, queue replacement, or platform expansion.
- No frontend rewrite unless needed to preserve the backend error message.

## Required Tests

Backend targeted tests, 60-second timeout per command when feasible:

```powershell
cd backend
mvn -q "-Dtest=ModelConfigCheckServiceTest,ModelConfigAdminControllerTest" test
mvn -q "-Dtest=UpstreamApiKeyEncryptorTest,ModelConfigServiceTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=DocumentProcessingTaskServiceTest,DocumentProcessingWorkerTest" test
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest,DocumentConfigTest" test
mvn -q "-Dtest=OpenAiCompatibleEmbeddingClientTest" test
mvn -q -DskipTests compile
```

If frontend files change:

```powershell
cd frontend
cmd /c npx vitest run src/__tests__/pages/ModelConfigPage.test.tsx src/__tests__/pages/KnowledgeBasePage.test.tsx
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

Runtime/manual smoke after implementation:

```powershell
# Saved config check through frontend or curl should show success or actionable config-not-ready message.
# Upload a small .md/.txt file and verify response is OK with UPLOADED/PENDING.
# Poll/list documents and verify async worker outcome is READY or a bounded FAILED/RETRYABLE error.
```

## Acceptance Criteria

- [ ] Runtime evidence identifies whether failures are env/key mismatch, code error-boundary issue, storage/DB/task failure, or async worker failure.
- [ ] Saved model check no longer reports an unexplained generic error for undecryptable saved keys.
- [ ] Existing encrypted model-config rows are recoverable through explicit operator action or an explicit one-time migration path; no silent fallback is added.
- [ ] Valid document upload returns `UPLOADED/PENDING` and does not synchronously run embedding before response.
- [ ] Async embedding key decrypt failure is persisted as safe task/document failure state, not an unexplained immediate upload `500`.
- [ ] Admin/frontend responses remain secret-safe and do not expose forbidden fields.
- [ ] Targeted backend tests pass.
- [ ] Frontend checks pass if frontend files are changed.
