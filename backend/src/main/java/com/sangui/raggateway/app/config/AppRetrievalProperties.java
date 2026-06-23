package com.sangui.raggateway.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rag.gateway.retrieval")
public class AppRetrievalProperties {

    private int defaultTopK = 5;
    private double defaultSimilarityThreshold = 0.300;
    private int defaultMaxContextChunks = 5;
    private int defaultMaxContextChars = 12000;
    private int defaultMaxSingleChunkChars = 3000;

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public double getDefaultSimilarityThreshold() {
        return defaultSimilarityThreshold;
    }

    public void setDefaultSimilarityThreshold(double defaultSimilarityThreshold) {
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    public int getDefaultMaxContextChunks() {
        return defaultMaxContextChunks;
    }

    public void setDefaultMaxContextChunks(int defaultMaxContextChunks) {
        this.defaultMaxContextChunks = defaultMaxContextChunks;
    }

    public int getDefaultMaxContextChars() {
        return defaultMaxContextChars;
    }

    public void setDefaultMaxContextChars(int defaultMaxContextChars) {
        this.defaultMaxContextChars = defaultMaxContextChars;
    }

    public int getDefaultMaxSingleChunkChars() {
        return defaultMaxSingleChunkChars;
    }

    public void setDefaultMaxSingleChunkChars(int defaultMaxSingleChunkChars) {
        this.defaultMaxSingleChunkChars = defaultMaxSingleChunkChars;
    }
}
