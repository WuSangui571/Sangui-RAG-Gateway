package com.sangui.raggateway.app;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AppAdminControllerTest {

    @Mock
    private AppService appService;

    @Mock
    private ModelConfigService modelConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppAdminController controller = new AppAdminController(appService, modelConfigService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldBindDefaultModelConfigWithSnakeCaseRequest() throws Exception {
        AppEntity app = createApp(1L, 100L);
        ModelConfigEntity modelConfig = createModelConfig(10L, 100L);
        AppEntity updated = createApp(1L, 100L);
        updated.setDefaultModelConfigId(10L);

        when(appService.findById(1L)).thenReturn(app);
        when(modelConfigService.findById(10L)).thenReturn(modelConfig);
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
        when(appService.bindDefaultModelConfig(1L, 10L, 100L)).thenReturn(null);

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

    private AppEntity createApp(Long id, Long userId) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setUserId(userId);
        app.setStatus("ENABLED");
        return app;
    }

    private ModelConfigEntity createModelConfig(Long id, Long userId) {
        ModelConfigEntity modelConfig = new ModelConfigEntity();
        modelConfig.setId(id);
        modelConfig.setUserId(userId);
        modelConfig.setStatus("ENABLED");
        return modelConfig;
    }
}
