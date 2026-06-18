package com.sangui.raggateway.retrieval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Bounded retrieval evidence persisted to rag_request_log.retrieval_evidence.
 * Stores metadata only; never content, prompts, embeddings, keys, or provider bodies.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrievalEvidence {

    private final int version;

    @JsonProperty("no_hits")
    private final boolean noHits;

    @JsonProperty("retrieval_latency_ms")
    private final long retrievalLatencyMs;

    @JsonProperty("top_k")
    private final int topK;

    @JsonProperty("similarity_threshold")
    private final double similarityThreshold;

    @JsonProperty("max_context_chunks")
    private final int maxContextChunks;

    private final List<Citation> citations;

    public RetrievalEvidence(int version, boolean noHits, long retrievalLatencyMs,
                             int topK, double similarityThreshold, int maxContextChunks,
                             List<Citation> citations) {
        this.version = version;
        this.noHits = noHits;
        this.retrievalLatencyMs = retrievalLatencyMs;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.maxContextChunks = maxContextChunks;
        this.citations = citations;
    }

    public int getVersion() { return version; }
    public boolean isNoHits() { return noHits; }
    public long getRetrievalLatencyMs() { return retrievalLatencyMs; }
    public int getTopK() { return topK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public int getMaxContextChunks() { return maxContextChunks; }
    public List<Citation> getCitations() { return citations; }
}
