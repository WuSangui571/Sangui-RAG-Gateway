package com.sangui.raggateway.knowledge.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;

import java.time.LocalDateTime;

public class KnowledgeBaseVO {

    private Long id;
    @JsonProperty("user_id")
    private Long userId;
    private String name;
    @JsonProperty("embedding_model")
    private String embeddingModel;
    @JsonProperty("embedding_dimension")
    private Integer embeddingDimension;
    private String status;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static KnowledgeBaseVO from(KnowledgeBaseEntity entity) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.id = entity.getId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.embeddingModel = entity.getEmbeddingModel();
        vo.embeddingDimension = entity.getEmbeddingDimension();
        vo.status = entity.getStatus();
        vo.createdAt = entity.getCreatedAt();
        vo.updatedAt = entity.getUpdatedAt();
        return vo;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmbeddingModel() { return embeddingModel; }
    public Integer getEmbeddingDimension() { return embeddingDimension; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
