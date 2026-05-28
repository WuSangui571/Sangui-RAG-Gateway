package com.sangui.raggateway.embedding;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

final class RestClientUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private RestClientUtils() {
    }

    static <T> T parseJson(byte[] body, Class<T> type) {
        try {
            return MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new EmbeddingException("Failed to parse embedding response", false);
        }
    }
}
