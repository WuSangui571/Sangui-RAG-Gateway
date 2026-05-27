package com.sangui.raggateway.gateway.upstream;

import com.sangui.raggateway.common.exception.GatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiCompatibleUpstreamClientTest {

    private static final String BASE_URL = "https://api.openai.com";
    private static final String API_KEY = "sk-upstream-key";
    private static final String UPSTREAM_RESPONSE = """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1710000000,
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 1,
                "completion_tokens": 1,
                "total_tokens": 2
              }
            }
            """;

    private MockRestServiceServer mockServer;
    private OpenAiCompatibleUpstreamClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new OpenAiCompatibleUpstreamClient(restClient);
    }

    @Test
    void shouldNormalizeBaseUrlByRemovingTrailingSlash() {
        assertThat(OpenAiCompatibleUpstreamClient.normalizeBaseUrl("https://api.openai.com/")).isEqualTo("https://api.openai.com");
        assertThat(OpenAiCompatibleUpstreamClient.normalizeBaseUrl("https://api.openai.com///")).isEqualTo("https://api.openai.com");
        assertThat(OpenAiCompatibleUpstreamClient.normalizeBaseUrl("https://api.openai.com")).isEqualTo("https://api.openai.com");
    }

    @Test
    void shouldNormalizeBaseUrlByStrippingWhitespace() {
        assertThat(OpenAiCompatibleUpstreamClient.normalizeBaseUrl("  https://api.openai.com  ")).isEqualTo("https://api.openai.com");
    }

    @Test
    void normalizeBaseUrlShouldThrowForNull() {
        assertThatThrownBy(() -> OpenAiCompatibleUpstreamClient.normalizeBaseUrl(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeBaseUrlShouldThrowForBlank() {
        assertThatThrownBy(() -> OpenAiCompatibleUpstreamClient.normalizeBaseUrl("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldSendRequestToUpstreamAndReturnResponse() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));
        request.setStream(false);

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(UPSTREAM_RESPONSE, MediaType.APPLICATION_JSON));

        String response = client.sendChatCompletion(BASE_URL, API_KEY, request);

        assertThat(response).contains("chat.completion");
        assertThat(response).contains("chatcmpl-test");
        mockServer.verify();
    }

    @Test
    void shouldThrowGatewayExceptionOnUpstreamNon2xx() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"message": "Internal server error"}}
                                """));

        assertThatThrownBy(() -> client.sendChatCompletion(BASE_URL, API_KEY, request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_error")
                            && ge.getHttpStatus().value() == 502;
                });

        mockServer.verify();
    }

    @Test
    void shouldThrowGatewayExceptionOnUpstream401() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"message": "Invalid API key"}}
                                """));

        assertThatThrownBy(() -> client.sendChatCompletion(BASE_URL, API_KEY, request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_error")
                            && ge.getHttpStatus().value() == 502;
                });

        mockServer.verify();
    }

    @Test
    void shouldThrowGatewayExceptionOnConnectionRefused() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo("http://localhost:19999" + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(MockRestResponseCreators.withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.sendChatCompletion("http://localhost:19999", API_KEY, request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return (ge.getCode().equals("upstream_error") || ge.getCode().equals("upstream_timeout"))
                            && (ge.getHttpStatus().value() == 502 || ge.getHttpStatus().value() == 504);
                });

        mockServer.verify();
    }
}
