package com.sangui.raggateway.app;

import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.apikey.dto.CreateApiKeyDTO;
import com.sangui.raggateway.apikey.dto.CreateApiKeyResult;
import com.sangui.raggateway.app.vo.AppReadinessCheckVO;
import com.sangui.raggateway.app.vo.AppReadinessVO;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AppAdminControllerTest {

    @Mock
    private AppService appService;

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppAdminController controller = new AppAdminController(appService, modelConfigService, apiKeyService, knowledgeBaseService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ---- Create App ----

    @Test
    void shouldCreateApp() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.create(eq("Demo App"), eq(100L))).thenReturn(app);

        mockMvc.perform(post("/api/admin/apps")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Demo App"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.name").value("Demo App"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        verify(appService).create("Demo App", 100L);
    }

    @Test
    void shouldRejectCreateAppWithBlankName() throws Exception {
        mockMvc.perform(post("/api/admin/apps")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "  "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(appService);
    }

    @Test
    void shouldRejectCreateAppWithNullBody() throws Exception {
        mockMvc.perform(post("/api/admin/apps")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(appService);
    }

    // ---- List Apps ----

    @Test
    void shouldListSameUserApps() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.listByUserId(eq(100L), isNull())).thenReturn(List.of(app));

        mockMvc.perform(get("/api/admin/apps")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].user_id").value(100));
    }

    @Test
    void shouldListAppsWithStatusFilter() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.listByUserId(eq(100L), eq("ENABLED"))).thenReturn(List.of(app));

        mockMvc.perform(get("/api/admin/apps?status=ENABLED")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ENABLED"));
    }

    @Test
    void shouldRejectInvalidAppStatusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/apps?status=INVALID")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- Get App Detail ----

    @Test
    void shouldGetAppDetailForSameUser() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(get("/api/admin/apps/1")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100));
    }

    @Test
    void shouldReturn404ForNonExistentApp() throws Exception {
        when(appService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/apps/999")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(get("/api/admin/apps/1")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ---- Create API Key ----

    @Test
    void shouldCreateApiKeyAndReturnPlaintextOnce() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiKeyEntity keyEntity = createKeyEntity(10L, 1L, 100L);
        CreateApiKeyResult result = new CreateApiKeyResult("sk-sangui-plaintext-once", keyEntity);
        when(apiKeyService.create(eq(1L), eq(100L), eq("Production Key"), isNull()))
                .thenReturn(result);

        mockMvc.perform(post("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Production Key"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.key").value("sk-sangui-plaintext-once"))
                .andExpect(jsonPath("$.data.key_prefix").value("sk-sang-abc12345"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.key_hash").doesNotExist());
    }

    @Test
    void shouldRejectCreateKeyForCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(post("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Production Key"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectCreateKeyForNonExistentApp() throws Exception {
        when(appService.findByIdAndUserId(999L, 100L)).thenReturn(null);
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/apps/999/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Production Key"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldRejectCreateKeyWithBlankName() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        mockMvc.perform(post("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectCreateKeyWithNullBody() throws Exception {
        mockMvc.perform(post("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(appService);
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void shouldRejectCreateKeyWithPastExpiry() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);
        when(apiKeyService.create(eq(1L), eq(100L), eq("Production Key"), any(LocalDateTime.class)))
                .thenThrow(new IllegalArgumentException("expiresAt must be in the future"));

        mockMvc.perform(post("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Production Key",
                                    "expires_at": "2026-01-01T00:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // ---- List API Keys ----

    @Test
    void shouldListApiKeysWithoutPlaintextOrHash() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        ApiKeyEntity keyEntity = createKeyEntity(10L, 1L, 100L);
        when(apiKeyService.listByAppIdAndUserId(1L, 100L)).thenReturn(List.of(keyEntity));

        mockMvc.perform(get("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].key_prefix").value("sk-sang-abc12345"))
                .andExpect(jsonPath("$.data[0].key").doesNotExist())
                .andExpect(jsonPath("$.data[0].key_hash").doesNotExist());
    }

    @Test
    void shouldReturn403ForListKeysOfCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(get("/api/admin/apps/1/api-keys")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(apiKeyService);
    }

    // ---- Admin identity validation ----

    @Test
    void shouldRejectMissingAdminUserIdHeader() throws Exception {
        mockMvc.perform(post("/api/admin/apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Demo App"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectNonPositiveAdminUserId() throws Exception {
        mockMvc.perform(post("/api/admin/apps")
                        .header("X-Admin-User-Id", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Demo App"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(appService);
    }

    // ---- Existing bind model config tests (preserved) ----

    @Test
    void shouldBindDefaultModelConfigWithSnakeCaseRequest() throws Exception {
        AppEntity app = createApp(1L, 100L);
        ModelConfigEntity modelConfig = createModelConfig(10L, 100L);
        AppEntity updated = createApp(1L, 100L);
        updated.setDefaultModelConfigId(10L);

        when(appService.findById(1L)).thenReturn(app);
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);
        when(modelConfigService.isChatCapable(modelConfig)).thenReturn(true);
        when(appService.bindDefaultModelConfig(1L, 10L, 100L)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/apps/1/default-model-config")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "model_config_id": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.app_id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.default_model_config_id").value(10));

        verify(appService).bindDefaultModelConfig(1L, 10L, 100L);
    }

    @Test
    void shouldRejectMissingModelConfigId() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findById(1L)).thenReturn(app);

        mockMvc.perform(put("/api/admin/apps/1/default-model-config")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(modelConfigService);
    }

    @Test
    void shouldRejectNullBindModelConfigBody() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/default-model-config")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(appService);
        verifyNoInteractions(modelConfigService);
    }

    @Test
    void shouldRejectCrossUserModelConfig() throws Exception {
        AppEntity app = createApp(1L, 100L);
        ModelConfigEntity otherUserConfig = createModelConfig(10L, 200L);

        when(appService.findById(1L)).thenReturn(app);
        when(modelConfigService.findById(10L)).thenReturn(otherUserConfig);

        mockMvc.perform(put("/api/admin/apps/1/default-model-config")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "model_config_id": 10
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldRejectDisabledModelConfig() throws Exception {
        AppEntity app = createApp(1L, 100L);
        ModelConfigEntity modelConfig = createModelConfig(10L, 100L);

        when(appService.findById(1L)).thenReturn(app);
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);
        when(modelConfigService.isChatCapable(modelConfig)).thenReturn(true);

        mockMvc.perform(put("/api/admin/apps/1/default-model-config")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "model_config_id": 10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_CONFIG_NOT_READY"));
    }

    // ---- Bind default knowledge base ----

    @Test
    void shouldBindDefaultKnowledgeBaseWithSnakeCaseRequest() throws Exception {
        AppEntity app = createApp(1L, 100L);
        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "READY");
        AppEntity updated = createApp(1L, 100L);
        updated.setDefaultKnowledgeBaseId(20L);

        when(appService.findById(1L)).thenReturn(app);
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);
        when(appService.bindDefaultKnowledgeBase(1L, 20L, 100L)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/apps/1/knowledge-base")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "knowledge_base_id": 20
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.app_id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.default_knowledge_base_id").value(20));

        verify(appService).bindDefaultKnowledgeBase(1L, 20L, 100L);
    }

    @Test
    void shouldRejectMissingKnowledgeBaseId() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findById(1L)).thenReturn(app);

        mockMvc.perform(put("/api/admin/apps/1/knowledge-base")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(knowledgeBaseService);
    }

    @Test
    void shouldReturn404ForMissingKnowledgeBaseWhenBinding() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findById(1L)).thenReturn(app);
        when(knowledgeBaseService.findById(20L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/apps/1/knowledge-base")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "knowledge_base_id": 20
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(appService, never()).bindDefaultKnowledgeBase(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectCrossUserKnowledgeBaseWhenBinding() throws Exception {
        AppEntity app = createApp(1L, 100L);
        KnowledgeBaseEntity otherUserKb = createKnowledgeBase(20L, 200L, "READY");
        when(appService.findById(1L)).thenReturn(app);
        when(knowledgeBaseService.findById(20L)).thenReturn(otherUserKb);

        mockMvc.perform(put("/api/admin/apps/1/knowledge-base")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "knowledge_base_id": 20
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Access denied"));

        verify(appService, never()).bindDefaultKnowledgeBase(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldRejectNonReadyKnowledgeBaseWhenBinding() throws Exception {
        AppEntity app = createApp(1L, 100L);
        KnowledgeBaseEntity kb = createKnowledgeBase(20L, 100L, "PROCESSING");
        when(appService.findById(1L)).thenReturn(app);
        when(knowledgeBaseService.findById(20L)).thenReturn(kb);

        mockMvc.perform(put("/api/admin/apps/1/knowledge-base")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "knowledge_base_id": 20
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_BASE_NOT_READY"));

        verify(appService, never()).bindDefaultKnowledgeBase(anyLong(), anyLong(), anyLong());
    }

    // ---- Disable App ----

    @Test
    void shouldDisableEnabledApp() throws Exception {
        AppEntity app = createApp(1L, 100L);
        AppEntity disabled = createApp(1L, 100L);
        disabled.setStatus("DISABLED");
        when(appService.disableApp(1L, 100L)).thenReturn(disabled);

        mockMvc.perform(post("/api/admin/apps/1/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        verify(appService).disableApp(1L, 100L);
    }

    @Test
    void shouldReturn404WhenDisableNonExistentApp() throws Exception {
        when(appService.disableApp(999L, 100L)).thenReturn(null);
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/apps/999/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenDisableCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.disableApp(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(post("/api/admin/apps/1/disable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- Enable App ----

    @Test
    void shouldEnableDisabledApp() throws Exception {
        AppEntity enabled = createApp(1L, 100L);
        when(appService.enableApp(1L, 100L)).thenReturn(enabled);

        mockMvc.perform(post("/api/admin/apps/1/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));

        verify(appService).enableApp(1L, 100L);
    }

    @Test
    void shouldReturn404WhenEnableNonExistentApp() throws Exception {
        when(appService.enableApp(999L, 100L)).thenReturn(null);
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(post("/api/admin/apps/999/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenEnableCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.enableApp(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(post("/api/admin/apps/1/enable")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---- Update Output Capture ----

    @Test
    void shouldUpdateOutputCaptureToTrue() throws Exception {
        AppEntity app = createApp(1L, 100L);
        AppEntity updated = createApp(1L, 100L);
        updated.setRequestLogOutputCaptureEnabled(true);

        when(appService.findById(1L)).thenReturn(app);
        when(appService.updateOutputCapture(1L, true, 100L)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.request_log_output_capture_enabled").value(true));
    }

    @Test
    void shouldUpdateOutputCaptureToFalse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        AppEntity updated = createApp(1L, 100L);
        updated.setRequestLogOutputCaptureEnabled(false);

        when(appService.findById(1L)).thenReturn(app);
        when(appService.updateOutputCapture(1L, false, 100L)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.request_log_output_capture_enabled").value(false));
    }

    @Test
    void shouldRejectOutputCaptureWithNullBody() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectOutputCaptureWithMissingField() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn404ForOutputCaptureOnMissingApp() throws Exception {
        when(appService.findById(999L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/apps/999/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForOutputCaptureOnCrossUserApp() throws Exception {
        AppEntity otherUserApp = createApp(1L, 200L);
        when(appService.findById(1L)).thenReturn(otherUserApp);

        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn404WhenOutputCaptureUpdateMissesAfterOwnershipCheck() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findById(1L)).thenReturn(app, null);
        when(appService.updateOutputCapture(1L, true, 100L)).thenReturn(null);

        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldNotContainForbiddenFieldsInOutputCaptureResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        AppEntity updated = createApp(1L, 100L);
        updated.setRequestLogOutputCaptureEnabled(true);

        when(appService.findById(1L)).thenReturn(app);
        when(appService.updateOutputCapture(1L, true, 100L)).thenReturn(updated);

        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"output_preview\""))))
                .andExpect(content().string(not(containsString("\"api_key\""))))
                .andExpect(content().string(not(containsString("\"key_hash\""))))
                .andExpect(content().string(not(containsString("\"api_key_encrypted\""))))
                .andExpect(content().string(not(containsString("\"upstream_api_key\""))))
                .andExpect(content().string(not(containsString("\"stack_trace\""))));
    }

    @Test
    void shouldRejectOutputCaptureWithNonNumericUserId() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "abc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectOutputCaptureWithNonPositiveUserId() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .header("X-Admin-User-Id", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectOutputCaptureWithMissingUserId() throws Exception {
        mockMvc.perform(put("/api/admin/apps/1/request-log-output-capture")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "request_log_output_capture_enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldIncludeOutputCaptureInAppListResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.listByUserId(eq(100L), isNull())).thenReturn(List.of(app));

        mockMvc.perform(get("/api/admin/apps")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].request_log_output_capture_enabled").value(false));
    }

    @Test
    void shouldIncludeOutputCaptureInCreateAppResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.create(eq("Demo App"), eq(100L))).thenReturn(app);

        mockMvc.perform(post("/api/admin/apps")
                        .header("X-Admin-User-Id", "100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Demo App"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_log_output_capture_enabled").value(false));
    }

    // ---- Readiness ----

    @Test
    void shouldReturnReadinessForOwnApp() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        AppReadinessVO readiness = new AppReadinessVO(1L, 100L, AppReadinessStatus.READY,
                List.of(new AppReadinessCheckVO("app", "App", AppReadinessStatus.READY, "App is enabled.", null)));
        when(appService.assembleReadiness(1L, 100L)).thenReturn(readiness);

        mockMvc.perform(get("/api/admin/apps/1/readiness")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.app_id").value(1))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.overall_status").value("READY"))
                .andExpect(jsonPath("$.data.checks").isArray())
                .andExpect(jsonPath("$.data.checks[0].key").value("app"))
                .andExpect(jsonPath("$.data.checks[0].status").value("READY"));
    }

    @Test
    void shouldReturn403ForCrossUserReadiness() throws Exception {
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(createApp(1L, 200L));

        mockMvc.perform(get("/api/admin/apps/1/readiness")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn404ForMissingAppReadiness() throws Exception {
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(null);
        when(appService.findById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/apps/1/readiness")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldRejectReadinessWithMissingUserId() throws Exception {
        mockMvc.perform(get("/api/admin/apps/1/readiness"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldRejectReadinessWithNegativeUserId() throws Exception {
        mockMvc.perform(get("/api/admin/apps/1/readiness")
                        .header("X-Admin-User-Id", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldNotContainForbiddenFieldsInReadinessResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.findByIdAndUserId(1L, 100L)).thenReturn(app);

        AppReadinessVO readiness = new AppReadinessVO(1L, 100L, AppReadinessStatus.READY,
                List.of(new AppReadinessCheckVO("app", "App", AppReadinessStatus.READY, "App is enabled.", null)));
        when(appService.assembleReadiness(1L, 100L)).thenReturn(readiness);

        mockMvc.perform(get("/api/admin/apps/1/readiness")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"api_key\""))))
                .andExpect(content().string(not(containsString("\"key_hash\""))))
                .andExpect(content().string(not(containsString("\"api_key_encrypted\""))))
                .andExpect(content().string(not(containsString("\"upstream_api_key\""))))
                .andExpect(content().string(not(containsString("\"stack_trace\""))));
    }

    // ---- Secret safety ----

    @Test
    void shouldNotContainKeyOrHashInListAppsResponse() throws Exception {
        AppEntity app = createApp(1L, 100L);
        when(appService.listByUserId(eq(100L), isNull())).thenReturn(List.of(app));

        mockMvc.perform(get("/api/admin/apps")
                        .header("X-Admin-User-Id", "100"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("\"key\""))))
                .andExpect(content().string(not(containsString("\"key_hash\""))));
    }

    private AppEntity createApp(Long id, Long userId) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setName("Demo App");
        app.setStatus("ENABLED");
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        return app;
    }

    private ModelConfigEntity createModelConfig(Long id, Long userId) {
        ModelConfigEntity modelConfig = new ModelConfigEntity();
        modelConfig.setId(id);
        modelConfig.setUserId(userId);
        modelConfig.setStatus("ENABLED");
        return modelConfig;
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

    private ApiKeyEntity createKeyEntity(Long id, Long appId, Long userId) {
        ApiKeyEntity key = new ApiKeyEntity();
        key.setId(id);
        key.setAppId(appId);
        key.setUserId(userId);
        key.setName("Production Key");
        key.setKeyPrefix("sk-sang-abc12345");
        key.setStatus("ACTIVE");
        key.setCreatedAt(LocalDateTime.now());
        key.setUpdatedAt(LocalDateTime.now());
        return key;
    }
}
