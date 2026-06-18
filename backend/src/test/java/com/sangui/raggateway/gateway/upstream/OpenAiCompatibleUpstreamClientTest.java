package com.sangui.raggateway.gateway.upstream;

import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.log.ChatCompletionLogHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.web.client.RestClient;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(OutputCaptureExtension.class)
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
        GatewayRequestContext context = new GatewayRequestContext(1L, 100L, 30L, "sk-sangui-prefix");
        context.setRequestId("request-123");
        GatewayRequestContextHolder.set(context);
    }

    @AfterEach
    void tearDown() {
        GatewayRequestContextHolder.clear();
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
    void shouldLogSafeUpstreamFieldsWithoutSecretsOrProviderBody(CapturedOutput output) {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "secret user message")));

        mockServer.expect(requestTo("https://user:password@api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"message": "provider-secret Authorization secret user message"}}
                                """));

        assertThatThrownBy(() -> client.sendChatCompletion("https://user:password@api.openai.com", API_KEY, request))
                .isInstanceOf(GatewayException.class);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.upstream_started");
        assertThat(logs).contains("gateway.chat.upstream_failed");
        assertThat(logs).contains("request_id=request-123");
        assertThat(logs).contains("upstream_url=api.openai.com/v1/chat/completions");
        assertThat(logs).doesNotContain(API_KEY);
        assertThat(logs).doesNotContain("Authorization");
        assertThat(logs).doesNotContain("provider-secret");
        assertThat(logs).doesNotContain("secret user message");
        assertThat(logs).doesNotContain("user:password");
        assertThat(ChatCompletionLogHelper.sanitizeUpstreamUrl("https://user:password@api.openai.com/v1/chat/completions?token=secret"))
                .isEqualTo("api.openai.com/v1/chat/completions");
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
    void shouldSendRequestWithBaseUrlContainingV1() {
        String baseUrl = "https://api.openai.com/v1";
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));
        request.setStream(false);

        mockServer.expect(requestTo(baseUrl + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(UPSTREAM_RESPONSE, MediaType.APPLICATION_JSON));

        String response = client.sendChatCompletion(baseUrl, API_KEY, request);

        assertThat(response).contains("chat.completion");
        mockServer.verify();
    }

    @Test
    void shouldSendRequestWithBaseUrlContainingV1AndTrailingSlash() {
        String baseUrl = "https://api.openai.com/v1/";
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));
        request.setStream(false);

        mockServer.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(UPSTREAM_RESPONSE, MediaType.APPLICATION_JSON));

        String response = client.sendChatCompletion(baseUrl, API_KEY, request);

        assertThat(response).contains("chat.completion");
        mockServer.verify();
    }

    @Test
    void shouldSendRequestWithBaseUrlTrailingSlash() {
        String baseUrl = "https://api.openai.com/";
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));
        request.setStream(false);

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(UPSTREAM_RESPONSE, MediaType.APPLICATION_JSON));

        String response = client.sendChatCompletion(baseUrl, API_KEY, request);

        assertThat(response).contains("chat.completion");
        mockServer.verify();
    }

    @Test
    void shouldSendRequestWithBaseUrlNoTrailingSlash() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));
        request.setStream(false);

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(UPSTREAM_RESPONSE, MediaType.APPLICATION_JSON));

        String response = client.sendChatCompletion(BASE_URL, API_KEY, request);

        assertThat(response).contains("chat.completion");
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

    @Test
    void shouldStreamChunksToSseEmitter() {
        String sseBody = """
                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hello"}}]}
                data: [DONE]
                """;
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andRespond(withSuccess(sseBody, MediaType.TEXT_PLAIN));

        SseEmitter emitter = new SseEmitter(0L);
        assertDoesNotThrow(() -> client.streamChatCompletion(BASE_URL, API_KEY, request, emitter, "request-stream-1"));
        mockServer.verify();
    }

    @Test
    void shouldThrowGatewayExceptionOnStreamUpstreamNon2xx() {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"message": "Internal server error"}}
                                """));

        SseEmitter emitter = new SseEmitter(0L);
        assertThatThrownBy(() -> client.streamChatCompletion(BASE_URL, API_KEY, request, emitter, "request-stream-2"))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_error")
                            && ge.getHttpStatus().value() == 502;
                });

        mockServer.verify();
    }

    @Test
    void shouldThrowGatewayExceptionWhenStreamClosesWithoutDone() {
        String sseBody = """
                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hello"}}]}
                
                """;
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(sseBody, MediaType.TEXT_PLAIN));

        SseEmitter emitter = new SseEmitter(0L);
        assertThatThrownBy(() -> client.streamChatCompletion(BASE_URL, API_KEY, request, emitter, "request-stream-no-done"))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_error")
                            && ge.getHttpStatus().value() == 502;
                });

        mockServer.verify();
    }

    @Test
    void shouldReturnCancelledWhenClientSendFails() {
        String sseBody = """
                data: {"id":"chatcmpl-1","object":"chat.completion.chunk","choices":[{"delta":{"content":"Hello"}}]}
                data: [DONE]
                """;
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(sseBody, MediaType.TEXT_PLAIN));

        SseEmitter emitter = new SseEmitter(0L) {
            @Override
            public void send(SseEmitter.SseEventBuilder builder) throws IOException {
                throw new IOException("client disconnected");
            }
        };

        StreamCompletionOutcome outcome = client.streamChatCompletion(
                BASE_URL, API_KEY, request, emitter, "request-stream-cancel");

        assertThat(outcome).isEqualTo(StreamCompletionOutcome.CANCELLED);
        mockServer.verify();
    }

    @Test
    void shouldLogSafeFieldsInStreamStartAndComplete(CapturedOutput output) {
        String sseBody = """
                data: {"id":"chatcmpl-1"}
                
                data: [DONE]
                
                """;
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "secret user message")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(sseBody, MediaType.TEXT_PLAIN));

        SseEmitter emitter = new SseEmitter(0L);
        assertDoesNotThrow(() -> client.streamChatCompletion(BASE_URL, API_KEY, request, emitter, "request-stream-3"));

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.stream_started");
        assertThat(logs).contains("gateway.chat.stream_completed");
        assertThat(logs).contains("request_id=request-stream-3");
        assertThat(logs).doesNotContain(API_KEY);
        assertThat(logs).doesNotContain("secret user message");
    }

    @Test
    void shouldLogStreamFailedOnNon2xx(CapturedOutput output) {
        UpstreamChatCompletionRequest request = new UpstreamChatCompletionRequest();
        request.setModel("gpt-4o-mini");
        request.setStream(true);
        request.setMessages(List.of(new UpstreamChatCompletionRequest.Message("user", "Hello")));

        mockServer.expect(requestTo(BASE_URL + "/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"message": "provider-secret details"}}
                                """));

        SseEmitter emitter = new SseEmitter(0L);
        assertThatThrownBy(() -> client.streamChatCompletion(BASE_URL, API_KEY, request, emitter, "request-stream-4"))
                .isInstanceOf(GatewayException.class);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.stream_started");
        assertThat(logs).contains("gateway.chat.stream_failed");
        assertThat(logs).contains("request_id=request-stream-4");
        assertThat(logs).doesNotContain("provider-secret");
    }
}
