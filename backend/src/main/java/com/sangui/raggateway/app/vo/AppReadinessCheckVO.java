package com.sangui.raggateway.app.vo;

import com.sangui.raggateway.app.AppReadinessStatus;

import java.util.Map;

public class AppReadinessCheckVO {

    private String key;
    private String label;
    private String status;
    private String message;
    private Map<String, Object> metadata;

    public AppReadinessCheckVO() {
    }

    public AppReadinessCheckVO(String key, String label, AppReadinessStatus status, String message, Map<String, Object> metadata) {
        this.key = key;
        this.label = label;
        this.status = status.name();
        this.message = message;
        this.metadata = metadata;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
