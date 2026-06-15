package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public class RequestLogOutputPreviewVO {

    @JsonProperty("request_id")
    private String requestId;
    @JsonProperty("output_capture_status")
    private String outputCaptureStatus;
    @JsonProperty("completion_length")
    private Integer completionLength;
    @JsonProperty("output_preview")
    private String outputPreview;
    @JsonProperty("output_preview_truncated")
    private Boolean outputPreviewTruncated;
    @JsonProperty("output_redacted")
    private Boolean outputRedacted;
    @JsonProperty("output_retention_expires_at")
    private LocalDateTime outputRetentionExpiresAt;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOutputCaptureStatus() { return outputCaptureStatus; }
    public void setOutputCaptureStatus(String outputCaptureStatus) { this.outputCaptureStatus = outputCaptureStatus; }
    public Integer getCompletionLength() { return completionLength; }
    public void setCompletionLength(Integer completionLength) { this.completionLength = completionLength; }
    public String getOutputPreview() { return outputPreview; }
    public void setOutputPreview(String outputPreview) { this.outputPreview = outputPreview; }
    public Boolean getOutputPreviewTruncated() { return outputPreviewTruncated; }
    public void setOutputPreviewTruncated(Boolean outputPreviewTruncated) { this.outputPreviewTruncated = outputPreviewTruncated; }
    public Boolean getOutputRedacted() { return outputRedacted; }
    public void setOutputRedacted(Boolean outputRedacted) { this.outputRedacted = outputRedacted; }
    public LocalDateTime getOutputRetentionExpiresAt() { return outputRetentionExpiresAt; }
    public void setOutputRetentionExpiresAt(LocalDateTime outputRetentionExpiresAt) { this.outputRetentionExpiresAt = outputRetentionExpiresAt; }
}
