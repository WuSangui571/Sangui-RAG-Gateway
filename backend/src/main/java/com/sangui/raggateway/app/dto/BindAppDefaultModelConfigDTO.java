package com.sangui.raggateway.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BindAppDefaultModelConfigDTO {

    @JsonProperty("model_config_id")
    private Long modelConfigId;

    public Long getModelConfigId() {
        return modelConfigId;
    }

    public void setModelConfigId(Long modelConfigId) {
        this.modelConfigId = modelConfigId;
    }
}
