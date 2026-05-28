package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.document.parser.DocumentParser;
import com.sangui.raggateway.document.parser.ParsedDocument;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.StoredFile;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private TextChunker textChunker;
    @Mock
    private DocumentParser documentParser;

    @Captor
    private ArgumentCaptor<DocumentEntity> documentCaptor;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentService = new DocumentService(
                documentMapper, documentChunkMapper, knowledgeBaseService,
                fileStorageService, textChunker, List.of(documentParser), documentProperties);
    }

    @Test
    void shouldUploadAndProcessSuccessfully() throws Exception {
        byte[] fileContent = "Hello World\n\nThis is a test document.".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World\n\nThis is a test document.", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World", "This is a test document."));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        verify(documentMapper).insert(any(DocumentEntity.class));
        verify(documentChunkMapper, times(2)).insertChunk(any(DocumentChunkEntity.class));
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.PROCESSING.name());
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.READY.name());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSED.name());
        assertThat(result.getChunkCount()).isEqualTo(2);
        assertThat(result.getOriginalFilename()).isEqualTo("test.md");
    }

    @Test
    void shouldUploadTxtFile() throws Exception {
        byte[] fileContent = "Plain text content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/readme.txt", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("readme.txt"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("readme.txt"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Plain text content", "plain-text"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Plain text content"));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "readme.txt", "text/plain", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PARSED.name());
        assertThat(result.getChunkCount()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        byte[] fileContent = "test".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> documentService.uploadAndProcess(
                100L, 1L, "test.pdf", "application/pdf", fileContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
        verifyNoInteractions(fileStorageService);
        verifyNoInteractions(documentMapper);
    }

    @Test
    void shouldRejectUnsupportedContentTypeBeforeStorage() {
        byte[] fileContent = "test".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> documentService.uploadAndProcess(
                100L, 1L, "test.md", "application/pdf", fileContent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported content type");
        verifyNoInteractions(fileStorageService);
        verifyNoInteractions(documentMapper);
    }

    @Test
    void shouldRejectOversizedFileBeforeStorage() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentProperties.setMaxFileSizeBytes(3);
        DocumentService limitedService = new DocumentService(
                documentMapper, documentChunkMapper, knowledgeBaseService,
                fileStorageService, textChunker, List.of(documentParser), documentProperties);

        assertThatThrownBy(() -> limitedService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", "test".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-file-size-bytes");
        verifyNoInteractions(fileStorageService);
        verifyNoInteractions(documentMapper);
    }

    @Test
    void shouldStoreSanitizedOriginalFilename() throws Exception {
        byte[] fileContent = "Plain text content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/secret.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("secret.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("secret.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Plain text content", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Plain text content"));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "../secret.md", "text/markdown", fileContent);

        assertThat(result.getOriginalFilename()).isEqualTo("secret.md");
    }

    @Test
    void shouldMarkDocumentFailedOnEmptyParsedText() throws Exception {
        byte[] fileContent = "  ".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("  ", "markdown"));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        verify(documentMapper).insert(any(DocumentEntity.class));
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldMarkDocumentFailedOnParseException() throws Exception {
        byte[] fileContent = "Some content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenThrow(new RuntimeException("Parse error: invalid encoding"));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        assertThat(result.getErrorMessage()).isNotNull();
    }

    @Test
    void shouldKeepKnowledgeBaseReadyWhenFailureHasExistingParsedDocument() throws Exception {
        byte[] fileContent = "Some content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenThrow(new RuntimeException("Parse error"));
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.READY.name());
    }

    @Test
    void shouldListDocumentsByKnowledgeBase() {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "test.md");
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(doc));

        List<DocumentEntity> result = documentService.listByKnowledgeBase(100L, 1L, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getKnowledgeBaseId()).isEqualTo(1L);
    }

    @Test
    void shouldListDocumentsWithStatusFilter() {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "test.md");
        when(documentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(doc));

        List<DocumentEntity> result = documentService.listByKnowledgeBase(100L, 1L, "PARSED");
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindDocumentByUserAndId() {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "test.md");
        when(documentMapper.selectOne(any())).thenReturn(doc);

        DocumentEntity result = documentService.findByIdAndUserId(10L, 100L);
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldReturnNullWhenDocumentNotFoundForUser() {
        when(documentMapper.selectOne(any())).thenReturn(null);
        DocumentEntity result = documentService.findByIdAndUserId(10L, 200L);
        assertThat(result).isNull();
    }

    @Test
    void shouldNotExposeStoragePathInVO() {
        DocumentEntity doc = createDoc(10L, 100L, 1L, "test.md");
        doc.setStoragePath("knowledge/1/uuid/test.md");

        var vo = com.sangui.raggateway.document.vo.DocumentVO.from(doc);
        assertThat(vo.getId()).isEqualTo(10L);
        assertThat(vo.getOriginalFilename()).isEqualTo("test.md");
    }

    private DocumentEntity createDoc(Long id, Long userId, Long kbId, String filename) {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(id);
        doc.setUserId(userId);
        doc.setKnowledgeBaseId(kbId);
        doc.setOriginalFilename(filename);
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
