package com.sangui.raggateway.document;

public enum DocumentStatus {
    UPLOADED,
    PARSING,
    PARSED,
    EMBEDDING,
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
