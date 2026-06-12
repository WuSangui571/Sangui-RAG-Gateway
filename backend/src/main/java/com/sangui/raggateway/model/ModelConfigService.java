package com.sangui.raggateway.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.common.security.UpstreamApiKeyMasker;
import com.sangui.raggateway.model.dto.CreateModelConfigDTO;
import com.sangui.raggateway.model.dto.UpdateModelConfigDTO;
import com.sangui.raggateway.model.vo.ModelConfigVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Profile("!test")
public class ModelConfigService {

    private static final Set<String> CHAT_CAPABILITY_FILTER_VALUES = Set.of(
            ModelConfigCapability.CHAT.name());
    private static final Set<String> EMBEDDING_CAPABILITY_FILTER_VALUES = Set.of(
            ModelConfigCapability.EMBEDDING.name());

    private final ModelConfigMapper modelConfigMapper;
    private final UpstreamApiKeyEncryptor encryptor;
    private final UpstreamApiKeyMasker masker;

    public ModelConfigService(ModelConfigMapper modelConfigMapper,
                              UpstreamApiKeyEncryptor encryptor,
                              UpstreamApiKeyMasker masker) {
        this.modelConfigMapper = modelConfigMapper;
        this.encryptor = encryptor;
        this.masker = masker;
    }

    @Transactional
    public ModelConfigEntity create(Long userId, String name, String providerName,
                                    String baseUrl, String chatModel) {
        return create(userId, name, providerName, baseUrl, chatModel, null, null);
    }

