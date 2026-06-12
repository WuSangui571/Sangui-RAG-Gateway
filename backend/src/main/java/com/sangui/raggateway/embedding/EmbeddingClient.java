package com.sangui.raggateway.embedding;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embed(String baseUrl, String apiKey, String model,
                        List<String> inputs, int expectedDimension);

    EmbeddingProbeResult probe(String baseUrl, String apiKey, String model);
}
