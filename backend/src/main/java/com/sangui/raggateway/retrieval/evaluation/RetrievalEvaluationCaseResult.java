package com.sangui.raggateway.retrieval.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Safe per-case evaluation result. Contains only metadata IDs and metrics; never chunk content,
 * prompts, embeddings, keys, provider bodies, or storage paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalEvaluationCaseResult {

    @JsonProperty("case_id")
    private final String caseId;

    private final String query;

    @JsonProperty("expected_chunk_ids")
    private final List<Long> expectedChunkIds;

    @JsonProperty("actual_chunk_ids")
    private final List<Long> actualChunkIds;

    @JsonProperty("expected_document_ids")
    private final List<Long> expectedDocumentIds;

    @JsonProperty("actual_document_ids")
    private final List<Long> actualDocumentIds;

    private final boolean hit;

    private final Integer rank;

    @JsonProperty("precision_at_k")
    private final double precisionAtK;

    @JsonProperty("recall_at_k")
    private final double recallAtK;

    private final double mrr;

    @JsonProperty("no_hits")
    private final boolean noHits;

    @JsonProperty("error_code")
    private final String errorCode;

    public RetrievalEvaluationCaseResult(String caseId, String query,
                                          List<Long> expectedChunkIds, List<Long> actualChunkIds,
                                          List<Long> expectedDocumentIds, List<Long> actualDocumentIds,
                                          boolean hit, Integer rank,
                                          double precisionAtK, double recallAtK, double mrr,
                                          boolean noHits, String errorCode) {
        this.caseId = caseId;
        this.query = query;
        this.expectedChunkIds = expectedChunkIds;
        this.actualChunkIds = actualChunkIds;
        this.expectedDocumentIds = expectedDocumentIds;
        this.actualDocumentIds = actualDocumentIds;
        this.hit = hit;
        this.rank = rank;
        this.precisionAtK = precisionAtK;
        this.recallAtK = recallAtK;
        this.mrr = mrr;
        this.noHits = noHits;
        this.errorCode = errorCode;
    }

    public String getCaseId() { return caseId; }
    public String getQuery() { return query; }
    public List<Long> getExpectedChunkIds() { return expectedChunkIds; }
    public List<Long> getActualChunkIds() { return actualChunkIds; }
    public List<Long> getExpectedDocumentIds() { return expectedDocumentIds; }
    public List<Long> getActualDocumentIds() { return actualDocumentIds; }
    public boolean isHit() { return hit; }
    public Integer getRank() { return rank; }
    public double getPrecisionAtK() { return precisionAtK; }
    public double getRecallAtK() { return recallAtK; }
    public double getMrr() { return mrr; }
    public boolean isNoHits() { return noHits; }
    public String getErrorCode() { return errorCode; }
}
