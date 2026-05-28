# Research: Knowledge Base and Document Upload Baseline

## Current Project Status From Journal

- Latest completed task: Chat Completions Streaming Baseline.
- Current gateway supports authenticated `GET /v1/models` and `POST /v1/chat/completions`.
- Non-streaming and streaming chat completions both forward to an app default OpenAI-compatible upstream model config.
- Request log persistence exists for chat completions through `rag_request_log`.
- Safe observability/logging rules are documented and tested.
- Full test suite previously passed with 243 tests after streaming baseline.
- No active Trellis task existed before this task was created.
- Working tree already had unrelated Trellis archive/workspace changes from the prior task; do not revert them.

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`: Product source of truth. Defines knowledge base, document, document chunk, local/MinIO storage abstraction, MVP txt/md/pdf/docx direction, and RAG ingestion flow.
- `.trellis/spec/backend/directory-structure.md`: Requires `knowledge` for KB management and `document` for upload/storage/parser/cleaning/chunking. Controllers should stay thin; services own business logic and transactions.
- `.trellis/spec/backend/database-guidelines.md`: Requires tenant-safe business tables, explicit status fields, migrations, indexes, fixed embedding model/dimension per KB, and document/chunk transaction boundaries.
- `.trellis/spec/backend/error-handling.md`: Admin APIs use `ApiResponse`; gateway APIs use OpenAI-compatible errors. Admin missing/cross-user resources should use 404/403 distinction where safe.
- `.trellis/spec/backend/logging-guidelines.md`: Document ingestion logs may include IDs, filename, parser, chunk count, status transitions, and failure reason, but never parsed text or raw file contents.
- `.trellis/spec/backend/quality-guidelines.md`: Requires parser/chunker tests, tenant isolation tests, explicit document status transitions, and no prompt/document content leakage.
- `.trellis/spec/frontend/type-safety.md`: Future admin types need explicit status unions for `KnowledgeBaseStatus` and `DocumentStatus`; backend response shapes should be stable and snake_case.
- `.trellis/spec/frontend/state-management.md`: Future UI should poll document statuses only while non-terminal and keep upload progress local/page scoped.
- `.trellis/spec/frontend/quality-guidelines.md`: Future UI must show document processing state clearly and avoid loading full document content into lists.
- `.trellis/spec/guides/cross-layer-thinking-guide.md`: Required because this task touches API, DB, storage, ingestion, tenant boundaries, and frontend contracts.
- `.trellis/spec/guides/code-reuse-thinking-guide.md`: Search-first guidance before adding new helpers or repeated validation logic.

## Code Patterns Found

- Admin controllers:
  - `backend/src/main/java/com/sangui/raggateway/app/AppAdminController.java`
  - `backend/src/main/java/com/sangui/raggateway/model/ModelConfigAdminController.java`
  - Pattern: `@RestController`, `@RequestMapping("/api/admin/...")`, `@Profile("!test")`, `ApiResponse.success(...)`, explicit `X-Admin-User-Id` positive validation.
  - Pattern: controller distinguishes missing resource vs cross-user resource by querying `findByIdAndUserId` and then `findById`.
- Admin error handling:
  - `backend/src/main/java/com/sangui/raggateway/common/exception/BusinessException.java`
  - `backend/src/main/java/com/sangui/raggateway/common/exception/GlobalExceptionHandler.java`
  - Pattern: admin validation throws `BusinessException("INVALID_REQUEST", ...)`; cross-user uses `HttpStatus.FORBIDDEN`; missing uses `HttpStatus.NOT_FOUND`.
- Service and mapper persistence:
  - `backend/src/main/java/com/sangui/raggateway/app/AppService.java`
  - `backend/src/main/java/com/sangui/raggateway/model/ModelConfigService.java`
  - `backend/src/main/java/com/sangui/raggateway/app/AppMapper.java`
  - Pattern: services use `@Transactional` on mutations, `LambdaQueryWrapper` for tenant-scoped lookups, MyBatis-Plus `BaseMapper`.
- Entity/VO/DTO mapping:
  - `backend/src/main/java/com/sangui/raggateway/app/AppEntity.java`
  - `backend/src/main/java/com/sangui/raggateway/model/ModelConfigEntity.java`
  - `backend/src/main/java/com/sangui/raggateway/model/vo/ModelConfigVO.java`
  - Pattern: entities use `@TableName` and `@TableId(type = IdType.AUTO)`; VOs expose snake_case through `@JsonProperty`; VOs use static `from(entity)`.
- Migration style:
  - `backend/src/main/resources/db/migration/V2__create_app_api_key_tables.sql`
  - `backend/src/main/resources/db/migration/V3__create_model_config_and_app_default.sql`
  - `backend/src/main/resources/db/migration/V4__create_request_log_table.sql`
  - Pattern: Flyway versioned SQL, `CREATE TABLE IF NOT EXISTS`, explicit indexes, `BIGSERIAL`, `TIMESTAMP DEFAULT CURRENT_TIMESTAMP`.
- Controller tests:
  - `backend/src/test/java/com/sangui/raggateway/app/AppAdminControllerTest.java`
  - `backend/src/test/java/com/sangui/raggateway/model/ModelConfigAdminControllerTest.java`
  - Pattern: standalone `MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler())`; mocks service layer; asserts JSON envelope, status codes, and absence of secret fields.
- Service tests:
  - `backend/src/test/java/com/sangui/raggateway/model/ModelConfigServiceTest.java`
  - `backend/src/test/java/com/sangui/raggateway/log/ApiRequestLogServiceTest.java`
  - Pattern: Mockito `ArgumentCaptor` verifies persisted entity fields; tests validation, tenant-scope query paths, and sensitive data absence.
- No existing document upload implementation:
  - Search for `MultipartFile`, `multipart`, `@RequestPart`, and upload patterns in backend code found no implementation to reuse.

## Files Likely To Modify

Backend implementation files:

- `backend/src/main/resources/db/migration/V5__create_knowledge_document_tables.sql`: new KB/document/chunk schema and indexes.
- `backend/src/main/resources/application.yml`: add local storage and document chunk config keys if implemented through properties.
- `.env.example`: add local storage path and chunk config env variables if config keys are introduced.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseEntity.java`: new entity.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseStatus.java`: new status enum.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseMapper.java`: new MyBatis mapper.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseService.java`: create/list/detail/status update business logic and tenant-scoped lookups.
- `backend/src/main/java/com/sangui/raggateway/knowledge/dto/CreateKnowledgeBaseDTO.java`: admin create payload.
- `backend/src/main/java/com/sangui/raggateway/knowledge/vo/KnowledgeBaseVO.java`: admin response payload.
- `backend/src/main/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminController.java`: admin KB endpoints.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentEntity.java`: new document metadata entity.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentStatus.java`: baseline status enum.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkEntity.java`: new chunk entity.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentMapper.java`: new document mapper.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentChunkMapper.java`: new chunk mapper.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`: upload/process/list/detail logic.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java`: multipart upload/list/detail endpoints.
- `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java`: safe document response without `storage_path`.
- `backend/src/main/java/com/sangui/raggateway/document/parser/DocumentParser.java`: parser abstraction.
- `backend/src/main/java/com/sangui/raggateway/document/parser/ParsedDocument.java`: parser result.
- `backend/src/main/java/com/sangui/raggateway/document/parser/PlainTextDocumentParser.java`: `.txt` parser.
- `backend/src/main/java/com/sangui/raggateway/document/parser/MarkdownDocumentParser.java`: `.md` parser.
- `backend/src/main/java/com/sangui/raggateway/document/chunk/TextChunker.java`: deterministic chunking.
- `backend/src/main/java/com/sangui/raggateway/document/storage/FileStorageService.java`: storage abstraction.
- `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java`: local storage implementation.
- `backend/src/main/java/com/sangui/raggateway/document/config/DocumentProperties.java`: chunk/file-size/storage properties, if grouped under document module.

