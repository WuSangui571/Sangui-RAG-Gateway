package com.sangui.raggateway.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Profile("!test")
public class ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;

    public ModelConfigService(ModelConfigMapper modelConfigMapper) {
        this.modelConfigMapper = modelConfigMapper;
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

    public boolean isEnabled(ModelConfigEntity entity) {
        return entity != null && ModelConfigStatus.ENABLED.name().equals(entity.getStatus());
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
