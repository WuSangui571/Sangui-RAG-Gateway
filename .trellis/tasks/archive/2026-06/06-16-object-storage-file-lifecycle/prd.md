# Object Storage And File Lifecycle

## Goal

Add an explicit storage abstraction contract for document files while preserving the existing local storage behavior and adding a production-ready object-storage implementation boundary.

The task must make document file lifecycle behavior observable and testable for upload, parse/embedding failure, document deletion, knowledge-base deletion, duplicate uploads, and storage cleanup failures. It must keep storage internals secret-safe: no API response, VO, log, README example, or frontend type may expose `storage_path`, object credentials, bucket secrets, access keys, secret keys, or internal absolute paths.

## Scope Classification

Complex Task.

This is structural, not a local hotfix, because it changes shared storage contracts, document lifecycle semantics, admin API behavior, deployment configuration, security documentation, and test matrices across backend, docs, env, and Compose.

## Current State Summary

- Existing branch: `feature/object-storage-file-lifecycle`.
- Existing local storage seam:
  - `FileStorageService.save(...)` exists.
  - `LocalFileStorageService` writes to `rag.gateway.storage.local-path`.
  - `DocumentConfig` always creates local storage and currently ignores `rag.gateway.storage.type`.
- Existing document lifecycle:
  - Upload validates file before storage.
  - `DocumentService.uploadAndProcess(...)` stores bytes, creates `rag_document`, parses/chunks, then embeds.
  - Parse or embedding failure marks document `FAILED`; the original stored file remains.
  - No document delete endpoint exists.
  - No knowledge-base delete endpoint exists.
- Existing security contract:
  - `DocumentVO` does not expose `storage_path`.
  - Request-log hit chunk APIs must not expose `storage_path`.
  - Prior filename work established a hard split between display basename (`original_filename`) and internal storage-safe key.

## Requirements

1. Preserve local storage compatibility.
   - Local storage remains the default for dev/test.
   - Existing local storage keys like `knowledge/{knowledgeBaseId}/{uuid}/{safeName}` remain valid.
   - Local storage must keep path traversal protection and must not log absolute filesystem paths.

2. Add object storage as a backend implementation.
   - Storage backend type is deployment-level: `local` or `object`.
   - Object storage must be S3-compatible and support MinIO-style endpoint/path-style configuration.
   - Object storage keys use the same opaque logical key shape as local storage.
   - Implementation may use AWS SDK v2 S3 client or another S3-compatible Java client already justified in `backend/pom.xml`.
   - Do not add MQ, async workers, or distributed lifecycle jobs.

3. Extend storage abstraction.
   - `FileStorageService` must support save and delete.
   - Delete must be idempotent: missing object/file is treated as cleanup-complete, not as a business failure.
   - Cleanup failures must fail visibly at the service/API boundary and be logged only with safe IDs and logical storage keys, never credentials or absolute paths.

4. Add or complete admin deletion lifecycle.
   - Add document deletion API if not present.
   - Add knowledge-base deletion API if not present.
   - Deleting a document deletes, in order, its stored original file, embedding rows, chunk rows, and document row.
   - Deleting a knowledge base deletes stored originals for all documents under that KB, then embeddings, chunks, documents, and the KB row.
   - Ownership checks must happen before any storage cleanup or DB mutation.

5. Preserve document failure semantics unless explicitly changed.
   - Parse/embedding failure should keep the stored original file and mark the document `FAILED`, matching current behavior, so future retry/debug workflows have a durable source.
   - No chunks/vectors should be active for parse failure.
   - Embedding failure may leave parsed chunks but must leave no vectors for the failed document unless vector persistence is fully transactional.
   - Failed documents are cleaned up only by explicit document or KB deletion in this task.

6. Duplicate upload behavior.
   - Duplicate filename or content creates a new document row and a new non-guessable storage key.
   - No overwrite by filename.
   - No deduplication feature in this task.

7. Secret-safe configuration, logging, docs, and API.
   - Configuration binding failures must name the invalid property but never echo secret values.
   - Logs may include `document_id`, `knowledge_base_id`, `user_id`, `storageKey`, size, backend type, and cleanup result.
   - Logs must never include object access key, secret key, authorization headers, signed URLs, internal absolute local paths, full uploaded content, chunk content, or stack traces in API responses.
   - Admin API responses must not expose `storage_path`, object endpoint, bucket, access key, secret key, local absolute path, or any internal path.

