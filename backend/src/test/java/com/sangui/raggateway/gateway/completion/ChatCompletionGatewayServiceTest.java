package com.sangui.raggateway.gateway.completion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppRetrievalConfig;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionRequest;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionResponse;
import com.sangui.raggateway.gateway.openai.OpenAiChatMessage;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.retrieval.RetrievalResult;
import com.sangui.raggateway.retrieval.RetrievalService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private RetrievalService retrievalService;

    private ChatCompletionGatewayService service;

    private static final Long APP_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long API_KEY_ID = 30L;
    private static final Long MODEL_CONFIG_ID = 10L;
    private static final Long KB_ID = 20L;
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
        service = new ChatCompletionGatewayService(appService, encryptor, upstreamClient, new ObjectMapper(), retrievalService);
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
        app.setDefaultKnowledgeBaseId(KB_ID);
        app.setRetrievalTopK(5);
        app.setRetrievalSimilarityThreshold(0.300);
        app.setRetrievalMaxContextChunks(5);
        app.setRetrievalMaxContextChars(12000);
        app.setRetrievalMaxSingleChunkChars(3000);
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

    private KnowledgeBaseEntity createReadyKnowledgeBase() {
        KnowledgeBaseEntity kb = new KnowledgeBaseEntity();
        kb.setId(KB_ID);
        kb.setUserId(USER_ID);
        kb.setName("Test KB");
        kb.setEmbeddingModel("text-embedding-3-small");
        kb.setEmbeddingDimension(1536);
        kb.setStatus("READY");
        return kb;
    }

    private OpenAiChatCompletionRequest createValidRequest() {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setModel("gpt-4o");
        request.setMessages(List.of(new OpenAiChatMessage("user", "Hello")));
        return request;
    }

    private RetrievalResult createHitRetrievalResult() {
        RetrievalResult.RetrievedChunk chunk = new RetrievalResult.RetrievedChunk(
                1L, 10L, 20L, 0, "handbook.md", "chunk content", null, 0.85, 13, 13, "S1");
        com.sangui.raggateway.retrieval.Citation citation = new com.sangui.raggateway.retrieval.Citation(
                "S1", 1L, 10L, 20L, "handbook.md", 0, 0.85, null, 13, 13);
        com.sangui.raggateway.retrieval.RetrievalEvidence evidence = new com.sangui.raggateway.retrieval.RetrievalEvidence(
                1, false, 50L, 5, 0.3, 5, List.of(citation));
        return new RetrievalResult(List.of(chunk), List.of(1L), List.of(citation), evidence, false, 50L);
    }

    private void stubResolveRetrievalConfig() {
        when(appService.resolveRetrievalConfig(any(AppEntity.class)))
                .thenAnswer(inv -> AppRetrievalConfig.from(inv.getArgument(0)));
    }

    @Test
    void shouldReturnValidResponseForGoodCase() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = createValidRequest();
        ChatCompletionResult result = service.processChatCompletion(request);
        OpenAiChatCompletionResponse response = result.getResponse();

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

        assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(result.getProviderName()).isEqualTo("openai");
        assertThat(result.getUpstreamLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.getPromptTokens()).isEqualTo(1);
        assertThat(result.getCompletionTokens()).isEqualTo(1);
        assertThat(result.getTotalTokens()).isEqualTo(2);
        assertThat(result.getQuestionSummary()).isEqualTo("Hello");
        assertThat(result.getHitChunkIds()).isEqualTo("[1]");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getCitationId()).isEqualTo("S1");
        assertThat(result.getRetrievalEvidence()).isNotNull();
        assertThat(result.getRetrievalEvidence()).contains("\"no_hits\":false");
        assertThat(result.getRetrievalEvidence()).contains("\"citation_id\":\"S1\"");
    }

    @Test
    void shouldUseConfiguredChatModelNotCallerModel() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
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
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
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
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
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
    void shouldReturn409WhenNoDefaultKnowledgeBase() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(null);

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("knowledge_base_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldReturn409WhenRetrievalConfigInvalid() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        when(appService.resolveRetrievalConfig(app))
                .thenThrow(new IllegalArgumentException("retrievalTopK must be positive, got: 0"));

        assertThatThrownBy(() -> service.processChatCompletion(createValidRequest()))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("model_config_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldPrepareStreamCompletionSuccessfully() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);

        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);
        ChatCompletionStreamPreparation prep = service.prepareStreamCompletion(request);

        assertThat(prep.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(prep.getProviderName()).isEqualTo("openai");
        assertThat(prep.getBaseUrl()).isEqualTo("https://api.openai.com");
        assertThat(prep.getApiKey()).isEqualTo(DECRYPTED_KEY);
        assertThat(prep.getUpstreamRequest().getStream()).isTrue();
        assertThat(prep.getUpstreamRequest().getModel()).isEqualTo("gpt-4o-mini");
        assertThat(prep.getQuestionSummary()).isEqualTo("Hello");
        assertThat(prep.getHitChunkIds()).isEqualTo("[1]");
    }

    @Test
    void shouldRejectNullRequestInPrepareStream() {
        assertThatThrownBy(() -> service.prepareStreamCompletion(null))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldRejectEmptyMessagesInPrepareStream() {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);
        request.setMessages(List.of());

        assertThatThrownBy(() -> service.prepareStreamCompletion(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }

    @Test
    void shouldReturn409WhenNoModelConfigInPrepareStream() {
        AppEntity app = createEnabledApp();
        app.setDefaultModelConfigId(null);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(null);

        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);

        assertThatThrownBy(() -> service.prepareStreamCompletion(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("model_config_not_ready")
                            && ge.getHttpStatus().value() == 409;
                });
    }

    @Test
    void shouldForwardStreamTrueToUpstreamForStreamRequest() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);

        OpenAiChatCompletionRequest request = createValidRequest();
        request.setStream(true);

        ChatCompletionStreamPreparation prep = service.prepareStreamCompletion(request);
        assertThat(prep.getUpstreamRequest().getStream()).isTrue();
    }

    @Test
    void shouldLogValidationFailureWithRequestIdWithoutMessageContent(CapturedOutput output) {
        OpenAiChatCompletionRequest request = createValidRequest();
        request.setMessages(null);

        assertThatThrownBy(() -> service.validateRequest(request))
                .isInstanceOf(GatewayException.class);

        String logs = output.getOut() + output.getErr();
        assertThat(logs).contains("gateway.chat.validation_failed");
        assertThat(logs).contains("request_id=request-123");
        assertThat(logs).contains("error_code=invalid_request");
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
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
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
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult retrievalResult = createHitRetrievalResult();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(retrievalResult);
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

    @Test
    void shouldCallUpstreamWithNoHits() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();
        KnowledgeBaseEntity kb = createReadyKnowledgeBase();
        RetrievalResult noHitResult = new RetrievalResult(List.of(), List.of(), List.of(), null, true, 50L);

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(kb);
        stubResolveRetrievalConfig();
        when(retrievalService.retrieve(eq("Hello"), eq(kb), anyInt(), anyDouble(),
                anyInt(), anyInt(), anyInt())).thenReturn(noHitResult);
        when(upstreamClient.sendChatCompletion(anyString(), anyString(), any(UpstreamChatCompletionRequest.class)))
                .thenReturn(UPSTREAM_RESPONSE);

        OpenAiChatCompletionRequest request = createValidRequest();
        ChatCompletionResult result = service.processChatCompletion(request);

        assertThat(result.getResponse().getObject()).isEqualTo("chat.completion");
        assertThat(result.getHitChunkIds()).isNull();
        assertThat(result.getQuestionSummary()).isEqualTo("Hello");
    }

    @Test
    void shouldReturn400WhenNoUserMessage() {
        AppEntity app = createEnabledApp();
        ModelConfigEntity config = createEnabledModelConfig();

        when(appService.findById(APP_ID)).thenReturn(app);
        when(appService.resolveDefaultModelConfig(app)).thenReturn(config);
        when(encryptor.decrypt(config.getApiKeyEncrypted())).thenReturn(DECRYPTED_KEY);
        when(appService.resolveDefaultKnowledgeBase(app)).thenReturn(createReadyKnowledgeBase());

        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest();
        request.setMessages(List.of(new OpenAiChatMessage("system", "You are a helpful assistant.")));

        assertThatThrownBy(() -> service.processChatCompletion(request))
                .isInstanceOf(GatewayException.class)
                .matches(e -> {
                    GatewayException ge = (GatewayException) e;
                    return ge.getCode().equals("invalid_request")
                            && ge.getHttpStatus().value() == 400;
                });
    }
}
