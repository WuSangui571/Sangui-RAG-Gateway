package com.sangui.raggateway.apikey;

import com.sangui.raggateway.apikey.vo.ApiKeyDetectionVO;
import com.sangui.raggateway.apikey.vo.ApiKeyVO;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/api-keys")
@Profile("!test")
public class ApiKeyAdminController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAdminController.class);

    private final ApiKeyService apiKeyService;
    private final AppService appService;

    public ApiKeyAdminController(ApiKeyService apiKeyService, AppService appService) {
        this.apiKeyService = apiKeyService;
        this.appService = appService;
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

    @PostMapping("/{id}/enable")
    public ApiResponse<ApiKeyVO> enableKey(
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

        ApiKeyEntity updated = apiKeyService.enable(id, userId);
        if (updated == null) {
            throw new BusinessException("NOT_FOUND", "API key not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(ApiKeyVO.from(updated));
    }

    @PostMapping("/{id}/detect")
    public ApiResponse<ApiKeyDetectionVO> detectKey(
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

        boolean keyUsable = apiKeyService.isValid(key);
        AppEntity app = appService.findById(key.getAppId());
        boolean appEnabled = app != null
                && userId.equals(app.getUserId())
                && appService.isEnabled(app);
        boolean usable = keyUsable && appEnabled;

        ApiKeyDetectionVO vo = new ApiKeyDetectionVO();
        vo.setKeyId(key.getId());
        vo.setAppId(key.getAppId());
        vo.setUsable(usable);
        vo.setStatus(key.getStatus());
        vo.setAppEnabled(appEnabled);
        vo.setExpiresAt(key.getExpiresAt());
        vo.setCheckedAt(LocalDateTime.now());

        return ApiResponse.success(vo);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }
    }
}
