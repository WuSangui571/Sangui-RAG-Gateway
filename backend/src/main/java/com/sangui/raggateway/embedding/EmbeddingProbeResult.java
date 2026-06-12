package com.sangui.raggateway.embedding;

public class EmbeddingProbeResult {

    private final String model;
    private final int dimension;

    public EmbeddingProbeResult(String model, int dimension) {
        this.model = model;
        this.dimension = dimension;
    }

    public String getModel() {
        return model;
    }

    public int getDimension() {
        return dimension;
    }
}
