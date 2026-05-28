package com.sangui.raggateway.gateway.completion;

import com.sangui.raggateway.gateway.openai.OpenAiChatCompletionResponse;

public class ChatCompletionResult {

    private final OpenAiChatCompletionResponse response;
    private final String model;
    private final String providerName;
    private final long upstreamLatencyMs;
    private final Integer promptTokens;
    private final Integer completionTokens;
    private final Integer totalTokens;

    public ChatCompletionResult(OpenAiChatCompletionResponse response,
                                String model,
                                String providerName,
                                long upstreamLatencyMs,
                                Integer promptTokens,
                                Integer completionTokens,
                                Integer totalTokens) {
        this.response = response;
        this.model = model;
        this.providerName = providerName;
        this.upstreamLatencyMs = upstreamLatencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public OpenAiChatCompletionResponse getResponse() {
        return response;
    }

    public String getModel() {
        return model;
    }

    public String getProviderName() {
        return providerName;
    }

    public long getUpstreamLatencyMs() {
        return upstreamLatencyMs;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }
}
