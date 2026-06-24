package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.util.PgVectorFormatter;
import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.document.parser.DocumentParser;
import com.sangui.raggateway.document.parser.ParsedDocument;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.StoredFile;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.embedding.EmbeddingProperties;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Profile("!test")
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentChunkEmbeddingMapper documentChunkEmbeddingMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelConfigService modelConfigService;
    private final FileStorageService fileStorageService;
    private final EmbeddingClient embeddingClient;
    private final TransactionTemplate transactionTemplate;
    private final TextChunker textChunker;
    private final List<DocumentParser> parsers;
    private final DocumentProperties documentProperties;
    private final DocumentProcessingTaskService taskService;
    private final DocumentProcessingProperties processingProperties;
    private final EmbeddingProperties embeddingProperties;

    public DocumentService(DocumentMapper documentMapper,
                           DocumentChunkMapper documentChunkMapper,
                           DocumentChunkEmbeddingMapper documentChunkEmbeddingMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           ModelConfigService modelConfigService,
                           FileStorageService fileStorageService,
                           EmbeddingClient embeddingClient,
                           TransactionTemplate transactionTemplate,
                           TextChunker textChunker,
                           List<DocumentParser> parsers,
                           DocumentProperties documentProperties,
                           DocumentProcessingTaskService taskService,
                           DocumentProcessingProperties processingProperties,
                           EmbeddingProperties embeddingProperties) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentChunkEmbeddingMapper = documentChunkEmbeddingMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.modelConfigService = modelConfigService;
        this.fileStorageService = fileStorageService;
        this.embeddingClient = embeddingClient;
        this.transactionTemplate = transactionTemplate;
        this.textChunker = textChunker;
        this.parsers = parsers;
        this.documentProperties = documentProperties;
        this.taskService = taskService;
        this.processingProperties = processingProperties;
        this.embeddingProperties = embeddingProperties;
    }

    public DocumentEntity uploadAndProcess(Long userId, Long knowledgeBaseId,
                                            String originalFilename, String contentType,
                                            byte[] fileContent) {
        DocumentEntity doc = transactionTemplate.execute(status ->
                uploadAndParse(userId, knowledgeBaseId, originalFilename, contentType, fileContent));
        if (doc == null || DocumentStatus.FAILED.name().equals(doc.getStatus())) {
            return doc;
        }
        return embedAndFinalize(userId, knowledgeBaseId, doc);
    }

    public DocumentEntity uploadAndEnqueue(Long userId, Long knowledgeBaseId,
                                            String originalFilename, String contentType,
                                            byte[] fileContent) {
        if (!DocumentUploadRules.isSupportedFilename(originalFilename)) {
            throw new IllegalArgumentException("Unsupported file type: " + originalFilename + ". Only .txt, .md, and .markdown files are supported.");
        }
        if (!DocumentUploadRules.isSupportedContentType(contentType)) {
            throw new IllegalArgumentException("Unsupported content type");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (fileContent.length > documentProperties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("File exceeds max-file-size-bytes");
        }

        String safeFilename = DocumentUploadRules.sanitizeFilename(originalFilename);
        String displayBasename = DocumentUploadRules.extractDisplayBasename(originalFilename);

        InputStream storageStream = new ByteArrayInputStream(fileContent);
        StoredFile storedFile;
        try {
            storedFile = fileStorageService.save("knowledge", knowledgeBaseId, safeFilename, storageStream);
        } catch (Exception e) {
            log.error("Storage save failed during upload: kbId={}, filename={}, errorClass={}",
                    knowledgeBaseId, safeFilename, e.getClass().getSimpleName());
            throw new BusinessException("STORAGE_ERROR",
                    "Upload failed: " + truncateSafe(e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String storageKey = storedFile.getStoragePath();

        try {
            return transactionTemplate.execute(status -> {
                DocumentEntity doc = new DocumentEntity();
                doc.setUserId(userId);
                doc.setKnowledgeBaseId(knowledgeBaseId);
                doc.setOriginalFilename(displayBasename);
                doc.setContentType(contentType);
                doc.setFileSize(storedFile.getFileSize());
                doc.setStoragePath(storageKey);
                doc.setStatus(DocumentStatus.UPLOADED.name());
                doc.setChunkCount(0);
                doc.setCreatedAt(LocalDateTime.now());
                doc.setUpdatedAt(LocalDateTime.now());
                documentMapper.insert(doc);
                log.info("Document created: id={}, kbId={}, filename={}, status=UPLOADED",
                        doc.getId(), knowledgeBaseId, displayBasename);

                taskService.createTask(userId, knowledgeBaseId, doc.getId(), processingProperties.getMaxAttempts());

                knowledgeBaseService.updateStatus(knowledgeBaseId, KnowledgeBaseStatus.PROCESSING.name());

                return doc;
            });
        } catch (Exception e) {
            log.error("Upload transaction failed: kbId={}, storageKey={}, errorClass={}",
                    knowledgeBaseId, storageKey, e.getClass().getSimpleName());
            try {
                fileStorageService.delete(storageKey);
            } catch (Exception deleteEx) {
                log.error("Storage cleanup failed after upload rollback: storageKey={}, kbId={}, userId={}, cleanupErrorClass={}",
                        storageKey, knowledgeBaseId, userId, deleteEx.getClass().getSimpleName());
            }
            throw new BusinessException("DATABASE_ERROR",
                    "Upload failed: " + truncateSafe(e.getMessage()),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public DocumentEntity uploadAndParse(Long userId, Long knowledgeBaseId,
                                          String originalFilename, String contentType,
                                          byte[] fileContent) {
        if (!DocumentUploadRules.isSupportedFilename(originalFilename)) {
            throw new IllegalArgumentException("Unsupported file type: " + originalFilename + ". Only .txt, .md, and .markdown files are supported.");
        }
        if (!DocumentUploadRules.isSupportedContentType(contentType)) {
            throw new IllegalArgumentException("Unsupported content type");
        }
        if (fileContent == null || fileContent.length == 0) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (fileContent.length > documentProperties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("File exceeds max-file-size-bytes");
        }

        String safeFilename = DocumentUploadRules.sanitizeFilename(originalFilename);
        String displayBasename = DocumentUploadRules.extractDisplayBasename(originalFilename);

        InputStream storageStream = new ByteArrayInputStream(fileContent);
        StoredFile storedFile = fileStorageService.save("knowledge", knowledgeBaseId, safeFilename, storageStream);

        DocumentEntity doc = new DocumentEntity();
        doc.setUserId(userId);
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setOriginalFilename(displayBasename);
        doc.setContentType(contentType);
        doc.setFileSize(storedFile.getFileSize());
        doc.setStoragePath(storedFile.getStoragePath());
        doc.setStatus(DocumentStatus.UPLOADED.name());
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        log.info("Document created: id={}, kbId={}, filename={}, status=UPLOADED",
                doc.getId(), knowledgeBaseId, displayBasename);

        knowledgeBaseService.updateStatus(knowledgeBaseId, KnowledgeBaseStatus.PROCESSING.name());

        return parseDocumentContent(doc, userId, knowledgeBaseId, displayBasename, contentType, fileContent);
    }

    DocumentEntity parseDocumentContent(DocumentEntity doc, Long userId, Long knowledgeBaseId,
                                         String displayBasename, String contentType, byte[] fileContent) {
        doc.setStatus(DocumentStatus.PARSING.name());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        try {
            DocumentParser parser = selectParser(contentType, displayBasename);
            if (parser == null) {
                throw new IllegalArgumentException("No parser found for file: " + displayBasename);
            }

            InputStream parseStream = new ByteArrayInputStream(fileContent);
            ParsedDocument parsed = parser.parse(parseStream);
            parseStream.close();

            String cleanedText = normalizeText(parsed.getText());
            if (cleanedText.isBlank()) {
                doc.setStatus(DocumentStatus.FAILED.name());
                doc.setErrorMessage("Document has no readable text");
                doc.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(doc);
                updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
                log.warn("Document parsed with no readable text: id={}", doc.getId());
                return doc;
            }

            List<String> chunks = textChunker.chunk(cleanedText);
            log.info("Document chunked: id={}, chunkCount={}", doc.getId(), chunks.size());

            for (int i = 0; i < chunks.size(); i++) {
                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setUserId(userId);
                chunk.setKnowledgeBaseId(knowledgeBaseId);
                chunk.setDocumentId(doc.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunks.get(i));
                chunk.setTokenCount(chunks.get(i).length());
                chunk.setMetadata("{\"parser\":\"" + parsed.getParserName() + "\",\"source\":\"" + escapeJson(displayBasename) + "\"}");
                chunk.setCreatedAt(LocalDateTime.now());
                chunk.setUpdatedAt(LocalDateTime.now());
                documentChunkMapper.insertChunk(chunk);
            }

            doc.setStatus(DocumentStatus.PARSED.name());
            doc.setChunkCount(chunks.size());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("Document parsed successfully: id={}, chunks={}", doc.getId(), chunks.size());
            return doc;

        } catch (Exception e) {
            log.error("Document processing failed: id={}", doc.getId(), e);
            doc.setStatus(DocumentStatus.FAILED.name());
            doc.setErrorMessage(truncateSafe(e.getMessage()));
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }
    }

    public void processDocument(DocumentProcessingTaskEntity task) {
        Long docId = task.getDocumentId();
        Long userId = task.getUserId();
        Long kbId = task.getKnowledgeBaseId();
        Long taskId = task.getId();

        DocumentEntity doc = findById(docId);
        if (doc == null) {
            log.error("Document not found for task: taskId={}, docId={}", taskId, docId);
            taskService.transitionToFailed(taskId, "Document not found");
            return;
        }

        clearChunksAndEmbeddings(docId);
        log.info("Document processing attempt cleanup completed: taskId={}, docId={}, attempt={}",
                taskId, docId, task.getAttemptCount());

        byte[] fileContent;
        try (InputStream storageStream = fileStorageService.read(doc.getStoragePath())) {
            fileContent = storageStream.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to read file from storage: taskId={}, docId={}, storageKey={}, errorClass={}",
                    taskId, docId, doc.getStoragePath(), e.getClass().getSimpleName());
            DocumentProcessingTaskEntity current = taskService.findById(taskId);
            int attempt = current != null && current.getAttemptCount() != null ? current.getAttemptCount() : task.getAttemptCount();
            int maxAttempts = current != null && current.getMaxAttempts() != null ? current.getMaxAttempts() : task.getMaxAttempts();
            String errorMessage = "Failed to read file from storage: " + e.getClass().getSimpleName();
            if (attempt < maxAttempts) {
                taskService.transitionToRetryable(taskId, errorMessage);
                markFailed(doc, errorMessage);
                updateKnowledgeBaseAfterFailure(userId, kbId);
            } else {
                taskService.transitionToFailed(taskId, errorMessage);
                markFailed(doc, errorMessage);
                updateKnowledgeBaseAfterFailure(userId, kbId);
            }
            return;
        }

        doc = parseDocumentContent(doc, userId, kbId,
                doc.getOriginalFilename(), doc.getContentType(), fileContent);

        if (DocumentStatus.FAILED.name().equals(doc.getStatus())) {
            int currentAttempt = task.getAttemptCount() != null ? task.getAttemptCount() : 0;
            int maxAttempts = task.getMaxAttempts() != null ? task.getMaxAttempts() : processingProperties.getMaxAttempts();
            if (currentAttempt < maxAttempts) {
                taskService.transitionToRetryable(taskId, doc.getErrorMessage());
            } else {
                taskService.transitionToFailed(taskId, doc.getErrorMessage());
            }
            return;
        }

        doc = embedAndFinalize(userId, kbId, doc);

        if (DocumentStatus.READY.name().equals(doc.getStatus())) {
            taskService.transitionToSucceeded(taskId);
        } else {
            int currentAttempt = task.getAttemptCount() != null ? task.getAttemptCount() : 0;
            int maxAttempts = task.getMaxAttempts() != null ? task.getMaxAttempts() : processingProperties.getMaxAttempts();
            if (currentAttempt < maxAttempts) {
                taskService.transitionToRetryable(taskId, doc.getErrorMessage());
            } else {
                taskService.transitionToFailed(taskId, doc.getErrorMessage());
            }
        }
    }

    public DocumentEntity retryDocument(Long userId, Long documentId) {
        DocumentEntity doc = findByIdAndUserId(documentId, userId);
        if (doc == null) {
            DocumentEntity any = findById(documentId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND);
        }

        DocumentProcessingTaskEntity task = taskService.findByDocumentId(documentId);

        if (task == null) {
            throw new BusinessException("INVALID_REQUEST",
                    "No processing task found for document", HttpStatus.BAD_REQUEST);
        }

        if (DocumentProcessingTaskStatus.PROCESSING.name().equals(task.getStatus())) {
            throw new BusinessException("DOCUMENT_PROCESSING",
                    "Document is currently being processed", HttpStatus.CONFLICT);
        }

        if (DocumentProcessingTaskStatus.PENDING.name().equals(task.getStatus())
                || DocumentProcessingTaskStatus.RETRYABLE.name().equals(task.getStatus())) {
            return doc;
        }

        if (DocumentProcessingTaskStatus.SUCCEEDED.name().equals(task.getStatus())
                && DocumentStatus.READY.name().equals(doc.getStatus())) {
            throw new BusinessException("INVALID_REQUEST",
                    "Document is already processed successfully", HttpStatus.BAD_REQUEST);
        }

        if (DocumentProcessingTaskStatus.FAILED.name().equals(task.getStatus())) {
            clearChunksAndEmbeddings(documentId);

            doc.setStatus(DocumentStatus.UPLOADED.name());
            doc.setErrorMessage(null);
            doc.setChunkCount(0);
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            taskService.resetForRetry(task.getId());

            knowledgeBaseService.updateStatus(doc.getKnowledgeBaseId(), KnowledgeBaseStatus.PROCESSING.name());

            log.info("Document retry queued: docId={}, taskId={}", documentId, task.getId());
            return doc;
        }

        throw new BusinessException("INVALID_REQUEST",
                "Cannot retry document in current state", HttpStatus.BAD_REQUEST);
    }

    void clearChunksAndEmbeddings(Long documentId) {
        LambdaQueryWrapper<DocumentChunkEmbeddingEntity> embeddingWrapper = new LambdaQueryWrapper<>();
        embeddingWrapper.eq(DocumentChunkEmbeddingEntity::getDocumentId, documentId);
        documentChunkEmbeddingMapper.delete(embeddingWrapper);

        LambdaQueryWrapper<DocumentChunkEntity> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(DocumentChunkEntity::getDocumentId, documentId);
        documentChunkMapper.delete(chunkWrapper);
    }

    DocumentEntity embedAndFinalize(Long userId, Long knowledgeBaseId, DocumentEntity doc) {
        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(knowledgeBaseId, userId);
        if (kb == null) {
            markFailed(doc, "Knowledge base not found");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }

        ModelConfigEntity embeddingConfig = modelConfigService.findEnabledEmbeddingConfig(
                userId, kb.getEmbeddingModel(), kb.getEmbeddingDimension());
        if (embeddingConfig == null) {
            markFailed(doc, "Embedding model config is not ready");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }

        String upstreamApiKey;
        try {
            upstreamApiKey = modelConfigService.decryptUpstreamKey(embeddingConfig);
        } catch (Exception e) {
            log.error("Failed to decrypt upstream key for embedding: docId={}", doc.getId(), e);
            markFailed(doc, "Embedding model config is not ready");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }

        if (upstreamApiKey == null || upstreamApiKey.isBlank()) {
            markFailed(doc, "Embedding model config is not ready");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }

        doc.setStatus(DocumentStatus.EMBEDDING.name());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        List<DocumentChunkEntity> chunks = findChunksByDocumentId(doc.getId());
        if (chunks.isEmpty()) {
            markFailed(doc, "No chunks found for document");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }

        List<String> chunkTexts = new ArrayList<>(chunks.size());
        for (DocumentChunkEntity chunk : chunks) {
            chunkTexts.add(chunk.getContent());
        }

        try {
            int batchSize = embeddingProperties.getBatchSize();
            List<float[]> vectors = new ArrayList<>(chunks.size());

            for (int start = 0; start < chunkTexts.size(); start += batchSize) {
                int end = Math.min(start + batchSize, chunkTexts.size());
                List<String> batch = chunkTexts.subList(start, end);
                List<float[]> batchVectors = embeddingClient.embed(
                        embeddingConfig.getBaseUrl(),
                        upstreamApiKey,
                        kb.getEmbeddingModel(),
                        batch,
                        kb.getEmbeddingDimension());
                vectors.addAll(batchVectors);
            }

            validateEmbeddingVectors(chunks, vectors, kb.getEmbeddingDimension());

            transactionTemplate.executeWithoutResult(status -> {
                persistEmbeddings(userId, knowledgeBaseId, doc.getId(), chunks, vectors,
                        kb.getEmbeddingModel(), kb.getEmbeddingDimension());

                doc.setStatus(DocumentStatus.READY.name());
                doc.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(doc);

                knowledgeBaseService.updateStatus(knowledgeBaseId, KnowledgeBaseStatus.READY.name());
            });
            log.info("Document embedding completed successfully: id={}, chunks={}", doc.getId(), vectors.size());
            return doc;

        } catch (EmbeddingException e) {
            log.error("Document embedding failed: id={}", doc.getId(), e);
            markFailed(doc, truncateSafe(e.getMessage()));
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        } catch (Exception e) {
            log.error("Document embedding unexpected failure: id={}", doc.getId(), e);
            markFailed(doc, "Embedding processing failed");
            updateKnowledgeBaseAfterFailure(userId, knowledgeBaseId);
            return doc;
        }
    }

    public void persistEmbeddings(Long userId, Long knowledgeBaseId, Long documentId,
                                   List<DocumentChunkEntity> chunks, List<float[]> vectors,
                                   String embeddingModel, int embeddingDimension) {
        for (int i = 0; i < vectors.size(); i++) {
            DocumentChunkEmbeddingEntity embedding = new DocumentChunkEmbeddingEntity();
            embedding.setUserId(userId);
            embedding.setKnowledgeBaseId(knowledgeBaseId);
            embedding.setDocumentId(documentId);
            embedding.setChunkId(chunks.get(i).getId());
            embedding.setEmbeddingModel(embeddingModel);
            embedding.setEmbeddingDimension(embeddingDimension);
            embedding.setEmbedding(PgVectorFormatter.format(vectors.get(i)));
            embedding.setCreatedAt(LocalDateTime.now());
            embedding.setUpdatedAt(LocalDateTime.now());
            documentChunkEmbeddingMapper.insertEmbedding(embedding);
        }
    }

    private void validateEmbeddingVectors(List<DocumentChunkEntity> chunks, List<float[]> vectors, int expectedDimension) {
        if (vectors == null || vectors.size() != chunks.size()) {
            throw new EmbeddingException("Embedding response count mismatch", false);
        }
        for (int i = 0; i < vectors.size(); i++) {
            float[] vector = vectors.get(i);
            if (vector == null) {
                throw new EmbeddingException("Embedding vector at index " + i + " is null", false);
            }
            if (vector.length != expectedDimension) {
                throw new EmbeddingException("Embedding dimension mismatch at index " + i, false);
            }
        }
    }

    private void markFailed(DocumentEntity doc, String errorMessage) {
        doc.setStatus(DocumentStatus.FAILED.name());
        doc.setErrorMessage(truncateSafe(errorMessage));
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
    }

    public List<DocumentChunkEntity> findChunksByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentChunkEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentChunkEntity::getDocumentId, documentId);
        wrapper.orderByAsc(DocumentChunkEntity::getChunkIndex);
        return documentChunkMapper.selectList(wrapper);
    }

    public List<DocumentEntity> listByKnowledgeBase(Long userId, Long knowledgeBaseId, String status) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentEntity::getUserId, userId);
        wrapper.eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(DocumentEntity::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(DocumentEntity::getCreatedAt);
        return documentMapper.selectList(wrapper);
    }

    public DocumentEntity findByIdAndUserId(Long documentId, Long userId) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentEntity::getId, documentId);
        wrapper.eq(DocumentEntity::getUserId, userId);
        return documentMapper.selectOne(wrapper);
    }

    public DocumentEntity findById(Long documentId) {
        return documentMapper.selectById(documentId);
    }

    private DocumentParser selectParser(String contentType, String filename) {
        for (DocumentParser parser : parsers) {
            if (parser.supports(contentType, filename)) {
                return parser;
            }
        }
        return null;
    }

    private String normalizeText(String text) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.trim();
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized;
    }

    private String truncateSafe(String message) {
        if (message == null) return null;
        if (message.length() <= 500) return message;
        return message.substring(0, 500);
    }

    void updateKnowledgeBaseAfterFailure(Long userId, Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentEntity::getUserId, userId);
        wrapper.eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.eq(DocumentEntity::getStatus, DocumentStatus.READY.name());
        Long readyCount = documentMapper.selectCount(wrapper);
        String nextStatus = readyCount != null && readyCount > 0
                ? KnowledgeBaseStatus.READY.name()
                : KnowledgeBaseStatus.FAILED.name();
        knowledgeBaseService.updateStatus(knowledgeBaseId, nextStatus);
    }

    public void deleteDocument(Long userId, Long documentId) {
        DocumentEntity doc = findByIdAndUserId(documentId, userId);
        if (doc == null) {
            DocumentEntity any = findById(documentId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND);
        }

        DocumentProcessingTaskEntity task = taskService.findByDocumentId(documentId);
        if (task != null && DocumentProcessingTaskStatus.PROCESSING.name().equals(task.getStatus())) {
            throw new BusinessException("DOCUMENT_PROCESSING",
                    "Document is currently being processed and cannot be deleted", HttpStatus.CONFLICT);
        }

        if (task != null && (DocumentProcessingTaskStatus.PENDING.name().equals(task.getStatus())
                || DocumentProcessingTaskStatus.RETRYABLE.name().equals(task.getStatus()))) {
            taskService.cancelTask(task.getId());
        }

        String storageKey = doc.getStoragePath();
        if (storageKey != null && !storageKey.isBlank()) {
            fileStorageService.delete(storageKey);
        }

        Long kbId = doc.getKnowledgeBaseId();

        clearChunksAndEmbeddings(documentId);

        documentMapper.deleteById(documentId);
        log.info("Document deleted: id={}, kbId={}, storageKey={}", documentId, kbId, storageKey);

        updateKbStatusAfterDocumentChange(userId, kbId);
    }

    public void deleteKnowledgeBase(Long userId, Long kbId) {
        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(kbId, userId);
        if (kb == null) {
            KnowledgeBaseEntity any = knowledgeBaseService.findById(kbId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Knowledge base not found", HttpStatus.NOT_FOUND);
        }

        knowledgeBaseService.checkNotReferencedByAnyApp(kbId, userId);

        if (taskService.hasProcessingTask(kbId)) {
            throw new BusinessException("DOCUMENT_PROCESSING",
                    "A document in this knowledge base is currently being processed", HttpStatus.CONFLICT);
        }

        List<DocumentProcessingTaskEntity> queuedTasks = taskService.findPendingOrRetryableByKbId(kbId);
        taskService.cancelTasks(queuedTasks);

        LambdaQueryWrapper<DocumentEntity> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(DocumentEntity::getKnowledgeBaseId, kbId);
        List<DocumentEntity> documents = documentMapper.selectList(docWrapper);

        for (DocumentEntity doc : documents) {
            String storageKey = doc.getStoragePath();
            if (storageKey != null && !storageKey.isBlank()) {
                fileStorageService.delete(storageKey);
            }

            clearChunksAndEmbeddings(doc.getId());

            documentMapper.deleteById(doc.getId());
            log.info("Document deleted in KB cleanup: id={}, kbId={}, storageKey={}",
                    doc.getId(), kbId, storageKey);
        }

        knowledgeBaseService.deleteKbRow(kbId);
        log.info("Knowledge base deleted: id={}, userId={}", kbId, userId);
    }

    private void updateKbStatusAfterDocumentChange(Long userId, Long kbId) {
        LambdaQueryWrapper<DocumentEntity> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.eq(DocumentEntity::getUserId, userId);
        allWrapper.eq(DocumentEntity::getKnowledgeBaseId, kbId);
        Long totalCount = documentMapper.selectCount(allWrapper);

        if (totalCount == null || totalCount == 0) {
            knowledgeBaseService.updateStatus(kbId, KnowledgeBaseStatus.EMPTY.name());
            return;
        }

        LambdaQueryWrapper<DocumentEntity> readyWrapper = new LambdaQueryWrapper<>();
        readyWrapper.eq(DocumentEntity::getUserId, userId);
        readyWrapper.eq(DocumentEntity::getKnowledgeBaseId, kbId);
        readyWrapper.eq(DocumentEntity::getStatus, DocumentStatus.READY.name());
        Long readyCount = documentMapper.selectCount(readyWrapper);

        if (readyCount != null && readyCount > 0) {
            knowledgeBaseService.updateStatus(kbId, KnowledgeBaseStatus.READY.name());
            return;
        }

        knowledgeBaseService.updateStatus(kbId, KnowledgeBaseStatus.FAILED.name());
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

}
