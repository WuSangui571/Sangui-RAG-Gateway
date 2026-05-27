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

    private static final String ERR_MESSAGE_STREAM_REJECTED = "Streaming is not supported in this version.";
    private static final String ERR_MESSAGE_EMPTY_MESSAGES = "messages must be a non-empty array.";
    private static final String ERR_MESSAGE_MISSING_ROLE = "Each message must have a role.";
    private static final String ERR_MESSAGE_UNSUPPORTED_ROLE = "Unsupported message role.";
    private static final String ERR_MESSAGE_MISSING_CONTENT = "Each message must have content.";
    private static final String ERR_MESSAGE_CONFIG_NOT_READY = "Default model config is not configured for this app.";

    private static final String ERR_TYPE_SERVER = "server_error";
    private static final Set<String> SUPPORTED_ROLES = Set.of("system", "user", "assistant");

    private final AppService appService;
    private final UpstreamApiKeyEncryptor encryptor;
    private final OpenAiCompatibleUpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;

    public ChatCompletionGatewayService(AppService appService,
                                        UpstreamApiKeyEncryptor encryptor,
                                        OpenAiCompatibleUpstreamClient upstreamClient,
                                        ObjectMapper objectMapper) {
        this.appService = appService;
        this.encryptor = encryptor;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
    }

    public OpenAiChatCompletionResponse processChatCompletion(OpenAiChatCompletionRequest request) {
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

        UpstreamChatCompletionRequest upstreamRequest = buildUpstreamRequest(request, modelConfig);

        try {
            String responseBody = upstreamClient.sendChatCompletion(
                    modelConfig.getBaseUrl(),
                    decryptedKey,
                    upstreamRequest
            );

            return parseResponse(responseBody, modelConfig.getChatModel());
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

    void validateRequest(OpenAiChatCompletionRequest request) {
        if (request == null) {
            throw new GatewayException(ERR_MESSAGE_EMPTY_MESSAGES, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            throw new GatewayException(ERR_MESSAGE_STREAM_REJECTED, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            throw new GatewayException(ERR_MESSAGE_EMPTY_MESSAGES, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
        }

        for (int i = 0; i < request.getMessages().size(); i++) {
            OpenAiChatMessage msg = request.getMessages().get(i);
            if (msg == null) {
                throw new GatewayException(ERR_MESSAGE_MISSING_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (msg.getRole() == null || msg.getRole().isBlank()) {
                throw new GatewayException(ERR_MESSAGE_MISSING_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (!SUPPORTED_ROLES.contains(msg.getRole())) {
                throw new GatewayException(ERR_MESSAGE_UNSUPPORTED_ROLE, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                throw new GatewayException(ERR_MESSAGE_MISSING_CONTENT, ERR_TYPE, ERR_CODE_INVALID_REQUEST, HttpStatus.BAD_REQUEST);
            }
        }
    }

    UpstreamChatCompletionRequest buildUpstreamRequest(OpenAiChatCompletionRequest request, ModelConfigEntity modelConfig) {
        UpstreamChatCompletionRequest upstream = new UpstreamChatCompletionRequest();

        upstream.setModel(modelConfig.getChatModel());

        List<UpstreamChatCompletionRequest.Message> upstreamMessages = new ArrayList<>();
        for (OpenAiChatMessage msg : request.getMessages()) {
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

    OpenAiChatCompletionResponse parseResponse(String responseBody, String chatModel) {
        try {
            OpenAiChatCompletionResponse response = objectMapper.readValue(responseBody, OpenAiChatCompletionResponse.class);
            if (response.getObject() == null) {
                response.setObject("chat.completion");
            }
            if (response.getModel() == null) {
                response.setModel(chatModel);
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to parse upstream chat completion response", e);
            throw new GatewayException(
                    "Upstream service returned an invalid response",
                    ERR_TYPE_SERVER,
                    "upstream_error",
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        }
    }
}
