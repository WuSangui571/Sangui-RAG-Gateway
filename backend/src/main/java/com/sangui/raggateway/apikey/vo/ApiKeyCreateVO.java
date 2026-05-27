package com.sangui.raggateway.apikey.vo;

import com.sangui.raggateway.apikey.ApiKeyEntity;

public class ApiKeyCreateVO extends ApiKeyVO {

    private String key;

    public static ApiKeyCreateVO of(String plaintextKey, ApiKeyEntity entity) {
        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.id = entity.getId();
        vo.appId = entity.getAppId();
        vo.userId = entity.getUserId();
        vo.name = entity.getName();
        vo.keyPrefix = entity.getKeyPrefix();
        vo.status = entity.getStatus();
        vo.expiresAt = entity.getExpiresAt();
        vo.lastUsedAt = entity.getLastUsedAt();
        vo.revokedAt = entity.getRevokedAt();
        vo.createdAt = entity.getCreatedAt();
        vo.updatedAt = entity.getUpdatedAt();
        vo.key = plaintextKey;
        return vo;
    }

    public String getKey() {
        return key;
    }
}
