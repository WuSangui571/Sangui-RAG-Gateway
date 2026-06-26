package com.sangui.raggateway.apikey;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.security.ApiKeyGenerator;
import com.sangui.raggateway.common.security.ApiKeyHasher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
        return create(appId, userId, name, null);
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

    @Transactional
    public CreateApiKeyResult create(Long appId, Long userId, String name, LocalDateTime expiresAt) {
        if (appId == null || appId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "appId must be a positive long");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("INVALID_REQUEST", "userId must be a positive long");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "name is required");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException("INVALID_REQUEST", "expiresAt must be in the future");
        }

        String plaintextKey = apiKeyGenerator.generate();
        String keyHash = apiKeyHasher.hash(plaintextKey);
        String keyPrefix = apiKeyGenerator.extractPrefix(plaintextKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setAppId(appId);
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setKeyHash(keyHash);
        entity.setKeyPrefix(keyPrefix);
        entity.setStatus(ApiKeyStatus.ACTIVE.name());
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        apiKeyMapper.insert(entity);
        return new CreateApiKeyResult(plaintextKey, entity);
    }

    public ApiKeyEntity findById(Long id) {
        return apiKeyMapper.selectById(id);
    }

    public ApiKeyEntity findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<ApiKeyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyEntity::getId, id);
        wrapper.eq(ApiKeyEntity::getUserId, userId);
        return apiKeyMapper.selectOne(wrapper);
    }

    public List<ApiKeyEntity> listByAppIdAndUserId(Long appId, Long userId) {
        LambdaQueryWrapper<ApiKeyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiKeyEntity::getAppId, appId);
        wrapper.eq(ApiKeyEntity::getUserId, userId);
        wrapper.orderByDesc(ApiKeyEntity::getCreatedAt);
        return apiKeyMapper.selectList(wrapper);
    }

    @Transactional
    public ApiKeyEntity disable(Long id, Long userId) {
        ApiKeyEntity key = findByIdAndUserId(id, userId);
        if (key == null) {
            return null;
        }
        if (ApiKeyStatus.REVOKED.name().equals(key.getStatus())) {
            throw new BusinessException("INVALID_REQUEST", "Revoked key cannot be disabled");
        }
        key.setStatus(ApiKeyStatus.DISABLED.name());
        key.setUpdatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);
        return key;
    }

    @Transactional
    public ApiKeyEntity enable(Long id, Long userId) {
        ApiKeyEntity key = findByIdAndUserId(id, userId);
        if (key == null) {
            return null;
        }
        if (ApiKeyStatus.REVOKED.name().equals(key.getStatus())) {
            throw new BusinessException("INVALID_REQUEST", "Revoked key cannot be enabled");
        }
        if (ApiKeyStatus.EXPIRED.name().equals(key.getStatus())) {
            throw new BusinessException("INVALID_REQUEST", "Expired key cannot be enabled");
        }
        key.setStatus(ApiKeyStatus.ACTIVE.name());
        key.setUpdatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);
        return key;
    }

    @Transactional
    public ApiKeyEntity revoke(Long id, Long userId) {
        ApiKeyEntity key = findByIdAndUserId(id, userId);
        if (key == null) {
            return null;
        }
        if (ApiKeyStatus.REVOKED.name().equals(key.getStatus())) {
            return key;
        }
        key.setStatus(ApiKeyStatus.REVOKED.name());
        key.setRevokedAt(LocalDateTime.now());
        key.setUpdatedAt(LocalDateTime.now());
        apiKeyMapper.updateById(key);
        return key;
    }
}
