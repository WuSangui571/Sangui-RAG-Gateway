package com.sangui.raggateway.common.security;

public class AdminAuthContext {

    private final Long userId;
    private final String username;
    private final String requestId;

    public AdminAuthContext(Long userId, String username, String requestId) {
        this.userId = userId;
        this.username = username;
        this.requestId = requestId;
    }

    public AdminAuthContext(Long userId, String username) {
        this(userId, username, null);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRequestId() {
        return requestId;
    }
}
