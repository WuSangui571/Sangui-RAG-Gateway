package com.sangui.raggateway.model;

public enum ModelConfigCapability {
    CHAT,
    EMBEDDING,
    CHAT_EMBEDDING;

    public boolean isChatCapable() {
        return this == CHAT;
    }

    public boolean isEmbeddingCapable() {
        return this == EMBEDDING;
    }
}
