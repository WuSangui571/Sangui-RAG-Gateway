package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.log.ApiRequestLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class ApiRequestLogVO {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogVO.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long id;
    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("app_id")
    private Long appId;
    @JsonProperty("api_key_id")
    private Long apiKeyId;
    private String model;
    @JsonProperty("provider_name")
    private String providerName;
    private String status;
    @JsonProperty("error_code")
    private String errorCode;
    @JsonProperty("latency_ms")
    private Long latencyMs;
    @JsonProperty("upstream_latency_ms")
    private Long upstreamLatencyMs;
    private RequestLogUsageVO usage;
    @JsonProperty("messages_count")
    private Integer messagesCount;
    @JsonProperty("question_summary")
    private String questionSummary;
    @JsonProperty("hit_chunk_ids")
    private List<Long> hitChunkIds;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    public static ApiRequestLogVO from(ApiRequestLogEntity entity) {
        ApiRequestLogVO vo = new ApiRequestLogVO();
        vo.id = entity.getId();
        vo.requestId = entity.getRequestId();
        vo.appId = entity.getAppId();
        vo.apiKeyId = entity.getApiKeyId();
        vo.model = entity.getModel();
        vo.providerName = entity.getProviderName();
        vo.status = entity.getStatus();
        vo.errorCode = entity.getErrorCode();
        vo.latencyMs = entity.getLatencyMs();
        vo.upstreamLatencyMs = entity.getUpstreamLatencyMs();
        vo.usage = RequestLogUsageVO.from(entity);
        vo.messagesCount = entity.getMessagesCount();
        vo.questionSummary = entity.getQuestionSummary();
        vo.hitChunkIds = parseHitChunkIds(entity.getHitChunkIds());
        vo.createdAt = entity.getCreatedAt();
        return vo;
    }

    private static List<Long> parseHitChunkIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            log.error("Failed to parse hit_chunk_ids JSONB value, raw={}", raw, e);
            throw new IllegalArgumentException("Failed to parse hit_chunk_ids: " + e.getMessage(), e);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Long latencyMs) { this.latencyMs = latencyMs; }
    public Long getUpstreamLatencyMs() { return upstreamLatencyMs; }
    public void setUpstreamLatencyMs(Long upstreamLatencyMs) { this.upstreamLatencyMs = upstreamLatencyMs; }
    public RequestLogUsageVO getUsage() { return usage; }
    public void setUsage(RequestLogUsageVO usage) { this.usage = usage; }
    public Integer getMessagesCount() { return messagesCount; }
    public void setMessagesCount(Integer messagesCount) { this.messagesCount = messagesCount; }
    public String getQuestionSummary() { return questionSummary; }
    public void setQuestionSummary(String questionSummary) { this.questionSummary = questionSummary; }
    public List<Long> getHitChunkIds() { return hitChunkIds; }
    public void setHitChunkIds(List<Long> hitChunkIds) { this.hitChunkIds = hitChunkIds; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
