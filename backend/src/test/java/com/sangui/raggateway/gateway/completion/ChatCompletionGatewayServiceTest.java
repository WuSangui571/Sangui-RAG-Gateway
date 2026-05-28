package com.sangui.raggateway.gateway.completion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionRequest;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionResponse;
import com.sangui.raggateway.gateway.openai.OpenAiChatMessage;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;
import com.sangui.raggateway.model.ModelConfigEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class ChatCompletionGatewayServiceTest {

    @Mock
    private AppService appService;

    @Mock
    private UpstreamApiKeyEncryptor encryptor;

    @Mock
    private OpenAiCompatibleUpstreamClient upstreamClient;

    private ChatCompletionGatewayService service;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long API_KEY_ID = 30L;
    private static final Long MODEL_CONFIG_ID = 10L;
    private static final String DECRYPTED_KEY = "sk-upstream-real-key";
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

    @BeforeEach
    void setUp() {
        service = new ChatCompletionGatewayService(appService, encryptor, upstreamClient, new ObjectMapper());
        GatewayRequestContext context = new GatewayRequestContext(APP_ID, USER_ID, API_KEY_ID, "sk-sangui-abcdef");
        context.setRequestId("request-123");
        GatewayRequestContextHolder.set(context);
    }

    @AfterEach
    void tearDown() {
        GatewayRequestContextHolder.clear();
    }

    private AppEntity createEnabledApp() {
        AppEntity app = new AppEntity();
        app.setId(APP_ID);
        app.setUserId(USER_ID);
        app.setName("Test App");
        app.setStatus("ENABLED");
        app.setDefaultModelConfigId(MODEL_CONFIG_ID);
        return app;
    }

    private ModelConfigEntity createEnabledModelConfig() {
        ModelConfigEntity config = new ModelConfigEntity();
        config.setId(MODEL_CONFIG_ID);
        config.setUserId(USER_ID);
        config.setProviderName("openai");
        config.setBaseUrl("https://api.openai.com");
        config.setChatModel("gpt-4o-mini");
        config.setApiKeyEncrypted("v1:encrypted:key");
        config.setStatus("ENABLED");
        return config;
    }

    private OpenAiChatCompletionRequest createValidRequest() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o");
        request.setMessages(List.of(new OpenAiChatMessage("user", "Hello")));
        return request;
    }

    @Test
    void shouldReturnValidResponseForGoodCase() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = createValidRequest();
        OpenAiChatCompletionResponse response = service.processChatCompletion(request);

        assertThat(response.getObject()).isEqualTo("chat.completion");
        assertThat(response.getId()).isEqualTo("chatcmpl-test");
        assertThat(response.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getMessage().getRole()).isEqualTo("assistant");
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("Hello");
        assertThat(response.getChoices().get(0).getFinishReason()).isEqualTo("stop");
        assertThat(response.getUsage().getPromptTokens()).isEqualTo(1);
        assertThat(response.getUsage().getCompletionTokens()).isEqualTo(1);
        assertThat(response.getUsage().getTotalTokens()).isEqualTo(2);
    }

    @Test
    void shouldUseConfiguredChatModelNotCallerModel() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4-turbo");
        request.setMessages(List.of(new OpenAiChatMessage("user", "Hello")));

        service.processChatCompletion(request);

        ArgumentCaptor<UpstreamChatCompletionRequest> captor =
                ArgumentCaptor.forClass(UpstreamChatCompletionRequest.class);
        verify(upstreamClient).sendChatCompletion(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldForwardOptionalFields() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4-turbo");
        request.setMessages(List.of(new OpenAiChatMessage("user", "Hello")));
        request.setTemperature(0.7);
        request.setMaxTokens(100);
        request.setTopP(0.9);

        service.processChatCompletion(request);

        ArgumentCaptor<UpstreamChatCompletionRequest> captor =
                ArgumentCaptor.forClass(UpstreamChatCompletionRequest.class);
        verify(upstreamClient).sendChatCompletion(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getTemperature()).isEqualTo(0.7);
        assertThat(captor.getValue().getMaxTokens()).isEqualTo(100);
        assertThat(captor.getValue().getTopP()).isEqualTo(0.9);
    }

    @Test
    void shouldForceStreamFalseWhenAbsent() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = createValidRequest();

        service.processChatCompletion(request);

        ArgumentCaptor<UpstreamChatCompletionRequest> captor =
                ArgumentCaptor.forClass(UpstreamChatCompletionRequest.class);
        verify(upstreamClient).sendChatCompletion(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getStream()).isFalse();
    }

    @Test
    void shouldReturn409WhenNoDefaultModelConfig() {
        AppEntity app = createEnabledApp();
        app.setDefaultModelConfigId(null);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(null);

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("model_config_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldReturn409WhenModelConfigHasNoEncryptedKey() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        config.setApiKeyEncrypted(null);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("model_config_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldReturn409WhenDecryptFails() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted()))
                .thenThrow(new IllegalArgumentException("Failed to decrypt upstream API key"));

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("model_config_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldReturn400WhenStreamIsTrue() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldLogValidationFailureWithRequestIdWithoutMessageContent(CapturedOutput output) {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.validation_failed");
        assertThat(logs).contains("request_id=request-123");
        assertThat(logs).contains("error_code=invalid_request");
        assertThat(logs).contains("reason=stream_rejected");
        assertThat(logs).doesNotContain("Hello");
        assertThat(logs).doesNotContain("sk-sangui-abcdef");
    }

    @Test
    void shouldReturn400WhenMessagesIsNull() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o");
        request.setMessages(null);

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn400WhenRequestBodyIsNull() {
        assertThatThrownBy(() -> service.validateRequest(null))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn400WhenMessagesIsEmpty() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setMessages(List.of());

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn400WhenMessageHasNoRole() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setMessages(List.of(new OpenAiChatMessage(null, "Hello")));

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn400WhenMessageRoleIsUnsupported() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setMessages(List.of(new OpenAiChatMessage("tool", "Hello")));

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn400WhenMessageHasNoContent() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setMessages(List.of(new OpenAiChatMessage("user", null)));

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldIgnoreUnknownFieldsInUpstreamSuccessResponse() {
        String responseWithExtraFields = """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1710000000,
                  "model": "gpt-4o-mini",
                  "system_fingerprint": "fp-test",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello",
                        "refusal": null
                      },
                      "finish_reason": "stop",
                      "logprobs": null
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 1,
                    "completion_tokens": 1,
                    "total_tokens": 2,
                    "prompt_tokens_details": {"cached_tokens": 0}
                  }
                }
                """;

        OpenAiChatCompletionResponse response = service.parseResponse(responseWithExtraFields, "gpt-4o-mini", 0L);

        assertThat(response.getObject()).isEqualTo("chat.completion");
        assertThat(response.getChoices()).hasSize(1);
        assertThat(response.getChoices().get(0).getMessage().getContent()).isEqualTo("Hello");
    }

    @Test
    void shouldLogParseFailureWithoutUpstreamBodyOrMessages(CapturedOutput output) {
        String invalidResponse = "provider-secret Hello";

        assertThatThrownBy(() -> service.parseResponse(invalidResponse, "gpt-4o-mini", 12L))
                .isInstanceOf(GatewayException.class)
                .matches(e -> ((GatewayException) e).getCode().equals("upstream_error"));

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.response_parse_failed");
        assertThat(logs).contains("request_id=request-123");
        assertThat(logs).contains("model=gpt-4o-mini");
        assertThat(logs).doesNotContain("provider-secret");
        assertThat(logs).doesNotContain("Hello");
    }

    @Test
    void shouldReturn502OnUpstreamError() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenThrow(new GatewayException("Upstream service returned an error",
                        "server_error", "upstream_error", org.springframework.http.HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_error")
                            && ge.getHttpStatus().value() == 502;
                });
    }

    @Test
    void shouldReturn504OnUpstreamTimeout() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenThrow(new GatewayException("Upstream request timed out",
                        "server_error", "upstream_timeout", org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("upstream_timeout")
                            && ge.getHttpStatus().value() == 504;
                });
    }
}
