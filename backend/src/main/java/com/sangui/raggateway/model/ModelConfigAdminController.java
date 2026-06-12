package com.sangui.raggateway.model;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.model.dto.CreateModelConfigDTO;
import com.sangui.raggateway.model.dto.UpdateModelConfigDTO;
import com.sangui.raggateway.model.vo.ModelConfigVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/model-configs")
@Profile("!test")
public class ModelConfigAdminController {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigAdminController.class);

    private final ModelConfigService modelConfigService;
    private final ModelConfigCheckService modelConfigCheckService;

    public ModelConfigAdminController(ModelConfigService modelConfigService,
                                      ModelConfigCheckService modelConfigCheckService) {
        this.modelConfigService = modelConfigService;
        this.modelConfigCheckService = modelConfigCheckService;
    }

    @PostMapping
    public ApiResponse<ModelConfigVO> create(@RequestHeader("X-Admin-User-Id") Long userId,
                                              @RequestBody CreateModelConfigDTO dto) {
        validateAdminUserId(userId);
        try {
            ModelConfigVO vo = modelConfigService.createAdminConfig(userId, dto);
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(@RequestHeader("X-Admin-User-Id") Long userId,
                                              @PathVariable Long id,
                                              @RequestBody UpdateModelConfigDTO dto) {
        validateAdminUserId(userId);
        ModelConfigEntity target = requireOwnedConfig(id, userId);
        try {
            ModelConfigVO vo = modelConfigService.updateAdminConfig(target.getId(), userId, dto);
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelConfigVO> detail(@RequestHeader("X-Admin-User-Id") Long userId,
                                              @PathVariable Long id) {
        validateAdminUserId(userId);
        requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.findAdminDetail(id, userId);
        return ApiResponse.success(vo);
    }

    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list(@RequestHeader("X-Admin-User-Id") Long userId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String capability) {
        validateAdminUserId(userId);
        if (status != null && !status.equals("ENABLED") && !status.equals("DISABLED")) {
            throw new BusinessException("INVALID_REQUEST", "Invalid status filter");
        }
        if (capability != null
                && !capability.equalsIgnoreCase("CHAT")
                && !capability.equalsIgnoreCase("EMBEDDING")) {
            throw new BusinessException("INVALID_REQUEST",
                    "Invalid capability filter. Must be CHAT or EMBEDDING.");
        }
        List<ModelConfigVO> list = modelConfigService.listAdminConfigs(userId, status, capability);
        return ApiResponse.success(list);
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<ModelConfigVO> disable(@RequestHeader("X-Admin-User-Id") Long userId,
                                               @PathVariable Long id) {
        validateAdminUserId(userId);
        requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.disableAdminConfig(id, userId);
        return ApiResponse.success(vo);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<ModelConfigVO> enable(@RequestHeader("X-Admin-User-Id") Long userId,
                                              @PathVariable Long id) {
        validateAdminUserId(userId);
        requireOwnedConfig(id, userId);
        try {
            ModelConfigVO vo = modelConfigService.enableAdminConfig(id, userId);
            return ApiResponse.success(vo);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @PostMapping("/check")
    public ApiResponse<ModelConfigCheckResult> checkUnsaved(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @RequestBody ModelConfigCheckRequest request) {
        validateAdminUserId(userId);
        try {
            ModelConfigCheckResult result = modelConfigCheckService.checkUnsavedConfig(userId, request);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @PostMapping("/{id}/check")
    public ApiResponse<ModelConfigCheckResult> checkSaved(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id,
            @RequestBody ModelConfigCheckRequest request) {
        validateAdminUserId(userId);
        requireOwnedConfig(id, userId);
        try {
            ModelConfigCheckResult result = modelConfigCheckService.checkSavedConfig(userId, id, request);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_REQUEST", e.getMessage());
        }
    }

    @GetMapping("/chat-capable")
    public ApiResponse<List<ModelConfigVO>> listChatCapable(
            @RequestHeader("X-Admin-User-Id") Long userId) {
        validateAdminUserId(userId);
        List<ModelConfigVO> list = modelConfigService.listEnabledChatCapableConfigs(userId);
        return ApiResponse.success(list);
    }

    private ModelConfigEntity requireOwnedConfig(Long id, Long userId) {
        ModelConfigEntity entity = modelConfigService.findById(id);
        if (entity == null) {
            throw new BusinessException("NOT_FOUND", "Model config not found", HttpStatus.NOT_FOUND);
        }
        if (!entity.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Access denied for this model config", HttpStatus.FORBIDDEN);
        }
        return entity;
    }

    private void validateAdminUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }
    }
}
