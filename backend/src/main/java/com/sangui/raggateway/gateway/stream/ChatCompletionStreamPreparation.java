package com.sangui.raggateway.gateway.stream;

import com.sangui.raggateway.gateway.upstream.UpstreamChatCompletionRequest;

public class ChatCompletionStreamPreparation {

    private final String baseUrl;
    private final String apiKey;
    private final UpstreamChatCompletionRequest upstreamRequest;
    private final String model;
    private final String providerName;
    private final String questionSummary;
    private final String hitChunkIds;
    private final String retrievalEvidence;

    public ChatCompletionStreamPreparation(String baseUrl, String apiKey,
                                            UpstreamChatCompletionRequest upstreamRequest,
                                            String model, String providerName,
                                            String questionSummary, String hitChunkIds,
                                            String retrievalEvidence) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.upstreamRequest = upstreamRequest;
        this.model = model;
        this.providerName = providerName;
        this.questionSummary = questionSummary;
        this.hitChunkIds = hitChunkIds;
        this.retrievalEvidence = retrievalEvidence;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public UpstreamChatCompletionRequest getUpstreamRequest() {
        return upstreamRequest;
    }

    public String getModel() {
        return model;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getQuestionSummary() {
        return questionSummary;
    }

    public String getHitChunkIds() {
        return hitChunkIds;
    }

    public String getRetrievalEvidence() {
        return retrievalEvidence;
    }
}
