# DeepSeek Handoff: Knowledge Base and Document Upload Baseline

## Task

- Task path: `.trellis/tasks/05-28-knowledge-document-upload-baseline`
- PRD: `.trellis/tasks/05-28-knowledge-document-upload-baseline/prd.md`
- Research: `.trellis/tasks/05-28-knowledge-document-upload-baseline/research.md`
- Current task has been activated with `task.py start`.

## Current Project State

- Existing gateway baseline is already implemented:
  - App/API key admin APIs.
  - Model config admin APIs with encrypted upstream keys.
  - Authenticated `GET /v1/models`.
  - Authenticated non-streaming and streaming `POST /v1/chat/completions`.
  - Safe chat request logging in `rag_request_log`.
- Previous streaming baseline was completed and archived. Existing working tree contains unrelated Trellis archive/workspace changes; do not revert them.
- There is currently no document upload, knowledge base, parser, chunker, storage abstraction, embedding, or retrieval implementation.

## Classification

Complex Task.

Reason: this change touches API contracts, DB migrations, local storage, ingestion pipeline, status transitions, tenant isolation, logging safety, and specs/tests.

## Must-Read Context

Injected into implement/check context:

- `.trellis/tasks/05-28-knowledge-document-upload-baseline/prd.md`
- `.trellis/tasks/05-28-knowledge-document-upload-baseline/research.md`
- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/directory-structure.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`

Existing code patterns to follow:

- `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
- `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
- `backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java`
- `backend/src/main/resources/db/migration/V4__create_request_log_table.sql`
- `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java`

## Expected Implementation Scope

Implement only the backend ingestion baseline:

- Knowledge base creation/list/detail admin APIs.
- Document upload/list/detail admin APIs.
- Local file storage abstraction and local implementation.
- TXT/Markdown parser abstraction and baseline parsers.
- Text cleaning and deterministic character-based chunking.
- DB migration for `rag_knowledge_base`, `rag_document`, `rag_document_chunk`.
- Service/controller/parser/chunker/storage tests.
- Spec updates documenting the implemented contract.

## Expected New/Changed Files

Database/config:

- `backend/src/main/resources/db/migration/V5__create_knowledge_document_tables.sql`
- `backend/src/main/resources/application.yml`
- `.env.example` if new environment keys are introduced.

Knowledge module:

- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseEntity.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseStatus.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseMapper.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/dto/CreateKnowledgeBaseDTO.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/vo/KnowledgeBaseVO.java`
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`

Document module:

- `backend/src/main/java/com/sangui/raggateway/document/DocumentEntity.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEntity.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentMapper.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`
- `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java`

Parser/chunker/storage:

- `backend/src/main/java/com/sangui/raggateway/document/parser/DocumentParser.java`
- `backend/src/main/java/com/sangui/raggateway/document/parser/ParsedDocument.java`
- `backend/src/main/java/com/sangui/raggateway/document/parser/PlainTextDocumentParser.java`
- `backend/src/main/java/com/sangui/raggateway/document/parser/MarkdownDocumentParser.java`
- `backend/src/main/java/com/sangui/raggateway/document/chunk/TextChunker.java`
- `backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java`
- `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java`
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentProperties.java` if using typed config.

Tests:

- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/parser/PlainTextDocumentParserTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/parser/MarkdownDocumentParserTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/chunk/TextChunkerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`

Spec updates:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`

## Do Not Cross These Boundaries

- Do not implement embeddings.
- Do not add pgvector columns for chunk embeddings.
- Do not implement retrieval.
- Do not modify `/v1/chat/completions` behavior.
- Do not implement prompt augmentation.
- Do not add app-to-knowledge-base binding unless a compile requirement from this task makes it unavoidable.
- Do not add frontend UI.
- Do not add PDF/DOCX parsing or dependencies.
- Do not add MinIO dependencies.
- Do not expose `storage_path`, raw local absolute paths, raw uploaded content, complete chunk lists, stack traces, app API keys, or upstream keys in responses/logs.
- Do not refactor existing app/model/api-key/gateway modules beyond minimal shared pattern reuse.
- Do not revert the existing uncommitted Trellis archive/workspace changes.

## Required API/DB Decisions From PRD

- Admin APIs use `ApiResponse<T>` and temporary `X-Admin-User-Id`.
- KB status values: `EMPTY`, `PROCESSING`, `READY`, `FAILED`.
- Document status values for this baseline: `UPLOADED`, `PARSING`, `PARSED`, `FAILED`.
- `rag_document_chunk` must include `user_id`, `knowledge_base_id`, and `document_id`.
- No embedding vector column in this task.
- Upload processing is synchronous for deterministic baseline tests.
- `storage_path` is DB-internal and not present in `DocumentVO`.
- `.txt`, `.md`, `.markdown` only.

## Mandatory Tests

Run targeted tests:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
```

Run regression tests:

```bash
cd backend
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,ModelConfigAdminControllerTest,ModelConfigServiceTest,AppServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```

## Planning Self-Check

- Acceptance criteria defined: yes, in `prd.md`.
- Forbidden/out-of-scope areas defined: yes, in `prd.md` and this handoff.
- Expected modified files listed: yes, in `research.md` and this handoff.
- Required tests listed: yes, in `prd.md`, `research.md`, and this handoff.
- Concrete guidelines read: yes, backend directory/database/error/logging/quality, frontend type-safety/state/quality, and cross-layer/code-reuse guides.
- Need user confirmation before coding: no known blocker. Assumptions are explicit in PRD.
- API/DB/frontend type/DTO alignment: yes, API payloads, DTO/VO fields, status enums, DB schema, and future frontend type unions are specified.
