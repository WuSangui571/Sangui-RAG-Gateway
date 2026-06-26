package com.sangui.raggateway.knowledge;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.document.DocumentService;
import com.sangui.raggateway.knowledge.dto.CreateKnowledgeBaseDTO;
import com.sangui.raggateway.knowledge.vo.KnowledgeBaseVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
@Profile("!test")
public class KnowledgeBaseAdminController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseAdminController.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentService documentService;

    public KnowledgeBaseAdminController(KnowledgeBaseService knowledgeBaseService,
                                         DocumentService documentService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentService = documentService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(@RequestBody CreateKnowledgeBaseDTO dto) {
        Long userId = getRequiredUserId();
        KnowledgeBaseEntity entity = knowledgeBaseService.create(
                userId, dto.getName(), dto.getEmbeddingModel(), dto.getEmbeddingDimension());
        return ApiResponse.success(KnowledgeBaseVO.from(entity));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list(@RequestParam(required = false) String status) {
        Long userId = getRequiredUserId();
        if (status != null && !status.isBlank() && !KnowledgeBaseStatus.isValid(status)) {
            throw new BusinessException("INVALID_REQUEST", "Invalid status filter");
        }
        List<KnowledgeBaseEntity> entities = knowledgeBaseService.listByUserId(userId, status);
        List<KnowledgeBaseVO> vos = entities.stream().map(KnowledgeBaseVO::from).toList();
        return ApiResponse.success(vos);
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> detail(@PathVariable Long id) {
        Long userId = getRequiredUserId();

        KnowledgeBaseEntity entity = knowledgeBaseService.findByIdAndUserId(id, userId);
        if (entity == null) {
            KnowledgeBaseEntity any = knowledgeBaseService.findById(id);
            if (any != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "Knowledge base not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(KnowledgeBaseVO.from(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = getRequiredUserId();
        documentService.deleteKnowledgeBase(userId, id);
        return ApiResponse.success(null);
    }

    private Long getRequiredUserId() {
        Long userId = AdminAuthContextHolder.getUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
