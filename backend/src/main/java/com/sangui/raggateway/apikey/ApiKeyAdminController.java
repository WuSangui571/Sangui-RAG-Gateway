package com.sangui.raggateway.apikey;

import com.sangui.raggateway.apikey.vo.ApiKeyVO;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/api-keys")
@Profile("!test")
public class ApiKeyAdminController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAdminController.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<ApiKeyVO> disableKey(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

        ApiKeyEntity key = apiKeyService.findById(id);
        if (key == null) {
            throw new BusinessException("NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        if (!key.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }

        ApiKeyEntity updated = apiKeyService.disable(id, userId);
        if (updated == null) {
            throw new BusinessException("NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(ApiKeyVO.from(updated));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<ApiKeyVO> revokeKey(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

        ApiKeyEntity key = apiKeyService.findById(id);
        if (key == null) {
            throw new BusinessException("NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        if (!key.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }

        ApiKeyEntity updated = apiKeyService.revoke(id, userId);
        if (updated == null) {
            throw new BusinessException("NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(ApiKeyVO.from(updated));
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }
    }
}
