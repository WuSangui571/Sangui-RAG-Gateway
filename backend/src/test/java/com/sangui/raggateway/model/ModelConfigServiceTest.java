package com.sangui.raggateway.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelConfigServiceTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;

    @Captor
    private ArgumentCaptor<ModelConfigEntity> entityCaptor;

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<ModelConfigEntity>> wrapperCaptor;

    private ModelConfigService modelConfigService;

    @BeforeEach
    void setUp() {
        modelConfigService = new ModelConfigService(modelConfigMapper);
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
}
