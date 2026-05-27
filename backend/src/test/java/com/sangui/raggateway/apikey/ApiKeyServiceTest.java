package com.sangui.raggateway.apikey;

import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
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

import static org.assertj.core.api.Assertions.assertThat;
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
}
