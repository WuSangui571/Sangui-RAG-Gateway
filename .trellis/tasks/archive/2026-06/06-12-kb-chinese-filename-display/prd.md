# 知识库上传中文文件名显示修复

## Goal

修复知识库文档上传后中文文件名在管理台显示为下划线的问题。上传链路应区分“内部存储安全名”和“用户可见原始展示名”：落盘路径继续使用安全化文件名，文档列表和详情继续通过 `original_filename` 展示用户上传时的合理原始文件名。

## Scope Classification

- 类型：Simple Task
- 范围：后端 document upload/service/storage 边界为主，前端知识库文档表只需确认或做极小展示调整。
- 触发的跨层边界：multipart filename -> backend service persistence -> DocumentVO -> frontend table display。
- 预期不需要 DB migration：`rag_document.original_filename` 和 `DocumentVO.original_filename` 已存在。

## Requirements

- 保留用户上传文件名中的中文、空格、括号和常见 Unicode 展示字符，只移除路径部分，避免路径穿越。
- `rag_document.original_filename` 应保存展示用 basename，例如 `../资料（第一版）.md` 应保存为 `资料（第一版）.md`，而不是 `______.md`。
- `storage_path` 继续使用安全化内部 key，不暴露给 Admin API 或前端。
- Parser 选择仍基于支持的扩展名，中文 basename 不应影响 `.txt`、`.md`、`.markdown` 判断。
- chunk metadata 中的 source/display source 应优先使用展示文件名；如果实现者认为应继续使用安全名，必须在交接/实现说明中解释原因。
- 上传、列表、详情 API 返回的 `DocumentVO.original_filename` 必须保留中文显示名。
- 前端知识库文档表继续使用 `original_filename`，不应改用 `storage_path`、内部 key 或客户端本地文件名作为二次来源。

## Non-Goals / Forbidden Scope

- 不新增数据库字段，除非实现者先证明现有 `original_filename` 无法满足展示需求并回到 Codex/用户确认。
- 不修改 `rag_document` schema、Flyway migration、DTO/VO 字段名或 API path。
- 不改变支持的上传格式范围，仍为 `.txt`、`.md`、`.markdown`。
- 不改 embedding、retrieval、RAG prompt、request log、安全日志、模型配置、API key、Docker/infra。
- 不暴露 `storage_path`、绝对路径、chunk content、embedding、prompt、provider body 或任何 secret。
- 不添加静默 fallback；不应在 filename 异常时伪造成功上传。

## Current Research Summary

### Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`：Document 记录 filename、storage path、status；storage path 是内部字段。
- `.trellis/spec/backend/database-guidelines.md`：`rag_document.original_filename` 已存在，`storage_path` 不暴露。
- `.trellis/spec/backend/error-handling.md`：KB/document admin API 错误矩阵，Unsupported filename/Missing multipart file 等应继续走 `INVALID_REQUEST`。
- `.trellis/spec/backend/logging-guidelines.md`：document filename 是允许的安全日志字段，但 raw content、storage absolute path 不可记录。
- `.trellis/spec/backend/quality-guidelines.md`：Document VO 必须排除 `storage_path`，上传/解析/租户测试必须保持。
- `.trellis/spec/frontend/type-safety.md`：`DocumentVO.original_filename` 是前端展示字段，snake_case 由后端提供。
- `.trellis/spec/frontend/state-management.md`：document upload 状态属于页面/服务端状态，不引入全局缓存。
- `.trellis/spec/frontend/quality-guidelines.md`：文档状态和上传结果需清晰展示，敏感字段默认遮蔽/省略。
- `.trellis/spec/rag/document-ingestion.md`：上传 -> 存储 -> document row -> parser -> chunking -> embedding -> status display 的链路必须保持状态明确。
- `.trellis/spec/security/rag-security.md`：证据/日志/响应不得暴露 `storage_path`、完整文档内容或内部路径。
- `.trellis/spec/guides/cross-layer-thinking-guide.md`：本任务跨 multipart、service、DB field、VO、UI，需要对字段边界做明确约束。

### Code Patterns Found

- `backend/src/main/java/com/sangui/raggateway/document/DocumentAdminController.java` 从 `MultipartFile.getOriginalFilename()` 读取原始文件名，验证扩展名和 content type 后传给 `DocumentService.uploadAndProcess(...)`。
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java` 当前在 `uploadAndParse(...)` 中调用 `DocumentUploadRules.sanitizeFilename(originalFilename)`，并把 `safeFilename` 同时用于 storage、`doc.setOriginalFilename(...)`、parser 选择和 chunk metadata source。这是中文显示为下划线的核心原因。
- `backend/src/main/java/com/sangui/raggateway/document/storage/LocalFileStorageService.java` 保存文件时再次调用 `DocumentUploadRules.sanitizeFilename(...)` 生成内部 storage key，符合“存储安全名”的需求。
- `backend/src/main/java/com/sangui/raggateway/document/vo/DocumentVO.java` 从 entity 映射 `original_filename`，不暴露 `storage_path`。
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx` 文档表 `dataIndex: 'original_filename'`，前端已在使用正确字段。

