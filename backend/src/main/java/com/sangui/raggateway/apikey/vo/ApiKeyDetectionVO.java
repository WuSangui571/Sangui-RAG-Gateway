package com.sangui.raggateway.apikey.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class ApiKeyDetectionVO {

    @JsonProperty("key_id")
    private Long keyId;

    @JsonProperty("app_id")
    private Long appId;

    private boolean usable;

    private String status;

    @JsonProperty("app_enabled")
    private boolean appEnabled;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    @JsonProperty("checked_at")
    private LocalDateTime checkedAt;

    public Long getKeyId() {
        return keyId;
    }

    public void setKeyId(Long keyId) {
        this.keyId = keyId;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public boolean isUsable() {
        return usable;
    }

    public void setUsable(boolean usable) {
        this.usable = usable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAppEnabled() {
        return appEnabled;
    }

    public void setAppEnabled(boolean appEnabled) {
        this.appEnabled = appEnabled;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
