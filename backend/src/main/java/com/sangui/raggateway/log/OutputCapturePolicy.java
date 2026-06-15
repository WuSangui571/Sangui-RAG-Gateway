package com.sangui.raggateway.log;

import com.sangui.raggateway.app.AppEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class OutputCapturePolicy {

    private final OutputCaptureProperties properties;

    public OutputCapturePolicy(OutputCaptureProperties properties) {
        this.properties = properties;
    }

    public boolean shouldCapture(AppEntity app) {
        if (!properties.isEnabled()) {
            return false;
        }
        if (app == null) {
            return false;
        }
        return Boolean.TRUE.equals(app.getRequestLogOutputCaptureEnabled());
    }

    public OutputCaptureResult capture(String assistantOutputContent) {
        if (assistantOutputContent == null || assistantOutputContent.isEmpty()) {
            return new OutputCaptureResult(null, 0, false, false, "EMPTY");
        }

        int completionLength = assistantOutputContent.length();
        int maxChars = properties.getPreviewMaxChars();

        boolean truncated = completionLength > maxChars;
        String preview = truncated
                ? assistantOutputContent.substring(0, maxChars)
                : assistantOutputContent;

        String redacted = OutputRedactionService.redact(preview);
        boolean redactedChanged = !preview.equals(redacted);

        if (OutputRedactionService.hasBlockingPatterns(redacted)) {
            return new OutputCaptureResult(null, completionLength, truncated, false, "REDACTION_BLOCKED");
        }

        LocalDateTime retentionExpiresAt = LocalDateTime.now().plusDays(properties.getRetentionDays());

        return new OutputCaptureResult(redacted, completionLength, truncated, redactedChanged,
                "CAPTURED", retentionExpiresAt);
    }

    public String getDisabledStatus() {
        return "DISABLED";
    }

    public static class OutputCaptureResult {
        private final String outputPreview;
        private final Integer completionLength;
        private final boolean outputPreviewTruncated;
        private final boolean outputRedacted;
        private final String outputCaptureStatus;
        private final LocalDateTime outputRetentionExpiresAt;

        public OutputCaptureResult(String outputPreview, Integer completionLength,
                                   boolean outputPreviewTruncated, boolean outputRedacted,
                                   String outputCaptureStatus) {
            this(outputPreview, completionLength, outputPreviewTruncated, outputRedacted,
                    outputCaptureStatus, null);
        }

        public OutputCaptureResult(String outputPreview, Integer completionLength,
                                   boolean outputPreviewTruncated, boolean outputRedacted,
                                   String outputCaptureStatus, LocalDateTime outputRetentionExpiresAt) {
            this.outputPreview = outputPreview;
            this.completionLength = completionLength;
            this.outputPreviewTruncated = outputPreviewTruncated;
            this.outputRedacted = outputRedacted;
            this.outputCaptureStatus = outputCaptureStatus;
            this.outputRetentionExpiresAt = outputRetentionExpiresAt;
        }

        public String getOutputPreview() { return outputPreview; }
        public Integer getCompletionLength() { return completionLength; }
        public boolean isOutputPreviewTruncated() { return outputPreviewTruncated; }
        public boolean isOutputRedacted() { return outputRedacted; }
        public String getOutputCaptureStatus() { return outputCaptureStatus; }
        public LocalDateTime getOutputRetentionExpiresAt() { return outputRetentionExpiresAt; }
    }
}