8. Deployment/spec synchronization.
   - Update `.env.example`.
   - Update `deploy/docker-compose.yml`.
   - Update `backend/src/main/resources/application.yml`.
   - Update `README.md`.
   - Update `.trellis/spec/sangui-rag-gateway.md` and focused backend/RAG/security specs as needed.

## Non-Goals

- No complex distributed async lifecycle jobs.
- No MQ.
- No background file reconciliation service unless a simple synchronous deletion helper is insufficient.
- No frontend upload UI redesign.
- No document preview/download feature.
- No pre-signed URL API.
- No object-storage browser UI.
- No deduplication by checksum/content.
- No migration tool for moving already-uploaded local files into object storage.

## Configuration Contract

Spring properties:

| Property | Env var | Required when | Notes |
|---|---|---|---|
| `rag.gateway.storage.type` | `FILE_STORAGE_TYPE` | always | Allowed: `local`, `object`. Default `local`. Unknown values fail startup. |
| `rag.gateway.storage.local-path` | `FILE_STORAGE_LOCAL_PATH` | `type=local` | Local root. Must not be logged as an absolute path. |
| `rag.gateway.storage.object.endpoint` | `FILE_STORAGE_OBJECT_ENDPOINT` | `type=object` | S3-compatible endpoint, e.g. MinIO or cloud S3 endpoint. |
| `rag.gateway.storage.object.bucket` | `FILE_STORAGE_OBJECT_BUCKET` | `type=object` | Bucket name. Treat as internal deployment metadata; do not expose in API. |
| `rag.gateway.storage.object.access-key` | `FILE_STORAGE_OBJECT_ACCESS_KEY` | `type=object` | Secret input. Never log or return. |
| `rag.gateway.storage.object.secret-key` | `FILE_STORAGE_OBJECT_SECRET_KEY` | `type=object` | Secret input. Never log or return. |
| `rag.gateway.storage.object.region` | `FILE_STORAGE_OBJECT_REGION` | `type=object` | Required or defaulted deliberately; document final behavior. |
| `rag.gateway.storage.object.path-style-access` | `FILE_STORAGE_OBJECT_PATH_STYLE_ACCESS` | `type=object` | Boolean. Default should support MinIO, likely `true`. |

Validation:

| Scenario | Expected result |
|---|---|
| `FILE_STORAGE_TYPE=local` with valid local path | Context starts; local service selected. |
| `FILE_STORAGE_TYPE=object` with all required object properties | Context starts; object service selected. |
| `FILE_STORAGE_TYPE=object` with blank endpoint/bucket/access key/secret key/region if region is required | Context fails visibly during binding/configuration. Error names the property and does not echo value. |
| Unknown `FILE_STORAGE_TYPE` | Context fails visibly. |
| Invalid path-style boolean | Spring binding fails visibly. |
| `prod` with `type=object` | Production guard accepts without `RAG_PRODUCTION_ALLOW_LOCAL_FILE_STORAGE`. |
| `prod` with `type=local` and no acknowledgement | Existing production guard still fails. |

## API / Command / Payload Contract

### Existing Upload API

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
Authorization: Bearer <admin-jwt>
Content-Type: multipart/form-data

file=<multipart file>
```

Response remains:

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
    "file_size": 1024,
    "status": "READY",
    "chunk_count": 2,
    "error_message": null,
    "created_at": "...",
    "updated_at": "..."
  }
}
```

Forbidden response fields: `storage_path`, `bucket`, `endpoint`, `access_key`, `secret_key`, `local_path`, `object_key`, `absolute_path`, `authorization`, `content`, `chunk_content`, `embedding`.

### New Document Delete API

```http
DELETE /api/admin/documents/{documentId}
Authorization: Bearer <admin-jwt>
```

Recommended response:

```json
{
  "code": "OK",
  "message": "success",
  "data": null
}
```

Behavior:

- Auth derives user identity from `AdminAuthContextHolder`.
- Missing document returns 404.
- Cross-user document returns 403 and must not call storage cleanup.
- Cleanup failure returns 500 `INTERNAL_ERROR` with safe message and leaves DB rows intact where possible.
- Successful cleanup removes vectors, chunks, document row, and updates KB status:
  - `READY` if any READY document remains.
  - `EMPTY` if no documents remain.
  - `FAILED` if only failed documents remain.
  - Preserve existing status conventions if service already centralizes this differently.

### New Knowledge Base Delete API