Backend tests:

- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/knowledge/KnowledgeBaseAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/parser/PlainTextDocumentParserTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/parser/MarkdownDocumentParserTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/chunk/TextChunkerTest.java`
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`

Spec files after implementation:

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/backend/database-guidelines.md`
- `.trellis/spec/backend/error-handling.md`
- `.trellis/spec/backend/logging-guidelines.md`
- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/frontend/type-safety.md`

## Risk / Boundary Notes

- Tenant isolation is the main correctness boundary. Every admin query must include `user_id` or explicitly verify ownership before mutation/listing.
- `rag_document_chunk` should carry `user_id` now, even before retrieval, so future vector retrieval can enforce tenant boundaries in SQL.
- Do not expose `storage_path` in VOs; local absolute paths are implementation details.
- Do not log parsed document text, chunk content, raw multipart payloads, or stack traces in admin responses.
- Do not change `/v1/chat/completions`; this is ingestion only.
- Avoid starting async processing in this baseline. Synchronous processing keeps tests deterministic and scope bounded.
- If a local file is saved and DB later fails, there may be orphaned local files. Either document best-effort cleanup or implement scoped cleanup inside storage/service tests.
- `@Profile("!test")` on controllers/services means standalone unit tests can instantiate controllers/services directly; full Spring context under test profile may not register these beans.
- The current backend has no multipart upload tests. Use Spring `MockMultipartFile` with standalone MockMvc for controller tests.
- The project has no frontend implementation yet; only frontend type-safety specs need update if backend contracts are finalized.

## Required Tests

Targeted:

```bash
cd backend
mvn -q -DskipTests compile
mvn -q "-Dtest=KnowledgeBaseServiceTest,KnowledgeBaseAdminControllerTest,DocumentServiceTest,DocumentAdminControllerTest,PlainTextDocumentParserTest,MarkdownDocumentParserTest,TextChunkerTest,LocalFileStorageServiceTest" test
```

Regression:

```bash
cd backend
mvn -q "-Dtest=AppAdminControllerTest,ApiKeyAdminControllerTest,ModelConfigAdminControllerTest,ModelConfigServiceTest,AppServiceTest" test
mvn -q "-Dtest=OpenAiChatCompletionsControllerTest,ChatCompletionGatewayServiceTest,OpenAiCompatibleUpstreamClientTest,ApiRequestLogServiceTest" test
mvn -q "-Dtest=GatewayAuthFilterTest,GlobalExceptionHandlerTest,GlobalExceptionHandlerIntegrationTest" test
mvn test
```
