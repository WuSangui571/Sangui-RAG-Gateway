package com.sangui.raggateway.gateway.openai;

import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyRateLimitResult;
import com.sangui.raggateway.apikey.ApiKeyRateLimitService;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.config.GatewayAuthConfig;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.ApiKeyGenerator;
import com.sangui.raggateway.common.security.ApiKeyHasher;
import com.sangui.raggateway.gateway.completion.ChatCompletionGatewayService;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.StreamCompletionOutcome;
import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;
import com.sangui.raggateway.log.ApiRequestLogService;
import com.sangui.raggateway.log.CreateRequestLogCommand;
import com.sangui.raggateway.log.OutputCapturePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenAiChatCompletionsRuntimeSmokeTest.SmokeApplication.class,
        properties = {
                "rag.gateway.secret-key=smoke-test-secret-key-at-least-32-bytes-long!!",
                "rag.gateway.streaming.emitter-timeout-seconds=3",
                "rag.gateway.api-key-limits.enabled=true"
        }
)
class OpenAiChatCompletionsRuntimeSmokeTest {

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long API_KEY_ID = 30L;

    private static final String REQUEST_BODY = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                { "role": "user", "content": "runtime streaming smoke" }
              ],
              "stream": true,
              "max_tokens": 16
            }
            """;

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            RedisAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class
    })
    @Import({
            OpenAiChatCompletionsController.class,
            GatewayAuthConfig.class,
            GlobalExceptionHandler.class,
            SmokeTestConfig.class
    })
    static class SmokeApplication {
    }

    @TestConfiguration
    static class SmokeTestConfig {

        @Bean
        ApiKeyService apiKeyService() {
            return mock(ApiKeyService.class);
        }

        @Bean
        AppService appService() {
            return mock(AppService.class);
        }

        @Bean
        ChatCompletionGatewayService chatCompletionGatewayService() {
            return mock(ChatCompletionGatewayService.class);
        }

        @Bean
        OpenAiCompatibleUpstreamClient upstreamClient() {
            return mock(OpenAiCompatibleUpstreamClient.class);
        }

        @Bean
        ApiRequestLogService apiRequestLogService() {
            return mock(ApiRequestLogService.class);
        }

        @Bean
        ApiKeyRateLimitService rateLimitService() {
            return mock(ApiKeyRateLimitService.class);
        }

        @Bean
        OutputCapturePolicy outputCapturePolicy() {
            return mock(OutputCapturePolicy.class);
        }

        @Bean
        ApiKeyLimitProperties rateLimitProperties() {
            ApiKeyLimitProperties props = new ApiKeyLimitProperties();
            props.setEnabled(true);
            return props;
        }
    }

    @Autowired
    private ApiKeyService apiKeyService;
    @Autowired
    private AppService appService;
    @Autowired
    private ChatCompletionGatewayService chatCompletionGatewayService;
    @Autowired
    private OpenAiCompatibleUpstreamClient upstreamClient;
    @Autowired
    private ApiRequestLogService apiRequestLogService;
    @Autowired
    private ApiKeyRateLimitService rateLimitService;
    @Autowired
    private ApiKeyHasher apiKeyHasher;
    @Autowired
    private ApiKeyGenerator apiKeyGenerator;

    @LocalServerPort
    private int port;

    private String testApiKey;

    @BeforeEach
    void setUp() {
        reset(apiKeyService, appService, chatCompletionGatewayService,
                upstreamClient, apiRequestLogService, rateLimitService);

        String plaintextKey = apiKeyGenerator.generate();
        String keyHash = apiKeyHasher.hash(plaintextKey);
        String keyPrefix = apiKeyGenerator.extractPrefix(plaintextKey);

        ApiKeyEntity mockApiKey = new ApiKeyEntity();
        mockApiKey.setId(API_KEY_ID);
        mockApiKey.setAppId(APP_ID);
        mockApiKey.setUserId(USER_ID);
        mockApiKey.setKeyPrefix(keyPrefix);
        mockApiKey.setStatus("ACTIVE");
        when(apiKeyService.findByHash(keyHash)).thenReturn(mockApiKey);
        when(apiKeyService.isValid(mockApiKey)).thenReturn(true);

        AppEntity mockApp = new AppEntity();
        mockApp.setId(APP_ID);
        mockApp.setUserId(USER_ID);
        when(appService.findById(APP_ID)).thenReturn(mockApp);
        when(appService.isEnabled(mockApp)).thenReturn(true);

        doNothing().when(chatCompletionGatewayService).validateRequest(any());

        this.testApiKey = plaintextKey;
    }

    private ChatCompletionStreamPreparation createStreamPreparation() {
        UpstreamChatCompletionRequest upstreamRequest = new UpstreamChatCompletionRequest();
        upstreamRequest.setModel("gpt-4o-mini");
        upstreamRequest.setStream(true);
        upstreamRequest.setMessages(List.of(
                new UpstreamChatCompletionRequest.Message("user", "runtime streaming smoke")));
        return new ChatCompletionStreamPreparation(
                "https://api.fake-smoke.local",
                "sk-upstream-fake-key",
                upstreamRequest,
                "gpt-4o-mini",
                "openai",
                "runtime streaming smoke",
                "[1,2,3]",
                null);
    }

    private HttpResponse<InputStream> sendStreamingRequest(HttpClient client, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(REQUEST_BODY))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    @Nested
    class NormalStreaming {

        @Test
        void shouldCompleteWithDoneAndRecordSuccess() throws Exception {
            ChatCompletionStreamPreparation prep = createStreamPreparation();
            when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                    .thenReturn(prep);
            when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                    .thenReturn(allowedReservation());

            doAnswer(inv -> {
                SseEmitter emitter = inv.getArgument(3);
                Runnable onStreamReady = inv.getArgument(5);
                onStreamReady.run();
                emitter.send(SseEmitter.event().data("{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"));
                emitter.send(SseEmitter.event().data("[DONE]"));
                return StreamCompletionOutcome.SUCCESS;
            }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                    any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = sendStreamingRequest(client, testApiKey);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .contains("text/event-stream");

            String allLines;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                allLines = sb.toString();
            }

            assertThat(allLines).contains("[DONE]");
            assertThat(allLines).contains("chat.completion.chunk");

            ArgumentCaptor<CreateRequestLogCommand> captor =
                    ArgumentCaptor.forClass(CreateRequestLogCommand.class);
            verify(apiRequestLogService, timeout(3000).times(1)).record(captor.capture());
            CreateRequestLogCommand cmd = captor.getValue();
            assertThat(cmd.getStatus()).isEqualTo("success");
            assertThat(cmd.getErrorCode()).isNull();
            assertThat(cmd.getModel()).isEqualTo("gpt-4o-mini");
            assertThat(cmd.getProviderName()).isEqualTo("openai");
            assertThat(cmd.getMessagesCount()).isEqualTo(1);
            assertThat(cmd.getOutputCaptureStatus()).isEqualTo("STREAMING_UNSUPPORTED");
            assertThat(cmd.getQuestionSummary()).isEqualTo("runtime streaming smoke");
            assertThat(cmd.getHitChunkIds()).isEqualTo("[1,2,3]");

            verify(rateLimitService, never()).releaseReservation(any(), any());
            verify(rateLimitService, never()).reconcileTokens(any(), any(), anyInt());
        }
    }

    @Nested
    class ClientDisconnect {

        @Test
        void shouldRecordCancelledAndReleaseReservationOnce() throws Exception {
            ChatCompletionStreamPreparation prep = createStreamPreparation();
            when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                    .thenReturn(prep);
            when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                    .thenReturn(allowedReservation());

            CountDownLatch clientDisconnected = new CountDownLatch(1);

            doAnswer(inv -> {
                SseEmitter emitter = inv.getArgument(3);
                Runnable onStreamReady = inv.getArgument(5);
                onStreamReady.run();
                emitter.send(SseEmitter.event().data("{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}"));

                clientDisconnected.await(10, TimeUnit.SECONDS);

                try {
                    emitter.send(SseEmitter.event().data("{\"id\":\"chatcmpl-2\"}"));
                } catch (IOException e) {
                    return StreamCompletionOutcome.CANCELLED;
                }
                return StreamCompletionOutcome.SUCCESS;
            }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                    any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = sendStreamingRequest(client, testApiKey);

            assertThat(response.statusCode()).isEqualTo(200);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String firstLine = reader.readLine();
                assertThat(firstLine).startsWith("data:");
            }

            clientDisconnected.countDown();

            ArgumentCaptor<CreateRequestLogCommand> captor =
                    ArgumentCaptor.forClass(CreateRequestLogCommand.class);
            verify(apiRequestLogService, timeout(5000).times(1)).record(captor.capture());
            CreateRequestLogCommand cmd = captor.getValue();
            assertThat(cmd.getStatus()).isEqualTo("cancelled");
            assertThat(cmd.getErrorCode()).isEqualTo("client_cancelled");
            assertThat(cmd.getOutputCaptureStatus()).isEqualTo("STREAMING_UNSUPPORTED");
            assertThat(cmd.getMessagesCount()).isEqualTo(1);
            assertThat(cmd.getQuestionSummary()).isEqualTo("runtime streaming smoke");

            verify(rateLimitService, timeout(1000).times(1))
                    .releaseReservation(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class));
            verify(rateLimitService, never())
                    .reconcileTokens(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class), anyInt());
        }
    }

    @Nested
    class EmitterTimeout {

        @Test
        void shouldTimeoutAndRecordStreamTimeoutWithReservationRelease() throws Exception {
            ChatCompletionStreamPreparation prep = createStreamPreparation();
            when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                    .thenReturn(prep);
            when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                    .thenReturn(allowedReservation());

            doAnswer(inv -> {
                Runnable onStreamReady = inv.getArgument(5);
                onStreamReady.run();
                Thread.sleep(15000);
                return StreamCompletionOutcome.SUCCESS;
            }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                    any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = sendStreamingRequest(client, testApiKey);

            assertThat(response.statusCode()).isEqualTo(200);

            try (InputStream is = response.body()) {
                byte[] buf = new byte[4096];
                int totalRead = 0;
                while (totalRead < 4096) {
                    int n = is.read(buf, totalRead, buf.length - totalRead);
                    if (n < 0) break;
                    totalRead += n;
                }
            }

            ArgumentCaptor<CreateRequestLogCommand> captor =
                    ArgumentCaptor.forClass(CreateRequestLogCommand.class);
            verify(apiRequestLogService, timeout(8000).times(1)).record(captor.capture());
            CreateRequestLogCommand cmd = captor.getValue();
            assertThat(cmd.getStatus()).isEqualTo("cancelled");
            assertThat(cmd.getErrorCode()).isEqualTo("stream_timeout");
            assertThat(cmd.getOutputCaptureStatus()).isEqualTo("STREAMING_UNSUPPORTED");
            assertThat(cmd.getMessagesCount()).isEqualTo(1);
            assertThat(cmd.getModel()).isEqualTo("gpt-4o-mini");
            assertThat(cmd.getProviderName()).isEqualTo("openai");

            verify(rateLimitService, timeout(1000).times(1))
                    .releaseReservation(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class));
            verify(rateLimitService, never())
                    .reconcileTokens(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class), anyInt());
        }
    }

    @Nested
    class PostStartUpstreamFailure {

        @Test
        void shouldRecordFailureAndReleaseReservationOnce() throws Exception {
            ChatCompletionStreamPreparation prep = createStreamPreparation();
            when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                    .thenReturn(prep);
            when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                    .thenReturn(allowedReservation());

            doAnswer(inv -> {
                Runnable onStreamReady = inv.getArgument(5);
                onStreamReady.run();
                throw new GatewayException("Upstream service is unavailable",
                        "server_error", "upstream_error", HttpStatus.BAD_GATEWAY);
            }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                    any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<InputStream> response = sendStreamingRequest(client, testApiKey);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse(""))
                    .contains("text/event-stream");

            try (InputStream is = response.body()) {
                byte[] buf = new byte[4096];
                int totalRead = 0;
                while (totalRead < 4096) {
                    int n = is.read(buf, totalRead, buf.length - totalRead);
                    if (n < 0) break;
                    totalRead += n;
                }
            }

            ArgumentCaptor<CreateRequestLogCommand> captor =
                    ArgumentCaptor.forClass(CreateRequestLogCommand.class);
            verify(apiRequestLogService, timeout(5000).times(1)).record(captor.capture());
            CreateRequestLogCommand cmd = captor.getValue();
            assertThat(cmd.getStatus()).isEqualTo("failure");
            assertThat(cmd.getErrorCode()).isEqualTo("upstream_error");
            assertThat(cmd.getOutputCaptureStatus()).isEqualTo("STREAMING_UNSUPPORTED");
            assertThat(cmd.getMessagesCount()).isEqualTo(1);
            assertThat(cmd.getModel()).isEqualTo("gpt-4o-mini");
            assertThat(cmd.getProviderName()).isEqualTo("openai");
            assertThat(cmd.getQuestionSummary()).isEqualTo("runtime streaming smoke");

            verify(rateLimitService, timeout(1000).times(1))
                    .releaseReservation(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class));
            verify(rateLimitService, never())
                    .reconcileTokens(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class), anyInt());
        }
    }

    private ApiKeyRateLimitResult allowedReservation() {
        return ApiKeyRateLimitResult.allowed(59, 59900, "202606181200", "20260618", 32);
    }
}
