package com.sangui.raggateway.retrieval.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One deterministic retrieval evaluation baseline case loaded from the repo-local sample set.
 * Ground truth is expressed as safe IDs only; never chunk content, prompts, or embeddings.
 */
public class RetrievalEvaluationCase {

    @JsonProperty("case_id")
    private String caseId;

    private String query;

    @JsonProperty("expected_chunk_ids")
    private List<Long> expectedChunkIds;

    @JsonProperty("expected_document_ids")
    private List<Long> expectedDocumentIds;

    @JsonProperty("required_source_filename")
    private String requiredSourceFilename;

    @JsonProperty("min_expected_similarity")
    private Double minExpectedSimilarity;

    @JsonProperty("no_hits")
    private Boolean noHits;

    public String getCaseId() { return caseId; }
    public void setCaseId(String caseId) { this.caseId = caseId; }
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public List<Long> getExpectedChunkIds() { return expectedChunkIds; }
    public void setExpectedChunkIds(List<Long> expectedChunkIds) { this.expectedChunkIds = expectedChunkIds; }
    public List<Long> getExpectedDocumentIds() { return expectedDocumentIds; }
    public void setExpectedDocumentIds(List<Long> expectedDocumentIds) { this.expectedDocumentIds = expectedDocumentIds; }
    public String getRequiredSourceFilename() { return requiredSourceFilename; }
    public void setRequiredSourceFilename(String requiredSourceFilename) { this.requiredSourceFilename = requiredSourceFilename; }
    public Double getMinExpectedSimilarity() { return minExpectedSimilarity; }
    public void setMinExpectedSimilarity(Double minExpectedSimilarity) { this.minExpectedSimilarity = minExpectedSimilarity; }
    public Boolean getNoHits() { return noHits; }
    public void setNoHits(Boolean noHits) { this.noHits = noHits; }
}
