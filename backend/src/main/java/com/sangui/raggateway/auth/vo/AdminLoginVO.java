package com.sangui.raggateway.auth.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class AdminLoginVO {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    private AdminUserVO user;

    public AdminLoginVO(String accessToken, String tokenType, LocalDateTime expiresAt, AdminUserVO user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public AdminUserVO getUser() {
        return user;
    }
}
