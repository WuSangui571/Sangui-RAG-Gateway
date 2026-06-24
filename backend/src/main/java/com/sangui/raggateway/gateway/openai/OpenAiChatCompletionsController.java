package com.sangui.raggateway.gateway.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyRateLimitResult;
import com.sangui.raggateway.apikey.ApiKeyRateLimitService;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.response.OpenAiErrorResponse;
import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;
import com.sangui.raggateway.gateway.completion.ChatCompletionGatewayService;
import com.sangui.raggateway.gateway.completion.ChatCompletionResult;
import com.sangui.raggateway.gateway.stream.ChatCompletionStreamPreparation;
import com.sangui.raggateway.log.ApiRequestLogService;
import com.sangui.raggateway.log.CreateRequestLogCommand;
import com.sangui.raggateway.log.OutputCapturePolicy;
import com.sangui.raggateway.gateway.upstream.OpenAiCompatibleUpstreamClient;
import com.sangui.raggateway.gateway.upstream.StreamCompletionOutcome;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
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
    private final AppService appService;
    private final OutputCapturePolicy outputCapturePolicy;
    private final ApiKeyRateLimitService rateLimitService;
    private final ApiKeyLimitProperties rateLimitProperties;
    private final long streamingEmitterTimeoutMs;

    public OpenAiChatCompletionsController(ChatCompletionGatewayService chatCompletionGatewayService,
                                           ApiRequestLogService apiRequestLogService,
                                           OpenAiCompatibleUpstreamClient upstreamClient,
                                           ObjectMapper objectMapper,
                                           AppService appService,
                                           OutputCapturePolicy outputCapturePolicy,
                                           ApiKeyRateLimitService rateLimitService,
                                           ApiKeyLimitProperties rateLimitProperties,
                                           @Value("${rag.gateway.streaming.emitter-timeout-seconds:300}") long streamingEmitterTimeoutSeconds) {
        this.chatCompletionGatewayService = chatCompletionGatewayService;
        this.apiRequestLogService = apiRequestLogService;
        this.upstreamClient = upstreamClient;
        this.objectMapper = objectMapper;
        this.appService = appService;
        this.outputCapturePolicy = outputCapturePolicy;
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.streamingEmitterTimeoutMs = streamingEmitterTimeoutSeconds * 1000L;
    }

    @PostMapping("/v1/chat/completions")
    public Object chatCompletions(@RequestBody OpenAiChatCompletionRequest request,
                                  @RequestHeader(value = "X-Sangui-Return-Citations", required = false) String returnCitationsHeader,
                                  HttpServletResponse servletResponse) {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        if (context == null) {
            throw new GatewayException("Invalid API key.", "invalid_request_error", "invalid_api_key", HttpStatus.UNAUTHORIZED);
        }

        boolean returnCitations = isReturnCitations(returnCitationsHeader);

        String requestId = UUID.randomUUID().toString();
        context.setRequestId(requestId);

        int messagesCount = request != null && request.getMessages() != null ? request.getMessages().size() : 0;
        long start = System.currentTimeMillis();

        try {
            chatCompletionGatewayService.validateRequest(request);
        } catch (GatewayException e) {
            recordGatewayFailure(context, requestId, messagesCount, start, e);
            throw e;
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            return handleStreamCompletion(request, context, requestId, messagesCount, start, returnCitations,
                    servletResponse);
        }

        int messagesChars = computeMessagesCharCount(request);
        ApiKeyRateLimitResult reservation = null;
        if (rateLimitProperties.isEnabled()) {
            ApiKeyRateLimitResult limitResult;
            try {
                limitResult = rateLimitService.checkAndReserve(
                        context.getApiKeyId(), messagesChars, request.getMaxTokens());
            } catch (GatewayException e) {
                recordGatewayFailure(context, requestId, messagesCount, start, e);
                throw e;
            }
            if (!limitResult.isAllowed()) {
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code=rate_limit_exceeded limit_type={} messages_count={} latency_ms={}",
                        requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                        limitResult.getLimitType(), messagesCount, latencyMs);

                safeRecord(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(context.getUserId())
                        .appId(context.getAppId())
                        .apiKeyId(context.getApiKeyId())
                        .status("failure")
                        .errorCode("rate_limit_exceeded")
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .outputCaptureStatus(outputCapturePolicy.getDisabledStatus())
                        .build());

                throw new GatewayException("Rate limit exceeded for this API key.",
                        "rate_limit_error", "rate_limit_exceeded", HttpStatus.TOO_MANY_REQUESTS);
            }
            reservation = limitResult;
        }

        try {
            ChatCompletionResult result = chatCompletionGatewayService.processChatCompletion(request);
            long latencyMs = System.currentTimeMillis() - start;

            if (reservation != null && result.getTotalTokens() != null) {
                rateLimitService.reconcileTokens(context.getApiKeyId(), reservation, result.getTotalTokens());
            }

            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=success messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    messagesCount, latencyMs);

            OutputCapturePolicy.OutputCaptureResult captureResult =
                    resolveCaptureResult(context.getAppId(),
                            result.getAssistantOutputContent(), result.getCompletionLength());

            safeRecord(CreateRequestLogCommand.builder()
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
                    .retrievalEvidence(result.getRetrievalEvidence())
                    .completionLength(captureResult.getCompletionLength())
                    .outputCaptureStatus(captureResult.getOutputCaptureStatus())
                    .outputPreview(captureResult.getOutputPreview())
                    .outputPreviewTruncated(captureResult.isOutputPreviewTruncated())
                    .outputRedacted(captureResult.isOutputRedacted())
                    .outputRetentionExpiresAt(captureResult.getOutputRetentionExpiresAt())
                    .build());

            OpenAiChatCompletionResponse response = result.getResponse();
            if (returnCitations) {
                List<com.sangui.raggateway.retrieval.Citation> citations = result.getCitations();
                response.setSanguiCitations(citations != null ? citations : List.of());
            }
            return ResponseEntity.ok(response);
        } catch (GatewayException e) {
            long latencyMs = System.currentTimeMillis() - start;
            if (reservation != null) {
                rateLimitService.releaseReservation(context.getApiKeyId(), reservation);
            }

            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    e.getCode(), messagesCount, latencyMs);

            safeRecord(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .status("failure")
                    .errorCode(e.getCode())
                    .latencyMs(latencyMs)
                    .messagesCount(messagesCount)
                    .outputCaptureStatus(outputCapturePolicy.getDisabledStatus())
                    .build());

            throw e;
        }
    }

    private SseEmitter handleStreamCompletion(OpenAiChatCompletionRequest request,
                                                GatewayRequestContext context,
                                                String requestId, int messagesCount, long start,
                                                boolean returnCitations,
                                                HttpServletResponse servletResponse) {
        int messagesChars = computeMessagesCharCount(request);
        ApiKeyRateLimitResult[] reservationHolder = new ApiKeyRateLimitResult[1];
        if (rateLimitProperties.isEnabled()) {
            ApiKeyRateLimitResult limitResult;
            try {
                limitResult = rateLimitService.checkAndReserve(
                        context.getApiKeyId(), messagesChars, request.getMaxTokens());
            } catch (GatewayException e) {
                recordGatewayFailure(context, requestId, messagesCount, start, e);
                throw e;
            }
            if (!limitResult.isAllowed()) {
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code=rate_limit_exceeded limit_type={} messages_count={} latency_ms={}",
                        requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                        limitResult.getLimitType(), messagesCount, latencyMs);

                safeRecord(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(context.getUserId())
                        .appId(context.getAppId())
                        .apiKeyId(context.getApiKeyId())
                        .status("failure")
                        .errorCode("rate_limit_exceeded")
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .outputCaptureStatus(outputCapturePolicy.getDisabledStatus())
                        .build());

                throw new GatewayException("Rate limit exceeded for this API key.",
                        "rate_limit_error", "rate_limit_exceeded", HttpStatus.TOO_MANY_REQUESTS);
            }
            reservationHolder[0] = limitResult;
        }

        ChatCompletionStreamPreparation prep;
        try {
            prep = chatCompletionGatewayService.prepareStreamCompletion(request);
        } catch (GatewayException e) {
            if (reservationHolder[0] != null) {
                rateLimitService.releaseReservation(context.getApiKeyId(), reservationHolder[0]);
            }
            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    e.getCode(), messagesCount, latencyMs);

            safeRecord(CreateRequestLogCommand.builder()
                    .requestId(requestId)
                    .userId(context.getUserId())
                    .appId(context.getAppId())
                    .apiKeyId(context.getApiKeyId())
                    .status("failure")
                    .errorCode(e.getCode())
                    .latencyMs(latencyMs)
                    .messagesCount(messagesCount)
                    .outputCaptureStatus(outputCapturePolicy.getDisabledStatus())
                    .build());

            throw e;
        }

        long emitterTimeout = streamingEmitterTimeoutMs > 0 ? streamingEmitterTimeoutMs : Long.MAX_VALUE;
        SseEmitter emitter = new SseEmitter(emitterTimeout);

        Long userId = context.getUserId();
        Long appId = context.getAppId();
        Long apiKeyId = context.getApiKeyId();
        String model = prep.getModel();
        String providerName = prep.getProviderName();
        CompletableFuture<Void> streamReady = new CompletableFuture<>();
        AtomicBoolean responseCommitted = new AtomicBoolean(false);
        AtomicBoolean terminalHandled = new AtomicBoolean(false);

        emitter.onTimeout(() -> {
            if (terminalHandled.compareAndSet(false, true)) {
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.stream_timeout request_id={} app_id={} api_key_id={} user_id={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, latencyMs);
                if (reservationHolder[0] != null) {
                    rateLimitService.releaseReservation(apiKeyId, reservationHolder[0]);
                }
                safeRecord(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .appId(appId)
                        .apiKeyId(apiKeyId)
                        .model(model)
                        .providerName(providerName)
                        .status("cancelled")
                        .errorCode("stream_timeout")
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .questionSummary(prep.getQuestionSummary())
                        .hitChunkIds(prep.getHitChunkIds())
                        .retrievalEvidence(prep.getRetrievalEvidence())
                        .outputCaptureStatus("STREAMING_UNSUPPORTED")
                        .build());
                completeEmitter(emitter, requestId, "stream_timeout");
            }
        });

        emitter.onError(ex -> {
            if (terminalHandled.compareAndSet(false, true)) {
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.stream_error request_id={} app_id={} api_key_id={} user_id={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, latencyMs);
                if (reservationHolder[0] != null) {
                    rateLimitService.releaseReservation(apiKeyId, reservationHolder[0]);
                }
                safeRecord(CreateRequestLogCommand.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .appId(appId)
                        .apiKeyId(apiKeyId)
                        .model(model)
                        .providerName(providerName)
                        .status("cancelled")
                        .errorCode("client_cancelled")
                        .latencyMs(latencyMs)
                        .messagesCount(messagesCount)
                        .questionSummary(prep.getQuestionSummary())
                        .hitChunkIds(prep.getHitChunkIds())
                        .retrievalEvidence(prep.getRetrievalEvidence())
                        .outputCaptureStatus("STREAMING_UNSUPPORTED")
                        .build());
            }
        });

        Thread.ofVirtual().start(() -> {
            try {
                long upstreamStart = System.currentTimeMillis();
                StreamCompletionOutcome outcome = upstreamClient.streamChatCompletion(
                        prep.getBaseUrl(), prep.getApiKey(),
                        prep.getUpstreamRequest(), emitter, requestId, () -> {
                            responseCommitted.set(true);
                            streamReady.complete(null);
                        });

                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }

                if (outcome == StreamCompletionOutcome.CANCELLED) {
                    long latencyMs = System.currentTimeMillis() - start;
                    if (reservationHolder[0] != null) {
                        rateLimitService.releaseReservation(apiKeyId, reservationHolder[0]);
                    }
                    log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=cancelled error_code=client_cancelled messages_count={} latency_ms={}",
                            requestId, appId, apiKeyId, userId, messagesCount, latencyMs);

                    safeRecord(CreateRequestLogCommand.builder()
                            .requestId(requestId)
                            .userId(userId)
                            .appId(appId)
                            .apiKeyId(apiKeyId)
                            .model(model)
                            .providerName(providerName)
                            .status("cancelled")
                            .errorCode("client_cancelled")
                            .latencyMs(latencyMs)
                            .messagesCount(messagesCount)
                            .questionSummary(prep.getQuestionSummary())
                            .hitChunkIds(prep.getHitChunkIds())
                            .retrievalEvidence(prep.getRetrievalEvidence())
                            .outputCaptureStatus("STREAMING_UNSUPPORTED")
                            .build());
                    return;
                }

                emitter.complete();

                long latencyMs = System.currentTimeMillis() - start;
                long upstreamLatencyMs = System.currentTimeMillis() - upstreamStart;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=success messages_count={} latency_ms={} upstream_latency_ms={}",
                        requestId, appId, apiKeyId, userId, messagesCount, latencyMs, upstreamLatencyMs);

                safeRecord(CreateRequestLogCommand.builder()
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
                        .retrievalEvidence(prep.getRetrievalEvidence())
                        .outputCaptureStatus("STREAMING_UNSUPPORTED")
                        .build());
            } catch (GatewayException e) {
                if (!responseCommitted.get()) {
                    streamReady.completeExceptionally(e);
                    return;
                }
                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }
                if (reservationHolder[0] != null) {
                    rateLimitService.releaseReservation(apiKeyId, reservationHolder[0]);
                }
                long latencyMs = System.currentTimeMillis() - start;
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, e.getCode(), messagesCount, latencyMs);

                sendSseError(emitter, requestId, e.getMessage(), e.getType(), e.getCode());

                safeRecord(CreateRequestLogCommand.builder()
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
                        .retrievalEvidence(prep.getRetrievalEvidence())
                        .outputCaptureStatus("STREAMING_UNSUPPORTED")
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
                if (!terminalHandled.compareAndSet(false, true)) {
                    return;
                }
                if (reservationHolder[0] != null) {
                    rateLimitService.releaseReservation(apiKeyId, reservationHolder[0]);
                }
                long latencyMs = System.currentTimeMillis() - start;
                String errorCode = "upstream_error";
                log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                        requestId, appId, apiKeyId, userId, errorCode, messagesCount, latencyMs);

                sendSseError(emitter, requestId, "Upstream service is unavailable", "server_error", errorCode);

                safeRecord(CreateRequestLogCommand.builder()
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
                        .retrievalEvidence(prep.getRetrievalEvidence())
                        .outputCaptureStatus("STREAMING_UNSUPPORTED")
                        .build());
            }
        });

        waitForStreamReady(streamReady, context, requestId, messagesCount, start, model, providerName,
                prep.getQuestionSummary(), prep.getHitChunkIds(), prep.getRetrievalEvidence(), reservationHolder[0]);
        servletResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
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
                                     String hitChunkIds,
                                     String retrievalEvidence,
                                     ApiKeyRateLimitResult reservation) {
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

            if (reservation != null) {
                rateLimitService.releaseReservation(context.getApiKeyId(), reservation);
            }

            long latencyMs = System.currentTimeMillis() - start;
            log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                    requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                    gatewayException.getCode(), messagesCount, latencyMs);

            safeRecord(CreateRequestLogCommand.builder()
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
                    .retrievalEvidence(retrievalEvidence)
                    .outputCaptureStatus("STREAMING_UNSUPPORTED")
                    .build());

            throw gatewayException;
        }
    }

    private void recordGatewayFailure(GatewayRequestContext context, String requestId, int messagesCount,
                                      long start, GatewayException e) {
        long latencyMs = System.currentTimeMillis() - start;
        log.info("gateway.chat.completed request_id={} app_id={} api_key_id={} user_id={} status=failure error_code={} messages_count={} latency_ms={}",
                requestId, context.getAppId(), context.getApiKeyId(), context.getUserId(),
                e.getCode(), messagesCount, latencyMs);

        safeRecord(CreateRequestLogCommand.builder()
                .requestId(requestId)
                .userId(context.getUserId())
                .appId(context.getAppId())
                .apiKeyId(context.getApiKeyId())
                .status("failure")
                .errorCode(e.getCode())
                .latencyMs(latencyMs)
                .messagesCount(messagesCount)
                .outputCaptureStatus(outputCapturePolicy.getDisabledStatus())
                .build());
    }

    private void safeRecord(CreateRequestLogCommand command) {
        try {
            apiRequestLogService.record(command);
        } catch (Exception e) {
            log.warn("request_log.controller_record_failed request_id={} error_class={}",
                    command.getRequestId(), e.getClass().getSimpleName());
        }
    }

    private void sendSseError(SseEmitter emitter, String requestId, String message, String type, String code) {
        try {
            OpenAiErrorResponse errorResponse = OpenAiErrorResponse.of(message, type, code);
            String errorData = objectMapper.writeValueAsString(errorResponse);
            emitter.send(SseEmitter.event().data(errorData));
        } catch (IOException e) {
            log.info("gateway.chat.sse_error_send_failed request_id={} error_code={} error_class={}",
                    requestId, code, e.getClass().getSimpleName());
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.info("gateway.chat.sse_complete_failed request_id={} error_code={} error_class={}",
                    requestId, code, e.getClass().getSimpleName());
        }
    }

    private void completeEmitter(SseEmitter emitter, String requestId, String errorCode) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.info("gateway.chat.sse_complete_failed request_id={} error_code={} error_class={}",
                    requestId, errorCode, e.getClass().getSimpleName());
        }
    }

    private int computeMessagesCharCount(OpenAiChatCompletionRequest request) {
        if (request == null || request.getMessages() == null) {
            return 0;
        }
        int total = 0;
        for (var msg : request.getMessages()) {
            if (msg != null && msg.getContent() != null) {
                total += msg.getContent().length();
            }
        }
        return total;
    }

    private boolean isReturnCitations(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return false;
        }
        return "true".equalsIgnoreCase(headerValue.trim());
    }

    private OutputCapturePolicy.OutputCaptureResult resolveCaptureResult(Long appId,
                                                                          String assistantOutputContent,
                                                                          Integer completionLength) {
        AppEntity app = appService.findById(appId);
        if (!outputCapturePolicy.shouldCapture(app)) {
            return new OutputCapturePolicy.OutputCaptureResult(
                    null, completionLength, false, false, outputCapturePolicy.getDisabledStatus());
        }
        if (completionLength == null || completionLength == 0) {
            return new OutputCapturePolicy.OutputCaptureResult(
                    null, 0, false, false, "EMPTY");
        }
        return outputCapturePolicy.capture(assistantOutputContent);
    }
}
