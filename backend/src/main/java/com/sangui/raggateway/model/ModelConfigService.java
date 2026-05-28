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

@Service
@Profile("!test")
public class ModelConfigService {

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
        validateEmbeddingConfig(embeddingModel, embeddingDimension);

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setProviderName(providerName);
        entity.setBaseUrl(baseUrl);
        entity.setChatModel(chatModel);
        entity.setEmbeddingModel(embeddingModel);
        entity.setEmbeddingDimension(embeddingDimension);
        entity.setStatus(ModelConfigStatus.ENABLED.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.insert(entity);
        return entity;
    }

    @Transactional
    public ModelConfigVO createAdminConfig(Long userId, CreateModelConfigDTO dto) {
        validateRequiredFields(dto.getName(), dto.getProviderName(), dto.getBaseUrl(), dto.getChatModel(), dto.getApiKey());
        validateEmbeddingConfig(dto.getEmbeddingModel(), dto.getEmbeddingDimension());

        String encrypted = encryptor.encrypt(dto.getApiKey());
        String masked = masker.mask(dto.getApiKey());

        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setUserId(userId);
        entity.setName(dto.getName());
        entity.setProviderName(dto.getProviderName());
        entity.setBaseUrl(dto.getBaseUrl());
        entity.setApiKeyEncrypted(encrypted);
        entity.setApiKeyMasked(masked);
        entity.setChatModel(dto.getChatModel());
        entity.setEmbeddingModel(dto.getEmbeddingModel());
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

        if (hasText(dto.getName())) {
            entity.setName(dto.getName());
        }
        if (hasText(dto.getProviderName())) {
            entity.setProviderName(dto.getProviderName());
        }
        if (hasText(dto.getBaseUrl())) {
            entity.setBaseUrl(dto.getBaseUrl());
        }
        if (hasText(dto.getChatModel())) {
            entity.setChatModel(dto.getChatModel());
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

        validateEmbeddingConfig(dto.getEmbeddingModel(), dto.getEmbeddingDimension());
        if (dto.getEmbeddingModel() != null) {
            entity.setEmbeddingModel(dto.getEmbeddingModel());
            entity.setEmbeddingDimension(dto.getEmbeddingDimension());
        }

        entity.setUpdatedAt(LocalDateTime.now());
        modelConfigMapper.updateById(entity);
        return ModelConfigVO.from(entity);
    }

    public ModelConfigVO findAdminDetail(Long id, Long userId) {
        ModelConfigEntity entity = findByIdAndUserId(id, userId);
        return ModelConfigVO.from(entity);
    }

    public List<ModelConfigVO> listAdminConfigs(Long userId, String status) {
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId);
        if (status != null) {
            wrapper.eq(ModelConfigEntity::getStatus, status);
        }
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

    public ModelConfigEntity findEnabledEmbeddingConfig(Long userId, String embeddingModel, Integer embeddingDimension) {
        if (embeddingModel == null || embeddingModel.isBlank()) {
            return null;
        }
        if (embeddingDimension == null || embeddingDimension <= 0) {
            return null;
        }
        LambdaQueryWrapper<ModelConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfigEntity::getUserId, userId)
                .eq(ModelConfigEntity::getEmbeddingModel, embeddingModel)
                .eq(ModelConfigEntity::getEmbeddingDimension, embeddingDimension)
                .eq(ModelConfigEntity::getStatus, ModelConfigStatus.ENABLED.name())
                .last("LIMIT 2");
        List<ModelConfigEntity> matches = modelConfigMapper.selectList(wrapper);
        return matches.size() == 1 ? matches.get(0) : null;
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

    private void validateRequiredFields(String name, String providerName, String baseUrl, String chatModel, String apiKey) {
        if (!hasText(name)) {
            throw new IllegalArgumentException("name is required");
        }
        if (!hasText(providerName)) {
            throw new IllegalArgumentException("providerName is required");
        }
        if (!hasText(baseUrl)) {
            throw new IllegalArgumentException("baseUrl is required");
        }
        if (!hasText(chatModel)) {
            throw new IllegalArgumentException("chatModel is required");
        }
        if (!hasText(apiKey)) {
            throw new IllegalArgumentException("apiKey is required");
        }
    }

    private void validateEmbeddingConfig(String embeddingModel, Integer embeddingDimension) {
        if (embeddingDimension != null && embeddingDimension <= 0) {
            throw new IllegalArgumentException("embeddingDimension must be positive when provided");
        }
        if (hasText(embeddingModel) && embeddingDimension == null) {
            throw new IllegalArgumentException("embeddingDimension is required when embeddingModel is provided");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
