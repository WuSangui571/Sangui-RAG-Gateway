package com.sangui.raggateway.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BindAppDefaultKnowledgeBaseVO {

    @JsonProperty("app_id")
    private Long appId;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("default_knowledge_base_id")
    private Long defaultKnowledgeBaseId;

    public BindAppDefaultKnowledgeBaseVO(Long appId, Long userId, Long defaultKnowledgeBaseId) {
        this.appId = appId;
        this.userId = userId;
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
    }

    public Long getAppId() {
        return appId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getDefaultKnowledgeBaseId() {
        return defaultKnowledgeBaseId;
    }
}
