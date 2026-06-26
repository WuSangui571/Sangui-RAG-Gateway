package com.sangui.raggateway.common.util;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

public final class RestClientTimeoutFactory {

    private RestClientTimeoutFactory() {
    }

    public static SimpleClientHttpRequestFactory createRequestFactory(
            int connectTimeoutSeconds,
            int responseTimeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(toPositiveMillis("connectTimeoutSeconds", connectTimeoutSeconds));
        factory.setReadTimeout(toPositiveMillis("responseTimeoutSeconds", responseTimeoutSeconds));
        return factory;
    }

    private static int toPositiveMillis(String fieldName, int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive, got: " + timeoutSeconds);
        }
        return Math.toIntExact(Duration.ofSeconds(timeoutSeconds).toMillis());
    }
}
