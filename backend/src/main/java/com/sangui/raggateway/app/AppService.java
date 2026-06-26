package com.sangui.raggateway.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.config.AppRetrievalProperties;
import com.sangui.raggateway.app.vo.AppReadinessCheckVO;
import com.sangui.raggateway.app.vo.AppReadinessVO;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import com.sangui.raggateway.model.ModelConfigStatus;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!test")
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppMapper appMapper;
    private final ModelConfigService modelConfigService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ApiKeyService apiKeyService;
    private final AppRetrievalProperties retrievalProperties;

    public AppService(AppMapper appMapper, ModelConfigService modelConfigService,
                      KnowledgeBaseService knowledgeBaseService, ApiKeyService apiKeyService,
                      AppRetrievalProperties retrievalProperties) {
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.apiKeyService = apiKeyService;
        this.retrievalProperties = retrievalProperties;
    }

    @Transactional
    public AppEntity create(String name, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive long");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        AppEntity app = new AppEntity();
        app.setName(name.trim());
        app.setUserId(userId);
        app.setStatus(AppStatus.ENABLED.name());
        app.setRetrievalTopK(retrievalProperties.getDefaultTopK());
        app.setRetrievalSimilarityThreshold(retrievalProperties.getDefaultSimilarityThreshold());
        app.setRetrievalMaxContextChunks(retrievalProperties.getDefaultMaxContextChunks());
        app.setRetrievalMaxContextChars(retrievalProperties.getDefaultMaxContextChars());
        app.setRetrievalMaxSingleChunkChars(retrievalProperties.getDefaultMaxSingleChunkChars());
        app.setNoHitPolicy(AppRetrievalConfig.STRICT_RAG_NO_HIT_POLICY);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.insert(app);
        return app;
    }

    public AppEntity findById(Long id) {
        return appMapper.selectById(id);
    }

    public boolean isEnabled(AppEntity app) {
        return app != null && AppStatus.ENABLED.name().equals(app.getStatus());
    }

    public ModelConfigEntity resolveDefaultModelConfig(AppEntity app) {
        if (app == null || app.getDefaultModelConfigId() == null) {
            return null;
        }
        return modelConfigService.findEnabledByIdAndUserId(app.getDefaultModelConfigId(), app.getUserId());
    }

    public AppEntity findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppEntity::getId, id);
        wrapper.eq(AppEntity::getUserId, userId);
        return appMapper.selectOne(wrapper);
    }

    public List<AppEntity> listByUserId(Long userId, String status) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppEntity::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(AppEntity::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(AppEntity::getCreatedAt);
        return appMapper.selectList(wrapper);
    }

    @Transactional
    public AppEntity bindDefaultModelConfig(Long appId, Long modelConfigId, Long userId) {
        AppEntity app = findById(appId);
        if (app == null || !app.getUserId().equals(userId)) {
            return null;
        }

        ModelConfigEntity modelConfig = modelConfigService.findEnabledByIdAndUserId(modelConfigId, userId);
        if (modelConfig == null) {
            return null;
        }

        if (!modelConfigService.isChatCapable(modelConfig)) {
            return null;
        }

        if (modelConfig.getChatModel() == null || modelConfig.getChatModel().isBlank()) {
            return null;
        }

        app.setDefaultModelConfigId(modelConfigId);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    @Transactional
    public AppEntity bindDefaultKnowledgeBase(Long appId, Long knowledgeBaseId, Long userId) {
        AppEntity app = findById(appId);
        if (app == null || !app.getUserId().equals(userId)) {
            return null;
        }

        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(knowledgeBaseId, userId);
        if (kb == null) {
            return null;
        }

        if (!KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            return null;
        }

        app.setDefaultKnowledgeBaseId(knowledgeBaseId);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    @Transactional
    public AppEntity updateOutputCapture(Long appId, boolean enabled, Long userId) {
        AppEntity app = findByIdAndUserId(appId, userId);
        if (app == null) {
            return null;
        }
        app.setRequestLogOutputCaptureEnabled(enabled);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    @Transactional
    public AppEntity disableApp(Long id, Long userId) {
        AppEntity app = findByIdAndUserId(id, userId);
        if (app == null) {
            return null;
        }
        app.setStatus(AppStatus.DISABLED.name());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    @Transactional
    public AppEntity enableApp(Long id, Long userId) {
        AppEntity app = findByIdAndUserId(id, userId);
        if (app == null) {
            return null;
        }
        app.setStatus(AppStatus.ENABLED.name());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    public KnowledgeBaseEntity resolveDefaultKnowledgeBase(AppEntity app) {
        if (app == null || app.getDefaultKnowledgeBaseId() == null) {
            return null;
        }
        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(
                app.getDefaultKnowledgeBaseId(), app.getUserId());
        if (kb == null || !KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            return null;
        }
        return kb;
    }

    public AppRetrievalConfig resolveRetrievalConfig(AppEntity app) {
        if (app == null) {
            throw new IllegalArgumentException("app must not be null");
        }
        try {
            return AppRetrievalConfig.from(app);
        } catch (IllegalArgumentException e) {
            log.error("Invalid retrieval config for appId={}, reason={}", app.getId(), e.getMessage());
            throw e;
        }
    }

    public AppReadinessVO assembleReadiness(Long appId, Long userId) {
        AppEntity app = findByIdAndUserId(appId, userId);

        List<AppReadinessCheckVO> checks = new ArrayList<>();

        AppReadinessCheckVO appCheck = checkApp(app);
        checks.add(appCheck);

        AppReadinessCheckVO modelConfigCheck = checkDefaultModelConfig(app);
        checks.add(modelConfigCheck);

        AppReadinessCheckVO kbCheck = checkDefaultKnowledgeBase(app);
        checks.add(kbCheck);

        KnowledgeBaseEntity kb = resolveBoundKb(app);
        AppReadinessCheckVO kbStatusCheck = checkKnowledgeBaseStatus(kb);
        checks.add(kbStatusCheck);

        AppReadinessCheckVO activeKeyCheck = checkActiveApiKey(app, userId);
        checks.add(activeKeyCheck);

        AppReadinessCheckVO embeddingCheck = checkEmbeddingConfig(userId, kb);
        checks.add(embeddingCheck);

        AppReadinessStatus overall = computeOverallStatus(checks);

        return new AppReadinessVO(app != null ? app.getId() : appId,
                userId, overall, checks);
    }

    private AppReadinessCheckVO checkApp(AppEntity app) {
        if (app == null) {
            return new AppReadinessCheckVO("app", "App",
                    AppReadinessStatus.MISSING, "App not found or not accessible.", null);
        }
        if (!AppStatus.ENABLED.name().equals(app.getStatus())) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("app_status", app.getStatus());
            return new AppReadinessCheckVO("app", "App",
                    AppReadinessStatus.DISABLED, "App is disabled. Enable the app before running smoke tests.", metadata);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("app_status", app.getStatus());
        return new AppReadinessCheckVO("app", "App",
                AppReadinessStatus.READY, "App is enabled.", metadata);
    }

    private AppReadinessCheckVO checkDefaultModelConfig(AppEntity app) {
        if (app == null || app.getDefaultModelConfigId() == null) {
            return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                    AppReadinessStatus.MISSING, "No default model config is bound. Bind an enabled chat model config to this app.", null);
        }
        ModelConfigEntity config = modelConfigService.findById(app.getDefaultModelConfigId());
        if (config == null || !config.getUserId().equals(app.getUserId())) {
            return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                    AppReadinessStatus.MISSING, "Bound default model config is no longer available. Bind a new enabled chat model config.", null);
        }
        if (!ModelConfigStatus.ENABLED.name().equals(config.getStatus())) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("default_model_config_id", config.getId());
            metadata.put("provider_name", config.getProviderName());
            metadata.put("chat_model", config.getChatModel());
            return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                    AppReadinessStatus.DISABLED, "Bound default model config is disabled. Enable it before running smoke tests.", metadata);
        }
        if (!modelConfigService.isChatCapable(config)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("default_model_config_id", config.getId());
            metadata.put("provider_name", config.getProviderName());
            metadata.put("capability", config.getCapability());
            return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                    AppReadinessStatus.NOT_READY, "Bound default model config is not chat-capable. Bind an enabled CHAT config.", metadata);
        }
        if (config.getChatModel() == null || config.getChatModel().isBlank()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("default_model_config_id", config.getId());
            metadata.put("provider_name", config.getProviderName());
            return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                    AppReadinessStatus.NOT_READY, "Bound default model config is missing a chat model. Configure the chat model field.", metadata);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("default_model_config_id", config.getId());
        metadata.put("provider_name", config.getProviderName());
        metadata.put("chat_model", config.getChatModel());
        return new AppReadinessCheckVO("default_model_config", "Default Model Config",
                AppReadinessStatus.READY, "Default model config is enabled.", metadata);
    }

    private AppReadinessCheckVO checkDefaultKnowledgeBase(AppEntity app) {
        if (app == null || app.getDefaultKnowledgeBaseId() == null) {
            return new AppReadinessCheckVO("default_knowledge_base", "Default Knowledge Base",
                    AppReadinessStatus.MISSING, "No default knowledge base is bound. Bind a knowledge base to this app.", null);
        }
        KnowledgeBaseEntity kb = knowledgeBaseService.findById(app.getDefaultKnowledgeBaseId());
        if (kb == null || !kb.getUserId().equals(app.getUserId())) {
            return new AppReadinessCheckVO("default_knowledge_base", "Default Knowledge Base",
                    AppReadinessStatus.MISSING, "Bound default knowledge base is no longer available. Bind a new knowledge base.", null);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("default_knowledge_base_id", kb.getId());
        return new AppReadinessCheckVO("default_knowledge_base", "Default Knowledge Base",
                AppReadinessStatus.READY, "Default knowledge base is bound.", metadata);
    }

    private KnowledgeBaseEntity resolveBoundKb(AppEntity app) {
        if (app == null || app.getDefaultKnowledgeBaseId() == null) {
            return null;
        }
        KnowledgeBaseEntity kb = knowledgeBaseService.findById(app.getDefaultKnowledgeBaseId());
        if (kb == null || !kb.getUserId().equals(app.getUserId())) {
            return null;
        }
        return kb;
    }

    private AppReadinessCheckVO checkKnowledgeBaseStatus(KnowledgeBaseEntity kb) {
        if (kb == null) {
            return new AppReadinessCheckVO("knowledge_base_status", "Knowledge Base Status",
                    AppReadinessStatus.MISSING, "No bound knowledge base available.", null);
        }
        if (KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("knowledge_base_status", kb.getStatus());
            return new AppReadinessCheckVO("knowledge_base_status", "Knowledge Base Status",
                    AppReadinessStatus.READY, "Knowledge base is ready.", metadata);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledge_base_status", kb.getStatus());
        return new AppReadinessCheckVO("knowledge_base_status", "Knowledge Base Status",
                AppReadinessStatus.NOT_READY, "Knowledge base is not ready. Upload and process documents until status is READY. Current status: " + kb.getStatus() + ".", metadata);
    }

    private AppReadinessCheckVO checkActiveApiKey(AppEntity app, Long userId) {
        if (app == null) {
            return new AppReadinessCheckVO("active_api_key", "Active API Key",
                    AppReadinessStatus.MISSING, "App not accessible.", null);
        }
        List<ApiKeyEntity> keys = apiKeyService.listByAppIdAndUserId(app.getId(), userId);
        long activeCount = keys.stream().filter(apiKeyService::isValid).count();
        if (activeCount == 0) {
            if (keys.isEmpty()) {
                return new AppReadinessCheckVO("active_api_key", "Active API Key",
                        AppReadinessStatus.MISSING, "No API key exists. Create an active API key for this app.", null);
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("active_key_count", 0);
            return new AppReadinessCheckVO("active_api_key", "Active API Key",
                    AppReadinessStatus.DISABLED, "All API keys are disabled, revoked, or expired. Create a new active API key.", metadata);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("active_key_count", activeCount);
        return new AppReadinessCheckVO("active_api_key", "Active API Key",
                AppReadinessStatus.READY, "At least one active API key is available.", metadata);
    }

    private AppReadinessCheckVO checkEmbeddingConfig(Long userId, KnowledgeBaseEntity kb) {
        if (kb == null) {
            return new AppReadinessCheckVO("embedding_config", "Embedding Config",
                    AppReadinessStatus.MISSING, "No bound knowledge base. Bind a knowledge base first.", null);
        }
        ModelConfigEntity config = modelConfigService.findMatchingEmbeddingConfig(
                userId, kb.getEmbeddingModel(), kb.getEmbeddingDimension());
        if (config == null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("embedding_model", kb.getEmbeddingModel());
            metadata.put("embedding_dimension", kb.getEmbeddingDimension());
            return new AppReadinessCheckVO("embedding_config", "Embedding Config",
                    AppReadinessStatus.MISSING,
                    "No embedding config matches the KB's embedding model (" + kb.getEmbeddingModel()
                            + ") and dimension (" + kb.getEmbeddingDimension()
                            + "). Create an enabled model config with matching embedding settings.",
                    metadata);
        }
        boolean isEnabled = ModelConfigStatus.ENABLED.name().equals(config.getStatus());
        boolean hasKey = config.getApiKeyEncrypted() != null && !config.getApiKeyEncrypted().isBlank();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("embedding_config_id", config.getId());
        metadata.put("embedding_provider_name", config.getProviderName());
        metadata.put("embedding_model", config.getEmbeddingModel());
        metadata.put("embedding_dimension", config.getEmbeddingDimension());
        if (!isEnabled) {
            return new AppReadinessCheckVO("embedding_config", "Embedding Config",
                    AppReadinessStatus.DISABLED,
                    "Matching embedding config exists but is disabled. Enable it before running smoke tests.",
                    metadata);
        }
        if (!hasKey) {
            return new AppReadinessCheckVO("embedding_config", "Embedding Config",
                    AppReadinessStatus.NOT_READY,
                    "Matching embedding config is enabled but missing an upstream API key. Configure the key before running smoke tests.",
                    metadata);
        }
        return new AppReadinessCheckVO("embedding_config", "Embedding Config",
                AppReadinessStatus.READY, "Enabled embedding config matches the KB.", metadata);
    }

    private AppReadinessStatus computeOverallStatus(List<AppReadinessCheckVO> checks) {
        if (checks.stream().anyMatch(c -> AppReadinessStatus.MISSING.name().equals(c.getStatus()))) {
            return AppReadinessStatus.MISSING;
        }
        if (checks.stream().anyMatch(c -> AppReadinessStatus.DISABLED.name().equals(c.getStatus()))) {
            return AppReadinessStatus.DISABLED;
        }
        if (checks.stream().anyMatch(c -> AppReadinessStatus.NOT_READY.name().equals(c.getStatus()))) {
            return AppReadinessStatus.NOT_READY;
        }
        return AppReadinessStatus.READY;
    }
}
