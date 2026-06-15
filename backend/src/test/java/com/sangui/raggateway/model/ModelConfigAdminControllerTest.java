package com.sangui.raggateway.model;

import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.AdminAuthContext;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.model.dto.CreateModelConfigDTO;
import com.sangui.raggateway.model.dto.UpdateModelConfigDTO;
import com.sangui.raggateway.model.vo.ModelConfigVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ModelConfigAdminControllerTest {

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private ModelConfigCheckService modelConfigCheckService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ModelConfigAdminController controller = new ModelConfigAdminController(modelConfigService, modelConfigCheckService);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @BeforeEach
    void setUpAuthContext() {
        AdminAuthContextHolder.set(new AdminAuthContext(100L, "testuser"));
    }

    @AfterEach
    void tearDownAuthContext() {
        AdminAuthContextHolder.clear();
    }

    @Test
    void shouldCreateConfigAndReturnMaskedVO() throws Exception {
        when(modelConfigService.createAdminConfig(eq(100L), any(CreateModelConfigDTO.class)))
                .thenReturn(createTestVO(10L, 100L, "sk-...cret", "ENABLED"));

        mockMvc.perform(post("/api/admin/model-configs")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Default OpenAI",
                                    "provider_name": "openai",
                                    "base_url": "https://api.openai.com/v1",
                                    "api_key": "sk-upstream-secret",
                                    "chat_model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.user_id").value(100))
                .andExpect(jsonPath("$.data.api_key_masked").value("sk-...cret"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"))
                .andExpect(jsonPath("$.data.api_key").doesNotExist())
                .andExpect(jsonPath("$.data.api_key_encrypted").doesNotExist());

        ArgumentCaptor<CreateModelConfigDTO> dtoCaptor = ArgumentCaptor.forClass(CreateModelConfigDTO.class);
        verify(modelConfigService).createAdminConfig(eq(100L), dtoCaptor.capture());
        CreateModelConfigDTO dto = dtoCaptor.getValue();
        assertThat(dto.getProviderName()).isEqualTo("openai");
        assertThat(dto.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(dto.getApiKey()).isEqualTo("sk-upstream-secret");
        assertThat(dto.getChatModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldRejectCreateWithoutAdminUserIdHeader() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/model-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Default OpenAI",
                                    "provider_name": "openai",
                                    "base_url": "https://api.openai.com/v1",
                                    "api_key": "sk-secret",
                                    "chat_model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldRejectCreateWithNonPositiveUserId() throws Exception {
        AdminAuthContextHolder.clear();

        mockMvc.perform(post("/api/admin/model-configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Default OpenAI",
                                    "provider_name": "openai",
                                    "base_url": "https://api.openai.com/v1",
                                    "api_key": "sk-secret",
                                    "chat_model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(modelConfigService);
    }

    @Test
    void shouldReturnConfigDetailForSameUser() throws Exception {
        ModelConfigEntity entityForDetail = createTestEntity(10L, 100L);
        when(modelConfigService.findById(10L)).thenReturn(entityForDetail);
        when(modelConfigService.findAdminDetail(10L, 100L))
                .thenReturn(createTestVO(10L, 100L, "sk-...cret", "ENABLED"));

        mockMvc.perform(get("/api/admin/model-configs/10")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.api_key_masked").value("sk-...cret"))
                .andExpect(jsonPath("$.data.api_key").doesNotExist())
                .andExpect(jsonPath("$.data.api_key_encrypted").doesNotExist());
    }

    @Test
    void shouldReturn404ForNonExistentConfig() throws Exception {
        when(modelConfigService.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/api/admin/model-configs/999")
                        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403ForDifferentUserConfig() throws Exception {
        ModelConfigEntity otherUserEntity = createTestEntity(10L, 200L);
        when(modelConfigService.findById(10L)).thenReturn(otherUserEntity);

        mockMvc.perform(get("/api/admin/model-configs/10")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void shouldListConfigsForUser() throws Exception {
        when(modelConfigService.listAdminConfigs(100L, null, null))
                .thenReturn(List.of(createTestVO(10L, 100L, "sk-...cret", "ENABLED")));

        mockMvc.perform(get("/api/admin/model-configs")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].api_key_masked").value("sk-...cret"));
    }

    @Test
    void shouldListConfigsWithStatusFilter() throws Exception {
        when(modelConfigService.listAdminConfigs(100L, "ENABLED", null))
                .thenReturn(List.of(createTestVO(10L, 100L, "sk-...cret", "ENABLED")));

        mockMvc.perform(get("/api/admin/model-configs?status=ENABLED")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ENABLED"));
    }

    @Test
    void shouldRejectInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/admin/model-configs?status=INVALID")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldUpdateConfigForSameUser() throws Exception {
        ModelConfigEntity entity = createTestEntity(10L, 100L);
        when(modelConfigService.findById(10L)).thenReturn(entity);
        when(modelConfigService.updateAdminConfig(eq(10L), eq(100L), any(UpdateModelConfigDTO.class)))
                .thenReturn(createTestVO(10L, 100L, "sk-...cret", "ENABLED"));

        mockMvc.perform(put("/api/admin/model-configs/10")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void shouldReturn403ForUpdateOfDifferentUserConfig() throws Exception {
        ModelConfigEntity otherUserEntity = createTestEntity(10L, 200L);
        when(modelConfigService.findById(10L)).thenReturn(otherUserEntity);

        mockMvc.perform(put("/api/admin/model-configs/10")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Updated"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldDisableConfigForSameUser() throws Exception {
        ModelConfigEntity entity = createTestEntity(10L, 100L);
        when(modelConfigService.findById(10L)).thenReturn(entity);
        when(modelConfigService.disableAdminConfig(10L, 100L))
                .thenReturn(createTestVO(10L, 100L, "sk-...cret", "DISABLED"));

        mockMvc.perform(post("/api/admin/model-configs/10/disable")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void shouldReturn403ForDisableOfDifferentUserConfig() throws Exception {
        ModelConfigEntity otherUserEntity = createTestEntity(10L, 200L);
        when(modelConfigService.findById(10L)).thenReturn(otherUserEntity);

        mockMvc.perform(post("/api/admin/model-configs/10/disable")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldEnableConfigForSameUser() throws Exception {
        ModelConfigEntity entity = createTestEntity(10L, 100L);
        entity.setStatus("DISABLED");
        when(modelConfigService.findById(10L)).thenReturn(entity);
        when(modelConfigService.enableAdminConfig(10L, 100L))
                .thenReturn(createTestVO(10L, 100L, "sk-...cret", "ENABLED"));

        mockMvc.perform(post("/api/admin/model-configs/10/enable")
                        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.status").value("ENABLED"));
    }

    @Test
    void shouldReturn403ForEnableOfDifferentUserConfig() throws Exception {
        ModelConfigEntity otherUserEntity = createTestEntity(10L, 200L);
        when(modelConfigService.findById(10L)).thenReturn(otherUserEntity);

        mockMvc.perform(post("/api/admin/model-configs/10/enable")
                        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldReturn400WhenEnableConfigWithoutKey() throws Exception {
        ModelConfigEntity entity = createTestEntity(10L, 100L);
        entity.setStatus("DISABLED");
        entity.setApiKeyEncrypted(null);
        when(modelConfigService.findById(10L)).thenReturn(entity);
        when(modelConfigService.enableAdminConfig(10L, 100L))
                .thenThrow(new IllegalArgumentException("Cannot enable model config without an upstream API key"));

        mockMvc.perform(post("/api/admin/model-configs/10/enable")
                        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void shouldNotContainPlaintextOrEncryptedKeyInAnyResponse() throws Exception {
        when(modelConfigService.listAdminConfigs(100L, null, null))
                .thenReturn(List.of(createTestVO(10L, 100L, "sk-...cret", "ENABLED")));

        mockMvc.perform(get("/api/admin/model-configs")
                        )
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("sk-upstream"))))
                .andExpect(content().string(not(containsString("v1:"))))
                .andExpect(content().string(not(containsString("api_key_encrypted"))));
    }

    @Test
    void shouldReturnApiResponseForCreateValidationError() throws Exception {
        when(modelConfigService.createAdminConfig(eq(100L), any(CreateModelConfigDTO.class)))
                .thenThrow(new IllegalArgumentException("name is required"));

        mockMvc.perform(post("/api/admin/model-configs")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "",
                                    "provider_name": "openai",
                                    "base_url": "https://api.openai.com/v1",
                                    "api_key": "sk-secret",
                                    "chat_model": "gpt-4o-mini"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldRejectCreateWithCHAT_EMBEDDINGCapability() throws Exception {
        when(modelConfigService.createAdminConfig(eq(100L), any(CreateModelConfigDTO.class)))
                .thenThrow(new IllegalArgumentException("CHAT_EMBEDDING is no longer supported. Use CHAT or EMBEDDING."));

        mockMvc.perform(post("/api/admin/model-configs")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "capability": "CHAT_EMBEDDING",
                                    "name": "Mixed config",
                                    "provider_name": "openai",
                                    "base_url": "https://api.openai.com/v1",
                                    "api_key": "sk-secret",
                                    "chat_model": "gpt-4o-mini",
                                    "embedding_model": "text-embedding-v4",
                                    "embedding_dimension": 1024
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("CHAT_EMBEDDING is no longer supported. Use CHAT or EMBEDDING."));
    }

    @Test
    void shouldRejectCheckUnsavedWithCHAT_EMBEDDINGCapability() throws Exception {
        when(modelConfigCheckService.checkUnsavedConfig(eq(100L), any(ModelConfigCheckRequest.class)))
                .thenThrow(new IllegalArgumentException("CHAT_EMBEDDING is no longer supported. Use CHAT or EMBEDDING."));

        mockMvc.perform(post("/api/admin/model-configs/check")

                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "capability": "CHAT_EMBEDDING",
                                    "base_url": "https://api.example.com/v1",
                                    "api_key": "sk-test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private ModelConfigVO createTestVO(Long id, Long userId, String apiKeyMasked, String status) {
        ModelConfigVO vo = new ModelConfigVO();
        // Use ModelConfigEntity to build the VO via from()
        ModelConfigEntity entity = createTestEntity(id, userId);
        entity.setApiKeyMasked(apiKeyMasked);
        entity.setStatus(status);
        return ModelConfigVO.from(entity);
    }

    private ModelConfigEntity createTestEntity(Long id, Long userId) {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName("Test Config");
        entity.setProviderName("openai");
        entity.setBaseUrl("https://api.openai.com/v1");
        entity.setApiKeyEncrypted("v1:encrypted:data");
        entity.setApiKeyMasked("sk-...cret");
        entity.setChatModel("gpt-4o-mini");
        entity.setStatus("ENABLED");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
