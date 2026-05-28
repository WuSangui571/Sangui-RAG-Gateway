package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.document.parser.DocumentParser;
import com.sangui.raggateway.document.parser.ParsedDocument;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.StoredFile;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private DocumentChunkMapper documentChunkMapper;
    @Mock
    private DocumentChunkEmbeddingMapper documentChunkEmbeddingMapper;
    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private ModelConfigService modelConfigService;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EmbeddingClient embeddingClient;
    @Mock
    private TextChunker textChunker;
    @Mock
    private DocumentParser documentParser;

    @Captor
    private ArgumentCaptor<DocumentChunkEmbeddingEntity> embeddingCaptor;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        DocumentProperties documentProperties = new DocumentProperties();
        documentService = new DocumentService(
                documentMapper, documentChunkMapper, documentChunkEmbeddingMapper,
                knowledgeBaseService, modelConfigService,
                fileStorageService, embeddingClient,
                transactionTemplate(),
                textChunker, List.of(documentParser), documentProperties);

        AtomicLong docIdCounter = new AtomicLong(10L);
        doAnswer(inv -> {
            DocumentEntity entity = inv.getArgument(0);
            entity.setId(docIdCounter.getAndIncrement());
            return 1;
        }).when(documentMapper).insert(any(DocumentEntity.class));
    }

    @Test
    void shouldParseStopsBeforeEmbeddingOnParseFailure() throws Exception {
        byte[] fileContent = "Some content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenThrow(new RuntimeException("Parse error"));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void shouldFailWhenNoEnabledEmbeddingConfig() throws Exception {
        byte[] fileContent = "Hello World\n\nThis is a test document.".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World\n\nThis is a test document.", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World", "This is a test document."));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);
        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(null);
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        assertThat(result.getErrorMessage()).contains("Embedding model config is not ready");
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());
        verifyNoInteractions(embeddingClient);
    }

    @Test
    void shouldTransitionEmbeddingToReady() throws Exception {
        byte[] fileContent = "Hello World\n\nThis is a test document.".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World\n\nThis is a test document.", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World", "This is a test document."));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        ModelConfigEntity config = createModelConfig(10L, 100L, "text-embedding-3-small", 2);
        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("decrypted-key");

        DocumentChunkEntity chunk1 = createChunk(1L, 100L, 1L, 10L, 0);
        DocumentChunkEntity chunk2 = createChunk(2L, 100L, 1L, 10L, 1);
        when(documentChunkMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(chunk1, chunk2));

        when(embeddingClient.embed(eq("https://api.example.com"), eq("decrypted-key"),
                eq("text-embedding-3-small"), anyList(), eq(2)))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}, new float[]{0.3f, 0.4f}));

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.READY.name());
        assertThat(result.getChunkCount()).isEqualTo(2);

        verify(documentChunkEmbeddingMapper, times(2)).insertEmbedding(any(DocumentChunkEmbeddingEntity.class));
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.READY.name());
    }

    @Test
    void shouldPersistOneVectorRowPerChunkWithCorrectTenantFields() throws Exception {
        byte[] fileContent = "Hello World\n\nThis is a test document.".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World\n\nThis is a test document.", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World"));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        ModelConfigEntity config = createModelConfig(10L, 100L, "text-embedding-3-small", 2);
        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("decrypted-key");

        DocumentChunkEntity chunk = createChunk(5L, 100L, 1L, 10L, 0);
        when(documentChunkMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(chunk));

        when(embeddingClient.embed(eq("https://api.example.com"), eq("decrypted-key"),
                eq("text-embedding-3-small"), anyList(), eq(2)))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));

        documentService.uploadAndProcess(100L, 1L, "test.md", "text/markdown", fileContent);

        verify(documentChunkEmbeddingMapper).insertEmbedding(embeddingCaptor.capture());
        DocumentChunkEmbeddingEntity embedding = embeddingCaptor.getValue();
        assertThat(embedding.getUserId()).isEqualTo(100L);
        assertThat(embedding.getKnowledgeBaseId()).isEqualTo(1L);
        assertThat(embedding.getDocumentId()).isEqualTo(10L);
        assertThat(embedding.getChunkId()).isEqualTo(5L);
        assertThat(embedding.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(embedding.getEmbeddingDimension()).isEqualTo(2);
    }

    @Test
    void shouldFailOnEmbeddingException() throws Exception {
        byte[] fileContent = "Hello World".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World"));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        ModelConfigEntity config = createModelConfig(10L, 100L, "text-embedding-3-small", 2);
        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("decrypted-key");

        DocumentChunkEntity chunk = createChunk(1L, 100L, 1L, 10L, 0);
        when(documentChunkMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(chunk));

        when(embeddingClient.embed(anyString(), anyString(), anyString(), anyList(), anyInt()))
                .thenThrow(new EmbeddingException("Embedding upstream returned status 500", false));
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        assertThat(result.getErrorMessage()).isNotNull();
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());
        verify(documentChunkEmbeddingMapper, never()).insertEmbedding(any());
    }

    @Test
    void shouldFailWhenEmbeddingClientReturnsWrongVectorCount() throws Exception {
        byte[] fileContent = "Hello World\n\nSecond chunk".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World\n\nSecond chunk", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World", "Second chunk"));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        ModelConfigEntity config = createModelConfig(10L, 100L, "text-embedding-3-small", 2);
        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(config);
        when(modelConfigService.decryptUpstreamKey(config)).thenReturn("decrypted-key");

        DocumentChunkEntity chunk1 = createChunk(1L, 100L, 1L, 10L, 0);
        DocumentChunkEntity chunk2 = createChunk(2L, 100L, 1L, 10L, 1);
        when(documentChunkMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(chunk1, chunk2));

        when(embeddingClient.embed(anyString(), anyString(), anyString(), anyList(), anyInt()))
                .thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        assertThat(result.getErrorMessage()).contains("count mismatch");
        verify(documentChunkEmbeddingMapper, never()).insertEmbedding(any());
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.FAILED.name());
    }

    @Test
    void shouldKeepKbReadyWhenPriorReadyDocExists() throws Exception {
        byte[] fileContent = "Hello World".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = new StoredFile("knowledge/1/uuid/test.md", fileContent.length);
        when(fileStorageService.save(eq("knowledge"), eq(1L), eq("test.md"), any(InputStream.class)))
                .thenReturn(storedFile);
        when(documentParser.supports(any(), eq("test.md"))).thenReturn(true);
        when(documentParser.parse(any(InputStream.class)))
                .thenReturn(new ParsedDocument("Hello World", "markdown"));
        when(textChunker.chunk(anyString()))
                .thenReturn(List.of("Hello World"));

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(null);
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        DocumentEntity result = documentService.uploadAndProcess(
                100L, 1L, "test.md", "text/markdown", fileContent);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.FAILED.name());
        verify(knowledgeBaseService).updateStatus(1L, KnowledgeBaseStatus.READY.name());
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
                documentMapper, documentChunkMapper, documentChunkEmbeddingMapper,
                knowledgeBaseService, modelConfigService,
                fileStorageService, embeddingClient,
                transactionTemplate(),
                textChunker, List.of(documentParser), documentProperties);

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

        KnowledgeBaseEntity kb = createKb(1L, 100L, "text-embedding-3-small", 2);
        when(knowledgeBaseService.findByIdAndUserId(1L, 100L)).thenReturn(kb);

        when(modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 2))
                .thenReturn(null);
        when(documentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

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

    private KnowledgeBaseEntity createKb(Long id, Long userId, String embeddingModel, int dimension) {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName("Test KB");
        entity.setEmbeddingModel(embeddingModel);
        entity.setEmbeddingDimension(dimension);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());
        return entity;
    }

    private ModelConfigEntity createModelConfig(Long id, Long userId, String embeddingModel, int dimension) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName("Test Config");
        entity.setProviderName("openai");
        entity.setBaseUrl("https://api.example.com");
        entity.setApiKeyEncrypted("encrypted-key");
        entity.setChatModel("gpt-4o-mini");
        entity.setEmbeddingModel(embeddingModel);
        entity.setEmbeddingDimension(dimension);
        entity.setStatus("ENABLED");
        return entity;
    }

    private DocumentChunkEntity createChunk(Long id, Long userId, Long kbId, Long docId, int index) {
        DocumentChunkEntity chunk = new DocumentChunkEntity();
        chunk.setId(id);
        chunk.setUserId(userId);
        chunk.setKnowledgeBaseId(kbId);
        chunk.setDocumentId(docId);
        chunk.setChunkIndex(index);
        chunk.setContent("Chunk " + index);
        chunk.setCreatedAt(LocalDateTime.now());
        chunk.setUpdatedAt(LocalDateTime.now());
        return chunk;
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(new NoopTransactionManager());
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
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
