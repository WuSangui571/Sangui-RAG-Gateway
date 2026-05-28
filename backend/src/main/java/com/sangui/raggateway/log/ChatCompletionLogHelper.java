package com.sangui.raggateway.log;

import com.sangui.raggateway.common.security.GatewayRequestContext;
import com.sangui.raggateway.common.security.GatewayRequestContextHolder;

import java.net.URI;

public final class ChatCompletionLogHelper {

    private ChatCompletionLogHelper() {
    }

    public static String sanitizeUpstreamUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "unknown";
        }
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null) {
                return "unknown";
            }
            if (path == null || path.isEmpty()) {
                path = "";
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            return host + (path.isEmpty() ? "" : path);
        } catch (IllegalArgumentException e) {
            return "unknown";
        }
    }

    public static String currentRequestId() {
        GatewayRequestContext context = GatewayRequestContextHolder.get();
        return context != null ? context.getRequestId() : null;
    }
}
