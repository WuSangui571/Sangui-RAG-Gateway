package com.sangui.raggateway.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BindAppDefaultModelConfigVO {

    @JsonProperty("app_id")
    private Long appId;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("default_model_config_id")
    private Long defaultModelConfigId;

    public BindAppDefaultModelConfigVO(Long appId, Long userId, Long defaultModelConfigId) {
        this.appId = appId;
        this.userId = userId;
        this.defaultModelConfigId = defaultModelConfigId;
    }

    public Long getAppId() {
        return appId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDefaultModelConfigId() {
        return defaultModelConfigId;
    }
}
