package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.log.ApiRequestLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

public class ApiRequestLogDetailVO extends ApiRequestLogVO {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogDetailVO.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
    @JsonProperty("output_capture_status")
    private String outputCaptureStatus;
    @JsonProperty("completion_length")
    private Integer completionLength;
    @JsonProperty("output_preview_available")
    private Boolean outputPreviewAvailable;
    @JsonProperty("output_preview_truncated")
    private Boolean outputPreviewTruncated;
    @JsonProperty("output_redacted")
    private Boolean outputRedacted;
    @JsonProperty("output_retention_expires_at")
    private LocalDateTime outputRetentionExpiresAt;
    @JsonProperty("retrieval_evidence")
    private RetrievalEvidenceVO retrievalEvidence;

    public static ApiRequestLogDetailVO from(ApiRequestLogEntity entity) {
        ApiRequestLogDetailVO vo = new ApiRequestLogDetailVO();
        vo.setBaseFields(entity);
        vo.userId = entity.getUserId();
        vo.updatedAt = entity.getUpdatedAt();
        vo.outputCaptureStatus = entity.getOutputCaptureStatus();
        vo.completionLength = entity.getCompletionLength();
        vo.outputPreviewAvailable = entity.getOutputPreview() != null
                && !"EXPIRED".equals(entity.getOutputCaptureStatus())
                && !"REDACTION_BLOCKED".equals(entity.getOutputCaptureStatus());
        vo.outputPreviewTruncated = entity.getOutputPreviewTruncated();
        vo.outputRedacted = entity.getOutputRedacted();
        vo.outputRetentionExpiresAt = entity.getOutputRetentionExpiresAt();
        vo.retrievalEvidence = parseRetrievalEvidence(entity.getRetrievalEvidence());
        return vo;
    }

    private static RetrievalEvidenceVO parseRetrievalEvidence(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, RetrievalEvidenceVO.class);
        } catch (Exception e) {
            log.error("Failed to parse retrieval_evidence JSONB value", e);
            throw new IllegalArgumentException("Failed to parse retrieval_evidence: " + e.getMessage(), e);
        }
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
    public String getOutputCaptureStatus() { return outputCaptureStatus; }
    public void setOutputCaptureStatus(String outputCaptureStatus) { this.outputCaptureStatus = outputCaptureStatus; }
    public Integer getCompletionLength() { return completionLength; }
    public void setCompletionLength(Integer completionLength) { this.completionLength = completionLength; }
    public Boolean getOutputPreviewAvailable() { return outputPreviewAvailable; }
    public void setOutputPreviewAvailable(Boolean outputPreviewAvailable) { this.outputPreviewAvailable = outputPreviewAvailable; }
    public Boolean getOutputPreviewTruncated() { return outputPreviewTruncated; }
    public void setOutputPreviewTruncated(Boolean outputPreviewTruncated) { this.outputPreviewTruncated = outputPreviewTruncated; }
    public Boolean getOutputRedacted() { return outputRedacted; }
    public void setOutputRedacted(Boolean outputRedacted) { this.outputRedacted = outputRedacted; }
    public LocalDateTime getOutputRetentionExpiresAt() { return outputRetentionExpiresAt; }
    public void setOutputRetentionExpiresAt(LocalDateTime outputRetentionExpiresAt) { this.outputRetentionExpiresAt = outputRetentionExpiresAt; }
    public RetrievalEvidenceVO getRetrievalEvidence() { return retrievalEvidence; }
    public void setRetrievalEvidence(RetrievalEvidenceVO retrievalEvidence) { this.retrievalEvidence = retrievalEvidence; }
}