```http
DELETE /api/admin/knowledge-bases/{id}
Authorization: Bearer <admin-jwt>
```

Recommended response:

```json
{
  "code": "OK",
  "message": "success",
  "data": null
}
```

Behavior:

- Ownership is checked before loading/deleting storage paths.
- Cross-user KB returns 403 and must not call storage cleanup.
- Missing KB returns 404.
- Cleanup failure returns 500 `INTERNAL_ERROR` with safe message and leaves DB rows intact where possible.
- Successful deletion removes all document vectors, chunks, document rows, and the KB row.
- If app default KB references this KB, implementation must either reject deletion with 400/409 and safe error or clear app binding explicitly with tests/spec. Preferred minimal contract: reject deletion while any app references the KB, because no existing cascade contract is present.

## Lifecycle Matrix

| Scenario | Storage behavior | DB behavior | API behavior | Required assertion |
|---|---|---|---|---|
| Upload succeeds through READY | Save once to selected backend using opaque key | `rag_document.status=READY`, chunks/vectors persisted | 200 `OK`, `DocumentVO` without storage fields | Storage `save` called; no forbidden fields in response/log assertions where feasible. |
| Parse fails after save | Keep original file | Document `FAILED`, bounded `error_message`, no vectors; KB status follows existing ready-count rule | 200 `OK` with `status=FAILED` | No fake ready state; no storage delete unless PRD is updated. |
| Embedding fails after parse | Keep original file | Document `FAILED`; no vectors for failed document; KB status follows existing ready-count rule | 200 `OK` with `status=FAILED` | Existing ready KB content not corrupted. |
| Document delete succeeds | Delete stored file/object idempotently | Delete embeddings, chunks, document; update KB status | 200 `OK` | No `storage_path` in response; storage called only after ownership verified. |
| Document delete storage object missing | Treat as cleanup-complete | Delete DB rows | 200 `OK` | Missing object/file is idempotent. |
| Document delete storage cleanup fails | Fail visibly | Do not delete DB rows if storage may still exist | 500 `INTERNAL_ERROR` safe message | No silent fallback; no credentials/path in response/log. |
| KB delete succeeds | Delete every document file/object idempotently | Delete embeddings, chunks, docs, KB | 200 `OK` | All owned document storage keys attempted. |
| KB delete cleanup fails for any document | Fail visibly | Do not partially delete DB rows if storage may still exist | 500 `INTERNAL_ERROR` safe message | Failure identifies safe boundary only, not credential/path. |
| Duplicate upload same filename/content | Save new object/key; no overwrite | New document row with independent ID | 200 `OK` | Two storage paths differ; display filename can match. |
| Unknown storage type | No storage bean | Startup/config fails visibly | n/a | Binding/config test fails safely. |

## Validation / Error Matrix

