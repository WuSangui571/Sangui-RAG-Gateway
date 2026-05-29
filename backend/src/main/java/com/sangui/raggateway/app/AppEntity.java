package com.sangui.raggateway.app;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_app")
public class AppEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String status;
    private Long defaultModelConfigId;
    private Long defaultKnowledgeBaseId;
    private Integer retrievalTopK;
    private Double retrievalSimilarityThreshold;
    private Integer retrievalMaxContextChunks;
    private Integer retrievalMaxContextChars;
    private Integer retrievalMaxSingleChunkChars;
    private String noHitPolicy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDefaultModelConfigId() {
        return defaultModelConfigId;
    }

    public void setDefaultModelConfigId(Long defaultModelConfigId) {
        this.defaultModelConfigId = defaultModelConfigId;
    }

    public Long getDefaultKnowledgeBaseId() {
        return defaultKnowledgeBaseId;
    }

    public void setDefaultKnowledgeBaseId(Long defaultKnowledgeBaseId) {
        this.defaultKnowledgeBaseId = defaultKnowledgeBaseId;
    }

    public Integer getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(Integer retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public Double getRetrievalSimilarityThreshold() {
        return retrievalSimilarityThreshold;
    }

    public void setRetrievalSimilarityThreshold(Double retrievalSimilarityThreshold) {
        this.retrievalSimilarityThreshold = retrievalSimilarityThreshold;
    }

    public Integer getRetrievalMaxContextChunks() {
        return retrievalMaxContextChunks;
    }

    public void setRetrievalMaxContextChunks(Integer retrievalMaxContextChunks) {
        this.retrievalMaxContextChunks = retrievalMaxContextChunks;
    }

    public Integer getRetrievalMaxContextChars() {
        return retrievalMaxContextChars;
    }

    public void setRetrievalMaxContextChars(Integer retrievalMaxContextChars) {
        this.retrievalMaxContextChars = retrievalMaxContextChars;
    }

    public Integer getRetrievalMaxSingleChunkChars() {
        return retrievalMaxSingleChunkChars;
    }

    public void setRetrievalMaxSingleChunkChars(Integer retrievalMaxSingleChunkChars) {
        this.retrievalMaxSingleChunkChars = retrievalMaxSingleChunkChars;
    }

    public String getNoHitPolicy() {
        return noHitPolicy;
    }

    public void setNoHitPolicy(String noHitPolicy) {
        this.noHitPolicy = noHitPolicy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