    @Transactional
    public ModelConfigEntity create(Long userId, String name, String providerName,
                                    String baseUrl, String chatModel,
                                    String embeddingModel, Integer embeddingDimension) {
        String normalizedName = normalizeRequiredText(name);
        String normalizedProviderName = normalizeRequiredText(providerName);
        String normalizedBaseUrl = normalizeRequiredText(baseUrl);
        String normalizedEmbeddingModel = normalizeOptionalText(embeddingModel);
        String normalizedChatModel = normalizedEmbeddingModel == null
                ? normalizeRequiredText(chatModel) : null;
        validateEmbeddingConfig(normalizedEmbeddingModel, embeddingDimension);

        ModelConfigCapability capability = resolveCapability(
                normalizedChatModel != null, normalizedEmbeddingModel != null);
        validateCapabilityFields(capability, normalizedChatModel,
                normalizedEmbeddingModel, embeddingDimension, true);

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setUserId(userId);
        entity.setCapability(capability.name());
        entity.setName(normalizedName);
        entity.setProviderName(normalizedProviderName);
        entity.setBaseUrl(normalizedBaseUrl);
        entity.setChatModel(normalizedChatModel);
        entity.setEmbeddingModel(normalizedEmbeddingModel);
        entity.setEmbeddingDimension(embeddingDimension);
        entity.setStatus(ModelConfigStatus.ENABLED.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.insert(entity);
        return entity;
    }

    @Transactional
    public ModelConfigVO createAdminConfig(Long userId, CreateModelConfigDTO dto) {
        ModelConfigCapability capability = parseCapability(dto.getCapability());
        String normalizedName = normalizeRequiredText(dto.getName());
        String normalizedProviderName = normalizeRequiredText(dto.getProviderName());
        String normalizedBaseUrl = normalizeRequiredText(dto.getBaseUrl());
        String normalizedChatModel = normalizeOptionalText(dto.getChatModel());
        String normalizedEmbeddingModel = normalizeOptionalText(dto.getEmbeddingModel());
        validateCapabilityFields(capability, normalizedChatModel, normalizedEmbeddingModel, dto.getEmbeddingDimension(), true);
        validateRequiredFields(normalizedName, normalizedProviderName, normalizedBaseUrl, dto.getApiKey());

        String encrypted = encryptor.encrypt(dto.getApiKey());
        String masked = masker.mask(dto.getApiKey());

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setUserId(userId);
        entity.setCapability(capability.name());
        entity.setName(normalizedName);
        entity.setProviderName(normalizedProviderName);
        entity.setBaseUrl(normalizedBaseUrl);
        entity.setApiKeyEncrypted(encrypted);
        entity.setApiKeyMasked(masked);
        entity.setChatModel(normalizedChatModel);
        entity.setEmbeddingModel(normalizedEmbeddingModel);
        entity.setEmbeddingDimension(dto.getEmbeddingDimension());
        entity.setStatus(ModelConfigStatus.ENABLED.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.insert(entity);
        return ModelConfigVO.from(entity);
    }

    @Transactional
    public ModelConfigVO updateAdminConfig(Long id, Long userId, UpdateModelConfigDTO dto) {
        ModelConfigEntity entity = findByIdAndUserId(id, userId);

        ModelConfigCapability currentCapability = parseCapability(entity.getCapability());
        ModelConfigCapability newCapability = dto.getCapability() != null
                ? parseCapability(dto.getCapability()) : currentCapability;

        if (hasText(dto.getName())) {
            entity.setName(normalizeRequiredText(dto.getName()));
        }
        if (hasText(dto.getProviderName())) {
            entity.setProviderName(normalizeRequiredText(dto.getProviderName()));
        }
        if (hasText(dto.getBaseUrl())) {
            entity.setBaseUrl(normalizeRequiredText(dto.getBaseUrl()));
        }
        if (dto.getCapability() != null) {
            entity.setCapability(newCapability.name());
        }

        if (dto.getChatModel() != null) {
            entity.setChatModel(normalizeOptionalText(dto.getChatModel()));
        }

        if (dto.getApiKey() != null) {
            if (dto.getApiKey().isBlank()) {
                throw new IllegalArgumentException("apiKey must not be blank");
            }
            String encrypted = encryptor.encrypt(dto.getApiKey());
            String masked = masker.mask(dto.getApiKey());
            entity.setApiKeyEncrypted(encrypted);
            entity.setApiKeyMasked(masked);
        }

        String normalizedEmbeddingModel = dto.getEmbeddingModel() != null
                ? normalizeOptionalText(dto.getEmbeddingModel()) : entity.getEmbeddingModel();
        Integer embeddingDimension = dto.getEmbeddingDimension() != null
                ? dto.getEmbeddingDimension() : entity.getEmbeddingDimension();
        if (dto.getEmbeddingModel() != null || dto.getEmbeddingDimension() != null) {
            entity.setEmbeddingModel(normalizedEmbeddingModel);
            entity.setEmbeddingDimension(embeddingDimension);
        }

        validateCapabilityFields(newCapability,
                entity.getChatModel(), entity.getEmbeddingModel(), entity.getEmbeddingDimension(), false);

        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.updateById(entity);
        return ModelConfigVO.from(entity);
    }

    public ModelConfigVO findAdminDetail(Long id, Long userId) {
        ModelConfigEntity entity = findByIdAndUserId(id, userId);
        return ModelConfigVO.from(entity);
    }

    public List<ModelConfigVO> listAdminConfigs(Long userId, String status, String capability) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId);
        if (status != null) {
            wrapper.eq(ModelConfigEntity::getStatus, status);
        }
        applyCapabilityFilter(wrapper, capability);
        wrapper.orderByDesc(ModelConfigEntity::getCreatedAt);
        List<ModelConfigEntity> entities = modelConfigMapper.selectList(wrapper);
        List<ModelConfigVO> result = new ArrayList<>(entities.size());
        for (ModelConfigEntity entity : entities) {
            result.add(ModelConfigVO.from(entity));
        }
        return result;
    }

    @Transactional
    public ModelConfigVO disableAdminConfig(Long id, Long userId) {
        ModelConfigEntity entity = findByIdAndUserId(id, userId);
        entity.setStatus(ModelConfigStatus.DISABLED.name());
        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.updateById(entity);
        return ModelConfigVO.from(entity);
    }

