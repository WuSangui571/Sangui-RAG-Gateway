package com.sangui.raggateway.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ModelConfigCheckResult {

    private String capability;
    @JsonProperty("overall_status")
    private String overallStatus;
    @JsonProperty("base_url_checked")
    private boolean baseUrlChecked;
    private ChatCheckResult chat;
    private EmbeddingCheckResult embedding;

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public boolean isBaseUrlChecked() {
        return baseUrlChecked;
    }

    public void setBaseUrlChecked(boolean baseUrlChecked) {
        this.baseUrlChecked = baseUrlChecked;
    }

    public ChatCheckResult getChat() {
        return chat;
    }

    public void setChat(ChatCheckResult chat) {
        this.chat = chat;
    }

    public EmbeddingCheckResult getEmbedding() {
        return embedding;
    }

    public void setEmbedding(EmbeddingCheckResult embedding) {
        this.embedding = embedding;
    }

    public static class ChatCheckResult {
        private String status;
        private String model;
        private String message;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class EmbeddingCheckResult {
        private String status;
        private String model;
        @JsonProperty("actual_dimension")
        private Integer actualDimension;
        @JsonProperty("configured_dimension")
        private Integer configuredDimension;
        private String message;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getActualDimension() {
            return actualDimension;
        }

        public void setActualDimension(Integer actualDimension) {
            this.actualDimension = actualDimension;
        }

        public Integer getConfiguredDimension() {
            return configuredDimension;
        }

        public void setConfiguredDimension(Integer configuredDimension) {
            this.configuredDimension = configuredDimension;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
