package com.sangui.raggateway.app.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BindAppDefaultKnowledgeBaseDTO {

    @JsonProperty("knowledge_base_id")
    private Long knowledgeBaseId;

    public Long getKnowledgeBaseId() {
        return knowledgeBaseId;
    }

    public void setKnowledgeBaseId(Long knowledgeBaseId) {
        this.knowledgeBaseId = knowledgeBaseId;
    }
}
