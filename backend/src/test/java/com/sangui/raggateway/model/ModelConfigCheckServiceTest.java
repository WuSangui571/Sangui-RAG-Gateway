package com.sangui.raggateway.model;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingProbeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@ExtendWith(MockitoExtension.class)
class ModelConfigCheckServiceTest {

    private static final String BASE_URL = "https://api.example.com";
    private static final String API_KEY = "sk-upstream";

    @Mock
    private ModelConfigService modelConfigService;

    @Mock
    private UpstreamApiKeyEncryptor encryptor;

    @Mock
    private EmbeddingClient embeddingClient;

    private MockRestServiceServer mockServer;
    private ModelConfigCheckService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new ModelConfigCheckService(modelConfigService, encryptor, embeddingClient, builder.build());
    }

    @Test
    void shouldReturnSuccessForEmbeddingOnlyProbe() {
        when(embeddingClient.probe(BASE_URL, API_KEY, "text-embedding-v4"))
                .thenReturn(new EmbeddingProbeResult("text-embedding-v4", 1024));

        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setCapability("EMBEDDING");
        request.setBaseUrl(BASE_URL);
        request.setApiKey(API_KEY);
        request.setEmbeddingModel("text-embedding-v4");

        ModelConfigCheckResult result = service.checkUnsavedConfig(100L, request);

        assertThat(result.getOverallStatus()).isEqualTo("SUCCESS");
        assertThat(result.getChat()).isNull();
        assertThat(result.getEmbedding().getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getEmbedding().getActualDimension()).isEqualTo(1024);
    }

    @Test
    void shouldFailChatCheckOnUpstreamNon2xx() {
        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"invalid key\"}}"));

        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setCapability("CHAT");
        request.setBaseUrl(BASE_URL);
        request.setApiKey(API_KEY);
        request.setChatModel("deepseek-v4-pro");

        ModelConfigCheckResult result = service.checkUnsavedConfig(100L, request);

        assertThat(result.getOverallStatus()).isEqualTo("FAILED");
        assertThat(result.getChat().getStatus()).isEqualTo("FAILED");
        assertThat(result.getChat().getMessage()).contains("upstream_error");
        mockServer.verify();
    }

    @Test
    void shouldRejectMissingCapability() {
        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setBaseUrl(BASE_URL);
        request.setApiKey(API_KEY);

        assertThatThrownBy(() -> service.checkUnsavedConfig(100L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capability is required");
    }

    @Test
    void shouldRejectMissingModelForCapability() {
        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setCapability("EMBEDDING");
        request.setBaseUrl(BASE_URL);
        request.setApiKey(API_KEY);

        assertThatThrownBy(() -> service.checkUnsavedConfig(100L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingModel is required");
    }

    @Test
    void shouldRejectCheckWithCHAT_EMBEDDINGCapability() {
        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setCapability("CHAT_EMBEDDING");
        request.setBaseUrl(BASE_URL);
        request.setApiKey(API_KEY);

        assertThatThrownBy(() -> service.checkUnsavedConfig(100L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CHAT_EMBEDDING is no longer supported");
    }

    @Test
    void shouldUseRequestApiKeyOverrideForSavedCheckWithoutDecryptingStoredKey() {
        ModelConfigEntity entity = savedChatConfig();
        when(modelConfigService.findByIdAndUserId(10L, 100L)).thenReturn(entity);
        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{}"));

        ModelConfigCheckRequest request = new ModelConfigCheckRequest();
        request.setApiKey(API_KEY);

        ModelConfigCheckResult result = service.checkSavedConfig(100L, 10L, request);

        assertThat(result.getOverallStatus()).isEqualTo("SUCCESS");
        verifyNoInteractions(encryptor);
        mockServer.verify();
    }

    @Test
    void shouldReturnNotReadyWhenSavedKeyCannotBeDecrypted() {
        ModelConfigEntity entity = savedChatConfig();
        when(modelConfigService.findByIdAndUserId(10L, 100L)).thenReturn(entity);
        when(encryptor.decrypt("v1:bad:data")).thenThrow(new IllegalArgumentException("bad tag"));

        ModelConfigCheckRequest request = new ModelConfigCheckRequest();

        assertThatThrownBy(() -> service.checkSavedConfig(100L, 10L, request))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("MODEL_CONFIG_NOT_READY");
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("RAG_GATEWAY_ENCRYPTION_SECRET_KEY");
                    assertThat(ex.getMessage()).doesNotContain("v1:");
                    assertThat(ex.getMessage()).doesNotContain("bad tag");
                });
    }

    private ModelConfigEntity savedChatConfig() {
        ModelConfigEntity entity = new ModelConfigEntity();
        entity.setId(10L);
        entity.setUserId(100L);
        entity.setProviderName("openai-compatible");
        entity.setBaseUrl(BASE_URL);
        entity.setApiKeyEncrypted("v1:bad:data");
        entity.setCapability(ModelConfigCapability.CHAT.name());
        entity.setChatModel("deepseek-v4-pro");
        entity.setStatus("ENABLED");
        return entity;
    }
}
