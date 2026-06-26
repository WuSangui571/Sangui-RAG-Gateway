package com.sangui.raggateway.apikey;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.security.ApiKeyGenerator;
import com.sangui.raggateway.common.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private ApiKeyGenerator apiKeyGenerator;

    @Mock
    private ApiKeyHasher apiKeyHasher;

    @Captor
    private ArgumentCaptor<ApiKeyEntity> entityCaptor;

    private ApiKeyService apiKeyService;

    private static final String PLAINTEXT = "sk-sangui-abcdef1234567890";
    private static final String HASH = "abc123hash";
    private static final String PREFIX = "sk-sangui-abcdef";

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyMapper, apiKeyGenerator, apiKeyHasher);
    }

    @Test
    void shouldIncludeOneTimePlaintextKeyInCreateResult() {
        when(apiKeyGenerator.generate()).thenReturn(PLAINTEXT);
        when(apiKeyHasher.hash(PLAINTEXT)).thenReturn(HASH);
        when(apiKeyGenerator.extractPrefix(PLAINTEXT)).thenReturn(PREFIX);

        CreateApiKeyResult result = apiKeyService.create(1L, 100L, "test-key");

        assertThat(result.getPlaintextKey()).isEqualTo(PLAINTEXT);
        assertThat(result.getEntity()).isNotNull();
        assertThat(result.getEntity().getKeyPrefix()).isEqualTo(PREFIX);
    }

    @Test
    void shouldNotPersistPlaintextKey() {
        when(apiKeyGenerator.generate()).thenReturn(PLAINTEXT);
        when(apiKeyHasher.hash(PLAINTEXT)).thenReturn(HASH);
        when(apiKeyGenerator.extractPrefix(PLAINTEXT)).thenReturn(PREFIX);

        apiKeyService.create(1L, 100L, "test-key");

        verify(apiKeyMapper).insert(entityCaptor.capture());
        ApiKeyEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getKeyHash()).isEqualTo(HASH);
        assertThat(persisted.getKeyHash()).isNotEqualTo(PLAINTEXT);
        assertThat(persisted.getKeyPrefix()).isEqualTo(PREFIX);
        assertThat(persisted.getKeyPrefix()).isNotEqualTo(PLAINTEXT);
    }

    @Test
    void shouldSetActiveStatusOnCreation() {
        when(apiKeyGenerator.generate()).thenReturn(PLAINTEXT);
        when(apiKeyHasher.hash(PLAINTEXT)).thenReturn(HASH);
        when(apiKeyGenerator.extractPrefix(PLAINTEXT)).thenReturn(PREFIX);

        apiKeyService.create(1L, 100L, "test-key");

        verify(apiKeyMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(ApiKeyStatus.ACTIVE.name());
    }

    @Test
    void shouldSetTimestampsOnCreation() {
        when(apiKeyGenerator.generate()).thenReturn(PLAINTEXT);
        when(apiKeyHasher.hash(PLAINTEXT)).thenReturn(HASH);
        when(apiKeyGenerator.extractPrefix(PLAINTEXT)).thenReturn(PREFIX);

        apiKeyService.create(1L, 100L, "test-key");

        verify(apiKeyMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCreatedAt()).isNotNull();
        assertThat(entityCaptor.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldConsiderActiveKeyValid() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setStatus(ApiKeyStatus.ACTIVE.name());

        assertThat(apiKeyService.isValid(key)).isTrue();
    }

    @Test
    void shouldConsiderDisabledKeyInvalid() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setStatus(ApiKeyStatus.DISABLED.name());

        assertThat(apiKeyService.isValid(key)).isFalse();
    }

    @Test
    void shouldConsiderRevokedKeyInvalid() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setStatus(ApiKeyStatus.REVOKED.name());

        assertThat(apiKeyService.isValid(key)).isFalse();
    }

    @Test
    void shouldConsiderExpiredKeyInvalid() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setStatus(ApiKeyStatus.ACTIVE.name());
        key.setExpiresAt(LocalDateTime.now().minusDays(1));

        assertThat(apiKeyService.isValid(key)).isFalse();
    }

    @Test
    void shouldConsiderNullKeyInvalid() {
        assertThat(apiKeyService.isValid(null)).isFalse();
    }

    @Test
    void shouldConsiderActiveKeyWithFutureExpiryValid() {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setStatus(ApiKeyStatus.ACTIVE.name());
        key.setExpiresAt(LocalDateTime.now().plusDays(30));

        assertThat(apiKeyService.isValid(key)).isTrue();
    }

    @Test
    void shouldUpdateLastUsedAndUpdatedAtTogether() {
        apiKeyService.updateLastUsed(10L);

        verify(apiKeyMapper).updateById(entityCaptor.capture());
        ApiKeyEntity updated = entityCaptor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        assertThat(updated.getLastUsedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isEqualTo(updated.getLastUsedAt());
    }

    @Test
    void shouldSetExpiresAtOnCreation() {
        LocalDateTime future = LocalDateTime.now().plusDays(30);
        when(apiKeyGenerator.generate()).thenReturn(PLAINTEXT);
        when(apiKeyHasher.hash(PLAINTEXT)).thenReturn(HASH);
        when(apiKeyGenerator.extractPrefix(PLAINTEXT)).thenReturn(PREFIX);

        apiKeyService.create(1L, 100L, "test-key", future);

        verify(apiKeyMapper).insert(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getExpiresAt()).isEqualTo(future);
    }

    @Test
    void shouldRejectPastExpiresAt() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        assertThatThrownBy(() -> apiKeyService.create(1L, 100L, "test-key", past))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expiresAt must be in the future");
    }

    @Test
    void shouldRejectCreateWithBlankName() {
        assertThatThrownBy(() -> apiKeyService.create(1L, 100L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectCreateWithInvalidAppId() {
        assertThatThrownBy(() -> apiKeyService.create(0L, 100L, "test-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appId must be a positive long");
    }

    @Test
    void shouldDisableActiveKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.ACTIVE.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.disable(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.DISABLED.name());
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldDisableAlreadyDisabledKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.DISABLED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.disable(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.DISABLED.name());
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldRejectDisableOfRevokedKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.REVOKED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        assertThatThrownBy(() -> apiKeyService.disable(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Revoked key cannot be disabled");
    }

    @Test
    void shouldReturnNullForDisableOfNonExistentKey() {
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiKeyEntity result = apiKeyService.disable(999L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldEnableDisabledKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.DISABLED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.enable(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE.name());
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldEnableAlreadyActiveKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.ACTIVE.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.enable(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE.name());
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldRejectEnableOfRevokedKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.REVOKED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        assertThatThrownBy(() -> apiKeyService.enable(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Revoked key cannot be enabled");
    }

    @Test
    void shouldRejectEnableOfExpiredKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.EXPIRED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        assertThatThrownBy(() -> apiKeyService.enable(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Expired key cannot be enabled");
    }

    @Test
    void shouldReturnNullForEnableOfNonExistentKey() {
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiKeyEntity result = apiKeyService.enable(999L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldRevokeActiveKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.ACTIVE.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.revoke(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.REVOKED.name());
        assertThat(result.getRevokedAt()).isNotNull();
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldRevokeDisabledKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.DISABLED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.revoke(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.REVOKED.name());
        assertThat(result.getRevokedAt()).isNotNull();
        verify(apiKeyMapper).updateById(key);
    }

    @Test
    void shouldReturnRevokedKeyForIdempotentRevoke() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.REVOKED.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.revoke(10L, 100L);

        assertThat(result.getStatus()).isEqualTo(ApiKeyStatus.REVOKED.name());
    }

    @Test
    void shouldReturnNullForRevokeOfNonExistentKey() {
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiKeyEntity result = apiKeyService.revoke(999L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldListKeysByAppIdAndUserId() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.ACTIVE.name());
        when(apiKeyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(key));

        List<ApiKeyEntity> result = apiKeyService.listByAppIdAndUserId(1L, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAppId()).isEqualTo(1L);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldFindByIdAndUserIdReturnKey() {
        ApiKeyEntity key = createKey(10L, 1L, 100L, ApiKeyStatus.ACTIVE.name());
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(key);

        ApiKeyEntity result = apiKeyService.findByIdAndUserId(10L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldFindByIdAndUserIdReturnNullForCrossUser() {
        when(apiKeyMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        ApiKeyEntity result = apiKeyService.findByIdAndUserId(10L, 999L);

        assertThat(result).isNull();
    }

    private ApiKeyEntity createKey(Long id, Long appId, Long userId, String status) {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(id);
        key.setAppId(appId);
        key.setUserId(userId);
        key.setName("Test Key");
        key.setKeyHash("somehash");
        key.setKeyPrefix(PREFIX);
        key.setStatus(status);
        key.setCreatedAt(LocalDateTime.now());
        key.setUpdatedAt(LocalDateTime.now());
        return key;
    }
}
