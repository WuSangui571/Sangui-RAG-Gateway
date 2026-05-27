package com.sangui.raggateway.common.security;

import java.security.SecureRandom;
import java.util.Base64;

public class ApiKeyGenerator {

    private static final String PREFIX = "sk-sangui-";
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return PREFIX + token;
    }

    public String extractPrefix(String fullKey) {
        if (fullKey == null || fullKey.length() <= PREFIX.length() + 8) {
            return fullKey != null ? fullKey : "";
        }
        return fullKey.substring(0, PREFIX.length() + 8);
    }

    public static boolean hasValidPrefix(String key) {
        return key != null && key.startsWith(PREFIX) && key.length() > PREFIX.length();
    }
}
