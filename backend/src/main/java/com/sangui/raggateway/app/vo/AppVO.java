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
    @JsonProperty("retrieval_top_k")
    private Integer retrievalTopK;
    @JsonProperty("retrieval_similarity_threshold")
    private Double retrievalSimilarityThreshold;
    @JsonProperty("retrieval_max_context_chunks")
    private Integer retrievalMaxContextChunks;
    @JsonProperty("retrieval_max_context_chars")
    private Integer retrievalMaxContextChars;
    @JsonProperty("retrieval_max_single_chunk_chars")
    private Integer retrievalMaxSingleChunkChars;
    @JsonProperty("no_hit_policy")
    private String noHitPolicy;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
    @JsonProperty("request_log_output_capture_enabled")
    private Boolean requestLogOutputCaptureEnabled;

    public static AppVO from(AppEntity entity) {
        AppVO vo = new AppVO();
        vo.id = entity.getId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.status = entity.getStatus();
        vo.defaultModelConfigId = entity.getDefaultModelConfigId();
        vo.defaultKnowledgeBaseId = entity.getDefaultKnowledgeBaseId();
        vo.retrievalTopK = entity.getRetrievalTopK();
        vo.retrievalSimilarityThreshold = entity.getRetrievalSimilarityThreshold();
        vo.retrievalMaxContextChunks = entity.getRetrievalMaxContextChunks();
        vo.retrievalMaxContextChars = entity.getRetrievalMaxContextChars();
        vo.retrievalMaxSingleChunkChars = entity.getRetrievalMaxSingleChunkChars();
        vo.noHitPolicy = entity.getNoHitPolicy();
        vo.requestLogOutputCaptureEnabled = Boolean.TRUE.equals(entity.getRequestLogOutputCaptureEnabled());
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

    public Integer getRetrievalTopK() {
        return retrievalTopK;
    }

    public Double getRetrievalSimilarityThreshold() {
        return retrievalSimilarityThreshold;
    }

    public Integer getRetrievalMaxContextChunks() {
        return retrievalMaxContextChunks;
    }

    public Integer getRetrievalMaxContextChars() {
        return retrievalMaxContextChars;
    }

    public Integer getRetrievalMaxSingleChunkChars() {
        return retrievalMaxSingleChunkChars;
    }

    public String getNoHitPolicy() {
        return noHitPolicy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Boolean getRequestLogOutputCaptureEnabled() {
        return requestLogOutputCaptureEnabled;
    }
}
