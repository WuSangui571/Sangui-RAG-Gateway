package com.sangui.raggateway.document;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DocumentAdminControllerTest {

    @Mock
    private DocumentService documentService;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private DocumentProcessingTaskService taskService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocumentAdminController controller = new DocumentAdminController(
                documentService, knowledgeBaseService, new DocumentProperties(), taskService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setUpAuthContext() {
        AdminAuthContextHolder.set(new AdminAuthContext(100L, "testuser"));
    }

    @AfterEach
    void tearDownAuthContext() {
        AdminAuthContextHolder.clear();
    }

    @Test
    void shouldUploadDocumentSuccessfully() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "UPLOADED");
        when(documentService.uploadAndEnqueue(eq(100L), eq(1L), eq("test.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);

        DocumentProcessingTaskEntity task = createTask(20L, 10L, "PENDING");
        when(taskService.findByDocumentId(10L)).thenReturn(task);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello World".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.original_filename").value("test.md"))
                .andExpect(jsonPath("$.data.status").value("UPLOADED"))
                .andExpect(jsonPath("$.data.chunk_count").value(0))
                .andExpect(jsonPath("$.data.processing_task_id").value(20))
                .andExpect(jsonPath("$.data.processing_task_status").value("PENDING"))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldUploadMarkdownFile() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "UPLOADED");
        doc.setOriginalFilename("guide.markdown");
        when(documentService.uploadAndEnqueue(eq(100L), eq(1L), eq("guide.markdown"), isNull(), any(byte[].class)))
                .thenReturn(doc);
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "guide.markdown", null, "Content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.original_filename").value("guide.markdown"));
    }

    @Test
    void shouldRejectEmptyFile() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", new byte[0]);

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldRejectUnsupportedFileExtension() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldRejectUnsupportedContentType() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldRejectOversizedFileBeforeReadingBytes() throws Exception {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setMaxFileSizeBytes(3);
        DocumentAdminController controller = new DocumentAdminController(
                documentService, knowledgeBaseService, documentProperties, taskService);
        MockMvc limitedMockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "test".getBytes());

        limitedMockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldUploadToMissingKnowledgeBase() throws Exception {
        when(knowledgeBaseService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(knowledgeBaseService.findById(999L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/999/documents")
                        .file(file)
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldUploadToCrossUserKnowledgeBase() throws Exception {
        KnowledgeBaseEntity otherKb = createKb(1L, 200L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(knowledgeBaseService.findById(1L)).thenReturn(otherKb);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldListDocumentsForKnowledgeBase() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "PARSED");
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].user_id").value(100));
    }

    @Test
    void shouldListDocumentsWithStatusFilterReady() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "READY");
        when(documentService.listByKnowledgeBase(100L, 1L, "READY")).thenReturn(List.of(doc));
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=READY")
                        )
                .andExpect(status().isOk());
    }

    @Test
    void shouldAcceptEmbeddingStatusFilter() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "EMBEDDING");
        when(documentService.listByKnowledgeBase(100L, 1L, "EMBEDDING")).thenReturn(List.of(doc));
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=EMBEDDING")
                        )
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidDocumentStatusFilter() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=INVALID")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldGetDocumentDetail() throws Exception {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "PARSED");
        when(documentService.findByIdAndUserId(10L, 100L)).thenReturn(doc);
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/documents/10")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldReturn404ForMissingDocument() throws Exception {
        when(documentService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(documentService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/documents/999")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForCrossUserDocument() throws Exception {
        DocumentEntity otherDoc = createDoc(10L, 200L, 1L, "PARSED");
        when(documentService.findByIdAndUserId(10L, 100L)).thenReturn(null);
        when(documentService.findById(10L)).thenReturn(otherDoc);

        mockMvc.perform(get("/api/admin/documents/10")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectMissingAdminUserIdForUpload() throws Exception {
        AdminAuthContextHolder.clear();

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldRejectNonPositiveAdminUserIdForDocumentList() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void shouldDeleteDocumentSuccessfully() throws Exception {
        mockMvc.perform(delete("/api/admin/documents/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(documentService).deleteDocument(100L, 10L);
    }

    @Test
    void shouldRejectMissingAdminUserIdForDocumentDelete() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(delete("/api/admin/documents/10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(documentService, never()).deleteDocument(anyLong(), anyLong());
    }

    @Test
    void shouldRetryFailedDocument() throws Exception {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "UPLOADED");
        when(documentService.retryDocument(100L, 10L)).thenReturn(doc);

        DocumentProcessingTaskEntity task = createTask(20L, 10L, "PENDING");
        when(taskService.findByDocumentId(10L)).thenReturn(task);

        mockMvc.perform(post("/api/admin/documents/10/processing-task/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.processing_task_status").value("PENDING"));

        verify(documentService).retryDocument(100L, 10L);
    }

    @Test
    void shouldRejectRetryWithoutAuth() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/documents/10/processing-task/retry"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(documentService, never()).retryDocument(anyLong(), anyLong());
    }

    @Test
    void shouldUploadWithChineseOriginalFilename() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "UPLOADED");
        doc.setOriginalFilename("中文 文件名（v1）.md");
        when(documentService.uploadAndEnqueue(eq(100L), eq(1L), eq("中文 文件名（v1）.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "中文 文件名（v1）.md", "text/markdown", "中文内容".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.original_filename").value("中文 文件名（v1）.md"))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldUploadWithChineseAndTraversalPathStripped() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(11L, 100L, 1L, "UPLOADED");
        doc.setOriginalFilename("中文.md");
        when(documentService.uploadAndEnqueue(eq(100L), eq(1L), eq("../中文.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);
        when(taskService.findByDocumentId(11L)).thenReturn(null);

        MockMultipartFile file = new MockMultipartFile("file", "../中文.md", "text/markdown", "内容".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.original_filename").value("中文.md"))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldListDocumentsWithChineseOriginalFilename() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "PARSED");
        doc.setOriginalFilename("测试文档.txt");
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].original_filename").value("测试文档.txt"))
                .andExpect(content().string(not(containsString("storage_path"))));
    }

    @Test
    void shouldNotExposeStoragePathInListResponse() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L, "PARSED");
        doc.setStoragePath("internal/secret/path");
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));
        when(taskService.findByDocumentId(10L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        )
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("storage_path"))));
    }

    private KnowledgeBaseEntity createKb(Long id, Long userId) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName("Test KB");
        entity.setEmbeddingModel("text-embedding-3-small");
        entity.setEmbeddingDimension(1536);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private DocumentEntity createDoc(Long id, Long userId, Long kbId, String status) {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setKnowledgeBaseId(kbId);
        doc.setOriginalFilename("test.md");
        doc.setContentType("text/markdown");
        doc.setFileSize(1024L);
        doc.setStoragePath("internal/path");
        doc.setStatus(status);
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    private DocumentProcessingTaskEntity createTask(Long id, Long documentId, String status) {
        DocumentProcessingTaskEntity task = new DocumentProcessingTaskEntity();
        task.setId(id);
        task.setDocumentId(documentId);
        task.setStatus(status);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
