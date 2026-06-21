# 知识库空状态和上传入口不明显

## 背景

上一轮已完成 API Key 创建后的 `base_url` / Chat Completions endpoint 指引，降低了外部系统接入网关的阻力。本任务紧接着改善首次配置链路中的另一个关键阻力：用户创建知识库后，需要更明确地知道“当前还没有可检索内容”以及“下一步应上传文档”。

现有知识库页面已经具备知识库列表、文档列表、上传按钮、文档状态、处理任务状态、失败重试和轮询能力；本任务不应重写后端链路，也不应新增 API。

## 任务分类

- 类型：Complex Task（规划型复杂任务，编码交给 DeepSeek）
- 开发范围：Frontend-only
- Hotfix vs Structural：局部结构性前端 UX 收敛。不是视觉微调，也不是后端/API/DB 改造。

## 目标

让知识库页面在空知识库、无文档、处理中、失败、已有文档等状态下提供明确、可执行且不误导的提示，并让上传入口在空状态中直接可见。

## 非目标 / 禁止修改范围

- 不修改 backend API、controller、service、mapper、migration、DTO/VO 字段或状态枚举。
- 不修改 document ingestion 状态机、处理任务调度、重试语义、轮询间隔语义或上传接口路径。
- 不新增私有文档内容预览，不展示 chunk content、storage_path、embedding、prompt、provider body、stack trace、API key 或 upstream key。
- 不把知识库页面扩展成聊天/调试/工作流平台。
- 不引入新的全局状态管理，不把文档列表缓存进全局 store。
- 不新增独立路由，除非代码研究证明现有页面无法清晰承载；当前预期是在 `KnowledgeBasePage.tsx` 内完成。

## 需求

1. 知识库列表为空时，应显示更可执行的空状态，而不是只有“暂无知识库”。
   - 提示用户需要先创建知识库。
   - 保留现有“创建知识库”入口。

2. 选中一个知识库后，如果文档列表为空，应显示明确的文档空状态。
   - 表达“当前知识库还没有可检索文档”。
   - 提供直接上传按钮，复用现有上传逻辑和文件类型限制。
   - 不隐藏原有顶部上传按钮；可以保留顶部入口，同时让空态入口更明显。

3. 对知识库状态和文档/任务状态进行可理解提示。
   - `KnowledgeBaseStatus.EMPTY`：说明需要上传文档后才可能形成可检索内容。
   - `PROCESSING`：说明文档正在解析/嵌入，页面会继续刷新状态。
   - `FAILED`：说明最近处理失败，应查看文档错误列并在符合条件时重试。
   - `READY`：不干扰现有文档列表，不额外制造噪音。
   - 文档失败且 `processing_task_status === FAILED` 时继续只显示现有 retry 行为。

4. 文案必须走 typed i18n dictionary。
   - 中文和英文 key 必须保持 parity。
   - 不在组件里硬编码新增用户可见文案。

5. 测试覆盖必须补齐知识库页面。
   - 新增 `frontend/src/__tests__/pages/KnowledgeBasePage.test.tsx` 或等价页面测试。
   - Mock typed API client boundary，不依赖真实后端、Docker、provider key、admin JWT 或文件系统。
   - 覆盖空知识库、选中 KB 后空文档且上传入口可见、已有文档时保留列表且不显示空态上传误导、处理中/失败状态提示或状态映射。

## 验收标准

- [ ] 无知识库时，页面出现可执行空状态，用户能看到创建知识库入口。
- [ ] 有知识库但无文档时，选中 KB 后出现直接上传入口，并复用当前 `.txt,.md,.markdown` 限制和 `uploadDocument(selectedKbId, file)` 流程。
- [ ] KB `EMPTY` / `PROCESSING` / `FAILED` / `READY` 的提示不误导：不把空/失败说成 ready，不把 processing 说成完成。
- [ ] 有文档时，原文档表格正常显示；空文档提示和空态上传 CTA 不干扰已有列表。
- [ ] 上传失败、文件类型不支持、文档失败重试路径仍然显式报错，不引入静默 fallback。
- [ ] 新增 i18n key 在 `zh-CN` 与 `en-US` 中保持一致，`DictionaryKeyParity` 继续通过。
- [ ] 前端测试覆盖新增状态分支，并确保 forbidden fields 不因本任务进入 DOM。

## Frontend Contract

现有 API / DTO / VO 不变：

```text
listKnowledgeBases(status?: string): ApiResponse<KnowledgeBaseVO[]>
listDocuments(knowledgeBaseId: number, status?: string): ApiResponse<DocumentVO[]>
uploadDocument(knowledgeBaseId: number, file: File): ApiResponse<DocumentVO>
retryDocument(documentId: number): ApiResponse<DocumentVO>
```

