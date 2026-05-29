package com.sangui.raggateway.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.response.OpenAiErrorResponse;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.gateway.completion.ChatCompletionGatewayService;
import com.sangui.raggateway.gateway.completion.ChatCompletionResult;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.log.ApiRequestLogService;
import com.sangui.raggateway.log.CreateRequestLogCommand;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@RestController
@Profile("!test")
public class OpenAiChatCompletionsController {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatCompletionsController.class);

    private final ChatCompletionGatewayService chatCompletionGatewayService;
    private final ApiRequestLogService apiRequestLogService;
    private final OpenAiCompatibleUpstreamClient upstreamClient;
    private final ObjectMapper objectMapper;

    public OpenAiChatCompletionsController(ChatCompletionGatewayService chatCompletionGatewayService,
                                           ApiRequestLogService apiRequestLogService,
                                           OpenAiCompatibleUpstreamClient upstreamClient,
                                           ObjectMapper objectMapper) {
        this.chatCompletionGatewayService = chatCompletionGatewayService;
        this.apiRequestLogService = apiRequestLogService;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/v1/chat/completions")
    public Object chatCompletions(@RequestBody OpenAiChatCompletionRequest request) {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        if (context == null) {
            throw new GatewayException("Invalid API key.", "invalid_request_error", "invalid_api_key", HttpStatus.UNAUTHORIZED);
        }

        String requestId = UUID.randomUUID().toString();
        context.setRequestId(requestId);

        int messagesCount = request != null && request.getMessages() != null ? request.getMessages().size() : 0;
        long start = System.currentTimeMillis();

        if (Boolean.TRUE.equals(request.getStream())) {
            return handleStreamCompletion(request, context, requestId, messagesCount, start);
        }

        try {
            ChatCompletionResult result = chatCompletionGatewayService.processChatCompletion(request);
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=success messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    messagesCount, latencyMs);

            apiRequestLogService.record(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .model(result.getModel())
                    .providerName(result.getProviderName())
                    .status("success")
                    .latencyMs(latencyMs)
                    .upstreamLatencyMs(result.getUpstreamLatencyMs())
                    .promptTokens(result.getPromptTokens())
                    .completionTokens(result.getCompletionTokens())
                    .totalTokens(result.getTotalTokens())
                    .messagesCount(messagesCount)
                    .questionSummary(result.getQuestionSummary())
                    .hitChunkIds(result.getHitChunkIds())
                    .build());

            return ResponseEntity.ok(result.getResponse());
        } catch (GatewayException e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    e.getCode(), messagesCount, latencyMs);

            apiRequestLogService.record(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .status("failure")
                    .errorCode(e.getCode())
                    .latencyMs(latencyMs)
                    .messagesCount(messagesCount)
                    .build());

            throw e;
        }
    }

    private SseEmitter handleStreamCompletion(OpenAiChatCompletionRequest request,
                                               GatewayRequestContext context,
                                               String requestId, int messagesCount, long start) {
        ChatCompletionStreamPreparation prep;
        try {
            prep = chatCompletionGatewayService.prepareStreamCompletion(request);
        } catch (GatewayException e) {
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    e.getCode(), messagesCount, latencyMs);

            apiRequestLogService.record(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .status("failure")
                    .errorCode(e.getCode())
                    .latencyMs(latencyMs)
                    .messagesCount(messagesCount)
                    .build());

            throw e;
        }

        SseEmitter emitter = new SseEmitter(0L);

        Long userId = context.getUserId();
        Long appId = context.getAppId();
        Long apiKeyId = context.getApiKeyId();
        String model = prep.getModel();
        String providerName = prep.getProviderName();
        CompletableFuture<Void> streamReady = new CompletableFuture<>();
        AtomicBoolean responseCommitted = new AtomicBoolean(false);

        Thread.ofVirtual().start(() -> {
            try {
                long upstreamStart = System.currentTimeMillis();
                upstreamClient.streamChatCompletion(prep.getBaseUrl(), prep.getApiKey(),
                        prep.getUpstreamRequest(), emitter, requestId, () -> {
                            responseCommitted.set(true);
                            streamReady.complete(null);
                        });
                emitter.complete();

                long latencyMs = System.currentTimeMillis() - start;
                long upstreamLatencyMs = System.currentTimeMillis() - upstreamStart;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=success messages_count={} latency_ms={} upstream_latency_ms={}",
                        requestId, appId, apiKeyId, userId, messagesCount, latencyMs, upstreamLatencyMs);

                apiRequestLogService.record(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .appId(appId)
                        .apiKeyId(apiKeyId)
                        .model(model)
                        .providerName(providerName)
                        .status("success")
                        .latencyMs(latencyMs)
                        .upstreamLatencyMs(upstreamLatencyMs)
                        .messagesCount(messagesCount)
                        .questionSummary(prep.getQuestionSummary())
                        .hitChunkIds(prep.getHitChunkIds())
                        .build());
            } catch (GatewayException e) {
                if (!responseCommitted.get()) {
                    streamReady.completeExceptionally(e);
                    return;
                }
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, e.getCode(), messagesCount, latencyMs);

                sendSseError(emitter, e.getMessage(), e.getType(), e.getCode());

                apiRequestLogService.record(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .appId(appId)
                        .apiKeyId(apiKeyId)
                        .model(model)
                        .providerName(providerName)
                        .status("failure")
                        .errorCode(e.getCode())
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .questionSummary(prep.getQuestionSummary())
                        .hitChunkIds(prep.getHitChunkIds())
                        .build());
            } catch (Exception e) {
                if (!responseCommitted.get()) {
                    streamReady.completeExceptionally(new GatewayException(
                            "Upstream service is unavailable",
                            "server_error",
                            "upstream_error",
                            HttpStatus.BAD_GATEWAY,
                            e
                    ));
                    return;
                }
                long latencyMs = System.currentTimeMillis() - start;
                String errorCode = "upstream_error";
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, errorCode, messagesCount, latencyMs);

                sendSseError(emitter, "Upstream service is unavailable", "server_error", errorCode);

                apiRequestLogService.record(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .appId(appId)
                        .apiKeyId(apiKeyId)
                        .model(model)
                        .providerName(providerName)
                        .status("failure")
                        .errorCode(errorCode)
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .questionSummary(prep.getQuestionSummary())
                        .hitChunkIds(prep.getHitChunkIds())
                        .build());
            }
        });

        waitForStreamReady(streamReady, context, requestId, messagesCount, start, model, providerName,
                prep.getQuestionSummary(), prep.getHitChunkIds());
        return emitter;
    }

    private void waitForStreamReady(CompletableFuture<Void> streamReady,
                                    GatewayRequestContext context,
                                    String requestId,
                                    int messagesCount,
                                    long start,
                                    String model,
                                    String providerName,
                                    String questionSummary,
                                    String hitChunkIds) {
        try {
            streamReady.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            GatewayException gatewayException;
            if (cause instanceof GatewayException ge) {
                gatewayException = ge;
            } else {
                gatewayException = new GatewayException(
                        "Upstream service is unavailable",
                        "server_error",
                        "upstream_error",
                        HttpStatus.BAD_GATEWAY,
                        cause
                );
            }

            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    gatewayException.getCode(), messagesCount, latencyMs);

            apiRequestLogService.record(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .model(model)
                    .providerName(providerName)
                    .status("failure")
                    .errorCode(gatewayException.getCode())
                    .latencyMs(latencyMs)
                    .messagesCount(messagesCount)
                    .questionSummary(questionSummary)
                    .hitChunkIds(hitChunkIds)
                    .build());

            throw gatewayException;
        }
    }

    private void sendSseError(SseEmitter emitter, String message, String type, String code) {
        try {
            OpenAiErrorResponse errorResponse = OpenAiErrorResponse.of(message, type, code);
            String errorData = objectMapper.writeValueAsString(errorResponse);
            emitter.send(SseEmitter.event().data(errorData));
        } catch (IOException ignored) {
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
