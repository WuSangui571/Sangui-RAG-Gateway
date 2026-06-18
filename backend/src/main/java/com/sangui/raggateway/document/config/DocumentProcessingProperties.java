package com.sangui.raggateway.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.document-processing.worker")
public class DocumentProcessingProperties {

    private boolean enabled = true;
    private long pollFixedDelayMs = 5000;
    private long staleProcessingTimeoutMs = 900000;
    private int maxAttempts = 3;
    private long retryBackoffMs = 60000;
    private String workerId = "local";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getPollFixedDelayMs() { return pollFixedDelayMs; }
    public void setPollFixedDelayMs(long pollFixedDelayMs) { this.pollFixedDelayMs = pollFixedDelayMs; }
    public long getStaleProcessingTimeoutMs() { return staleProcessingTimeoutMs; }
    public void setStaleProcessingTimeoutMs(long staleProcessingTimeoutMs) { this.staleProcessingTimeoutMs = staleProcessingTimeoutMs; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public long getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(long retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }
}
