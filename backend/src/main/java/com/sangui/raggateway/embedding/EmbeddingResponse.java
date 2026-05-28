package com.sangui.raggateway.embedding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EmbeddingResponse {

    private String object;
    private List<EmbeddingData> data;
    private String model;
    private EmbeddingUsage usage;

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public List<EmbeddingData> getData() {
        return data;
    }

    public void setData(List<EmbeddingData> data) {
        this.data = data;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public EmbeddingUsage getUsage() {
        return usage;
    }

    public void setUsage(EmbeddingUsage usage) {
        this.usage = usage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingData {

        private String object;
        private int index;
        private List<Float> embedding;

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public List<Float> getEmbedding() {
            return embedding;
        }

        public void setEmbedding(List<Float> embedding) {
            this.embedding = embedding;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EmbeddingUsage {

        @JsonProperty("prompt_tokens")
        private int promptTokens;
        @JsonProperty("total_tokens")
        private int totalTokens;

        public int getPromptTokens() {
            return promptTokens;
        }

        public void setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
        }

        public int getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