    @Transactional
    public ModelConfigVO enableAdminConfig(Long id, Long userId) {
        ModelConfigEntity entity = findByIdAndUserId(id, userId);
        if (entity.getApiKeyEncrypted() == null || entity.getApiKeyEncrypted().isBlank()) {
            throw new IllegalArgumentException("Cannot enable model config without an upstream API key");
        }
        if (ModelConfigCapability.EMBEDDING.name().equals(entity.getCapability())
                && (entity.getEmbeddingDimension() == null || entity.getEmbeddingDimension() <= 0)) {
            throw new IllegalArgumentException(
                    "Cannot enable embedding config without a positive embedding dimension");
        }
        String chatModel = entity.getChatModel();
        String embeddingModel = entity.getEmbeddingModel();
        Integer embeddingDimension = entity.getEmbeddingDimension();
        ModelConfigCapability capability;
        if (ModelConfigCapability.EMBEDDING.name().equals(entity.getCapability())) {
            capability = ModelConfigCapability.EMBEDDING;
        } else {
            capability = ModelConfigCapability.CHAT;
        }
        validateCapabilityFields(capability, chatModel,
                embeddingModel, embeddingDimension, false);
        entity.setStatus(ModelConfigStatus.ENABLED.name());
        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.updateById(entity);
        return ModelConfigVO.from(entity);
    }

    public ModelConfigEntity findById(Long id) {
        return modelConfigMapper.selectById(id);
    }

