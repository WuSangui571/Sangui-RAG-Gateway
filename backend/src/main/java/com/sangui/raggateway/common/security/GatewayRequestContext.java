package com.sangui.raggateway.common.security;

public class GatewayRequestContext {

    private final Long appId;
    private final Long userId;
    private final Long apiKeyId;
    private final String apiKeyPrefix;
    private String requestId;

    public GatewayRequestContext(Long appId, Long userId, Long apiKeyId, String apiKeyPrefix) {
        this.appId = appId;
        this.userId = userId;
        this.apiKeyId = apiKeyId;
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public Long getAppId() {
        return appId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getApiKeyId() {
        return apiKeyId;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
