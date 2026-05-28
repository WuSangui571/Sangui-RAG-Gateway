package com.sangui.raggateway.embedding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiCompatibleEmbeddingClientTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "sk-upstream-key";
    private static final String MODEL = "text-embedding-3-small";
    private static final int DIMENSION = 1536;

    private MockRestServiceServer mockServer;
    private OpenAiCompatibleEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new OpenAiCompatibleEmbeddingClient(restClient);
    }

    @Test
    void shouldBuildEmbeddingUrlForBaseUrlVariants() {
        assertThat(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("https://api.example.com"))
                .isEqualTo("https://api.example.com");
        assertThat(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("https://api.example.com/"))
                .isEqualTo("https://api.example.com");
        assertThat(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("https://api.example.com/v1"))
                .isEqualTo("https://api.example.com/v1");
        assertThat(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("https://api.example.com/v1/"))
                .isEqualTo("https://api.example.com/v1");
        assertThat(OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("  https://api.example.com  "))
                .isEqualTo("https://api.example.com");
    }

    @Test
    void normalizeBaseUrlShouldThrowForNull() {
        assertThatThrownBy(() -> OpenAiCompatibleEmbeddingClient.normalizeBaseUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeBaseUrlShouldThrowForBlank() {
        assertThatThrownBy(() -> OpenAiCompatibleEmbeddingClient.normalizeBaseUrl("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSendEmbeddingRequestAndReturnVectors() {
        String responseBody = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]},
                    {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 10, "total_tokens": 10}
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1", "text2"), 3);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        mockServer.verify();
    }

    @Test
    void shouldSendRequestWithBaseUrlContainingV1() {
        String baseUrl = "https://api.openai.com/v1";
        String responseBody = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """;

        mockServer.expect(requestTo(baseUrl + "/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(baseUrl, API_KEY, MODEL,
                List.of("text1"), 2);

        assertThat(vectors).hasSize(1);
        mockServer.verify();
    }

    @Test
    void shouldThrowOnResponseCountMismatch() {
        String responseBody = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 0, "embedding": [0.1, 0.2]}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1", "text2"), 2))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("count mismatch");
        mockServer.verify();
    }

    @Test
    void shouldThrowOnDimensionMismatch() {
        String responseBody = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 5, "total_tokens": 5}
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1"), 1536))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("dimension mismatch");
        mockServer.verify();
    }

    @Test
    void shouldThrowOnUpstreamNon2xx() {
        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"message\": \"Internal server error\"}}"));

        assertThatThrownBy(() -> client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1"), DIMENSION))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("status 500");
        mockServer.verify();
    }

    @Test
    void shouldThrowOnMalformedResponseBody() {
        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1"), DIMENSION))
                .isInstanceOf(EmbeddingException.class);
        mockServer.verify();
    }

    @Test
    void shouldHandleOutOfOrderResponseIndexes() {
        String responseBody = """
                {
                  "object": "list",
                  "data": [
                    {"object": "embedding", "index": 1, "embedding": [0.4, 0.5, 0.6]},
                    {"object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3]}
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {"prompt_tokens": 10, "total_tokens": 10}
                }
                """;

        mockServer.expect(requestTo(BASE_URL + "/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        List<float[]> vectors = client.embed(BASE_URL, API_KEY, MODEL,
                List.of("text1", "text2"), 3);

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(vectors.get(1)).containsExactly(0.4f, 0.5f, 0.6f);
        mockServer.verify();
    }
}
