package com.sangui.raggateway.document;

public class TextNormalizer {

    public String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = normalized.trim();
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        return normalized;
    }
}
