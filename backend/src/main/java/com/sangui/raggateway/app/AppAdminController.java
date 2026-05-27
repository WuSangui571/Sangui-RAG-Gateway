package com.sangui.raggateway.app;

import com.sangui.raggateway.app.dto.BindAppDefaultModelConfigDTO;
import com.sangui.raggateway.app.vo.BindAppDefaultModelConfigVO;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/apps")
@Profile("!test")
public class AppAdminController {

    private static final Logger log = LoggerFactory.getLogger(AppAdminController.class);

    private final AppService appService;
    private final ModelConfigService modelConfigService;

    public AppAdminController(AppService appService, ModelConfigService modelConfigService) {
        this.appService = appService;
        this.modelConfigService = modelConfigService;
    }

    @PutMapping("/{appId}/default-model-config")
    public ApiResponse<BindAppDefaultModelConfigVO> bindDefaultModelConfig(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long appId,
            @RequestBody BindAppDefaultModelConfigDTO dto) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }

        AppEntity app = appService.findById(appId);
        if (app == null) {
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }
        if (!app.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Access denied for this app", HttpStatus.FORBIDDEN);
        }

        Long modelConfigId = dto.getModelConfigId();
        if (modelConfigId == null) {
            throw new BusinessException("INVALID_REQUEST", "modelConfigId is required");
        }

        ModelConfigEntity modelConfig = modelConfigService.findById(modelConfigId);
        if (modelConfig == null) {
            throw new BusinessException("NOT_FOUND", "Model config not found", HttpStatus.NOT_FOUND);
        }
        if (!modelConfig.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Model config does not belong to this user", HttpStatus.FORBIDDEN);
        }

        AppEntity updated = appService.bindDefaultModelConfig(appId, modelConfigId, userId);
        if (updated == null) {
            throw new BusinessException("MODEL_CONFIG_NOT_READY", "Model config is not available for this app", HttpStatus.BAD_REQUEST);
        }

        BindAppDefaultModelConfigVO vo = new BindAppDefaultModelConfigVO(
                updated.getId(), updated.getUserId(), updated.getDefaultModelConfigId());
        return ApiResponse.success(vo);
    }
}
