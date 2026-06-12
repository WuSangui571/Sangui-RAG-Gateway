package com.sangui.raggateway.model;

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
}
