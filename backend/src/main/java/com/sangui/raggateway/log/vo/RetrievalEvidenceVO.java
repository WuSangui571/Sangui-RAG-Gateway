package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Parsed retrieval evidence for request-log detail. Null when the stored column is absent
 * (old rows). Malformed stored JSON fails visibly during parsing; old rows are not treated
 * as retrieval success.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalEvidenceVO {

    private int version;

    @JsonProperty("no_hits")
    private boolean noHits;

    @JsonProperty("retrieval_latency_ms")
    private long retrievalLatencyMs;

    @JsonProperty("top_k")
    private int topK;

    @JsonProperty("similarity_threshold")
    private double similarityThreshold;

    @JsonProperty("max_context_chunks")
    private int maxContextChunks;

    private List<CitationVO> citations;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public boolean isNoHits() { return noHits; }
    public void setNoHits(boolean noHits) { this.noHits = noHits; }
    public long getRetrievalLatencyMs() { return retrievalLatencyMs; }
    public void setRetrievalLatencyMs(long retrievalLatencyMs) { this.retrievalLatencyMs = retrievalLatencyMs; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public int getMaxContextChunks() { return maxContextChunks; }
    public void setMaxContextChunks(int maxContextChunks) { this.maxContextChunks = maxContextChunks; }
    public List<CitationVO> getCitations() { return citations; }
    public void setCitations(List<CitationVO> citations) { this.citations = citations; }
}
