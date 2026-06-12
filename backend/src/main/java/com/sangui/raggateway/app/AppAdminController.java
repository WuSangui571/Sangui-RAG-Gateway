package com.sangui.raggateway.app;

import com.sangui.raggateway.app.dto.BindAppDefaultModelConfigDTO;
import com.sangui.raggateway.app.dto.BindAppDefaultKnowledgeBaseDTO;
import com.sangui.raggateway.app.dto.CreateAppDTO;
import com.sangui.raggateway.app.vo.AppReadinessVO;
import com.sangui.raggateway.app.vo.AppVO;
import com.sangui.raggateway.app.vo.BindAppDefaultModelConfigVO;
import com.sangui.raggateway.app.vo.BindAppDefaultKnowledgeBaseVO;
import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.apikey.dto.CreateApiKeyDTO;
import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
import com.sangui.raggateway.apikey.vo.ApiKeyCreateVO;
import com.sangui.raggateway.apikey.vo.ApiKeyVO;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/apps")
@Profile("!test")
public class AppAdminController {

    private static final Logger log = LoggerFactory.getLogger(AppAdminController.class);

    private final AppService appService;
    private final ModelConfigService modelConfigService;
    private final ApiKeyService apiKeyService;
    private final KnowledgeBaseService knowledgeBaseService;

    public AppAdminController(AppService appService, ModelConfigService modelConfigService,
                              ApiKeyService apiKeyService, KnowledgeBaseService knowledgeBaseService) {
        this.appService = appService;
        this.modelConfigService = modelConfigService;
        this.apiKeyService = apiKeyService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<AppVO> createApp(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @RequestBody CreateAppDTO dto) {
        validateUserId(userId);
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "name is required");
        }

        AppEntity app = appService.create(dto.getName().trim(), userId);
        return ApiResponse.success(AppVO.from(app));
    }

    @GetMapping
    public ApiResponse<List<AppVO>> listApps(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @RequestParam(required = false) String status) {
        validateUserId(userId);
        if (status != null && !status.isBlank()) {
            try {
                AppStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_REQUEST", "Invalid status filter");
            }
        }

        List<AppEntity> apps = appService.listByUserId(userId, status);
        List<AppVO> vos = apps.stream().map(AppVO::from).toList();
        return ApiResponse.success(vos);
    }

    @GetMapping("/{id}")
    public ApiResponse<AppVO> getApp(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

        AppEntity app = appService.findByIdAndUserId(id, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(id);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(AppVO.from(app));
    }

    @GetMapping("/{appId}/readiness")
    public ApiResponse<AppReadinessVO> getReadiness(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long appId) {
        validateUserId(userId);

        AppEntity app = appService.findByIdAndUserId(appId, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(appId);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }

        AppReadinessVO readiness = appService.assembleReadiness(appId, userId);
        return ApiResponse.success(readiness);
    }

    @PostMapping("/{appId}/api-keys")
    public ApiResponse<ApiKeyCreateVO> createApiKey(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long appId,
            @RequestBody CreateApiKeyDTO dto) {
        validateUserId(userId);

        AppEntity app = appService.findByIdAndUserId(appId, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(appId);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }

        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "name is required");
        }

        CreateApiKeyResult result = apiKeyService.create(appId, userId, dto.getName().trim(), dto.getExpiresAt());
        return ApiResponse.success(ApiKeyCreateVO.of(result.getPlaintextKey(), result.getEntity()));
    }

    @GetMapping("/{appId}/api-keys")
    public ApiResponse<List<ApiKeyVO>> listApiKeys(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long appId) {
        validateUserId(userId);

        AppEntity app = appService.findByIdAndUserId(appId, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(appId);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }

        List<ApiKeyEntity> keys = apiKeyService.listByAppIdAndUserId(appId, userId);
        List<ApiKeyVO> vos = keys.stream().map(ApiKeyVO::from).toList();
        return ApiResponse.success(vos);
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
            throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }

        Long modelConfigId = dto == null ? null : dto.getModelConfigId();
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
        if (!modelConfigService.isChatCapable(modelConfig)) {
            throw new BusinessException("MODEL_CONFIG_NOT_READY",
                    "Model config is not chat-capable. Only CHAT configs can be bound as the default model.",
                    HttpStatus.BAD_REQUEST);
        }

        AppEntity updated = appService.bindDefaultModelConfig(appId, modelConfigId, userId);
        if (updated == null) {
            throw new BusinessException("MODEL_CONFIG_NOT_READY", "Model config is not available for this app", HttpStatus.BAD_REQUEST);
        }

        BindAppDefaultModelConfigVO vo = new BindAppDefaultModelConfigVO(
                updated.getId(), updated.getUserId(), updated.getDefaultModelConfigId());
        return ApiResponse.success(vo);
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<AppVO> disableApp(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

        AppEntity app = appService.disableApp(id, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(id);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(AppVO.from(app));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<AppVO> enableApp(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long id) {
        validateUserId(userId);

        AppEntity app = appService.enableApp(id, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(id);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(AppVO.from(app));
    }

    @PutMapping("/{appId}/knowledge-base")
    public ApiResponse<BindAppDefaultKnowledgeBaseVO> bindDefaultKnowledgeBase(
            @RequestHeader("X-Admin-User-Id") Long userId,
            @PathVariable Long appId,
            @RequestBody BindAppDefaultKnowledgeBaseDTO dto) {
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

        Long knowledgeBaseId = dto == null ? null : dto.getKnowledgeBaseId();
        if (knowledgeBaseId == null || knowledgeBaseId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "knowledge_base_id is required");
        }

        KnowledgeBaseEntity kb = knowledgeBaseService.findById(knowledgeBaseId);
        if (kb == null) {
            throw new BusinessException("NOT_FOUND", "Knowledge base not found", HttpStatus.NOT_FOUND);
        }
        if (!kb.getUserId().equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
        }
        if (!KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            throw new BusinessException("KNOWLEDGE_BASE_NOT_READY",
                    "Knowledge base is not ready", HttpStatus.BAD_REQUEST);
        }

        AppEntity updated = appService.bindDefaultKnowledgeBase(appId, knowledgeBaseId, userId);
        if (updated == null) {
            throw new BusinessException("KNOWLEDGE_BASE_NOT_READY",
                    "Knowledge base is not available for this app", HttpStatus.BAD_REQUEST);
        }

        BindAppDefaultKnowledgeBaseVO vo = new BindAppDefaultKnowledgeBaseVO(
                updated.getId(), updated.getUserId(), updated.getDefaultKnowledgeBaseId());
        return ApiResponse.success(vo);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "X-Admin-User-Id must be a positive long");
        }
    }
}
