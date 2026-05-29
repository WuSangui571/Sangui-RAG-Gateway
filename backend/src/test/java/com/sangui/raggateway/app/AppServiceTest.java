package com.sangui.raggateway.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private AppService appService;

    @BeforeEach
    void setUp() {
        appService = new AppService(appMapper, modelConfigService, knowledgeBaseService);
    }

    @Test
    void shouldResolveDefaultModelConfigWithAppUserBoundary() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultModelConfigId(10L);

        ModelConfigEntity modelConfig = new ModelConfigEntity();
        modelConfig.setId(10L);
        modelConfig.setUserId(100L);
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(modelConfig);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isSameAs(modelConfig);
        verify(modelConfigService).findEnabledByIdAndUserId(10L, 100L);
    }

    @Test
    void shouldNotResolveWhenDefaultModelConfigIsMissing() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isNull();
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void shouldReturnNullWhenDefaultModelConfigDoesNotMatchAppUser() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultModelConfigId(10L);
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(null);

        ModelConfigEntity result = appService.resolveDefaultModelConfig(app);

        assertThat(result).isNull();
        verify(modelConfigService).findEnabledByIdAndUserId(10L, 100L);
    }

    @Test
    void shouldBindDefaultModelConfigForSameUserEnabledConfig() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("ENABLED");
        when(appMapper.selectById(1L)).thenReturn(app);

        ModelConfigEntity modelConfig = new ModelConfigEntity();
        modelConfig.setId(10L);
        modelConfig.setUserId(100L);
        modelConfig.setStatus("ENABLED");
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(modelConfig);

        AppEntity result = appService.bindDefaultModelConfig(1L, 10L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getDefaultModelConfigId()).isEqualTo(10L);
        verify(appMapper).updateById(any(AppEntity.class));
    }

    @Test
    void shouldFailBindWhenConfigIsDisabled() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectById(1L)).thenReturn(app);

        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(null);

        AppEntity result = appService.bindDefaultModelConfig(1L, 10L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldFailBindForCrossUserConfig() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectById(1L)).thenReturn(app);

        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(null);

        AppEntity result = appService.bindDefaultModelConfig(1L, 10L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldFailBindForCrossUserApp() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(200L);
        when(appMapper.selectById(1L)).thenReturn(app);

        AppEntity result = appService.bindDefaultModelConfig(1L, 10L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldFailBindWhenAppNotFound() {
        when(appMapper.selectById(1L)).thenReturn(null);

        AppEntity result = appService.bindDefaultModelConfig(1L, 10L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldResolveDefaultKnowledgeBaseWithAppUserBoundary() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultKnowledgeBaseId(20L);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findByIdAndUserId(20L, 100L)).thenReturn(kb);

        KnowledgeBaseEntity result = appService.resolveDefaultKnowledgeBase(app);

        assertThat(result).isSameAs(kb);
        verify(knowledgeBaseService).findByIdAndUserId(20L, 100L);
    }

    @Test
    void shouldNotResolveWhenDefaultKnowledgeBaseIsMissing() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);

        KnowledgeBaseEntity result = appService.resolveDefaultKnowledgeBase(app);

        assertThat(result).isNull();
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void shouldNotResolveNonReadyDefaultKnowledgeBase() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setDefaultKnowledgeBaseId(20L);
        when(knowledgeBaseService.findByIdAndUserId(20L, 100L))
                .thenReturn(createKnowledgeBase(20L, 100L, "EMPTY"));

        KnowledgeBaseEntity result = appService.resolveDefaultKnowledgeBase(app);

        assertThat(result).isNull();
    }

    @Test
    void shouldBindDefaultKnowledgeBaseForSameUserReadyKb() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("ENABLED");
        when(appMapper.selectById(1L)).thenReturn(app);
        when(knowledgeBaseService.findByIdAndUserId(20L, 100L))
                .thenReturn(createKnowledgeBase(20L, 100L, "READY"));

        AppEntity result = appService.bindDefaultKnowledgeBase(1L, 20L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getDefaultKnowledgeBaseId()).isEqualTo(20L);
        verify(appMapper).updateById(any(AppEntity.class));
    }

    @Test
    void shouldFailBindDefaultKnowledgeBaseForCrossUserApp() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(200L);
        when(appMapper.selectById(1L)).thenReturn(app);

        AppEntity result = appService.bindDefaultKnowledgeBase(1L, 20L, 100L);

        assertThat(result).isNull();
        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void shouldFailBindDefaultKnowledgeBaseWhenKbMissingOrCrossUser() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectById(1L)).thenReturn(app);
        when(knowledgeBaseService.findByIdAndUserId(20L, 100L)).thenReturn(null);

        AppEntity result = appService.bindDefaultKnowledgeBase(1L, 20L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldFailBindDefaultKnowledgeBaseWhenKbNotReady() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectById(1L)).thenReturn(app);
        when(knowledgeBaseService.findByIdAndUserId(20L, 100L))
                .thenReturn(createKnowledgeBase(20L, 100L, "PROCESSING"));

        AppEntity result = appService.bindDefaultKnowledgeBase(1L, 20L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldCreateAppWithEnabledStatus() {
        ArgumentCaptor<AppEntity> captor = ArgumentCaptor.forClass(AppEntity.class);
        appService.create("Test App", 100L);

        verify(appMapper).insert(captor.capture());
        AppEntity created = captor.getValue();
        assertThat(created.getName()).isEqualTo("Test App");
        assertThat(created.getUserId()).isEqualTo(100L);
        assertThat(created.getStatus()).isEqualTo("ENABLED");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectCreateAppWithBlankName() {
        assertThatThrownBy(() -> appService.create(" ", 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectCreateAppWithInvalidUserId() {
        assertThatThrownBy(() -> appService.create("Test App", 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId must be a positive long");
    }

    @Test
    void shouldListAppsByUserId() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(app));

        List<AppEntity> result = appService.listByUserId(100L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldListAppsWithStatusFilter() {
        when(appMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<AppEntity> result = appService.listByUserId(100L, "ENABLED");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindByIdAndUserIdReturnApp() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        AppEntity result = appService.findByIdAndUserId(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldFindByIdAndUserIdReturnNullForCrossUser() {
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppEntity result = appService.findByIdAndUserId(1L, 999L);

        assertThat(result).isNull();
    }

    private KnowledgeBaseEntity createKnowledgeBase(Long id, Long userId, String status) {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(id);
        kb.setUserId(userId);
        kb.setName("Default KB");
        kb.setEmbeddingModel("text-embedding-v4");
        kb.setEmbeddingDimension(1536);
        kb.setStatus(status);
        return kb;
    }
}
