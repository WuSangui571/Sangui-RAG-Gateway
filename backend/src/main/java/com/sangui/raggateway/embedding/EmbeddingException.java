package com.sangui.raggateway.embedding;

public class EmbeddingException extends RuntimeException {

    private final boolean retryable;

    public EmbeddingException(String message) {
        super(message);
        this.retryable = false;
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = cause instanceof java.net.SocketTimeoutException;
    }

    public EmbeddingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
