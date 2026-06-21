package com.sangui.raggateway.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.production-guard")
public class ProductionGuardProperties {

    private boolean allowLocalFileStorage = false;
    private boolean allowOutputCapture = false;
    private boolean allowWeakLocalSecret = false;

    public boolean isAllowLocalFileStorage() {
        return allowLocalFileStorage;
    }

    public void setAllowLocalFileStorage(boolean allowLocalFileStorage) {
        this.allowLocalFileStorage = allowLocalFileStorage;
    }

    public boolean isAllowOutputCapture() {
        return allowOutputCapture;
    }

    public void setAllowOutputCapture(boolean allowOutputCapture) {
        this.allowOutputCapture = allowOutputCapture;
    }

    public boolean isAllowWeakLocalSecret() {
        return allowWeakLocalSecret;
    }

    public void setAllowWeakLocalSecret(boolean allowWeakLocalSecret) {
        this.allowWeakLocalSecret = allowWeakLocalSecret;
    }
}
