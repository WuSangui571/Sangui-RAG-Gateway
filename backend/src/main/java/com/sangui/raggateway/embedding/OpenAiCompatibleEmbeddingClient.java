package com.sangui.raggateway.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Profile("!test")
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClient.class);

    private static final String EMBEDDING_PATH = "/v1/embeddings";

    private final RestClient restClient;

    @Autowired
    public OpenAiCompatibleEmbeddingClient(
            @Value("${rag.gateway.embedding.timeout-seconds:30}") int timeoutSeconds) {
        this(createRestClient(timeoutSeconds));
    }

    OpenAiCompatibleEmbeddingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient createRestClient(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public List<float[]> embed(String baseUrl, String apiKey, String model,
                               List<String> inputs, int expectedDimension) {
        String base = normalizeBaseUrl(baseUrl);
        String url = base.endsWith("/v1") ? base + "/embeddings" : base + EMBEDDING_PATH;

        EmbeddingRequest request = new EmbeddingRequest(model, inputs);

        long start = System.currentTimeMillis();
        try {
            EmbeddingResponse response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange((req, resp) -> {
                        HttpStatusCode status = resp.getStatusCode();
                        if (!status.is2xxSuccessful()) {
                            long latency = System.currentTimeMillis() - start;
                            log.warn("embedding.upstream_failed status={} model={} input_count={} latency_ms={}",
                                    status.value(), model, inputs.size(), latency);
                            throw new EmbeddingException("Embedding upstream returned status " + status.value(), false);
                        }
                        byte[] body = resp.getBody().readAllBytes();
                        return RestClientUtils.parseJson(body, EmbeddingResponse.class);
                    });

            long latency = System.currentTimeMillis() - start;
            log.info("embedding.completed model={} input_count={} output_count={} dimension={} latency_ms={}",
                    model, inputs.size(),
                    response.getData() != null ? response.getData().size() : 0,
                    expectedDimension, latency);

            return validateAndExtract(response, inputs.size(), expectedDimension);
        } catch (EmbeddingException e) {
            throw e;
        } catch (ResourceAccessException e) {
            long latency = System.currentTimeMillis() - start;
            Throwable cause = e.getCause();
            boolean timeout = cause instanceof SocketTimeoutException
                    || (cause instanceof java.net.ConnectException
                    && cause.getMessage() != null
                    && cause.getMessage().contains("time"));
            log.error("embedding.upstream_failed error_class={} timeout={} model={} input_count={} latency_ms={}",
                    e.getClass().getSimpleName(), timeout, model, inputs.size(), latency);
            throw new EmbeddingException(
                    timeout ? "Embedding upstream timed out" : "Embedding upstream is unavailable",
                    timeout);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("embedding.upstream_failed error_class={} model={} input_count={} latency_ms={}",
                    e.getClass().getSimpleName(), model, inputs.size(), latency);
            throw new EmbeddingException("Embedding upstream is unavailable", false);
        }
    }

    private List<float[]> validateAndExtract(EmbeddingResponse response, int inputCount, int expectedDimension) {
        if (response.getData() == null) {
            throw new EmbeddingException("Embedding response data is null", false);
        }
        if (response.getData().size() != inputCount) {
            throw new EmbeddingException(
                    "Embedding response count mismatch: expected " + inputCount
                            + " but got " + response.getData().size(), false);
        }

        List<EmbeddingResponse.EmbeddingData> sorted = new ArrayList<>(response.getData());
        sorted.sort(Comparator.comparingInt(EmbeddingResponse.EmbeddingData::getIndex));

        for (int i = 0; i < sorted.size(); i++) {
            EmbeddingResponse.EmbeddingData data = sorted.get(i);
            if (data.getIndex() != i) {
                throw new EmbeddingException(
                        "Embedding index mismatch at position " + i + ": expected " + i
                                + " but got " + data.getIndex(), false);
            }
            if (data.getEmbedding() == null) {
                throw new EmbeddingException("Embedding vector at index " + i + " is null", false);
            }
            if (data.getEmbedding().size() != expectedDimension) {
                throw new EmbeddingException(
                        "Embedding dimension mismatch at index " + i + ": expected " + expectedDimension
                                + " but got " + data.getEmbedding().size(), false);
            }
        }

        List<float[]> vectors = new ArrayList<>(sorted.size());
        for (EmbeddingResponse.EmbeddingData data : sorted) {
            List<Float> floats = data.getEmbedding();
            float[] vector = new float[floats.size()];
            for (int j = 0; j < floats.size(); j++) {
                vector[j] = floats.get(j);
            }
            vectors.add(vector);
        }
        return vectors;
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
