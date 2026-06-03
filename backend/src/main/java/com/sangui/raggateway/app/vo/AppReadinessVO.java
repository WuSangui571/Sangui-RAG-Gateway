package com.sangui.raggateway.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.app.AppReadinessStatus;

import java.util.List;

public class AppReadinessVO {

    @JsonProperty("app_id")
    private Long appId;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("overall_status")
    private String overallStatus;
    private List<AppReadinessCheckVO> checks;

    public AppReadinessVO() {
    }

    public AppReadinessVO(Long appId, Long userId, AppReadinessStatus overallStatus, List<AppReadinessCheckVO> checks) {
        this.appId = appId;
        this.userId = userId;
        this.overallStatus = overallStatus.name();
        this.checks = checks;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(String overallStatus) {
        this.overallStatus = overallStatus;
    }

    public List<AppReadinessCheckVO> getChecks() {
        return checks;
    }

    public void setChecks(List<AppReadinessCheckVO> checks) {
        this.checks = checks;
    }
}
