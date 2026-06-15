package com.sangui.raggateway.log;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.request-log.output-capture")
public class OutputCaptureProperties {

    private boolean enabled = false;
    private int previewMaxChars = 1000;
    private int retentionDays = 7;
    private boolean cleanupEnabled = true;
    private int reasonMaxChars = 256;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPreviewMaxChars() {
        return previewMaxChars;
    }

    public void setPreviewMaxChars(int previewMaxChars) {
        this.previewMaxChars = previewMaxChars;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public boolean isCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public int getReasonMaxChars() {
        return reasonMaxChars;
    }

    public void setReasonMaxChars(int reasonMaxChars) {
        this.reasonMaxChars = reasonMaxChars;
    }
}
