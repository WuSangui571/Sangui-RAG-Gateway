package com.sangui.raggateway.knowledge;

public enum KnowledgeBaseStatus {
    EMPTY,
    PROCESSING,
    READY,
    FAILED;

    public static boolean isValid(String value) {
        if (value == null) return false;
        try {
            valueOf(value.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
