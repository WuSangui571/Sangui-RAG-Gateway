package com.sangui.raggateway.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.model.ModelConfigEntity;

import java.time.LocalDateTime;

public class ModelConfigVO {

    private Long id;
    @JsonProperty("user_id")
    private Long userId;
    private String name;
    @JsonProperty("provider_name")
    private String providerName;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("api_key_masked")
    private String apiKeyMasked;
    @JsonProperty("chat_model")
    private String chatModel;
    @JsonProperty("embedding_model")
    private String embeddingModel;
    @JsonProperty("embedding_dimension")
    private Integer embeddingDimension;
    private String status;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ModelConfigVO from(ModelConfigEntity entity) {
        ModelConfigVO vo = new ModelConfigVO();
        vo.id = entity.getId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.providerName = entity.getProviderName();
        vo.baseUrl = entity.getBaseUrl();
        vo.apiKeyMasked = entity.getApiKeyMasked();
        vo.chatModel = entity.getChatModel();
        vo.embeddingModel = entity.getEmbeddingModel();
        vo.embeddingDimension = entity.getEmbeddingDimension();
        vo.status = entity.getStatus();
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

    public String getProviderName() {
        return providerName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKeyMasked() {
        return apiKeyMasked;
    }

    public String getChatModel() {
        return chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
