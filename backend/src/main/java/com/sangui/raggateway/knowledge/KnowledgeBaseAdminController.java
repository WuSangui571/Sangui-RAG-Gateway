package com.sangui.raggateway.knowledge;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
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

    public KnowledgeBaseAdminController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @RequestBody CreateKnowledgeBaseDTO dto) {
        validateUserId(userId);
        try {
            KnowledgeBaseEntity entity = knowledgeBaseService.create(
                    userId, dto.getName(), dto.getEmbeddingModel(), dto.getEmbeddingDimension());
            return ApiResponse.success(KnowledgeBaseVO.from(entity));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseVO>> list(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @RequestParam(required = false) String status) {
        validateUserId(userId);
        if (status != null && !status.isBlank() && !KnowledgeBaseStatus.isValid(status)) {
            throw new BusinessException("INVALID_REQUEST", "Invalid status filter");
        }
        List<KnowledgeBaseEntity> entities = knowledgeBaseService.listByUserId(userId, status);
        List<KnowledgeBaseVO> vos = entities.stream().map(KnowledgeBaseVO::from).toList();
        return ApiResponse.success(vos);
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> detail(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

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

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }
    }
}