现有状态 union 不变：

```text
KnowledgeBaseStatus = EMPTY | PROCESSING | READY | FAILED
DocumentStatus = UPLOADED | PARSING | PARSED | EMBEDDING | READY | FAILED
DocumentProcessingTaskStatus = PENDING | PROCESSING | SUCCEEDED | RETRYABLE | FAILED | CANCELED
```

## Validation / Error Matrix

| 场景 | 预期 UI | 断言点 |
|---|---|---|
| `listKnowledgeBases` 返回空数组 | 可执行空态，创建知识库入口可见 | 页面测试 |
| 有 KB，未选中 | 不请求文档列表，不显示文档空态 | 页面测试 |
| 选中 `EMPTY` KB，`listDocuments` 返回空数组 | 文档空态 + 直接上传按钮可见 | 页面测试 |
| 选中 `PROCESSING` KB，文档为空或有非终态文档 | 显示 processing 提示，不声称 ready；轮询逻辑不变 | 页面测试 / 现有逻辑 |
| 选中 `FAILED` KB | 显示失败提示；文档错误列/重试入口仍按现有条件显示 | 页面测试 |
| 选中 `READY` KB 且已有文档 | 表格显示文档；不显示空态上传 CTA | 页面测试 |
| 上传文件扩展名不支持 | 显示现有不支持类型错误；不调用 upload API | 页面测试 |
| `uploadDocument` 返回非 OK 或 reject | 显示错误；不伪造成功状态 | 页面测试 |
| DOM forbidden fields 扫描 | 不包含 `storage_path`, `chunk_content`, `embedding`, `prompt`, `api_key`, `stack_trace` 等 | 页面测试 |

## Good / Base / Bad Cases

| Case | Expected result |
|---|---|
| Good | 用户创建/选择空 KB 后立即看到“上传文档”主路径，上传后继续进入现有文档状态/轮询流程；已有文档列表不受干扰。 |
| Base | KB 处理中或失败时，页面只给出状态和下一步，不展示内部异常、存储路径或文档内容；失败仍依赖后端失败状态和已有 retry API。 |
| Bad | 空态只做文案装饰没有可点击上传入口；前端伪造 ready；新增第二套上传逻辑；为了测试吞掉错误；展示 chunk 内容、storage_path 或后端内部错误。 |

## Relevant Specs

- `.trellis/spec/sangui-rag-gateway.md`
- `.trellis/spec/frontend/index.md`
- `.trellis/spec/frontend/directory-structure.md`
- `.trellis/spec/frontend/component-guidelines.md`
- `.trellis/spec/frontend/hook-guidelines.md`
- `.trellis/spec/frontend/state-management.md`
- `.trellis/spec/frontend/type-safety.md`
- `.trellis/spec/frontend/quality-guidelines.md`
- `.trellis/spec/rag/document-ingestion.md`
- `.trellis/spec/security/rag-security.md`
- `.trellis/spec/guides/index.md`
- `.trellis/spec/guides/code-reuse-thinking-guide.md`
- `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Files Likely To Modify

- `frontend/src/pages/knowledge/KnowledgeBasePage.tsx`
  - 增加知识库/文档空态渲染和状态提示，复用现有上传/刷新/错误处理。
- `frontend/src/app/i18n/dict.ts`
  - 增加知识库空态、文档空态、状态提示的中英文文案。
- `frontend/src/__tests__/pages/KnowledgeBasePage.test.tsx`
  - 新增页面级 Vitest/RTL 测试。

可选：

- `frontend/src/components/domain/KnowledgeBaseEmptyState.tsx` 或类似组件
  - 只有当 `KnowledgeBasePage.tsx` 内重复/复杂明显增加时再抽；不要为一次性简单布局过度抽象。

## Required Tests

在 `frontend/` 下运行：

```bash
cmd /c npx vitest run src/__tests__/pages/KnowledgeBasePage.test.tsx
cmd /c npm run lint
cmd /c npm run test
cmd /c npm run typecheck
cmd /c npm run build
```

如本任务没有改登录页、全局 theme、Playwright visual baseline，可不跑 `npm run test:visual`，但需在交付说明中写明原因。

## Planning Self-Check

- [x] 验收标准已明确。
- [x] 禁止修改范围已明确。
- [x] 预计修改文件已列出。
- [x] 必跑测试已列出。
- [x] 已读取具体 frontend guideline，而不只是 spec index。
- [x] 当前需求足够明确，无需用户追加确认。
- [x] 无 API / DB / DTO 字段变更；现有 frontend types 与 backend VO 字段保持不变。
