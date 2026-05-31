package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.log.ApiRequestLogEntity;

import java.time.LocalDateTime;

public class ApiRequestLogDetailVO extends ApiRequestLogVO {

    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static ApiRequestLogDetailVO from(ApiRequestLogEntity entity) {
        ApiRequestLogDetailVO vo = new ApiRequestLogDetailVO();
        vo.setBaseFields(entity);
        vo.userId = entity.getUserId();
        vo.updatedAt = entity.getUpdatedAt();
        return vo;
    }

    private void setBaseFields(ApiRequestLogEntity entity) {
        setId(entity.getId());
        setRequestId(entity.getRequestId());
        setAppId(entity.getAppId());
        setApiKeyId(entity.getApiKeyId());
        setModel(entity.getModel());
        setProviderName(entity.getProviderName());
        setStatus(entity.getStatus());
        setErrorCode(entity.getErrorCode());
        setLatencyMs(entity.getLatencyMs());
        setUpstreamLatencyMs(entity.getUpstreamLatencyMs());
        setUsage(RequestLogUsageVO.from(entity));
        setMessagesCount(entity.getMessagesCount());
        setQuestionSummary(entity.getQuestionSummary());
        setHitChunkIds(ApiRequestLogVO.from(entity).getHitChunkIds());
        setCreatedAt(entity.getCreatedAt());
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
