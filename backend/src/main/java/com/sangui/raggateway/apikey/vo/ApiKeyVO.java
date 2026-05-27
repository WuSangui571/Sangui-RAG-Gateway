package com.sangui.raggateway.apikey.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.apikey.ApiKeyEntity;

import java.time.LocalDateTime;

public class ApiKeyVO {

    protected Long id;
    @JsonProperty("app_id")
    protected Long appId;
    @JsonProperty("user_id")
    protected Long userId;
    protected String name;
    @JsonProperty("key_prefix")
    protected String keyPrefix;
    protected String status;
    @JsonProperty("expires_at")
    protected LocalDateTime expiresAt;
    @JsonProperty("last_used_at")
    protected LocalDateTime lastUsedAt;
    @JsonProperty("revoked_at")
    protected LocalDateTime revokedAt;
    @JsonProperty("created_at")
    protected LocalDateTime createdAt;
    @JsonProperty("updated_at")
    protected LocalDateTime updatedAt;

    public static ApiKeyVO from(ApiKeyEntity entity) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.id = entity.getId();
        vo.appId = entity.getAppId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.keyPrefix = entity.getKeyPrefix();
        vo.status = entity.getStatus();
        vo.expiresAt = entity.getExpiresAt();
        vo.lastUsedAt = entity.getLastUsedAt();
        vo.revokedAt = entity.getRevokedAt();
        vo.createdAt = entity.getCreatedAt();
        vo.updatedAt = entity.getUpdatedAt();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getAppId() {
        return appId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
