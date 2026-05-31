package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.log.ApiRequestLogEntity;

public class RequestLogUsageVO {

    @JsonProperty("prompt_tokens")
    private Integer promptTokens;
    @JsonProperty("completion_tokens")
    private Integer completionTokens;
    @JsonProperty("total_tokens")
    private Integer totalTokens;

    public static RequestLogUsageVO from(ApiRequestLogEntity entity) {
        if (entity.getPromptTokens() == null && entity.getCompletionTokens() == null && entity.getTotalTokens() == null) {
            return null;
        }
        RequestLogUsageVO vo = new RequestLogUsageVO();
        vo.promptTokens = entity.getPromptTokens();
        vo.completionTokens = entity.getCompletionTokens();
        vo.totalTokens = entity.getTotalTokens();
        return vo;
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
