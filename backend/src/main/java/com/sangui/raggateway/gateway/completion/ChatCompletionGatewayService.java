package com.sangui.raggateway.gateway.completion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionRequest;
import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionResponse;
import com.sangui.raggateway.gateway.openai.OpenAiChatMessage;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.log.ChatCompletionLogHelper;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.rag.prompt.RagPromptBuilder;
import com.sangui.raggateway.retrieval.RetrievalResult;
import com.sangui.raggateway.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Profile("!test")
public class ChatCompletionGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionGatewayService.class);

    private static final String ERR_TYPE = "invalid_request_error";
    private static final String ERR_CODE_INVALID_REQUEST = "invalid_request";
    private static final String ERR_CODE_MODEL_CONFIG_NOT_READY = "model_config_not_ready";
    private static final String ERR_CODE_KNOWLEDGE_BASE_NOT_READY = "knowledge_base_not_ready";
    private static final String ERR_CODE_EMBEDDING_FAILED = "embedding_failed";

    private static final String ERR_MESSAGE_EMPTY_MESSAGES = "messages must be a non-empty array.";
    private static final String ERR_MESSAGE_MISSING_ROLE = "Each message must have a role.";
    private static final String ERR_MESSAGE_UNSUPPORTED_ROLE = "Unsupported message role.";
    private static final String ERR_MESSAGE_MISSING_CONTENT = "Each message must have content.";
    private static final String ERR_MESSAGE_CONFIG_NOT_READY = "Default model config is not configured for this app.";
    private static final String ERR_MESSAGE_KB_NOT_READY = "Knowledge base is not ready for this app.";
    private static final String ERR_MESSAGE_NO_USER_MESSAGE = "At least one user message is required for retrieval.";

    private static final String ERR_TYPE_SERVER = "server_error";
    private static final Set<String> SUPPORTED_ROLES = Set.of("system", "user", "assistant");

    private final AppService appService;
    private final UpstreamApiKeyEncryptor encryptor;
    private final OpenAiCompatibleUpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;
    private final RetrievalService retrievalService;

    public ChatCompletionGatewayService(AppService appService,
                                        UpstreamApiKeyEncryptor encryptor,
                                        OpenAiCompatibleUpstreamClient upstreamClient,
                                        ObjectMapper objectMapper,
                                        RetrievalService retrievalService) {
        this.appService = appService;
        this.encryptor = encryptor;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
        this.retrievalService = retrievalService;
    }

    public ChatCompletionResult processChatCompletion(OpenAiChatCompletionRequest request) {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        if (context == null) {
            throw new GatewayException("Invalid API key.", ERR_TYPE, "invalid_api_key", HttpStatus.UNAUTHORIZED);
        }

        validateRequest(request);

        AppEntity app = appService.findById(context.getAppId());
        if (app == null) {
            log.warn("App not found for appId={}", context.getAppId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        ModelConfigEntity modelConfig = appService.resolveDefaultModelConfig(app);
        if (modelConfig == null) {
            log.warn("Default model config not ready for appId={}", app.getId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        if (modelConfig.getApiKeyEncrypted() == null || modelConfig.getApiKeyEncrypted().isBlank()) {
            log.warn("Model config has no encrypted API key for configId={}", modelConfig.getId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        String decryptedKey;
        try {
            decryptedKey = encryptor.decrypt(modelConfig.getApiKeyEncrypted());
        } catch (Exception e) {
            log.warn("Failed to decrypt upstream API key for configId={}, errorType={}",
                    modelConfig.getId(), e.getClass().getSimpleName());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        log.info("gateway.chat.config_resolved request_id={} app_id={} api_key_id={} provider_name={} model={}",
                context.getRequestId(), context.getAppId(), context.getApiKeyId(),
                modelConfig.getProviderName(), modelConfig.getChatModel());

        RetrievalResult retrievalResult = performRetrieval(request, app);

        List<OpenAiChatMessage> augmentedMessages = RagPromptBuilder.buildAugmentedMessages(
                request.getMessages(), retrievalResult);

        UpstreamChatCompletionRequest upstreamRequest = buildUpstreamRequest(
                request, modelConfig, augmentedMessages);

        try {
            long upstreamStart = System.currentTimeMillis();
            String responseBody = upstreamClient.sendChatCompletion(
                    modelConfig.getBaseUrl(),
                    decryptedKey,
                    upstreamRequest
            );
            long upstreamLatency = System.currentTimeMillis() - upstreamStart;

            OpenAiChatCompletionResponse response = parseResponse(responseBody, modelConfig.getChatModel(), upstreamLatency);
            Integer promptTokens = null;
            Integer completionTokens = null;
            Integer totalTokens = null;
            if (response.getUsage() != null) {
                promptTokens = response.getUsage().getPromptTokens();
                completionTokens = response.getUsage().getCompletionTokens();
                totalTokens = response.getUsage().getTotalTokens();
            }
            String questionSummary = truncateForSummary(extractLastUserMessage(request.getMessages()));
            String hitChunkIdsJson = toJsonArray(retrievalResult.getHitChunkIds());
            return new ChatCompletionResult(response, modelConfig.getChatModel(),
                    modelConfig.getProviderName(), upstreamLatency,
                    promptTokens, completionTokens, totalTokens,
                    questionSummary, hitChunkIdsJson);
        } catch (GatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error processing chat completion", e);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    "upstream_error",
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        }
    }

    public ChatCompletionStreamPreparation prepareStreamCompletion(OpenAiChatCompletionRequest request) {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        if (context == null) {
            throw new GatewayException("Invalid API key.", ERR_TYPE, "invalid_api_key", HttpStatus.UNAUTHORIZED);
        }

        validateRequest(request);

        AppEntity app = appService.findById(context.getAppId());
        if (app == null) {
            log.warn("App not found for appId={}", context.getAppId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        ModelConfigEntity modelConfig = appService.resolveDefaultModelConfig(app);
        if (modelConfig == null) {
            log.warn("Default model config not ready for appId={}", app.getId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        if (modelConfig.getApiKeyEncrypted() == null || modelConfig.getApiKeyEncrypted().isBlank()) {
            log.warn("Model config has no encrypted API key for configId={}", modelConfig.getId());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        String decryptedKey;
        try {
            decryptedKey = encryptor.decrypt(modelConfig.getApiKeyEncrypted());
        } catch (Exception e) {
            log.warn("Failed to decrypt upstream API key for configId={}, errorType={}",
                    modelConfig.getId(), e.getClass().getSimpleName());
            throw new GatewayException(ERR_MESSAGE_CONFIG_NOT_READY, ERR_TYPE, ERR_CODE_MODEL_CONFIG_NOT_READY, HttpStatus.CONFLICT);
        }

        log.info("gateway.chat.config_resolved request_id={} app_id={} api_key_id={} provider_name={} model={}",
                context.getRequestId(), context.getAppId(), context.getApiKeyId(),
                modelConfig.getProviderName(), modelConfig.getChatModel());

        RetrievalResult retrievalResult = performRetrieval(request, app);

        List<OpenAiChatMessage> augmentedMessages = RagPromptBuilder.buildAugmentedMessages(
                request.getMessages(), retrievalResult);

        UpstreamChatCompletionRequest upstreamRequest = buildUpstreamRequest(
                request, modelConfig, augmentedMessages);
        upstreamRequest.setStream(true);

        String questionSummary = truncateForSummary(extractLastUserMessage(request.getMessages()));
        String hitChunkIdsJson = toJsonArray(retrievalResult.getHitChunkIds());

        return new ChatCompletionStreamPreparation(
                modelConfig.getBaseUrl(),
                decryptedKey,
                upstreamRequest,
                modelConfig.getChatModel(),
                modelConfig.getProviderName(),
                questionSummary,
                hitChunkIdsJson
        );
    }

    void validateRequest(OpenAiChatCompletionRequest request) {
        if (request == null) {
            logValidationFailed(null);
            throw new GatewayException(ERR_MESSAGE_EMPTY_MESSAGES, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            logValidationFailed("empty_messages");
            throw new GatewayException(ERR_MESSAGE_EMPTY_MESSAGES, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        for (int i = 0; i < request.getMessages().size(); i++) {
            OpenAiChatMessage msg = request.getMessages().get(i);
            if (msg == null) {
                logValidationFailed("null_message");
                throw new GatewayException(ERR_MESSAGE_MISSING_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (msg.getRole() == null || msg.getRole().isBlank()) {
                logValidationFailed("missing_role");
                throw new GatewayException(ERR_MESSAGE_MISSING_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (!SUPPORTED_ROLES.contains(msg.getRole())) {
                logValidationFailed("unsupported_role");
                throw new GatewayException(ERR_MESSAGE_UNSUPPORTED_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                logValidationFailed("missing_content");
                throw new GatewayException(ERR_MESSAGE_MISSING_CONTENT, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void logValidationFailed(String reason) {
        String requestId = ChatCompletionLogHelper.currentRequestId();
        if (reason == null) {
            log.warn("gateway.chat.validation_failed request_id={} error_code={}",
                    requestId, ERR_CODE_INVALID_REQUEST);
            return;
        }
        log.warn("gateway.chat.validation_failed request_id={} error_code={} reason={}",
                requestId, ERR_CODE_INVALID_REQUEST, reason);
    }

    UpstreamChatCompletionRequest buildUpstreamRequest(OpenAiChatCompletionRequest request,
                                                       ModelConfigEntity modelConfig,
                                                       List<OpenAiChatMessage> augmentedMessages) {
        UpstreamChatCompletionRequest upstream = new UpstreamChatCompletionRequest();

        upstream.setModel(modelConfig.getChatModel());

        List<UpstreamChatCompletionRequest.Message> upstreamMessages = new ArrayList<>();
        for (OpenAiChatMessage msg : augmentedMessages) {
            upstreamMessages.add(new UpstreamChatCompletionRequest.Message(msg.getRole(), msg.getContent()));
        }
        upstream.setMessages(upstreamMessages);

        if (request.getTemperature() != null) {
            upstream.setTemperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            upstream.setMaxTokens(request.getMaxTokens());
        }
        if (request.getTopP() != null) {
            upstream.setTopP(request.getTopP());
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            upstream.setStream(true);
        } else {
            upstream.setStream(false);
        }

        return upstream;
    }

    OpenAiChatCompletionResponse parseResponse(String responseBody, String chatModel, long upstreamLatencyMs) {
        try {
            OpenAiChatCompletionResponse response = objectMapper.readValue(responseBody, OpenAiChatCompletionResponse.class);
            if (response.getObject() == null) {
                response.setObject("chat.completion");
            }
            if (response.getModel() == null) {
                response.setModel(chatModel);
            }
            String requestId = ChatCompletionLogHelper.currentRequestId();
            log.info("gateway.chat.response_parse_succeeded request_id={} model={} upstream_latency_ms={}",
                    requestId, chatModel, upstreamLatencyMs);
            return response;
        } catch (Exception e) {
            String requestId = ChatCompletionLogHelper.currentRequestId();
            log.error("gateway.chat.response_parse_failed request_id={} model={} error_class={}",
                    requestId, chatModel, e.getClass().getSimpleName());
            throw new GatewayException(
                    "Upstream service returned an invalid response",
                    ERR_TYPE_SERVER,
                    "upstream_error",
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        }
    }

    private RetrievalResult performRetrieval(OpenAiChatCompletionRequest request, AppEntity app) {
        KnowledgeBaseEntity kb = appService.resolveDefaultKnowledgeBase(app);
        if (kb == null) {
            log.warn("Default knowledge base not ready for appId={}", app.getId());
            throw new GatewayException(ERR_MESSAGE_KB_NOT_READY, ERR_TYPE,
                    ERR_CODE_KNOWLEDGE_BASE_NOT_READY, HttpStatus.CONFLICT);
        }

        String userMessage = extractLastUserMessage(request.getMessages());
        if (userMessage == null) {
            throw new GatewayException(ERR_MESSAGE_NO_USER_MESSAGE, ERR_TYPE,
                    ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        int topK = app.getRetrievalTopK() != null ? app.getRetrievalTopK() : 5;
        double threshold = app.getRetrievalSimilarityThreshold() != null
                ? app.getRetrievalSimilarityThreshold() : 0.700;
        int maxChunks = app.getRetrievalMaxContextChunks() != null
                ? app.getRetrievalMaxContextChunks() : 5;
        int maxChars = app.getRetrievalMaxContextChars() != null
                ? app.getRetrievalMaxContextChars() : 12000;
        int maxSingleChars = app.getRetrievalMaxSingleChunkChars() != null
                ? app.getRetrievalMaxSingleChunkChars() : 3000;

        try {
            return retrievalService.retrieve(
                    userMessage, kb, topK, threshold, maxChunks, maxChars, maxSingleChars);
        } catch (EmbeddingException e) {
            log.warn("Embedding failed for appId={} kbId={}", app.getId(), kb.getId());
            throw new GatewayException("Embedding failed for retrieval",
                    ERR_TYPE_SERVER, ERR_CODE_EMBEDDING_FAILED, HttpStatus.BAD_GATEWAY);
        }
    }

    static String extractLastUserMessage(List<OpenAiChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            OpenAiChatMessage msg = messages.get(i);
            if ("user".equals(msg.getRole()) && msg.getContent() != null) {
                return msg.getContent();
            }
        }
        return null;
    }

    static String truncateForSummary(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= 512) {
            return text;
        }
        return text.substring(0, 512);
    }

    static String toJsonArray(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(ids.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}
