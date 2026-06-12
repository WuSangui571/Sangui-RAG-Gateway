package com.sangui.raggateway.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateModelConfigDTO {

    private String capability;
    private String name;
    @JsonProperty("provider_name")
    private String providerName;
    @JsonProperty("base_url")
    private String baseUrl;
    @JsonProperty("api_key")
    private String apiKey;
    @JsonProperty("chat_model")
    private String chatModel;
    @JsonProperty("embedding_model")
    private String embeddingModel;
    @JsonProperty("embedding_dimension")
    private Integer embeddingDimension;

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatModel() {
        return chatModel;
    }

    public void setChatModel(String chatModel) {
        this.chatModel = chatModel;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Integer getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(Integer embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }
}
