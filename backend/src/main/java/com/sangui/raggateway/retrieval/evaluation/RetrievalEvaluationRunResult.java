package com.sangui.raggateway.retrieval.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Aggregate retrieval evaluation run result. Safe metadata and metrics only.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalEvaluationRunResult {

    @JsonProperty("app_id")
    private final Long appId;

    @JsonProperty("knowledge_base_id")
    private final Long knowledgeBaseId;

    @JsonProperty("case_count")
    private final int caseCount;

    @JsonProperty("hit_count")
    private final int hitCount;

    @JsonProperty("precision_at_k")
    private final double precisionAtK;

    @JsonProperty("recall_at_k")
    private final double recallAtK;

    private final double mrr;

    private final List<RetrievalEvaluationCaseResult> cases;

    public RetrievalEvaluationRunResult(Long appId, Long knowledgeBaseId,
                                         int caseCount, int hitCount,
                                         double precisionAtK, double recallAtK, double mrr,
                                         List<RetrievalEvaluationCaseResult> cases) {
        this.appId = appId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.caseCount = caseCount;
        this.hitCount = hitCount;
        this.precisionAtK = precisionAtK;
        this.recallAtK = recallAtK;
        this.mrr = mrr;
        this.cases = cases;
    }

    public Long getAppId() { return appId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public int getCaseCount() { return caseCount; }
    public int getHitCount() { return hitCount; }
    public double getPrecisionAtK() { return precisionAtK; }
    public double getRecallAtK() { return recallAtK; }
    public double getMrr() { return mrr; }
    public List<RetrievalEvaluationCaseResult> getCases() { return cases; }
}
