package com.sangui.raggateway.model;

import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.common.util.RestClientTimeoutFactory;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.embedding.EmbeddingProbeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

@Service
@Profile("!test")
public class ModelConfigCheckService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigCheckService.class);

    private static final String CHAT_PATH = "/v1/chat/completions";

    private final ModelConfigService modelConfigService;
    private final UpstreamApiKeyEncryptor encryptor;
    private final EmbeddingClient embeddingClient;
    private final RestClient restClient;

    @Autowired
    public ModelConfigCheckService(ModelConfigService modelConfigService,
                                   UpstreamApiKeyEncryptor encryptor,
                                   EmbeddingClient embeddingClient,
                                   @Value("${rag.gateway.upstream.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                                   @Value("${rag.gateway.upstream.response-timeout-seconds:${rag.gateway.upstream.timeout-seconds:30}}") int responseTimeoutSeconds) {
        this.modelConfigService = modelConfigService;
        this.encryptor = encryptor;
        this.embeddingClient = embeddingClient;
        this.restClient = RestClient.builder()
                .requestFactory(RestClientTimeoutFactory.createRequestFactory(
                        connectTimeoutSeconds, responseTimeoutSeconds))
                .build();
    }

    ModelConfigCheckService(ModelConfigService modelConfigService,
                            UpstreamApiKeyEncryptor encryptor,
                            EmbeddingClient embeddingClient,
                            RestClient restClient) {
        this.modelConfigService = modelConfigService;
        this.encryptor = encryptor;
        this.embeddingClient = embeddingClient;
        this.restClient = restClient;
    }

    public ModelConfigCheckResult checkUnsavedConfig(Long userId, ModelConfigCheckRequest request) {
        ModelConfigCapability capability = ModelConfigService.parseCapability(request.getCapability());
        String baseUrl = requireNonBlank(request.getBaseUrl(), "baseUrl");
        String apiKey = requireNonBlank(request.getApiKey(), "apiKey");
        requireCheckFields(capability, request.getChatModel(), request.getEmbeddingModel());

        return doCheck(capability, request.getProviderName(), baseUrl, apiKey,
                request.getChatModel(), request.getEmbeddingModel(), request.getEmbeddingDimension());
    }

    public ModelConfigCheckResult checkSavedConfig(Long userId, Long configId, ModelConfigCheckRequest request) {
        ModelConfigEntity entity = modelConfigService.findByIdAndUserId(configId, userId);
        if (entity == null) {
            throw new IllegalArgumentException("Model config not found or not owned by this user");
        }

        ModelConfigCapability capability;
        if (request.getCapability() != null) {
            capability = ModelConfigService.parseCapability(request.getCapability());
        } else {
            String storedCap = entity.getCapability();
            if (ModelConfigCapability.CHAT_EMBEDDING.name().equals(storedCap)) {
                capability = resolveLegacyChatEmbeddingCapability(entity);
            } else {
                capability = ModelConfigService.parseCapability(storedCap);
            }
        }

        String baseUrl = entity.getBaseUrl();
        if (request.getBaseUrl() != null && !request.getBaseUrl().isBlank()) {
            baseUrl = request.getBaseUrl();
        }

        String apiKey = null;
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            apiKey = request.getApiKey();
        } else {
            try {
                apiKey = encryptor.decrypt(entity.getApiKeyEncrypted());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("MODEL_CONFIG_NOT_READY",
                        "The saved upstream API key cannot be decrypted with the current encryption secret. " +
                        "To restore: set RAG_GATEWAY_ENCRYPTION_SECRET_KEY to the original AES secret used when " +
                        "this key was saved, or update this model config with a new upstream API key.",
                        HttpStatus.BAD_REQUEST);
            }
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("MODEL_CONFIG_NOT_READY",
                    "No upstream API key is saved for this config. " +
                    "Update this model config with a new upstream API key.",
                    HttpStatus.BAD_REQUEST);
        }

        String chatModel = entity.getChatModel();
        if (request.getChatModel() != null && !request.getChatModel().isBlank()) {
            chatModel = request.getChatModel();
        }

        String embeddingModel = entity.getEmbeddingModel();
        if (request.getEmbeddingModel() != null && !request.getEmbeddingModel().isBlank()) {
            embeddingModel = request.getEmbeddingModel();
        }

        Integer embeddingDimension = entity.getEmbeddingDimension();
        if (request.getEmbeddingDimension() != null) {
            embeddingDimension = request.getEmbeddingDimension();
        }

        requireCheckFields(capability, chatModel, embeddingModel);
        return doCheck(capability, entity.getProviderName(), baseUrl, apiKey,
                chatModel, embeddingModel, embeddingDimension);
    }

    private ModelConfigCheckResult doCheck(ModelConfigCapability capability, String providerName,
                                           String baseUrl, String apiKey,
                                           String chatModel, String embeddingModel,
                                           Integer configuredDimension) {
        ModelConfigCheckResult result = new ModelConfigCheckResult();
        result.setCapability(capability.name());
        result.setBaseUrlChecked(false);

        boolean checkedChat = false;
        boolean checkedEmbedding = false;

        if (capability.isChatCapable() && chatModel != null && !chatModel.isBlank()) {
            ModelConfigCheckResult.ChatCheckResult chatResult = checkChat(baseUrl, apiKey, chatModel);
            result.setChat(chatResult);
            checkedChat = true;
            result.setBaseUrlChecked(true);
        }

        if (capability.isEmbeddingCapable() && embeddingModel != null && !embeddingModel.isBlank()) {
            ModelConfigCheckResult.EmbeddingCheckResult embResult =
                    checkEmbedding(baseUrl, apiKey, embeddingModel, configuredDimension);
            result.setEmbedding(embResult);
            checkedEmbedding = true;
            result.setBaseUrlChecked(true);
        }

        boolean hasSuccess = isSuccess(result.getChat()) || isSuccess(result.getEmbedding());
        boolean hasFailure = isFailed(result.getChat()) || isFailed(result.getEmbedding());

        if (!checkedChat && !checkedEmbedding) {
            result.setOverallStatus("FAILED");
        } else if (hasSuccess && !hasFailure) {
            result.setOverallStatus("SUCCESS");
        } else if (!hasSuccess) {
            result.setOverallStatus("FAILED");
        } else {
            result.setOverallStatus("PARTIAL");
        }

        return result;
    }

    private ModelConfigCheckResult.ChatCheckResult checkChat(String baseUrl, String apiKey, String chatModel) {
        ModelConfigCheckResult.ChatCheckResult result = new ModelConfigCheckResult.ChatCheckResult();
        result.setModel(chatModel);

        String base = normalizeBaseUrl(baseUrl);
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + CHAT_PATH;

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(Map.of("role", "user", "content", "ping")),
                "max_tokens", 1,
                "stream", false
        );

        try {
            boolean succeeded = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            log.info("gateway.check.chat_success model={}", chatModel);
                            return true;
                        }
                        log.warn("gateway.check.chat_failed status={} model={}",
                                status.value(), chatModel);
                        return false;
                    });

            if (succeeded) {
                result.setStatus("SUCCESS");
                result.setMessage("Chat check succeeded.");
            } else {
                result.setStatus("FAILED");
                result.setMessage("Chat check failed: upstream_error");
            }
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            boolean timeout = cause instanceof SocketTimeoutException
                    || (cause instanceof java.net.ConnectException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("time"));
            log.warn("gateway.check.chat_failed error_class={} timeout={} model={}",
                    e.getClass().getSimpleName(), timeout, chatModel);
            result.setStatus("FAILED");
            result.setMessage(timeout ? "Upstream timeout" : "Upstream is unavailable");
        } catch (Exception e) {
            log.warn("gateway.check.chat_failed error_class={} model={}",
                    e.getClass().getSimpleName(), chatModel);
            result.setStatus("FAILED");
            result.setMessage("Chat check failed: " + classifyError(e));
        }

        return result;
    }

    private boolean isSuccess(ModelConfigCheckResult.ChatCheckResult result) {
        return result != null && "SUCCESS".equals(result.getStatus());
    }

    private boolean isSuccess(ModelConfigCheckResult.EmbeddingCheckResult result) {
        return result != null && "SUCCESS".equals(result.getStatus());
    }

    private boolean isFailed(ModelConfigCheckResult.ChatCheckResult result) {
        return result != null && "FAILED".equals(result.getStatus());
    }

    private boolean isFailed(ModelConfigCheckResult.EmbeddingCheckResult result) {
        return result != null && "FAILED".equals(result.getStatus());
    }

    private void requireCheckFields(ModelConfigCapability capability, String chatModel, String embeddingModel) {
        if (capability.isChatCapable()) {
            requireNonBlank(chatModel, "chatModel");
        }
        if (capability.isEmbeddingCapable()) {
            requireNonBlank(embeddingModel, "embeddingModel");
        }
    }

    private ModelConfigCheckResult.EmbeddingCheckResult checkEmbedding(
            String baseUrl, String apiKey, String embeddingModel, Integer configuredDimension) {
        ModelConfigCheckResult.EmbeddingCheckResult result = new ModelConfigCheckResult.EmbeddingCheckResult();
        result.setModel(embeddingModel);
        result.setConfiguredDimension(configuredDimension);

        try {
            EmbeddingProbeResult probe = embeddingClient.probe(baseUrl, apiKey, embeddingModel);
            result.setActualDimension(probe.getDimension());
            if (configuredDimension != null
                    && !configuredDimension.equals(probe.getDimension())) {
                result.setStatus("FAILED");
                result.setMessage("Dimension mismatch: configured " + configuredDimension
                        + " but actual " + probe.getDimension());
            } else {
                result.setStatus("SUCCESS");
                result.setMessage("Embedding check succeeded.");
            }
        } catch (EmbeddingException e) {
            log.warn("gateway.check.embedding_failed error_class={} model={}",
                    e.getClass().getSimpleName(), embeddingModel);
            result.setStatus("FAILED");
            result.setMessage("Embedding check failed: " + e.getMessage());
        } catch (Exception e) {
            log.warn("gateway.check.embedding_failed error_class={} model={}",
                    e.getClass().getSimpleName(), embeddingModel);
            result.setStatus("FAILED");
            result.setMessage("Embedding check failed: " + classifyError(e));
        }

        return result;
    }

    private String classifyError(Exception e) {
        if (e instanceof ResourceAccessException) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                return "upstream_timeout";
            }
            return "upstream_unavailable";
        }
        return "upstream_error";
    }

    private static ModelConfigCapability resolveLegacyChatEmbeddingCapability(ModelConfigEntity entity) {
        boolean hasChat = entity.getChatModel() != null && !entity.getChatModel().isBlank();
        boolean hasEmbedding = entity.getEmbeddingModel() != null && !entity.getEmbeddingModel().isBlank();
        if (hasEmbedding) {
            return ModelConfigCapability.EMBEDDING;
        }
        if (hasChat) {
            return ModelConfigCapability.CHAT;
        }
        return ModelConfigCapability.CHAT;
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", fieldName + " is required");
        }
        return value;
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "baseUrl must not be blank");
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
