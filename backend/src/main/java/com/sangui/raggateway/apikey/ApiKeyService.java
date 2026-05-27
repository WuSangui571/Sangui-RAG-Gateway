package com.sangui.raggateway.apikey;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
import com.sangui.raggateway.common.security.ApiKeyGenerator;
import com.sangui.raggateway.common.security.ApiKeyHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Profile("!test")
public class ApiKeyService {

    private final ApiKeyMapper apiKeyMapper;
    private final ApiKeyGenerator apiKeyGenerator;
    private final ApiKeyHasher apiKeyHasher;

    public ApiKeyService(ApiKeyMapper apiKeyMapper,
                         ApiKeyGenerator apiKeyGenerator,
                         ApiKeyHasher apiKeyHasher) {
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyGenerator = apiKeyGenerator;
        this.apiKeyHasher = apiKeyHasher;
    }

    @Transactional
    public CreateApiKeyResult create(Long appId, Long userId, String name) {
        String plaintextKey = apiKeyGenerator.generate();
        String keyHash = apiKeyHasher.hash(plaintextKey);
        String keyPrefix = apiKeyGenerator.extractPrefix(plaintextKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setAppId(appId);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setKeyHash(keyHash);
        entity.setKeyPrefix(keyPrefix);
        entity.setStatus(ApiKeyStatus.ACTIVE.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        apiKeyMapper.insert(entity);
        return new CreateApiKeyResult(plaintextKey, entity);
    }

    public ApiKeyEntity findByHash(String keyHash) {
        LambdaQueryWrapper<ApiKeyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyEntity::getKeyHash, keyHash);
        return apiKeyMapper.selectOne(wrapper);
    }

    @Transactional
    public void updateLastUsed(Long id) {
        LocalDateTime now = LocalDateTime.now();
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(id);
        entity.setLastUsedAt(now);
        entity.setUpdatedAt(now);
        apiKeyMapper.updateById(entity);
    }

    public boolean isValid(ApiKeyEntity apiKey) {
        if (apiKey == null) {
            return false;
        }
        String status = apiKey.getStatus();
        if (ApiKeyStatus.ACTIVE.name().equals(status)) {
            if (apiKey.getExpiresAt() != null && apiKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                return false;
            }
            return true;
        }
        return false;
    }
}
