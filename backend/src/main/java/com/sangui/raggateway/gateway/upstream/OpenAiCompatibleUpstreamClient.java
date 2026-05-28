package com.sangui.raggateway.gateway.upstream;

import com.sangui.raggateway.common.exception.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;

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
            @Value("${rag.gateway.upstream.timeout-seconds:30}") int timeoutSeconds) {
        this(createRestClient(timeoutSeconds));
    }

    OpenAiCompatibleUpstreamClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient createRestClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public String sendChatCompletion(String baseUrl, String apiKey, UpstreamChatCompletionRequest request) {
        String base = normalizeBaseUrl(baseUrl);
        String url = base.endsWith("/v1") ? base + "/chat/completions" : base + CHAT_PATH;

        log.info("Forwarding chat completion to upstream: url={}, model={}, messagesCount={}",
                url, request.getModel(), request.getMessages().size());

        try {
            return restClient.post()
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
                        log.warn("Upstream returned non-2xx: status={}, url={}", status.value(), url);
                        throw new GatewayException(
                                "Upstream service returned an error",
                                ERR_TYPE_SERVER,
                                ERR_CODE_UPSTREAM_ERROR,
                                HttpStatus.BAD_GATEWAY
                        );
                    });
        } catch (GatewayException e) {
            throw e;
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException
                    || (cause instanceof java.net.ConnectException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("time"))) {
                log.error("Upstream timeout: url={}", url, e);
                throw new GatewayException(
                        "Upstream request timed out",
                        ERR_TYPE_SERVER,
                        ERR_CODE_UPSTREAM_TIMEOUT,
                        HttpStatus.GATEWAY_TIMEOUT,
                        e
                );
            }
            log.error("Upstream network error: url={}", url, e);
            throw new GatewayException(
                    "Upstream service is unavailable",
                    ERR_TYPE_SERVER,
                    ERR_CODE_UPSTREAM_ERROR,
                    HttpStatus.BAD_GATEWAY,
                    e
            );
        } catch (Exception e) {
            log.error("Upstream unexpected error: url={}", url, e);
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
