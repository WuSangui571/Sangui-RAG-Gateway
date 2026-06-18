package com.sangui.raggateway.retrieval.evaluation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RetrievalEvaluationRunDTO {

    @JsonProperty("case_ids")
    private List<String> caseIds;

    private Integer limit;

    public List<String> getCaseIds() { return caseIds; }
    public void setCaseIds(List<String> caseIds) { this.caseIds = caseIds; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