| Boundary | Scenario | HTTP / startup result | Code | Notes |
|---|---|---:|---|---|
| Upload | Missing admin JWT | 401 | `UNAUTHORIZED` | Existing AdminAuthFilter path; no storage call. |
| Upload | Cross-user KB | 403 | `FORBIDDEN` | No storage call. |
| Upload | Missing KB | 404 | `NOT_FOUND` | No storage call. |
| Upload | Unsupported extension/content type/empty/oversized | 400 | `INVALID_REQUEST` | No storage call. |
| Upload | Storage save throws | 500 | `INTERNAL_ERROR` | Safe message; no document row if save failed before insert. |
| Upload | Parse or embedding fails after save | 200 | `OK` | Document status `FAILED`; bounded error. |
| Document delete | Missing admin JWT | 401 | `UNAUTHORIZED` | No storage call. |
| Document delete | Missing document | 404 | `NOT_FOUND` | No storage call. |
| Document delete | Cross-user document | 403 | `FORBIDDEN` | No storage call. |
| Document delete | Storage delete fails | 500 | `INTERNAL_ERROR` | DB rows not deleted where possible. |
| KB delete | Missing KB | 404 | `NOT_FOUND` | No storage call. |
| KB delete | Cross-user KB | 403 | `FORBIDDEN` | No storage call. |
| KB delete | KB referenced by app | 400 or 409 | `INVALID_REQUEST` or `KNOWLEDGE_BASE_IN_USE` | Preferred: reject explicitly unless clearing binding is implemented and tested. |
| Config | Object storage missing endpoint/bucket/access/secret | Startup failure | n/a | Names property; no secret echo. |
| Config | Local storage in production without acknowledgement | Startup failure | n/a | Existing production guard remains. |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | `FILE_STORAGE_TYPE=object` starts with endpoint/bucket/access/secret/region/path-style configured, upload saves one object with a non-guessable key, document reaches READY, deletion removes object plus DB rows, API/VO/logs never expose storage internals or credentials, and prod guard accepts object storage without local-storage acknowledgement. |
| Base | `FILE_STORAGE_TYPE=local` behavior remains unchanged for dev/test; duplicate upload creates independent documents; parse/embedding failure keeps original file and marks document FAILED with bounded error; delete treats already-missing file/object as successful cleanup. |
| Bad | Object credentials or `storage_path` appear in API responses/logs/docs; unknown storage type silently falls back to local; storage cleanup failure still deletes DB rows or returns success; cross-user delete attempts cleanup; duplicate filename overwrites an existing object; production local storage bypasses the acknowledgement guard. |

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: product boundary, storage abstraction, deployment env, production guard, document upload schema, forbidden fields.
- `.trellis/spec/backend/directory-structure.md`: storage belongs under `document.storage`, configuration under `document.config` or `common.config` as appropriate.
- `.trellis/spec/backend/database-guidelines.md`: document/chunk/embedding tables, tenant boundaries, transaction and external-call boundaries.
- `.trellis/spec/backend/error-handling.md`: admin error envelope, document upload/delete error shapes, safe messages.
- `.trellis/spec/backend/logging-guidelines.md`: safe document ingestion logs; no absolute paths, content, credentials.
- `.trellis/spec/backend/quality-guidelines.md`: required backend tests and regression checks.
- `.trellis/spec/rag/document-ingestion.md`: status transitions, reprocessing/dirty chunk constraints, failure semantics.
- `.trellis/spec/security/rag-security.md`: forbidden response/log fields including `storage_path` and internal filesystem paths.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: env/API/DB/security/document lifecycle cross-layer checklist.
- `.trellis/spec/frontend/type-safety.md`: frontend `DocumentVO` must not type `storage_path`; only update types if delete API clients/types are introduced.
- `.trellis/spec/frontend/quality-guidelines.md`: no frontend storage of secrets or raw document content.

## Code Patterns Found

- Storage seam:
  - `backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java`
  - `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java`
  - `backend/src/main/java/com/sangui/raggateway/document/storage/StoredFile.java`
- Storage bean configuration:
  - `backend/src/main/java/com/sangui/raggateway/document/config/DocumentConfig.java`
  - `backend/src/main/resources/application.yml`
- Document lifecycle:
  - `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
  - `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java`
- Knowledge-base lifecycle:
  - `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`
  - `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`
- Production guard:
  - `backend/src/main/java/com/sangui/raggateway/common/config/ProductionConfigGuard.java`
  - `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`
- Existing tests to extend:
  - `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`
  - `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
  - `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
  - `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`

## Files Likely To Modify

Backend implementation:

- `backend/pom.xml`: add S3-compatible object storage client dependency if needed.
- `backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java`: add delete contract.
- `backend/src/main/java/com/sangui/raggateway/document/storage/StoredFile.java`: keep opaque key contract; no credentials.
- `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java`: implement delete and keep safe logs.
- `backend/src/main/java/com/sangui/raggateway/document/storage/ObjectFileStorageService.java`: new S3-compatible implementation.
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentConfig.java`: select local/object backend based on properties.
- `backend/src/main/java/com/sangui/raggateway/document/config/StorageProperties.java` or equivalent: validated storage properties.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`: document deletion lifecycle and storage cleanup.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`: `DELETE /api/admin/documents/{documentId}`.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`: KB deletion lifecycle and app-reference guard if needed.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`: `DELETE /api/admin/knowledge-bases/{id}`.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentMapper.java`, `DocumentChunkMapper.java`, `DocumentChunkEmbeddingMapper.java`: tenant-scoped delete helpers if MyBatis-Plus wrappers are insufficient/readability suffers.
- `backend/src/main/resources/application.yml`: storage object properties.

Deployment/docs/spec:

- `.env.example`: object storage env vars with placeholder-safe values only.
- `deploy/docker-compose.yml`: pass object storage env vars; optional MinIO service only if deliberately included and documented.
- `README.md`: storage config, secret-safe notes, delete lifecycle behavior.
- `.trellis/spec/sangui-rag-gateway.md`: object storage and lifecycle executable contract.
- `.trellis/spec/backend/logging-guidelines.md`: safe storage lifecycle logs.
- `.trellis/spec/backend/error-handling.md`: delete API error matrix.
- `.trellis/spec/rag/document-ingestion.md`: lifecycle matrix if behavior changes.
- `.trellis/spec/security/rag-security.md`: storage/object credential forbidden fields.
- `frontend/src/types/document.ts` and/or API clients only if delete API client/types are added; no UI redesign.

Tests:

- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`: delete success/missing/traversal/safe logging where feasible.
- `backend/src/test/java/com/sangui/raggateway/document/storage/ObjectFileStorageServiceTest.java`: object key creation, delete, missing object idempotence, no secret leak in errors.
- `backend/src/test/java/com/sangui/raggateway/document/config/StoragePropertiesTest.java` or `DocumentConfigTest`: binding/selection failures.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`: lifecycle matrix.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`: delete API auth/403/404/500/success and no forbidden fields.
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseServiceTest.java`: KB delete lifecycle, referenced-by-app behavior.
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminControllerTest.java`: KB delete API matrix.
- `backend/src/test/java/com/sangui/raggateway/ProductionConfigGuardTest.java`: object storage in prod accepted; local still guarded.
- `backend/src/test/java/com/sangui/raggateway/ProductionContextSmokeTest.java`: storage property binding smoke if aligned with existing smoke scope.

