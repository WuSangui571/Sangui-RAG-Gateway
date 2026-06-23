package com.sangui.raggateway.app;

public class AppRetrievalConfig {

    private final int topK;
    private final double similarityThreshold;
    private final int maxContextChunks;
    private final int maxContextChars;
    private final int maxSingleChunkChars;

    private AppRetrievalConfig(int topK, double similarityThreshold,
                               int maxContextChunks, int maxContextChars, int maxSingleChunkChars) {
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
        this.maxContextChunks = maxContextChunks;
        this.maxContextChars = maxContextChars;
        this.maxSingleChunkChars = maxSingleChunkChars;
    }

    public static AppRetrievalConfig from(AppEntity app) {
        if (app.getRetrievalTopK() == null) {
            throw new IllegalArgumentException("retrievalTopK must not be null");
        }
        if (app.getRetrievalSimilarityThreshold() == null) {
            throw new IllegalArgumentException("retrievalSimilarityThreshold must not be null");
        }
        if (app.getRetrievalMaxContextChunks() == null) {
            throw new IllegalArgumentException("retrievalMaxContextChunks must not be null");
        }
        if (app.getRetrievalMaxContextChars() == null) {
            throw new IllegalArgumentException("retrievalMaxContextChars must not be null");
        }
        if (app.getRetrievalMaxSingleChunkChars() == null) {
            throw new IllegalArgumentException("retrievalMaxSingleChunkChars must not be null");
        }

        int topK = app.getRetrievalTopK();
        double threshold = app.getRetrievalSimilarityThreshold();
        int maxChunks = app.getRetrievalMaxContextChunks();
        int maxChars = app.getRetrievalMaxContextChars();
        int maxSingleChars = app.getRetrievalMaxSingleChunkChars();

        if (topK <= 0) {
            throw new IllegalArgumentException("retrievalTopK must be positive, got: " + topK);
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("retrievalSimilarityThreshold must be in [0.0, 1.0], got: " + threshold);
        }
        if (maxChunks <= 0) {
            throw new IllegalArgumentException("retrievalMaxContextChunks must be positive, got: " + maxChunks);
        }
        if (maxChars <= 0) {
            throw new IllegalArgumentException("retrievalMaxContextChars must be positive, got: " + maxChars);
        }
        if (maxSingleChars <= 0) {
            throw new IllegalArgumentException("retrievalMaxSingleChunkChars must be positive, got: " + maxSingleChars);
        }

        return new AppRetrievalConfig(topK, threshold, maxChunks, maxChars, maxSingleChars);
    }

    public int getTopK() {
        return topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getMaxContextChunks() {
        return maxContextChunks;
    }

    public int getMaxContextChars() {
        return maxContextChars;
    }

    public int getMaxSingleChunkChars() {
        return maxSingleChunkChars;
    }
}
