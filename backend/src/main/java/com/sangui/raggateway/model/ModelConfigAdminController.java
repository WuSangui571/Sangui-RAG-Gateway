package com.sangui.raggateway.model;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
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
    public ApiResponse<ModelConfigVO> create(@RequestBody CreateModelConfigDTO dto) {
        Long userId = getRequiredUserId();
        ModelConfigVO vo = modelConfigService.createAdminConfig(userId, dto);
        return ApiResponse.success(vo);
    }

    @PutMapping("/{id}")
    public ApiResponse<ModelConfigVO> update(@PathVariable Long id,
                                              @RequestBody UpdateModelConfigDTO dto) {
        Long userId = getRequiredUserId();
        ModelConfigEntity target = requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.updateAdminConfig(target.getId(), userId, dto);
        return ApiResponse.success(vo);
    }

    @GetMapping("/{id}")
    public ApiResponse<ModelConfigVO> detail(@PathVariable Long id) {
        Long userId = getRequiredUserId();
        requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.findAdminDetail(id, userId);
        return ApiResponse.success(vo);
    }

    @GetMapping
    public ApiResponse<List<ModelConfigVO>> list(@RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String capability) {
        Long userId = getRequiredUserId();
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
    public ApiResponse<ModelConfigVO> disable(@PathVariable Long id) {
        Long userId = getRequiredUserId();
        requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.disableAdminConfig(id, userId);
        return ApiResponse.success(vo);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<ModelConfigVO> enable(@PathVariable Long id) {
        Long userId = getRequiredUserId();
        requireOwnedConfig(id, userId);
        ModelConfigVO vo = modelConfigService.enableAdminConfig(id, userId);
        return ApiResponse.success(vo);
    }

    @PostMapping("/check")
    public ApiResponse<ModelConfigCheckResult> checkUnsaved(@RequestBody ModelConfigCheckRequest request) {
        Long userId = getRequiredUserId();
        ModelConfigCheckResult result = modelConfigCheckService.checkUnsavedConfig(userId, request);
        return ApiResponse.success(result);
    }

    @PostMapping("/{id}/check")
    public ApiResponse<ModelConfigCheckResult> checkSaved(@PathVariable Long id,
                                                           @RequestBody ModelConfigCheckRequest request) {
        Long userId = getRequiredUserId();
        requireOwnedConfig(id, userId);
        ModelConfigCheckResult result = modelConfigCheckService.checkSavedConfig(userId, id, request);
        return ApiResponse.success(result);
    }

    @GetMapping("/chat-capable")
    public ApiResponse<List<ModelConfigVO>> listChatCapable() {
        Long userId = getRequiredUserId();
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

    private Long getRequiredUserId() {
        Long userId = AdminAuthContextHolder.getUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
