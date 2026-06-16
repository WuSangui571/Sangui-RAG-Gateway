package com.sangui.raggateway.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyRateLimitResult;
import com.sangui.raggateway.apikey.ApiKeyRateLimitService;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.exception.GlobalExceptionHandler;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.gateway.completion.ChatCompletionGatewayService;
import com.sangui.raggateway.gateway.completion.ChatCompletionResult;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;
import com.sangui.raggateway.log.ApiRequestLogService;
import com.sangui.raggateway.log.OutputCapturePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OpenAiChatCompletionsControllerTest {

    @Mock
    private ChatCompletionGatewayService chatCompletionGatewayService;

    @Mock
    private ApiRequestLogService apiRequestLogService;

    @Mock
    private OpenAiCompatibleUpstreamClient upstreamClient;

    @Mock
    private AppService appService;

    @Mock
    private OutputCapturePolicy outputCapturePolicy;

    @Mock
    private ApiKeyRateLimitService rateLimitService;

    @Mock
    private ApiKeyLimitProperties rateLimitProperties;

    private ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long API_KEY_ID = 30L;

    @BeforeEach
    void setUp() {
        OpenAiChatCompletionsController controller = new OpenAiChatCompletionsController(
                chatCompletionGatewayService, apiRequestLogService, upstreamClient, objectMapper,
                appService, outputCapturePolicy, rateLimitService, rateLimitProperties);
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
        GatewayRequestContextHolder.set(new GatewayRequestContext(APP_ID, USER_ID, API_KEY_ID, "sk-sangui-abcdef"));
    }

    private ChatCompletionResult createSuccessResult() {
        OpenAiChatCompletionResponse mockResponse = new OpenAiChatCompletionResponse();
        mockResponse.setId("chatcmpl-test");
        mockResponse.setObject("chat.completion");
        mockResponse.setCreated(1710000000);
        mockResponse.setModel("gpt-4o-mini");

        OpenAiChatCompletionResponse.Choice choice = new OpenAiChatCompletionResponse.Choice();
        choice.setIndex(0);
        OpenAiChatCompletionResponse.Message message = new OpenAiChatCompletionResponse.Message();
        message.setRole("assistant");
        message.setContent("Hello");
        choice.setMessage(message);
        choice.setFinishReason("stop");
        mockResponse.setChoices(List.of(choice));

        OpenAiChatCompletionResponse.Usage usage = new OpenAiChatCompletionResponse.Usage();
        usage.setPromptTokens(1);
        usage.setCompletionTokens(1);
        usage.setTotalTokens(2);
        mockResponse.setUsage(usage);
        return new ChatCompletionResult(mockResponse, "gpt-4o-mini", "openai", 500L, 1, 1, 2,
                "What is RAG?", "[1,2,3]", "Hello", 5);
    }

    private ChatCompletionStreamPreparation createStreamPreparation() {
        UpstreamChatCompletionRequest upstreamRequest = new UpstreamChatCompletionRequest();
        upstreamRequest.setModel("gpt-4o-mini");
        upstreamRequest.setStream(true);
        return new ChatCompletionStreamPreparation("https://api.openai.com", "sk-upstream-key",
                upstreamRequest, "gpt-4o-mini", "openai",
                "What is RAG?", "[1,2,3]");
    }

    @Test
    void shouldReturn200WithOpenAiCompatibleResponseOnSuccess() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any())).thenReturn(createSuccessResult());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.object").value("chat.completion"))
                .andExpect(jsonPath("$.id").value("chatcmpl-test"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.choices[0].message.role").value("assistant"))
                .andExpect(jsonPath("$.choices[0].message.content").value("Hello"))
                .andExpect(jsonPath("$.choices[0].finish_reason").value("stop"))
                .andExpect(jsonPath("$.usage.prompt_tokens").value(1))
                .andExpect(jsonPath("$.usage.completion_tokens").value(1))
                .andExpect(jsonPath("$.usage.total_tokens").value(2))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        com.sangui.raggateway.log.CreateRequestLogCommand command = captor.getValue();
        assertThat(command.getUserId()).isEqualTo(USER_ID);
        assertThat(command.getAppId()).isEqualTo(APP_ID);
        assertThat(command.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(command.getStatus()).isEqualTo("success");
        assertThat(command.getErrorCode()).isNull();
        assertThat(command.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(command.getProviderName()).isEqualTo("openai");
        assertThat(command.getUpstreamLatencyMs()).isEqualTo(500L);
        assertThat(command.getPromptTokens()).isEqualTo(1);
        assertThat(command.getCompletionTokens()).isEqualTo(1);
        assertThat(command.getTotalTokens()).isEqualTo(2);
        assertThat(command.getMessagesCount()).isEqualTo(1);
        assertThat(command.getQuestionSummary()).isEqualTo("What is RAG?");
        assertThat(command.getHitChunkIds()).isEqualTo("[1,2,3]");
    }

    @Test
    void shouldReturnSseEmitterWhenStreamIsTrue() throws Exception {
        setContext();
        when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                .thenReturn(createStreamPreparation());
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(3);
            Runnable onStreamReady = invocation.getArgument(5);
            onStreamReady.run();
            emitter.complete();
            return null;
        }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ],
                                  "stream": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        com.sangui.raggateway.log.CreateRequestLogCommand command = captor.getValue();
        assertThat(command.getUserId()).isEqualTo(USER_ID);
        assertThat(command.getAppId()).isEqualTo(APP_ID);
        assertThat(command.getApiKeyId()).isEqualTo(API_KEY_ID);
        assertThat(command.getStatus()).isEqualTo("success");
        assertThat(command.getErrorCode()).isNull();
        assertThat(command.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(command.getProviderName()).isEqualTo("openai");
        assertThat(command.getMessagesCount()).isEqualTo(1);
        assertThat(command.getQuestionSummary()).isEqualTo("What is RAG?");
        assertThat(command.getHitChunkIds()).isEqualTo("[1,2,3]");
    }

    @Test
    void shouldReturn502JsonWhenStreamUpstreamFailsBeforeReady() throws Exception {
        setContext();
        when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                .thenReturn(createStreamPreparation());
        doAnswer(invocation -> {
            throw new GatewayException("Upstream service returned an error",
                    "server_error", "upstream_error", HttpStatus.BAD_GATEWAY);
        }).when(upstreamClient).streamChatCompletion(anyString(), anyString(),
                any(UpstreamChatCompletionRequest.class), any(SseEmitter.class), anyString(), any(Runnable.class));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ],
                                  "stream": true
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("upstream_error"))
                .andExpect(jsonPath("$.error.type").value("server_error"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        com.sangui.raggateway.log.CreateRequestLogCommand command = captor.getValue();
        assertThat(command.getStatus()).isEqualTo("failure");
        assertThat(command.getErrorCode()).isEqualTo("upstream_error");
        assertThat(command.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(command.getProviderName()).isEqualTo("openai");
        assertThat(command.getQuestionSummary()).isEqualTo("What is RAG?");
        assertThat(command.getHitChunkIds()).isEqualTo("[1,2,3]");
    }

    @Test
    void shouldReturn400WhenStreamPreValidationFails() throws Exception {
        setContext();
        when(chatCompletionGatewayService.prepareStreamCompletion(any()))
                .thenThrow(new GatewayException("messages must be a non-empty array.",
                        "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [],
                                  "stream": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());

        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        com.sangui.raggateway.log.CreateRequestLogCommand command = captor.getValue();
        assertThat(command.getStatus()).isEqualTo("failure");
        assertThat(command.getErrorCode()).isEqualTo("invalid_request");
        assertThat(command.getMessagesCount()).isEqualTo(0);
        assertThat(command.getModel()).isNull();
        assertThat(command.getProviderName()).isNull();
    }

    @Test
    void shouldReturn400WhenMessagesIsEmpty() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("messages must be a non-empty array.",
                        "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn400WhenMessagesIsMissing() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("messages must be a non-empty array.",
                        "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn400WhenMessageMissingRole() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Each message must have a role.",
                        "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn400WhenMessageMissingContent() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Each message must have content.",
                        "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldNotContainUpstreamKeyInResponse() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any())).thenReturn(createSuccessResult());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("sk-"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    @Test
    void shouldReturn400ForMalformedJsonBody() throws Exception {
        setContext();

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ invalid json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(content().string(not(containsString("invalid json"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void shouldReturn409ForModelConfigNotReady() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Default model config is not configured for this app.",
                        "invalid_request_error", "model_config_not_ready", HttpStatus.CONFLICT));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("model_config_not_ready"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn502ForUpstreamError() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Upstream service is unavailable",
                        "server_error", "upstream_error", HttpStatus.BAD_GATEWAY));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("upstream_error"))
                .andExpect(jsonPath("$.error.type").value("server_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn504ForUpstreamTimeout() throws Exception {
        setContext();
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Upstream request timed out",
                        "server_error", "upstream_timeout", HttpStatus.GATEWAY_TIMEOUT));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("upstream_timeout"))
                .andExpect(jsonPath("$.error.type").value("server_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn401ForMissingContext() throws Exception {
        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_api_key"))
                .andExpect(jsonPath("$.error.type").value("invalid_request_error"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                .thenReturn(ApiKeyRateLimitResult.rejected("rpm", 0, 60000, 30L));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limit_exceeded"))
                .andExpect(jsonPath("$.error.type").value("rate_limit_error"))
                .andExpect(jsonPath("$.error.message").value("Rate limit exceeded for this API key."))
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        com.sangui.raggateway.log.CreateRequestLogCommand command = captor.getValue();
        assertThat(command.getStatus()).isEqualTo("failure");
        assertThat(command.getErrorCode()).isEqualTo("rate_limit_exceeded");
        assertThat(command.getUserId()).isEqualTo(USER_ID);
        assertThat(command.getAppId()).isEqualTo(APP_ID);
        assertThat(command.getApiKeyId()).isEqualTo(API_KEY_ID);
    }

    @Test
    void shouldNotCallUpstreamWhenRateLimited() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                .thenReturn(ApiKeyRateLimitResult.rejected("tpm", 60, 0, 30L));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isTooManyRequests());

        verify(chatCompletionGatewayService, never()).processChatCompletion(any());
    }

    @Test
    void shouldProceedWhenRateLimitAllowed() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                .thenReturn(ApiKeyRateLimitResult.allowed(59, 59900));
        when(chatCompletionGatewayService.processChatCompletion(any())).thenReturn(createSuccessResult());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        verify(rateLimitService).reconcileTokens(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class), eq(2));
    }

    @Test
    void shouldReleaseReservationOnUpstreamFailure() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                .thenReturn(ApiKeyRateLimitResult.allowed(59, 59900));
        when(chatCompletionGatewayService.processChatCompletion(any()))
                .thenThrow(new GatewayException("Upstream service is unavailable",
                        "server_error", "upstream_error", org.springframework.http.HttpStatus.BAD_GATEWAY));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadGateway());

        verify(rateLimitService).releaseReservation(eq(API_KEY_ID), any(ApiKeyRateLimitResult.class));
    }

    @Test
    void shouldValidateBeforeRateLimit() throws Exception {
        setContext();
        doThrow(new GatewayException("messages must be a non-empty array.",
                "invalid_request_error", "invalid_request", HttpStatus.BAD_REQUEST))
                .when(chatCompletionGatewayService).validateRequest(any(OpenAiChatCompletionRequest.class));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_request"));

        verify(rateLimitService, never()).checkAndReserve(any(), anyInt(), any());
        ArgumentCaptor<com.sangui.raggateway.log.CreateRequestLogCommand> captor =
                ArgumentCaptor.forClass(com.sangui.raggateway.log.CreateRequestLogCommand.class);
        verify(apiRequestLogService).record(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("failure");
        assertThat(captor.getValue().getErrorCode()).isEqualTo("invalid_request");
    }

    @Test
    void shouldSkipRateLimitWhenDisabled() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(false);
        when(chatCompletionGatewayService.processChatCompletion(any())).thenReturn(createSuccessResult());

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        verify(rateLimitService, never()).checkAndReserve(any(), anyInt(), any());
    }

    @Test
    void shouldNotExposeInternalDetailsInRateLimitResponse() throws Exception {
        setContext();
        when(rateLimitProperties.isEnabled()).thenReturn(true);
        when(rateLimitService.checkAndReserve(any(), anyInt(), any()))
                .thenReturn(ApiKeyRateLimitResult.rejected("daily_requests", 0, 100000, 3600L));

        mockMvc.perform(post("/v1/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "model": "gpt-4o",
                                  "messages": [
                                    {"role": "user", "content": "Hello"}
                                  ]
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(not(containsString("sk-"))))
                .andExpect(content().string(not(containsString("api_key"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("java."))))
                .andExpect(content().string(not(containsString("redis"))));
    }
}
