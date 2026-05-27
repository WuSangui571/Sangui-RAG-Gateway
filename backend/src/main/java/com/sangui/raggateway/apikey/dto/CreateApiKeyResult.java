package com.sangui.raggateway.apikey.dto;

import com.sangui.raggateway.apikey.ApiKeyEntity;

public class CreateApiKeyResult {

    private final String plaintextKey;
    private final ApiKeyEntity entity;

    public CreateApiKeyResult(String plaintextKey, ApiKeyEntity entity) {
        this.plaintextKey = plaintextKey;
        this.entity = entity;
    }

    public String getPlaintextKey() {
        return plaintextKey;
    }

    public ApiKeyEntity getEntity() {
        return entity;
    }
}
