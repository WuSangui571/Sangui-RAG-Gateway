package com.sangui.raggateway.common.security;

public class UpstreamApiKeyMasker {

    private static final int PREFIX_LENGTH = 3;
    private static final int SUFFIX_LENGTH = 4;
    private static final int MIN_LENGTH_FOR_PARTIAL_MASK = 8;

    public String mask(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (plaintext.length() < MIN_LENGTH_FOR_PARTIAL_MASK) {
            return "*".repeat(plaintext.length());
        }
        int maskLength = plaintext.length() - PREFIX_LENGTH - SUFFIX_LENGTH;
        if (maskLength <= 0) {
            return "*".repeat(plaintext.length());
        }
        String mask = "*".repeat(maskLength);
        return plaintext.substring(0, PREFIX_LENGTH) + mask + plaintext.substring(plaintext.length() - SUFFIX_LENGTH);
    }
}
