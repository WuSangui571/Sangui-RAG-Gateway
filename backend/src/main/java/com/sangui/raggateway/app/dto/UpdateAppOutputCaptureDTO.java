package com.sangui.raggateway.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UpdateAppOutputCaptureDTO {

    @JsonProperty("request_log_output_capture_enabled")
    private Boolean requestLogOutputCaptureEnabled;

    public Boolean getRequestLogOutputCaptureEnabled() {
        return requestLogOutputCaptureEnabled;
    }

    public void setRequestLogOutputCaptureEnabled(Boolean requestLogOutputCaptureEnabled) {
        this.requestLogOutputCaptureEnabled = requestLogOutputCaptureEnabled;
    }
}
