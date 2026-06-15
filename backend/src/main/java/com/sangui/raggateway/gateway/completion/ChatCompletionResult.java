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
    private final String questionSummary;
    private final String hitChunkIds;
    private final String assistantOutputContent;
    private final Integer completionLength;

    public ChatCompletionResult(OpenAiChatCompletionResponse response,
                                String model,
                                String providerName,
                                long upstreamLatencyMs,
                                Integer promptTokens,
                                Integer completionTokens,
                                Integer totalTokens,
                                String questionSummary,
                                String hitChunkIds,
                                String assistantOutputContent,
                                Integer completionLength) {
        this.response = response;
        this.model = model;
        this.providerName = providerName;
        this.upstreamLatencyMs = upstreamLatencyMs;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.questionSummary = questionSummary;
        this.hitChunkIds = hitChunkIds;
        this.assistantOutputContent = assistantOutputContent;
        this.completionLength = completionLength;
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

    public String getQuestionSummary() {
        return questionSummary;
    }

    public String getHitChunkIds() {
        return hitChunkIds;
    }

    public String getAssistantOutputContent() {
        return assistantOutputContent;
    }

    public Integer getCompletionLength() {
        return completionLength;
    }
}
