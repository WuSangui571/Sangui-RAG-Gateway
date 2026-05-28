package com.sangui.raggateway.log;

public class CreateRequestLogCommand {

    private final String requestId;
    private final Long userId;
    private final Long appId;
    private final Long apiKeyId;
    private final String model;
    private final String providerName;
    private final String status;
    private final String errorCode;
    private final long latencyMs;
    private final Long upstreamLatencyMs;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;
    private final Integer messagesCount;
    private final String questionSummary;
    private final String hitChunkIds;

    private CreateRequestLogCommand(Builder builder) {
        this.requestId = builder.requestId;
        this.userId = builder.userId;
        this.appId = builder.appId;
        this.apiKeyId = builder.apiKeyId;
        this.model = builder.model;
        this.providerName = builder.providerName;
        this.status = builder.status;
        this.errorCode = builder.errorCode;
        this.latencyMs = builder.latencyMs;
        this.upstreamLatencyMs = builder.upstreamLatencyMs;
        this.promptTokens = builder.promptTokens;
        this.completionTokens = builder.completionTokens;
        this.totalTokens = builder.totalTokens;
        this.messagesCount = builder.messagesCount;
        this.questionSummary = builder.questionSummary;
        this.hitChunkIds = builder.hitChunkIds;
    }

    public String getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public Long getAppId() { return appId; }
    public Long getApiKeyId() { return apiKeyId; }
    public String getModel() { return model; }
    public String getProviderName() { return providerName; }
    public String getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public long getLatencyMs() { return latencyMs; }
    public Long getUpstreamLatencyMs() { return upstreamLatencyMs; }
    public Integer getPromptTokens() { return promptTokens; }
    public Integer getCompletionTokens() { return completionTokens; }
    public Integer getTotalTokens() { return totalTokens; }
    public Integer getMessagesCount() { return messagesCount; }
    public String getQuestionSummary() { return questionSummary; }
    public String getHitChunkIds() { return hitChunkIds; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String requestId;
        private Long userId;
        private Long appId;
        private Long apiKeyId;
        private String model;
        private String providerName;
        private String status;
        private String errorCode;
        private long latencyMs;
        private Long upstreamLatencyMs;
        private Integer promptTokens;
        private Integer completionTokens;
        private Integer totalTokens;
        private Integer messagesCount;
        private String questionSummary;
        private String hitChunkIds;

        public Builder requestId(String requestId) { this.requestId = requestId; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder appId(Long appId) { this.appId = appId; return this; }
        public Builder apiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder providerName(String providerName) { this.providerName = providerName; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder latencyMs(long latencyMs) { this.latencyMs = latencyMs; return this; }
        public Builder upstreamLatencyMs(Long upstreamLatencyMs) { this.upstreamLatencyMs = upstreamLatencyMs; return this; }
        public Builder promptTokens(Integer promptTokens) { this.promptTokens = promptTokens; return this; }
        public Builder completionTokens(Integer completionTokens) { this.completionTokens = completionTokens; return this; }
        public Builder totalTokens(Integer totalTokens) { this.totalTokens = totalTokens; return this; }
        public Builder messagesCount(Integer messagesCount) { this.messagesCount = messagesCount; return this; }
        public Builder questionSummary(String questionSummary) { this.questionSummary = questionSummary; return this; }
        public Builder hitChunkIds(String hitChunkIds) { this.hitChunkIds = hitChunkIds; return this; }

        public CreateRequestLogCommand build() {
            return new CreateRequestLogCommand(this);
        }
    }
}
