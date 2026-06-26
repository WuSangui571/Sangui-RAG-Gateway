package com.sangui.raggateway.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.exception.BusinessException;
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
        assertThat(persisted.getCapability()).isEqualTo("CHAT");
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
    void shouldPersistEmbeddingFieldsViaInternalCreateAsEMBEDDING() {
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
        assertThat(persisted.getCapability()).isEqualTo("EMBEDDING");
        assertThat(persisted.getChatModel()).isNull();
        assertThat(persisted.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(persisted.getEmbeddingDimension()).isEqualTo(1536);
    }

    @Test
    void shouldTrimTextFieldsOnCreation() {
        modelConfigService.create(
                100L,
                " test-config ",
                " openai ",
                " https://api.openai.com/v1 ",
                " gpt-4o-mini ",
                " text-embedding-3-small ",
                1536
        );

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getName()).isEqualTo("test-config");
        assertThat(persisted.getProviderName()).isEqualTo("openai");
        assertThat(persisted.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(persisted.getChatModel()).isNull();
        assertThat(persisted.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
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
    void shouldRejectEmbeddingDimensionWithoutModel() {
        assertThatThrownBy(() -> modelConfigService.create(
                100L,
                "test-config",
                "openai",
                "https://api.openai.com/v1",
                "gpt-4o-mini",
                " ",
                1536
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingModel is required");
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
        dto.setCapability("CHAT");
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
        assertThat(persisted.getCapability()).isEqualTo("CHAT");
        assertThat(persisted.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void shouldCreateAdminConfigAndReturnMaskedVO() {
        when(encryptor.encrypt(any())).thenReturn("v1:iv:ciphertext");
        when(masker.mask(any())).thenReturn("sk-...cret");

        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-upstream-secret");
        dto.setChatModel("gpt-4o-mini");

        ModelConfigVO vo = modelConfigService.createAdminConfig(100L, dto);

        assertThat(vo.getApiKeyMasked()).isEqualTo("sk-...cret");
    }

    @Test
    void shouldRejectAdminCreateWithCHAT_EMBEDDING() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT_EMBEDDING");
        dto.setName(" Text Embedding ");
        dto.setProviderName(" openai-compatible ");
        dto.setBaseUrl(" https://dashscope.aliyuncs.com/compatible-mode/v1 ");
        dto.setApiKey("sk-upstream-secret");
        dto.setChatModel(" deepseek-v4 ");
        dto.setEmbeddingModel(" text-embedding-v4 ");
        dto.setEmbeddingDimension(1024);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHAT_EMBEDDING is no longer supported");
    }

    @Test
    void shouldRejectCreateAdminConfigWithoutRequiredFields() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT");
        dto.setName("");
        dto.setChatModel("gpt-4o-mini");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectCreateAdminConfigWithoutApiKey() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setChatModel("gpt-4o-mini");
        dto.setApiKey(null);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("apiKey is required");
    }

    @Test
    void shouldRejectCreateConfigWithCHAT_EMBEDDINGCapability() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT_EMBEDDING");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");
        dto.setChatModel("gpt-4o-mini");
        dto.setEmbeddingModel("text-embedding-3-small");
        dto.setEmbeddingDimension(0);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHAT_EMBEDDING is no longer supported");
    }

    @Test
    void shouldRejectCreateCHATWithEmbeddingFields() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");
        dto.setChatModel("gpt-4o-mini");
        dto.setEmbeddingModel("text-embedding-3-small");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("embedding fields must not be set for CHAT capability");
    }

    @Test
    void shouldCreateEMBEDDINGConfigWithoutChatModel() {
        when(encryptor.encrypt(any())).thenReturn("v1:iv:ciphertext");
        when(masker.mask(any())).thenReturn("sk-...cret");

        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("EMBEDDING");
        dto.setName("DashScope Embedding");
        dto.setProviderName("dashscope");
        dto.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        dto.setApiKey("sk-upstream-secret");
        dto.setChatModel(null);
        dto.setEmbeddingModel("text-embedding-v4");
        dto.setEmbeddingDimension(1024);

        ModelConfigVO vo = modelConfigService.createAdminConfig(100L, dto);

        verify(modelConfigMapper).insert(entityCaptor.capture());
        ModelConfigEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getCapability()).isEqualTo("EMBEDDING");
        assertThat(persisted.getChatModel()).isNull();
        assertThat(persisted.getEmbeddingModel()).isEqualTo("text-embedding-v4");
        assertThat(persisted.getEmbeddingDimension()).isEqualTo(1024);
    }

    @Test
    void shouldRejectCreateEMBEDDINGConfigWithChatModel() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("EMBEDDING");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");
        dto.setChatModel("gpt-4o-mini");
        dto.setEmbeddingModel("text-embedding-v4");
        dto.setEmbeddingDimension(1024);

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chatModel must not be set for EMBEDDING capability");
    }

    @Test
    void shouldRejectCreateConfigWithoutCapability() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setName("Config");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("capability is required");
    }

    @Test
    void shouldRejectCreateConfigWithInvalidCapability() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("INVALID");
        dto.setName("Config");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid capability");
    }

    @Test
    void shouldUpdateWithoutApiKeyPreserveExistingEncryptedKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
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
        existing.setCapability("CHAT");
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
    void shouldTrimTextFieldsOnAdminUpdate() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("EMBEDDING");
        existing.setName("Old Name");
        existing.setProviderName("openai");
        existing.setBaseUrl("https://api.openai.com/v1");
        existing.setEmbeddingModel("text-embedding-v4");
        existing.setEmbeddingDimension(1024);
        existing.setApiKeyEncrypted("v1:old:encrypted");
        existing.setApiKeyMasked("sk-***old");
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        UpdateModelConfigDTO dto = new UpdateModelConfigDTO();
        dto.setName(" New Name ");
        dto.setProviderName(" openai-compatible ");
        dto.setBaseUrl(" https://dashscope.aliyuncs.com/compatible-mode/v1 ");
        dto.setEmbeddingModel(" text-embedding-v4 ");
        dto.setEmbeddingDimension(1024);

        modelConfigService.updateAdminConfig(10L, 100L, dto);

        verify(modelConfigMapper).updateById(entityCaptor.capture());
        ModelConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getProviderName()).isEqualTo("openai-compatible");
        assertThat(updated.getBaseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(updated.getEmbeddingModel()).isEqualTo("text-embedding-v4");
        assertThat(updated.getEmbeddingDimension()).isEqualTo(1024);
    }

    @Test
    void shouldRejectUpdateWithBlankApiKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
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
                .isInstanceOf(BusinessException.class)
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
    void shouldEnableDisabledConfigWithEncryptedKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
        existing.setChatModel("gpt-4o-mini");
        existing.setStatus("DISABLED");
        existing.setApiKeyEncrypted("v1:iv:ciphertext");
        existing.setApiKeyMasked("sk-...cret");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        ModelConfigVO vo = modelConfigService.enableAdminConfig(10L, 100L);

        verify(modelConfigMapper).updateById(entityCaptor.capture());
        ModelConfigEntity updated = entityCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo("ENABLED");
        assertThat(updated.getApiKeyEncrypted()).isEqualTo("v1:iv:ciphertext");
        assertThat(vo.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void shouldEnableAlreadyEnabledConfigIdempotently() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
        existing.setChatModel("gpt-4o-mini");
        existing.setStatus("ENABLED");
        existing.setApiKeyEncrypted("v1:iv:ciphertext");
        existing.setApiKeyMasked("sk-...cret");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        ModelConfigVO vo = modelConfigService.enableAdminConfig(10L, 100L);

        assertThat(vo.getStatus()).isEqualTo("ENABLED");
        verify(modelConfigMapper).updateById(any(ModelConfigEntity.class));
    }

    @Test
    void shouldRejectEnableConfigWithoutEncryptedKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
        existing.setChatModel("gpt-4o-mini");
        existing.setStatus("DISABLED");
        existing.setApiKeyEncrypted(null);
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> modelConfigService.enableAdminConfig(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot enable model config without an upstream API key");
    }

    @Test
    void shouldRejectEnableConfigWithBlankEncryptedKey() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
        existing.setChatModel("gpt-4o-mini");
        existing.setStatus("DISABLED");
        existing.setApiKeyEncrypted("  ");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> modelConfigService.enableAdminConfig(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot enable model config without an upstream API key");
    }

    @Test
    void shouldRejectEnableEmbeddingConfigWithoutDimension() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("EMBEDDING");
        existing.setEmbeddingModel("text-embedding-v4");
        existing.setEmbeddingDimension(null);
        existing.setStatus("DISABLED");
        existing.setApiKeyEncrypted("v1:iv:ciphertext");
        existing.setCreatedAt(LocalDateTime.now());
        existing.setUpdatedAt(LocalDateTime.now());

        when(modelConfigMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() -> modelConfigService.enableAdminConfig(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("positive embedding dimension");
    }

    @Test
    void shouldListConfigsByUserId() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setApiKeyMasked("sk-***");
        entity.setStatus("ENABLED");

        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<ModelConfigVO> result = modelConfigService.listAdminConfigs(100L, null, null);

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

        List<ModelConfigVO> result = modelConfigService.listAdminConfigs(100L, "ENABLED", null);

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldListConfigsByCapability() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setCapability("CHAT");
        entity.setApiKeyMasked("sk-***");
        entity.setStatus("ENABLED");

        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<ModelConfigVO> result = modelConfigService.listAdminConfigs(100L, "ENABLED", "CHAT");

        assertThat(result).hasSize(1);
        verify(modelConfigMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue()).isNotNull();
    }

    @Test
    void shouldRejectInvalidCapabilityFilter() {
        assertThatThrownBy(() -> modelConfigService.listAdminConfigs(100L, null, "INVALID"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid capability filter");
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
        entity.setCapability("EMBEDDING");
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
    void shouldTrimEmbeddingModelForEnabledEmbeddingLookup() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setCapability("EMBEDDING");
        entity.setEmbeddingModel("text-embedding-v4");
        entity.setEmbeddingDimension(1024);
        entity.setStatus("ENABLED");
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        ModelConfigEntity result = modelConfigService.findEnabledEmbeddingConfig(
                100L, " text-embedding-v4 ", 1024);

        assertThat(result).isSameAs(entity);
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
    void shouldPreferEnabledEmbeddingConfigForReadinessWhenDisabledConfigIsNewer() {
        ModelConfigEntity enabled = new ModelConfigEntity();
        enabled.setId(12L);
        enabled.setStatus(ModelConfigStatus.ENABLED.name());
        ModelConfigEntity disabled = new ModelConfigEntity();
        disabled.setId(13L);
        disabled.setStatus(ModelConfigStatus.DISABLED.name());
        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enabled, disabled));

        ModelConfigEntity result = modelConfigService.findMatchingEmbeddingConfig(
                100L, "text-embedding-3-small", 1536);

        assertThat(result).isSameAs(enabled);
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

    @Test
    void isChatCapableShouldReturnTrueForCHAT() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setCapability("CHAT");
        assertThat(modelConfigService.isChatCapable(entity)).isTrue();
    }

    @Test
    void isChatCapableShouldReturnFalseForCHAT_EMBEDDING() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setCapability("CHAT_EMBEDDING");
        assertThat(modelConfigService.isChatCapable(entity)).isFalse();
    }

    @Test
    void isChatCapableShouldReturnFalseForEMBEDDING() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setCapability("EMBEDDING");
        assertThat(modelConfigService.isChatCapable(entity)).isFalse();
    }

    @Test
    void isChatCapableShouldReturnFalseForNull() {
        assertThat(modelConfigService.isChatCapable(null)).isFalse();
    }

    @Test
    void shouldListEnabledChatCapableConfigs() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setCapability("CHAT");
        entity.setApiKeyMasked("sk-***");
        entity.setStatus("ENABLED");

        when(modelConfigMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<ModelConfigVO> result = modelConfigService.listEnabledChatCapableConfigs(100L);

        assertThat(result).hasSize(1);
        verify(modelConfigMapper).selectList(wrapperCaptor.capture());
    }

    @Test
    void shouldRejectUpdateWithCHAT_EMBEDDINGCapability() {
        ModelConfigEntity existing = new ModelConfigEntity();
        existing.setId(10L);
        existing.setUserId(100L);
        existing.setCapability("CHAT");
        existing.setName("Old Name");
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
        dto.setCapability("CHAT_EMBEDDING");

        assertThatThrownBy(() -> modelConfigService.updateAdminConfig(10L, 100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHAT_EMBEDDING is no longer supported");
    }

    @Test
    void parseCapabilityShouldRejectCHAT_EMBEDDING() {
        assertThatThrownBy(() -> ModelConfigService.parseCapability("CHAT_EMBEDDING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CHAT_EMBEDDING is no longer supported");
    }

    @Test
    void parseCapabilityShouldAcceptCHAT() {
        assertThat(ModelConfigService.parseCapability("CHAT")).isEqualTo(ModelConfigCapability.CHAT);
    }

    @Test
    void parseCapabilityShouldAcceptEMBEDDING() {
        assertThat(ModelConfigService.parseCapability("EMBEDDING")).isEqualTo(ModelConfigCapability.EMBEDDING);
    }

    @Test
    void shouldRejectCreateAdminCHATWithoutChatModel() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("CHAT");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chatModel is required for CHAT capability");
    }

    @Test
    void shouldRejectCreateAdminEMBEDDINGWithoutEmbeddingModel() {
        CreateModelConfigDTO dto = new CreateModelConfigDTO();
        dto.setCapability("EMBEDDING");
        dto.setName("Config");
        dto.setProviderName("openai");
        dto.setBaseUrl("https://api.openai.com/v1");
        dto.setApiKey("sk-secret");

        assertThatThrownBy(() -> modelConfigService.createAdminConfig(100L, dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("embeddingModel is required for EMBEDDING capability");
    }
}