    public ModelConfigEntity findEnabledByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getId, id)
                .eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getStatus, ModelConfigStatus.ENABLED.name());
        return modelConfigMapper.selectOne(wrapper);
    }

    public ModelConfigEntity findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getId, id)
                .eq(ModelConfigEntity::getUserId, userId);
        return modelConfigMapper.selectOne(wrapper);
    }

    public ModelConfigEntity findMatchingEmbeddingConfig(Long userId, String embeddingModel, Integer embeddingDimension) {
        String normalizedEmbeddingModel = normalizeOptionalText(embeddingModel);
        if (normalizedEmbeddingModel == null) {
            return null;
        }
        if (embeddingDimension == null || embeddingDimension <= 0) {
            return null;
        }
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getEmbeddingModel, normalizedEmbeddingModel)
                .eq(ModelConfigEntity::getEmbeddingDimension, embeddingDimension)
                .in(ModelConfigEntity::getCapability,
                        EMBEDDING_CAPABILITY_FILTER_VALUES)
                .orderByDesc(ModelConfigEntity::getStatus)
                .orderByDesc(ModelConfigEntity::getUpdatedAt)
                .orderByDesc(ModelConfigEntity::getId)
                .last("LIMIT 1");
        List<ModelConfigEntity> matches = modelConfigMapper.selectList(wrapper);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public ModelConfigEntity findEnabledEmbeddingConfig(Long userId, String embeddingModel, Integer embeddingDimension) {
        String normalizedEmbeddingModel = normalizeOptionalText(embeddingModel);
        if (normalizedEmbeddingModel == null) {
            return null;
        }
        if (embeddingDimension == null || embeddingDimension <= 0) {
            return null;
        }
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getEmbeddingModel, normalizedEmbeddingModel)
                .eq(ModelConfigEntity::getEmbeddingDimension, embeddingDimension)
                .eq(ModelConfigEntity::getStatus, ModelConfigStatus.ENABLED.name())
                .in(ModelConfigEntity::getCapability,
                        EMBEDDING_CAPABILITY_FILTER_VALUES)
                .orderByDesc(ModelConfigEntity::getUpdatedAt)
                .orderByDesc(ModelConfigEntity::getId)
                .last("LIMIT 1");
        List<ModelConfigEntity> matches = modelConfigMapper.selectList(wrapper);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<ModelConfigVO> listEnabledChatCapableConfigs(Long userId) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getStatus, ModelConfigStatus.ENABLED.name())
                .in(ModelConfigEntity::getCapability, CHAT_CAPABILITY_FILTER_VALUES)
                .orderByDesc(ModelConfigEntity::getCreatedAt);
        List<ModelConfigEntity> entities = modelConfigMapper.selectList(wrapper);
        List<ModelConfigVO> result = new ArrayList<>(entities.size());
        for (ModelConfigEntity entity : entities) {
            result.add(ModelConfigVO.from(entity));
        }
        return result;
    }

    public String decryptUpstreamKey(ModelConfigEntity config) {
        if (config == null || config.getApiKeyEncrypted() == null) {
            return null;
        }
        return encryptor.decrypt(config.getApiKeyEncrypted());
    }

    public boolean isEnabled(ModelConfigEntity entity) {
        return entity != null && ModelConfigStatus.ENABLED.name().equals(entity.getStatus());
    }

    public boolean isChatCapable(ModelConfigEntity entity) {
        if (entity == null) {
            return false;
        }
        return ModelConfigCapability.CHAT.name().equals(entity.getCapability());
    }

    static ModelConfigCapability resolveCapability(boolean hasChatModel, boolean hasEmbeddingModel) {
        if (hasEmbeddingModel) {
            return ModelConfigCapability.EMBEDDING;
        }
        return ModelConfigCapability.CHAT;
    }

    static ModelConfigCapability parseCapability(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("capability is required");
        }
        ModelConfigCapability cap;
        try {
            cap = ModelConfigCapability.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid capability: " + value
                    + ". Must be CHAT or EMBEDDING.");
        }
        if (cap == ModelConfigCapability.CHAT_EMBEDDING) {
            throw new IllegalArgumentException(
                    "CHAT_EMBEDDING is no longer supported. Use CHAT or EMBEDDING.");
        }
        return cap;
    }

    private void validateCapabilityFields(ModelConfigCapability capability, String chatModel,
                                          String embeddingModel, Integer embeddingDimension, boolean isCreate) {
        switch (capability) {
            case CHAT:
                if (!hasText(chatModel)) {
                    throw new IllegalArgumentException("chatModel is required for CHAT capability");
                }
                if (hasText(embeddingModel) || (embeddingDimension != null && embeddingDimension > 0)) {
                    throw new IllegalArgumentException(
                            "embedding fields must not be set for CHAT capability");
                }
                break;
            case EMBEDDING:
                if (hasText(chatModel)) {
                    throw new IllegalArgumentException("chatModel must not be set for EMBEDDING capability");
                }
                if (!hasText(embeddingModel)) {
                    throw new IllegalArgumentException("embeddingModel is required for EMBEDDING capability");
                }
                if (isCreate) {
                    if (embeddingDimension != null && embeddingDimension <= 0) {
                        throw new IllegalArgumentException(
                                "embeddingDimension must be positive when provided");
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported capability: " + capability);
        }
    }

    private void applyCapabilityFilter(LambdaQueryWrapper<ModelConfigEntity> wrapper, String capability) {
        if (capability == null) {
            return;
        }
        switch (capability.toUpperCase()) {
            case "CHAT":
                wrapper.in(ModelConfigEntity::getCapability, CHAT_CAPABILITY_FILTER_VALUES);
                break;
            case "EMBEDDING":
                wrapper.in(ModelConfigEntity::getCapability, EMBEDDING_CAPABILITY_FILTER_VALUES);
                break;
            default:
                throw new IllegalArgumentException("Invalid capability filter: " + capability
                        + ". Must be CHAT or EMBEDDING.");
        }
    }

    private void validateRequiredFields(String name, String providerName, String baseUrl, String apiKey) {
        if (!hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (!hasText(providerName)) {
            throw new IllegalArgumentException("providerName is required");
        }
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (!hasText(apiKey)) {
            throw new IllegalArgumentException("apiKey is required");
        }
    }

    private void validateEmbeddingConfig(String embeddingModel, Integer embeddingDimension) {
        if (embeddingDimension != null && embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive when provided");
        }
        if (!hasText(embeddingModel) && embeddingDimension != null) {
            throw new IllegalArgumentException("embeddingModel is required when embeddingDimension is provided");
        }
        if (hasText(embeddingModel) && embeddingDimension == null) {
            throw new IllegalArgumentException("embeddingDimension is required when embeddingModel is provided");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeRequiredText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
