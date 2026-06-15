package com.sangui.raggateway.log.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RequestLogOutputAccessDTO {

    @JsonProperty("confirm_access")
    private Boolean confirmAccess;
    private String reason;

    public Boolean getConfirmAccess() {
        return confirmAccess;
    }

    public void setConfirmAccess(Boolean confirmAccess) {
        this.confirmAccess = confirmAccess;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