## API / Command / Payload Fields

No API path or field changes expected.

Existing upload endpoint:

```http
POST /api/admin/knowledge-bases/{knowledgeBaseId}/documents
X-Admin-User-Id: <userId>
Content-Type: multipart/form-data
file=<uploaded file>
```

Existing response field to preserve:

```json
{
  "code": "OK",
  "data": {
    "original_filename": "中文 文件名（v1）.md"
  }
}
```

Existing internal-only field:

```text
rag_document.storage_path
```

`storage_path` must remain absent from `DocumentVO` and frontend types.

## Validation / Error Matrix

| Scenario | Expected Result | Assertion Point |
|---|---|---|
| Upload `中文 文件名（v1）.md` | `DocumentVO.original_filename` and persisted entity original filename retain `中文 文件名（v1）.md` | `DocumentServiceTest`, `DocumentAdminControllerTest` |
| Upload `../中文.md` | Path segment is stripped; display filename is `中文.md`; storage path does not contain `..` | `DocumentServiceTest`, `LocalFileStorageServiceTest` |
| Upload `报告 final (v2).txt` | Spaces/parentheses remain in `original_filename`; storage path remains safe | service/storage tests |
| Upload `test.pdf` | Still rejected as unsupported | existing controller/service tests |
| Upload empty file | Still rejected before service mutation | existing controller/service tests |
| List documents | Response contains `original_filename`; does not contain `storage_path` | `DocumentAdminControllerTest` |
| Frontend table render | Uses `original_filename`; no frontend fallback to storage key | typecheck/build, optional manual UI smoke |

## Good / Base / Bad Cases

| Case | Expected Result |
|---|---|
| Good | A Chinese/space/parentheses mixed filename uploads successfully, storage uses safe internal key, document list/detail display the original basename, parser/chunking/embedding behavior remains unchanged. |
| Base | ASCII filename behavior remains unchanged; path traversal input strips directory components and stores only basename for display. |
| Bad | UI displays sanitized underscores, backend exposes `storage_path`, DB migration adds a duplicate display field without need, parser fails because Unicode basename was separated incorrectly, or storage starts using unsafe raw user filename. |

## Files Likely To Modify

- `backend/src/main/java/com/sangui/raggateway/document/DocumentUploadRules.java`: likely add or rename helper for display-safe basename vs storage-safe filename.
- `backend/src/main/java/com/sangui/raggateway/document/DocumentService.java`: save display basename to `originalFilename`; pass storage-safe name only to storage if needed; ensure parser selection and chunk metadata use the intended name.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentServiceTest.java`: update `shouldStoreSanitizedOriginalFilename` expectation and add Chinese/mixed filename coverage.
- `backend/src/test/java/com/sangui/raggateway/document/DocumentAdminControllerTest.java`: add/adjust upload response coverage for Chinese filename and `storage_path` absence.
- `backend/src/test/java/com/sangui/raggateway/document/storage/LocalFileStorageServiceTest.java`: add storage key safety coverage for Chinese/mixed filenames if implementation changes storage helper behavior.
- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`: likely no change; only adjust if UI needs tooltip/render clarity for long Unicode names.

## Required Tests

Backend, from `backend/`:

```powershell
mvn -q -DskipTests compile
mvn -q "-Dtest=DocumentServiceTest,DocumentAdminControllerTest,LocalFileStorageServiceTest" test
```

Frontend, from `frontend/` if any frontend file changes, or as regression check for the admin page:

```powershell
cmd /c npm run typecheck
cmd /c npm run build
```

Optional manual validation:

- Upload `中文 文件名（v1）.md`
- Upload `Sangui RAG Gateway (测试版).txt`
- Upload `../路径穿越.md`
- Confirm document list and detail display the basename with Chinese characters preserved.
- Confirm storage path/internal key is not shown in API response or UI.

## Planning Self-Check

- [x] 验收标准已明确：中文/空格/括号混合文件名展示保留，storage key 继续安全化。
- [x] 禁止修改范围已明确：不改 DB/API 字段、不碰模型配置/API key/security logs/retrieval/prompt/infra。
- [x] 预计修改文件已列出。
- [x] 必跑测试已列出。
- [x] 已读取具体 guideline，不只是 spec index。
- [x] 当前没有需要用户确认的问题；只有当实现者认为必须新增 DB 字段/API 字段时才需要停止确认。
- [x] API/DB/frontend types/DTO 字段已对齐：复用 `original_filename`，不新增 `displayName`。
