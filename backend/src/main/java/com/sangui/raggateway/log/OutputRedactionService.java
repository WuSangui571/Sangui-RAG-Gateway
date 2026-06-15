package com.sangui.raggateway.log;

import java.util.regex.Pattern;

public final class OutputRedactionService {

    private static final Pattern APP_KEY_PATTERN = Pattern.compile(
            "sk-sangui-[A-Za-z0-9+/=_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "Bearer\\s+[A-Za-z0-9+/=_-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPSTREAM_KEY_PATTERN = Pattern.compile(
            "\\bsk-[A-Za-z0-9+/=_-]{20,}\\b");
    private static final Pattern AUTH_HEADER_PATTERN = Pattern.compile(
            "Authorization\\s*:\\s*[^\\n\\r]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern API_KEY_FIELD_PATTERN = Pattern.compile(
            "(api_key|apiKey|api_key_encrypted|key_hash)\\s*[:=]\\s*[^\\s\\n,}]+", Pattern.CASE_INSENSITIVE);

    private OutputRedactionService() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = text;
        result = APP_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_APP_KEY]");
        result = BEARER_TOKEN_PATTERN.matcher(result).replaceAll("Bearer [REDACTED]");
        result = UPSTREAM_KEY_PATTERN.matcher(result).replaceAll("[REDACTED_UPSTREAM_KEY]");
        result = AUTH_HEADER_PATTERN.matcher(result).replaceAll("Authorization: [REDACTED]");
        result = API_KEY_FIELD_PATTERN.matcher(result).replaceAll("$1: [REDACTED]");
        return result;
    }

    public static boolean hasBlockingPatterns(String text) {
        if (text == null) {
            return false;
        }
        return APP_KEY_PATTERN.matcher(text).find()
                || BEARER_TOKEN_PATTERN.matcher(text).find()
                || UPSTREAM_KEY_PATTERN.matcher(text).find()
                || AUTH_HEADER_PATTERN.matcher(text).find()
                || API_KEY_FIELD_PATTERN.matcher(text).find();
    }
}
