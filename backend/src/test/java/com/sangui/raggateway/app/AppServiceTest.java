package com.sangui.raggateway.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.vo.AppReadinessCheckVO;
import com.sangui.raggateway.app.vo.AppReadinessVO;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import com.sangui.raggateway.model.ModelConfigStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AppServiceTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ApiKeyService apiKeyService;

    private AppService appService;

    @BeforeEach
    void setUp() {
        appService = new AppService(appMapper, modelConfigService, knowledgeBaseService, apiKeyService);
        lenient().when(modelConfigService.isChatCapable(any())).thenReturn(true);
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
        modelConfig.setCapability("CHAT");
        modelConfig.setChatModel("gpt-4o-mini");
        modelConfig.setStatus("ENABLED");
        when(modelConfigService.findEnabledByIdAndUserId(10L, 100L)).thenReturn(modelConfig);
        when(modelConfigService.isChatCapable(modelConfig)).thenReturn(true);

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

    @Test
    void shouldDisableEnabledApp() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("ENABLED");
        app.setDefaultModelConfigId(10L);
        app.setDefaultKnowledgeBaseId(20L);
        app.setRetrievalTopK(5);
        app.setRetrievalSimilarityThreshold(0.3);
        app.setRetrievalMaxContextChunks(5);
        app.setRetrievalMaxContextChars(12000);
        app.setRetrievalMaxSingleChunkChars(3000);
        app.setNoHitPolicy("STRICT_RAG");
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        AppEntity result = appService.disableApp(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        ArgumentCaptor<AppEntity> captor = ArgumentCaptor.forClass(AppEntity.class);
        verify(appMapper).updateById(captor.capture());
        AppEntity updated = captor.getValue();
        assertThat(updated.getDefaultModelConfigId()).isEqualTo(10L);
        assertThat(updated.getDefaultKnowledgeBaseId()).isEqualTo(20L);
        assertThat(updated.getRetrievalTopK()).isEqualTo(5);
        assertThat(updated.getRetrievalSimilarityThreshold()).isEqualTo(0.3);
        assertThat(updated.getRetrievalMaxContextChunks()).isEqualTo(5);
        assertThat(updated.getRetrievalMaxContextChars()).isEqualTo(12000);
        assertThat(updated.getRetrievalMaxSingleChunkChars()).isEqualTo(3000);
        assertThat(updated.getNoHitPolicy()).isEqualTo("STRICT_RAG");
    }

    @Test
    void shouldDisableAlreadyDisabledAppIdempotently() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("DISABLED");
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        AppEntity result = appService.disableApp(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("DISABLED");
        verify(appMapper).updateById(any(AppEntity.class));
    }

    @Test
    void shouldReturnNullWhenDisableAppNotFound() {
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppEntity result = appService.disableApp(999L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenDisableAppBelongsToAnotherUser() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(200L);
        app.setStatus("ENABLED");
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppEntity result = appService.disableApp(1L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldEnableDisabledApp() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("DISABLED");
        app.setDefaultModelConfigId(10L);
        app.setDefaultKnowledgeBaseId(20L);
        app.setRetrievalTopK(5);
        app.setRetrievalSimilarityThreshold(0.3);
        app.setRetrievalMaxContextChunks(5);
        app.setRetrievalMaxContextChars(12000);
        app.setRetrievalMaxSingleChunkChars(3000);
        app.setNoHitPolicy("STRICT_RAG");
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        AppEntity result = appService.enableApp(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ENABLED");
        ArgumentCaptor<AppEntity> captor = ArgumentCaptor.forClass(AppEntity.class);
        verify(appMapper).updateById(captor.capture());
        AppEntity updated = captor.getValue();
        assertThat(updated.getDefaultModelConfigId()).isEqualTo(10L);
        assertThat(updated.getDefaultKnowledgeBaseId()).isEqualTo(20L);
        assertThat(updated.getRetrievalTopK()).isEqualTo(5);
        assertThat(updated.getRetrievalSimilarityThreshold()).isEqualTo(0.3);
        assertThat(updated.getRetrievalMaxContextChunks()).isEqualTo(5);
        assertThat(updated.getRetrievalMaxContextChars()).isEqualTo(12000);
        assertThat(updated.getRetrievalMaxSingleChunkChars()).isEqualTo(3000);
        assertThat(updated.getNoHitPolicy()).isEqualTo("STRICT_RAG");
    }

    @Test
    void shouldEnableAlreadyEnabledAppIdempotently() {
        AppEntity app = new AppEntity();
        app.setId(1L);
        app.setUserId(100L);
        app.setStatus("ENABLED");
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        AppEntity result = appService.enableApp(1L, 100L);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("ENABLED");
        verify(appMapper).updateById(any(AppEntity.class));
    }

    @Test
    void shouldReturnNullWhenEnableAppNotFound() {
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppEntity result = appService.enableApp(999L, 100L);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullWhenEnableAppBelongsToAnotherUser() {
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AppEntity result = appService.enableApp(1L, 100L);

        assertThat(result).isNull();
    }

    // ---- Readiness ----

    @Test
    void shouldReturnAllReadyForFullyPreparedApp() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);
        when(modelConfigService.isChatCapable(modelConfig)).thenReturn(true);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        assertThat(result.getOverallStatus()).isEqualTo("READY");
        assertThat(result.getChecks()).hasSize(6);
        assertThat(result.getChecks()).allMatch(c -> "READY".equals(c.getStatus()));
        assertThat(result.getAppId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldReportMissingDefaultModelConfig() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", null, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        assertThat(result.getOverallStatus()).isEqualTo("MISSING");
        AppReadinessCheckVO modelCheck = findCheck(result, "default_model_config");
        assertThat(modelCheck.getStatus()).isEqualTo("MISSING");
    }

    @Test
    void shouldReportDisabledApp() {
        AppEntity app = createAppFull(1L, 100L, "DISABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO appCheck = findCheck(result, "app");
        assertThat(appCheck.getStatus()).isEqualTo("DISABLED");
        assertThat(result.getOverallStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReportMissingDefaultKnowledgeBase() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, null);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO kbCheck = findCheck(result, "default_knowledge_base");
        assertThat(kbCheck.getStatus()).isEqualTo("MISSING");
        AppReadinessCheckVO kbStatusCheck = findCheck(result, "knowledge_base_status");
        assertThat(kbStatusCheck.getStatus()).isEqualTo("MISSING");
        AppReadinessCheckVO embeddingCheck = findCheck(result, "embedding_config");
        assertThat(embeddingCheck.getStatus()).isEqualTo("MISSING");
    }

    @Test
    void shouldReportNotReadyKnowledgeBase() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "PROCESSING");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO kbStatusCheck = findCheck(result, "knowledge_base_status");
        assertThat(kbStatusCheck.getStatus()).isEqualTo("NOT_READY");
        assertThat(result.getOverallStatus()).isEqualTo("NOT_READY");
    }

    @Test
    void shouldReportNoActiveApiKey() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(new ArrayList<>());

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO keyCheck = findCheck(result, "active_api_key");
        assertThat(keyCheck.getStatus()).isEqualTo("MISSING");
        assertThat(result.getOverallStatus()).isEqualTo("MISSING");
    }

    @Test
    void shouldReportDisabledApiKeyAsDisabled() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity disabledKey = createApiKey(1L, 1L, 100L, "DISABLED");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(disabledKey));
        when(apiKeyService.isValid(disabledKey)).thenReturn(false);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO keyCheck = findCheck(result, "active_api_key");
        assertThat(keyCheck.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReportMissingEmbeddingConfig() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(null);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO embeddingCheck = findCheck(result, "embedding_config");
        assertThat(embeddingCheck.getStatus()).isEqualTo("MISSING");
        assertThat(result.getOverallStatus()).isEqualTo("MISSING");
    }

    @Test
    void shouldReportDisabledDefaultModelConfig() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "DISABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "ENABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO modelCheck = findCheck(result, "default_model_config");
        assertThat(modelCheck.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReportDisabledEmbeddingConfig() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        ModelConfigEntity embeddingConfig = createEmbeddingConfig(30L, 100L, "DISABLED", "text-embedding-v4", 1536);
        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(embeddingConfig);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        AppReadinessCheckVO embeddingCheck = findCheck(result, "embedding_config");
        assertThat(embeddingCheck.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldNotContainForbiddenFieldsInReadinessResponse() {
        AppEntity app = createAppFull(1L, 100L, "ENABLED", 10L, 20L);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);

        ModelConfigEntity modelConfig = createModelConfig(10L, 100L, "ENABLED", "openai", "gpt-4o-mini");
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);

        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "FAILED");
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        when(modelConfigService.findMatchingEmbeddingConfig(100L, "text-embedding-v4", 1536))
                .thenReturn(null);

        ApiKeyEntity activeKey = createApiKey(1L, 1L, 100L, "ACTIVE");
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(activeKey));
        when(apiKeyService.isValid(activeKey)).thenReturn(true);

        AppReadinessVO result = appService.assembleReadiness(1L, 100L);

        for (AppReadinessCheckVO check : result.getChecks()) {
            if (check.getMetadata() != null) {
                assertThat(check.getMetadata()).doesNotContainKey("api_key");
                assertThat(check.getMetadata()).doesNotContainKey("key_hash");
                assertThat(check.getMetadata()).doesNotContainKey("api_key_encrypted");
                assertThat(check.getMetadata()).doesNotContainKey("upstream_api_key");
                assertThat(check.getMetadata()).doesNotContainKey("authorization");
                assertThat(check.getMetadata()).doesNotContainKey("storage_path");
                assertThat(check.getMetadata()).doesNotContainKey("stack_trace");
            }
        }
    }

    private AppReadinessCheckVO findCheck(AppReadinessVO vo, String key) {
        return vo.getChecks().stream()
                .filter(c -> key.equals(c.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Check not found: " + key));
    }

    private AppEntity createAppFull(Long id, Long userId, String status, Long modelConfigId, Long kbId) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setName("Test App");
        app.setStatus(status);
        app.setDefaultModelConfigId(modelConfigId);
        app.setDefaultKnowledgeBaseId(kbId);
        return app;
    }

    private ModelConfigEntity createModelConfig(Long id, Long userId, String status, String providerName, String chatModel) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(id);
        config.setUserId(userId);
        config.setName("Test Config");
        config.setProviderName(providerName);
        config.setBaseUrl("https://api.example.com");
        config.setCapability("CHAT");
        config.setChatModel(chatModel);
        config.setStatus(status);
        return config;
    }

    private ModelConfigEntity createEmbeddingConfig(Long id, Long userId, String status, String embeddingModel, Integer embeddingDimension) {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(id);
        config.setUserId(userId);
        config.setName("Embedding Config");
        config.setProviderName("openai");
        config.setBaseUrl("https://api.example.com");
        config.setCapability("EMBEDDING");
        config.setChatModel(null);
        config.setEmbeddingModel(embeddingModel);
        config.setEmbeddingDimension(embeddingDimension);
        config.setApiKeyEncrypted("encrypted-key-data");
        config.setStatus(status);
        return config;
    }

    private ApiKeyEntity createApiKey(Long id, Long appId, Long userId, String status) {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(id);
        key.setAppId(appId);
        key.setUserId(userId);
        key.setName("Test Key");
        key.setKeyHash("hash");
        key.setKeyPrefix("sk-sangui-test");
        key.setStatus(status);
        return key;
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