## Risk / Boundary Notes

- Do not silently fall back from object storage to local when object config is invalid.
- Do not introduce a second source of truth for display filename; `original_filename` remains display basename, storage key remains internal.
- Avoid broad `catch` blocks that swallow cleanup errors. Cleanup failures must be visible.
- External storage delete is not transactionally coupled to DB. Preferred deletion order is ownership check -> storage cleanup -> DB deletes. Missing object/file is idempotent. If DB delete fails after storage cleanup, return a visible failure; subsequent retry should still succeed because missing storage is cleanup-complete.
- Current DB schema has no per-document storage backend column. Treat storage backend as deployment-level. Do not add `storage_type` unless implementation chooses to support mixed-backend migration; if added, include migration/spec/frontend impact explicitly.
- If KB deletion conflicts with `rag_app.default_knowledge_base_id`, do not rely on DB FK failure. Check app references first and return a safe explicit error, or deliberately clear bindings with tests.
- Object storage bucket/endpoint are deployment internals. Even if bucket name is not a credential, it should not be returned in API/VO fields.
- No signed URL or download API should be added in this task.

## Required Tests And Assertion Points

Targeted backend tests:

```powershell
cd backend
mvn -q "-Dtest=LocalFileStorageServiceTest,ObjectFileStorageServiceTest" test
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest" test
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest" test
mvn -q "-Dtest=ProductionConfigGuardTest,ProductionContextSmokeTest" test
```

If a dedicated config test is added:

```powershell
cd backend
mvn -q "-Dtest=StoragePropertiesTest,DocumentConfigTest" test
```

Regression checks:

```powershell
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest" test
mvn -q "-Dtest=RetrievalServiceTest,ChatCompletionGatewayServiceTest,OpenAiChatCompletionsControllerTest" test
mvn -q test
```

Docs/deployment checks:

```powershell
git diff --check
docker compose --env-file .env.example -f deploy/docker-compose.yml config
```

Frontend only if API client/types are touched:

```powershell
cd frontend
cmd /c npm run typecheck
cmd /c npm run build
```

Assertions:

- Upload and delete responses must not contain `storage_path`.
- Search changed API/VO/docs/tests for forbidden field exposure:
  - `storage_path`
  - `FILE_STORAGE_OBJECT_SECRET_KEY`
  - `secret-key`
  - `access-key`
  - `absolute path`
- Secret values must not appear in exception messages.
- Storage cleanup failure returns visible safe error and does not report success.
- Cross-user delete never invokes storage deletion.
- Duplicate upload generates two distinct storage keys.

## Planning Self-Check

- Acceptance criteria are explicit in Requirements, Lifecycle Matrix, Good/Base/Bad, and Required Tests.
- Forbidden modification range is explicit in Non-Goals.
- Expected modified files are listed.
- Required tests and assertion points are listed.
- Concrete guideline files were read, not only spec indexes.
- No open requirement blocks implementation. Main implementation choice left to DeepSeek: whether to add a DB `storage_type` column. Default plan does not require it.
- API, error, frontend type, DTO, env, Compose, README, and spec synchronization points are identified.

