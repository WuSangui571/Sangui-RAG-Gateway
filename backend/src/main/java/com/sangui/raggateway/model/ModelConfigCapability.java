package com.sangui.raggateway.model;

public enum ModelConfigCapability {
    CHAT,
    EMBEDDING,
    CHAT_EMBEDDING;

    public boolean isChatCapable() {
        return this == CHAT || this == CHAT_EMBEDDING;
    }

    public boolean isEmbeddingCapable() {
        return this == EMBEDDING || this == CHAT_EMBEDDING;
    }
}
