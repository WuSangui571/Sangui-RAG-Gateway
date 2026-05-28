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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!test")
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final FileStorageService fileStorageService;
    private final TextChunker textChunker;
    private final List<DocumentParser> parsers;
    private final DocumentProperties documentProperties;

    public DocumentService(DocumentMapper documentMapper,
                           DocumentChunkMapper documentChunkMapper,
                           KnowledgeBaseService knowledgeBaseService,
                           FileStorageService fileStorageService,
                           TextChunker textChunker,
                           List<DocumentParser> parsers,
                           DocumentProperties documentProperties) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.fileStorageService = fileStorageService;
        this.textChunker = textChunker;
        this.parsers = parsers;
        this.documentProperties = documentProperties;
    }

    @Transactional
    public DocumentEntity uploadAndProcess(Long userId, Long knowledgeBaseId,
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

        InputStream storageStream = new ByteArrayInputStream(fileContent);
        StoredFile storedFile = fileStorageService.save("knowledge", knowledgeBaseId, safeFilename, storageStream);

        DocumentEntity doc = new DocumentEntity();
        doc.setUserId(userId);
        doc.setKnowledgeBaseId(knowledgeBaseId);
        doc.setOriginalFilename(safeFilename);
        doc.setContentType(contentType);
        doc.setFileSize(storedFile.getFileSize());
        doc.setStoragePath(storedFile.getStoragePath());
        doc.setStatus(DocumentStatus.UPLOADED.name());
        doc.setChunkCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        log.info("Document created: id={}, kbId={}, filename={}, status=UPLOADED",
                doc.getId(), knowledgeBaseId, safeFilename);

        knowledgeBaseService.updateStatus(knowledgeBaseId, KnowledgeBaseStatus.PROCESSING.name());

        doc.setStatus(DocumentStatus.PARSING.name());
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        try {
            DocumentParser parser = selectParser(contentType, safeFilename);
            if (parser == null) {
                throw new IllegalArgumentException("No parser found for file: " + safeFilename);
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
                chunk.setMetadata("{\"parser\":\"" + parsed.getParserName() + "\",\"source\":\"" + escapeJson(safeFilename) + "\"}");
                chunk.setCreatedAt(LocalDateTime.now());
                chunk.setUpdatedAt(LocalDateTime.now());
                documentChunkMapper.insertChunk(chunk);
            }

            doc.setStatus(DocumentStatus.PARSED.name());
            doc.setChunkCount(chunks.size());
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);

            knowledgeBaseService.updateStatus(knowledgeBaseId, KnowledgeBaseStatus.READY.name());
            log.info("Document processed successfully: id={}, chunks={}", doc.getId(), chunks.size());
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

    private void updateKnowledgeBaseAfterFailure(Long userId, Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentEntity::getUserId, userId);
        wrapper.eq(DocumentEntity::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.eq(DocumentEntity::getStatus, DocumentStatus.PARSED.name());
        Long parsedCount = documentMapper.selectCount(wrapper);
        String nextStatus = parsedCount != null && parsedCount > 0
                ? KnowledgeBaseStatus.READY.name()
                : KnowledgeBaseStatus.FAILED.name();
        knowledgeBaseService.updateStatus(knowledgeBaseId, nextStatus);
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
