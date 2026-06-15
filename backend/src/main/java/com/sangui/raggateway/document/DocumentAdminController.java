package com.sangui.raggateway.document;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.document.config.DocumentProperties;
import com.sangui.raggateway.document.vo.DocumentVO;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@Profile("!test")
public class DocumentAdminController {

    private static final Logger log = LoggerFactory.getLogger(DocumentAdminController.class);

    private final DocumentService documentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentProperties documentProperties;

    public DocumentAdminController(DocumentService documentService,
                                    KnowledgeBaseService knowledgeBaseService,
                                    DocumentProperties documentProperties) {
        this.documentService = documentService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentProperties = documentProperties;
    }

    @PostMapping("/api/admin/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<DocumentVO> upload(
            @PathVariable Long knowledgeBaseId,
            @RequestParam("file") MultipartFile file) {
        Long userId = getRequiredUserId();

        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(knowledgeBaseId, userId);
        if (kb == null) {
            KnowledgeBaseEntity any = knowledgeBaseService.findById(knowledgeBaseId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Knowledge base not found", HttpStatus.NOT_FOUND);
        }

        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new BusinessException("INVALID_REQUEST", "File must not be empty");
        }
        if (file.getSize() > documentProperties.getMaxFileSizeBytes()) {
            throw new BusinessException("INVALID_REQUEST", "File exceeds max-file-size-bytes");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "Filename must not be blank");
        }
        if (!DocumentUploadRules.isSupportedFilename(originalFilename)) {
            throw new BusinessException("INVALID_REQUEST", "Unsupported file type. Only .txt, .md, and .markdown files are supported.");
        }
        if (!DocumentUploadRules.isSupportedContentType(file.getContentType())) {
            throw new BusinessException("INVALID_REQUEST", "Unsupported content type");
        }

        try {
            byte[] fileContent = file.getBytes();
            DocumentEntity doc = documentService.uploadAndProcess(
                    userId, knowledgeBaseId, originalFilename,
                    file.getContentType(), fileContent);
            return ApiResponse.success(DocumentVO.from(doc));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            throw new BusinessException("INTERNAL_ERROR", "Failed to process uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/api/admin/knowledge-bases/{knowledgeBaseId}/documents")
    public ApiResponse<List<DocumentVO>> listDocuments(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(required = false) String status) {
        Long userId = getRequiredUserId();

        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(knowledgeBaseId, userId);
        if (kb == null) {
            KnowledgeBaseEntity any = knowledgeBaseService.findById(knowledgeBaseId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Knowledge base not found", HttpStatus.NOT_FOUND);
        }

        if (status != null && !status.isBlank() && !DocumentStatus.isValid(status)) {
            throw new BusinessException("INVALID_REQUEST", "Invalid status filter");
        }

        List<DocumentEntity> documents = documentService.listByKnowledgeBase(userId, knowledgeBaseId, status);
        List<DocumentVO> vos = documents.stream().map(DocumentVO::from).toList();
        return ApiResponse.success(vos);
    }

    @GetMapping("/api/admin/documents/{documentId}")
    public ApiResponse<DocumentVO> getDocument(@PathVariable Long documentId) {
        Long userId = getRequiredUserId();

        DocumentEntity doc = documentService.findByIdAndUserId(documentId, userId);
        if (doc == null) {
            DocumentEntity any = documentService.findById(documentId);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(DocumentVO.from(doc));
    }

    private Long getRequiredUserId() {
        Long userId = AdminAuthContextHolder.getUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
