package com.sangui.raggateway.knowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreateKnowledgeBaseDTO {

    private String name;
    @JsonProperty("embedding_model")
    private String embeddingModel;
    @JsonProperty("embedding_dimension")
    private Integer embeddingDimension;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public Integer getEmbeddingDimension() { return embeddingDimension; }
    public void setEmbeddingDimension(Integer embeddingDimension) { this.embeddingDimension = embeddingDimension; }
}
