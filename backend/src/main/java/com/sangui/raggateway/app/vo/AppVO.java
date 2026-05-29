package com.sangui.raggateway.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.app.AppEntity;

import java.time.LocalDateTime;

public class AppVO {

    private Long id;
    @JsonProperty("user_id")
    private Long userId;
    private String name;
    private String status;
    @JsonProperty("default_model_config_id")
    private Long defaultModelConfigId;
    @JsonProperty("default_knowledge_base_id")
    private Long defaultKnowledgeBaseId;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static AppVO from(AppEntity entity) {
        AppVO vo = new AppVO();
        vo.id = entity.getId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.status = entity.getStatus();
        vo.defaultModelConfigId = entity.getDefaultModelConfigId();
        vo.defaultKnowledgeBaseId = entity.getDefaultKnowledgeBaseId();
        vo.createdAt = entity.getCreatedAt();
        vo.updatedAt = entity.getUpdatedAt();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Long getDefaultModelConfigId() {
        return defaultModelConfigId;
    }

    public Long getDefaultKnowledgeBaseId() {
        return defaultKnowledgeBaseId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
