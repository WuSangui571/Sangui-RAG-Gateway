package com.sangui.raggateway.document;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DocumentAdminController controller = new DocumentAdminController(
                documentService, knowledgeBaseService, new DocumentProperties());
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldUploadDocumentSuccessfully() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        when(documentService.uploadAndProcess(eq(100L), eq(1L), eq("test.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello World".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.original_filename").value("test.md"))
                .andExpect(jsonPath("$.data.status").value("PARSED"))
                .andExpect(jsonPath("$.data.chunk_count").value(2))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldUploadMarkdownFile() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setOriginalFilename("guide.markdown");
        when(documentService.uploadAndProcess(eq(100L), eq(1L), eq("guide.markdown"), isNull(), any(byte[].class)))
                .thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "guide.markdown", null, "Content".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldRejectOversizedFileBeforeReadingBytes() throws Exception {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setMaxFileSizeBytes(3);
        DocumentAdminController controller = new DocumentAdminController(
                documentService, knowledgeBaseService, documentProperties);
        MockMvc limitedMockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "test".getBytes());

        limitedMockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(documentService);
    }

    @Test
    void shouldListDocumentsForKnowledgeBase() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].user_id").value(100));
    }

    @Test
    void shouldListDocumentsWithStatusFilterReady() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setStatus(DocumentStatus.READY.name());
        when(documentService.listByKnowledgeBase(100L, 1L, "READY")).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=READY")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAcceptEmbeddingStatusFilter() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setStatus(DocumentStatus.EMBEDDING.name());
        when(documentService.listByKnowledgeBase(100L, 1L, "EMBEDDING")).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=EMBEDDING")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidDocumentStatusFilter() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents?status=INVALID")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldGetDocumentDetail() throws Exception {
        DocumentEntity doc = createDoc(10L, 100L, 1L);
        when(documentService.findByIdAndUserId(10L, 100L)).thenReturn(doc);

        mockMvc.perform(get("/api/admin/documents/10")
                        .header("X-Admin-User-Id", "100"))
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
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForCrossUserDocument() throws Exception {
        DocumentEntity otherDoc = createDoc(10L, 200L, 1L);
        when(documentService.findByIdAndUserId(10L, 100L)).thenReturn(null);
        when(documentService.findById(10L)).thenReturn(otherDoc);

        mockMvc.perform(get("/api/admin/documents/10")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectMissingAdminUserIdForUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.md", "text/markdown", "Hello".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectNonPositiveAdminUserIdForDocumentList() throws Exception {
        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        .header("X-Admin-User-Id", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldUploadWithChineseOriginalFilename() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setOriginalFilename("中文 文件名（v1）.md");
        when(documentService.uploadAndProcess(eq(100L), eq(1L), eq("中文 文件名（v1）.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "中文 文件名（v1）.md", "text/markdown", "中文内容".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.original_filename").value("中文 文件名（v1）.md"))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldUploadWithChineseAndTraversalPathStripped() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(11L, 100L, 1L);
        doc.setOriginalFilename("中文.md");
        when(documentService.uploadAndProcess(eq(100L), eq(1L), eq("../中文.md"), eq("text/markdown"), any(byte[].class)))
                .thenReturn(doc);

        MockMultipartFile file = new MockMultipartFile("file", "../中文.md", "text/markdown", "内容".getBytes());

        mockMvc.perform(multipart("/api/admin/knowledge-bases/1/documents")
                        .file(file)
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.original_filename").value("中文.md"))
                .andExpect(jsonPath("$.data.storage_path").doesNotExist());
    }

    @Test
    void shouldListDocumentsWithChineseOriginalFilename() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setOriginalFilename("测试文档.txt");
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].original_filename").value("测试文档.txt"))
                .andExpect(content().string(not(containsString("storage_path"))));
    }

    @Test
    void shouldNotExposeStoragePathInListResponse() throws Exception {
        KnowledgeBaseEntity kb = createKb(1L, 100L);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        DocumentEntity doc = createDoc(10L, 100L, 1L);
        doc.setStoragePath("internal/secret/path");
        when(documentService.listByKnowledgeBase(100L, 1L, null)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/admin/knowledge-bases/1/documents")
                        .header("X-Admin-User-Id", "100"))
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

    private DocumentEntity createDoc(Long id, Long userId, Long kbId) {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setKnowledgeBaseId(kbId);
        doc.setOriginalFilename("test.md");
        doc.setContentType("text/markdown");
        doc.setFileSize(1024L);
        doc.setStoragePath("internal/path");
        doc.setStatus(DocumentStatus.PARSED.name());
        doc.setChunkCount(2);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }
}
