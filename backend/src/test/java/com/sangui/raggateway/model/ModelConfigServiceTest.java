package com.sangui.raggateway.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.common.security.UpstreamApiKeyMasker;
import com.sangui.raggateway.model.dto.CreateModelConfigDTO;
import com.sangui.raggateway.model.dto.UpdateModelConfigDTO;
import com.sangui.raggateway.model.vo.ModelConfigVO;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigServiceTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Mock
    private UpstreamApiKeyEncryptor encryptor;

    @Mock
    private UpstreamApiKeyMasker masker;

    @Captor
    private ArgumentCaptor<ModelConfigEntity> entityCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ModelConfigEntity>> wrapperCaptor;

    private ModelConfigService modelConfigService;

    @BeforeEach
    void setUp() {
        modelConfigService = new ModelConfigService(modelConfigMapper, encryptor, masker);
    }

    @Test
    void shouldPersistRequiredFieldsOnCreation() {
        modelConfigService.create(100L, "test-config", "openai", "https://api.openai.com/v1", "gpt-4o-mini");

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(100L);
        assertThat(persisted.getName()).isEqualTo("test-config");
        assertThat(persisted.getProviderName()).isEqualTo("openai");
        assertThat(persisted.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(persisted.getChatModel()).isEqualTo("gpt-4o-mini");
        assertThat(persisted.getStatus()).isEqualTo(ModelConfigStatus.ENABLED.name());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldNeverPersistPlaintextUpstreamKey() {
        modelConfigService.create(100L, "test-config", "openai", "https://api.openai.com/v1", "gpt-4o-mini");

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getApiKeyEncrypted()).isNull();
        assertThat(persisted.getApiKeyMasked()).isNull();
    }

    @Test
    void shouldPersistEmbeddingFieldsWhenProvided() {
        modelConfigService.create(
                100L,
                "test-config",
                "openai",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                "text-embedding-3-small",
                1536
        );

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(persisted.getEmbeddingDimension()).isEqualTo(1536);
    }

    @Test
    void shouldRejectNonPositiveEmbeddingDimension() {
        assertThatThrownBy(() -> modelConfigService.create(
                100L,
                "test-config",
                "openai",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                "text-embedding-3-small",
                0
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension must be positive");
    }

    @Test
    void shouldRejectEmbeddingModelWithoutDimension() {
        assertThatThrownBy(() -> modelConfigService.create(
                100L,
                "test-config",
                "openai",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                "text-embedding-3-small",
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension is required");
    }

    @Test
    void shouldConsiderEnabledEntityEnabled() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setStatus(ModelConfigStatus.ENABLED.name());

        assertThat(modelConfigService.isEnabled(entity)).isTrue();
    }

    @Test
    void shouldConsiderDisabledEntityNotEnabled() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setStatus(ModelConfigStatus.DISABLED.name());

        assertThat(modelConfigService.isEnabled(entity)).isFalse();
    }

    @Test
    void shouldConsiderNullEntityNotEnabled() {
        assertThat(modelConfigService.isEnabled(null)).isFalse();
    }

    @Test
    void shouldFindById() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(1L);
        entity.setChatModel("gpt-4o-mini");
        when(modelConfigMapper.selectById(1L)).thenReturn(entity);

        ModelConfigEntity result = modelConfigService.findById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getChatModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldFindEnabledByIdAndUserIdWithTenantAndStatusScope() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setStatus(ModelConfigStatus.ENABLED.name());
        when(modelConfigMapper.selectOne(any())).thenReturn(entity);

        ModelConfigEntity result = modelConfigService.findEnabledByIdAndUserId(10L, 100L);

        assertThat(result).isSameAs(entity);
        verify(modelConfigMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenEnabledLookupDoesNotMatch() {
        when(modelConfigMapper.selectOne(any())).thenReturn(null);

        ModelConfigEntity result = modelConfigService.findEnabledByIdAndUserId(10L, 200L);

        assertThat(result).isNull();
        verify(modelConfigMapper).selectOne(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldCreateAdminConfigWithEncryptedAndMaskedKey() {
        when(encryptor.encrypt("sk-upstream-secret")).thenReturn("v1:iv:ciphertext");
        when(masker.mask("sk-upstream-secret")).thenReturn("sk-...cret");

        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Default OpenAI");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-upstream-secret");
        dto.setChatModel("gpt-4o-mini");

        ModelConfigVO vo = modelConfigService.createAdminConfig(100L, dto);

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getApiKeyEncrypted()).isEqualTo("v1:iv:ciphertext");
        assertThat(persisted.getApiKeyMasked()).isEqualTo("sk-...cret");
        assertThat(persisted.getUserId()).isEqualTo(100L);
        assertThat(persisted.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void shouldCreateAdminConfigAndReturnMaskedVO() {
        when(encryptor.encrypt(any())).thenReturn("v1:iv:ciphertext");
        when(masker.mask(any())).thenReturn("sk-...cret");

        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-upstream-secret");
        dto.setChatModel("gpt-4o-mini");

        ModelConfigVO vo = modelConfigService.createAdminConfig(100L, dto);

        assertThat(vo.getApiKeyMasked()).isEqualTo("sk-...cret");
    }

    @Test
    void shouldRejectCreateAdminConfigWithoutRequiredFields() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectCreateAdminConfigWithoutApiKey() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setChatModel("gpt-4o-mini");
        dto.setApiKey(null);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey is required");
    }

    @Test
    void shouldRejectCreateAdminConfigWithNonPositiveEmbeddingDimension() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");
        dto.setChatModel("gpt-4o-mini");
        dto.setEmbeddingDimension(0);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension must be positive");
    }

    @Test
    void shouldRejectCreateAdminConfigWithEmbeddingModelWithoutDimension() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");
        dto.setChatModel("gpt-4o-mini");
        dto.setEmbeddingModel("text-embedding-3-small");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension is required");
    }

    @Test
    void shouldUpdateWithoutApiKeyPreserveExistingEncryptedKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setName("Old Name");
        existing.setProviderName("openai");
        existing.setBaseUrl("https://api.openai.com/v1");
        existing.setChatModel("gpt-4o-mini");
        existing.setApiKeyEncrypted("v1:old:encrypted");
        existing.setApiKeyMasked("sk-***old");
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        UpdateModelConfigDTO dto = new UpdateModelConfigDTO();
        dto.setName("New Name");

        modelConfigService.updateAdminConfig(10L, 100L, dto);

        verify(modelConfigMapper).updateById(entityCaptor.capture());
        ModelConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getApiKeyEncrypted()).isEqualTo("v1:old:encrypted");
        assertThat(updated.getApiKeyMasked()).isEqualTo("sk-***old");
        assertThat(updated.getName()).isEqualTo("New Name");
        verifyNoInteractions(encryptor);
    }

    @Test
    void shouldUpdateWithApiKeyReplaceEncryptedAndMasked() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setName("Old Name");
        existing.setProviderName("openai");
        existing.setBaseUrl("https://api.openai.com/v1");
        existing.setChatModel("gpt-4o-mini");
        existing.setApiKeyEncrypted("v1:old:encrypted");
        existing.setApiKeyMasked("sk-***old");
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);
        when(encryptor.encrypt("sk-new-key")).thenReturn("v1:new:encrypted");
        when(masker.mask("sk-new-key")).thenReturn("sk-***new");

        UpdateModelConfigDTO dto = new UpdateModelConfigDTO();
        dto.setApiKey("sk-new-key");

        modelConfigService.updateAdminConfig(10L, 100L, dto);

        verify(modelConfigMapper).updateById(entityCaptor.capture());
        ModelConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getApiKeyEncrypted()).isEqualTo("v1:new:encrypted");
        assertThat(updated.getApiKeyMasked()).isEqualTo("sk-***new");
    }

    @Test
    void shouldRejectUpdateWithBlankApiKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setName("Old");
        existing.setProviderName("openai");
        existing.setBaseUrl("https://api.openai.com/v1");
        existing.setChatModel("gpt-4o-mini");
        existing.setApiKeyEncrypted("v1:encrypted");
        existing.setApiKeyMasked("sk-***");
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        UpdateModelConfigDTO dto = new UpdateModelConfigDTO();
        dto.setApiKey("");

        assertThatThrownBy(() -> modelConfigService.updateAdminConfig(10L, 100L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey must not be blank");
    }

    @Test
    void shouldDisableConfigWithTenantScope() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        ModelConfigVO vo = modelConfigService.disableAdminConfig(10L, 100L);

        verify(modelConfigMapper).updateById(entityCaptor.capture());
        ModelConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo("DISABLED");
        assertThat(vo.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldListConfigsByUserId() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setApiKeyMasked("sk-***");
        entity.setStatus("ENABLED");

        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<ModelConfigVO> result = modelConfigService.listAdminConfigs(100L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApiKeyMasked()).isEqualTo("sk-***");
    }

    @Test
    void shouldListConfigsByUserIdAndStatus() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setApiKeyMasked("sk-***");
        entity.setStatus("ENABLED");

        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<ModelConfigVO> result = modelConfigService.listAdminConfigs(100L, "ENABLED");

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindAdminDetailWithTenantScope() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setApiKeyMasked("sk-***");
        when(modelConfigMapper.selectOne(any())).thenReturn(entity);

        ModelConfigVO vo = modelConfigService.findAdminDetail(10L, 100L);

        assertThat(vo.getApiKeyMasked()).isEqualTo("sk-***");
    }

    @Test
    void shouldFindEnabledEmbeddingConfig() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setEmbeddingModel("text-embedding-3-small");
        entity.setEmbeddingDimension(1536);
        entity.setStatus("ENABLED");
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(
                100L, "text-embedding-3-small", 1536);

        assertThat(result).isNotNull();
        assertThat(result.getEmbeddingDimension()).isEqualTo(1536);
        verify(modelConfigMapper).selectList(wrapperCaptor.capture());
    }

    @Test
    void shouldReturnNullWhenNoMatchingEmbeddingConfig() {
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(
                100L, "text-embedding-3-small", 1536);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnLatestEnabledEmbeddingConfigWhenMultipleMatch() {
        ModelConfigEntity first = new ModelConfigEntity();
        first.setId(12L);
        ModelConfigEntity second = new ModelConfigEntity();
        second.setId(11L);
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(first, second));

        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(
                100L, "text-embedding-3-small", 1536);

        assertThat(result).isSameAs(first);
        verify(modelConfigMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldReturnNullWhenEmbeddingModelIsNull() {
        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(100L, null, 1536);
        assertThat(result).isNull();
        verifyNoInteractions(modelConfigMapper);
    }

    @Test
    void shouldReturnNullWhenEmbeddingDimensionIsZero() {
        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(100L, "text-embedding-3-small", 0);
        assertThat(result).isNull();
        verifyNoInteractions(modelConfigMapper);
    }

    @Test
    void shouldDecryptUpstreamKey() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setApiKeyEncrypted("v1:encrypted:key");
        when(encryptor.decrypt("v1:encrypted:key")).thenReturn("plaintext-key");

        String result = modelConfigService.decryptUpstreamKey(config);

        assertThat(result).isEqualTo("plaintext-key");
    }

    @Test
    void shouldReturnNullWhenDecryptConfigHasNoEncryptedKey() {
        ModelConfigEntity config = new ModelConfigEntity();

        String result = modelConfigService.decryptUpstreamKey(config);

        assertThat(result).isNull();
        verifyNoInteractions(encryptor);
    }

    @Test
    void shouldReturnNullWhenDecryptConfigIsNull() {
        String result = modelConfigService.decryptUpstreamKey(null);

        assertThat(result).isNull();
        verifyNoInteractions(encryptor);
    }
}
