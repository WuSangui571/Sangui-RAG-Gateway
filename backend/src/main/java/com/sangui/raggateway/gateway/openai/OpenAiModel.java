package com.sangui.raggateway.gateway.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OpenAiModel {

    private final String id;
    @JsonProperty("object")
    private final String object;
    private final long created;
    @JsonProperty("owned_by")
    private final String ownedBy;

    public OpenAiModel(String id, String ownedBy) {
        this.id = id;
        this.object = "model";
        this.created = 0;
        this.ownedBy = ownedBy;
    }

    public String getId() {
        return id;
    }

    public String getObject() {
        return object;
    }

    public long getCreated() {
        return created;
    }

    public String getOwnedBy() {
        return ownedBy;
    }
}
