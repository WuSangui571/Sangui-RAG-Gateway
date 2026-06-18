package com.sangui.raggateway.document;

public enum DocumentProcessingTaskStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    RETRYABLE,
    FAILED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELED;
    }

    public boolean isActive() {
        return this == PENDING || this == PROCESSING || this == RETRYABLE;
    }
}
