package com.sangui.raggateway.gateway.upstream;

import com.sangui.raggateway.common.exception.GatewayException;
import com.sangui.raggateway.common.util.RestClientTimeoutFactory;
import com.sangui.raggateway.log.ChatCompletionLogHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

@Component
public class OpenAiCompatibleUpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleUpstreamClient.class);

    private static final String CHAT_PATH = "/v1/chat/completions";

    private static final String ERR_TYPE_SERVER = "server_error";
    private static final String ERR_CODE_UPSTREAM_ERROR = "upstream_error";
    private static final String ERR_CODE_UPSTREAM_TIMEOUT = "upstream_timeout";

    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleUpstreamClient(
            @Value("${rag.gateway.upstream.connect-timeout-seconds:5}") int connectTimeoutSeconds,
            @Value("${rag.gateway.upstream.response-timeout-seconds:${rag.gateway.upstream.timeout-seconds:30}}") int responseTimeoutSeconds) {
        this(createRestClient(connectTimeoutSeconds, responseTimeoutSeconds));
    }

    OpenAiCompatibleUpstreamClient(RestClient restClient) {
        this.restClient = restClient;
    }

    static RestClient createRestClient(int connectTimeoutSeconds, int responseTimeoutSeconds) {
        return RestClient.builder()
                .requestFactory(RestClientTimeoutFactory.createRequestFactory(
                        connectTimeoutSeconds, responseTimeoutSeconds))
                .build();
    }

    public String sendChatCompletion(String baseUrl, String apiKey, UpstreamChatCompletionRequest request) {
        String base = normalizeBaseUrl(baseUrl);
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + CHAT_PATH;
        String safeUrl = ChatCompletionLogHelper.sanitizeUpstreamUrl(url);
        String requestId = ChatCompletionLogHelper.currentRequestId();

        log.info("gateway.chat.upstream_started request_id={} upstream_url={} model={} messages_count={}",
                requestId, safeUrl, request.getModel(), request.getMessages().size());

        long start = System.currentTimeMillis();
        try {
            String result = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (status.is2xxSuccessful()) {
                            byte[] body = resp.getBody().readAllBytes();
                            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
                        }
                        long upstreamLatency = System.currentTimeMillis() - start;
                        log.warn("gateway.chat.upstream_failed request_id={} upstream_url={} status={} model={} upstream_latency_ms={}",
                                requestId, safeUrl, status.value(), request.getModel(), upstreamLatency);
                        throw new GatewayException(
                                "Upstream service returned an error",
                                ERR_TYPE_SERVER,
                                ERR_CODE_UPSTREAM_ERROR,
                                HttpStatus.BAD_GATEWAY
                        );
                    });
            long upstreamLatency = System.currentTimeMillis() - start;
            log.info("gateway.chat.upstream_succeeded request_id={} upstream_url={} model={} upstream_latency_ms={}",
                    requestId, safeUrl, request.getModel(), upstreamLatency);
            return result;
        } catch (GatewayException e) {
            throw e;
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException
                    || (cause instanceof java.net.ConnectException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("time"))) {
                long upstreamLatency = System.currentTimeMillis() - start;
                log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                        requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_TIMEOUT, upstreamLatency);
                throw new GatewayException(
                        "Upstream request timed out",
                        ERR_TYPE_SERVER,
                        ERR_CODE_UPSTREAM_TIMEOUT,
                        HttpStatus.GATEWAY_TIMEOUT,
                        e
                );
            }
            long upstreamLatency = System.currentTimeMillis() - start;
            log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                    requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_ERROR, upstreamLatency);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    ERR_CODE_UPSTREAM_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        } catch (Exception e) {
            long upstreamLatency = System.currentTimeMillis() - start;
            log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                    requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_ERROR, upstreamLatency);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    ERR_CODE_UPSTREAM_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        }
    }

    public StreamCompletionOutcome streamChatCompletion(String baseUrl, String apiKey,
                                      UpstreamChatCompletionRequest request,
                                      SseEmitter emitter, String requestId) {
        return streamChatCompletion(baseUrl, apiKey, request, emitter, requestId, () -> {
        });
    }

    public StreamCompletionOutcome streamChatCompletion(String baseUrl, String apiKey,
                                      UpstreamChatCompletionRequest request,
                                      SseEmitter emitter, String requestId,
                                      Runnable onStreamReady) {
        String base = normalizeBaseUrl(baseUrl);
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + CHAT_PATH;
        String safeUrl = ChatCompletionLogHelper.sanitizeUpstreamUrl(url);

        log.info("gateway.chat.stream_started request_id={} upstream_url={} model={} messages_count={}",
                requestId, safeUrl, request.getModel(), request.getMessages().size());

        long start = System.currentTimeMillis();
        try {
            StreamCompletionOutcome outcome = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            long upstreamLatency = System.currentTimeMillis() - start;
                            log.warn("gateway.chat.upstream_failed request_id={} upstream_url={} status={} model={} upstream_latency_ms={}",
                                    requestId, safeUrl, status.value(), request.getModel(), upstreamLatency);
                            throw new GatewayException(
                                    "Upstream service returned an error",
                                    ERR_TYPE_SERVER,
                                    ERR_CODE_UPSTREAM_ERROR,
                                    HttpStatus.BAD_GATEWAY
                            );
                        }

                        onStreamReady.run();

                        boolean doneReceived = false;
                        try (InputStream bodyStream = resp.getBody();
                             BufferedReader reader = new BufferedReader(
                                     new InputStreamReader(bodyStream, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("data: ")) {
                                    String data = line.substring(6);
                                    try {
                                        emitter.send(SseEmitter.event().data(data));
                                    } catch (IOException e) {
                                        log.info("gateway.chat.stream_cancelled request_id={}",
                                                requestId);
                                        return StreamCompletionOutcome.CANCELLED;
                                    }
                                    if ("[DONE]".equals(data.trim())) {
                                        doneReceived = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (!doneReceived) {
                            throw new GatewayException(
                                    "Upstream stream closed before completion",
                                    ERR_TYPE_SERVER,
                                    ERR_CODE_UPSTREAM_ERROR,
                                    HttpStatus.BAD_GATEWAY
                            );
                        }
                        return StreamCompletionOutcome.SUCCESS;
                    });

            long upstreamLatency = System.currentTimeMillis() - start;
            if (outcome == StreamCompletionOutcome.SUCCESS) {
                log.info("gateway.chat.stream_completed request_id={} upstream_url={} model={} upstream_latency_ms={}",
                        requestId, safeUrl, request.getModel(), upstreamLatency);
            }
            return outcome;
        } catch (GatewayException e) {
            long upstreamLatency = System.currentTimeMillis() - start;
            log.info("gateway.chat.stream_failed request_id={} upstream_url={} error_code={} upstream_latency_ms={}",
                    requestId, safeUrl, e.getCode(), upstreamLatency);
            throw e;
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            long upstreamLatency = System.currentTimeMillis() - start;
            if (cause instanceof SocketTimeoutException
                    || (cause instanceof java.net.ConnectException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("time"))) {
                log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                        requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_TIMEOUT, upstreamLatency);
                throw new GatewayException(
                        "Upstream request timed out",
                        ERR_TYPE_SERVER,
                        ERR_CODE_UPSTREAM_TIMEOUT,
                        HttpStatus.GATEWAY_TIMEOUT,
                        e
                );
            }
            log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                    requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_ERROR, upstreamLatency);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    ERR_CODE_UPSTREAM_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        } catch (Exception e) {
            long upstreamLatency = System.currentTimeMillis() - start;
            log.error("gateway.chat.upstream_failed request_id={} upstream_url={} error_class={} error_code={} upstream_latency_ms={}",
                    requestId, safeUrl, e.getClass().getSimpleName(), ERR_CODE_UPSTREAM_ERROR, upstreamLatency);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    ERR_CODE_UPSTREAM_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        }
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
