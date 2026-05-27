package com.sangui.raggateway.gateway.openai;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.model.ModelConfigEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OpenAiModelsControllerTest {

    @Mock
    private AppService appService;

    private MockMvc mockMvc;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long MODEL_CONFIG_ID = 10L;

    @BeforeEach
    void setUp() {
        OpenAiModelsController controller = new OpenAiModelsController(appService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        GatewayRequestContextHolder.clear();
    }

    private void setContext() {
        GatewayRequestContextHolder.set(new GatewayRequestContext(APP_ID, USER_ID, 30L, "sk-sangui-abcdef"));
    }

    @Test
    void shouldReturnModelListForValidAppWithEnabledConfig() throws Exception {
        setContext();

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDefaultModelConfigId(MODEL_CONFIG_ID);

        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(MODEL_CONFIG_ID);
        config.setUserId(USER_ID);
        config.setChatModel("gpt-4o-mini");
        config.setProviderName("openai");
        config.setStatus("ENABLED");

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("list"))
                .andExpect(jsonPath("$.data[0].id").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.data[0].object").value("model"))
                .andExpect(jsonPath("$.data[0].owned_by").value("openai"))
                .andExpect(jsonPath("$.data[0].created").value(0))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldNotContainUpstreamKeyInResponse() throws Exception {
        setContext();

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDefaultModelConfigId(MODEL_CONFIG_ID);

        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(MODEL_CONFIG_ID);
        config.setUserId(USER_ID);
        config.setChatModel("gpt-4o-mini");
        config.setProviderName("openai");
        config.setStatus("ENABLED");

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("sk-"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void shouldReturn409WhenNoDefaultModelConfig() throws Exception {
        setContext();

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDefaultModelConfigId(null);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(null);

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Default model config is not configured for this app."))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.error.code").value("model_config_not_ready"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldReturn409WhenModelConfigIsDisabled() throws Exception {
        setContext();

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDefaultModelConfigId(MODEL_CONFIG_ID);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(null);

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("model_config_not_ready"));
    }

    @Test
    void shouldReturn409WhenModelConfigHasDifferentUserId() throws Exception {
        setContext();

        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setDefaultModelConfigId(MODEL_CONFIG_ID);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(null);

        mockMvc.perform(get("/v1/models"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("model_config_not_ready"));
    }
}
