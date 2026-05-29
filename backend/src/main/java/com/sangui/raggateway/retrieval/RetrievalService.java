package com.sangui.raggateway.retrieval;

import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.EmbeddingException;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@Profile("!test")
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final RetrievalMapper retrievalMapper;
    private final ModelConfigService modelConfigService;
    private final EmbeddingClient embeddingClient;

    public RetrievalService(RetrievalMapper retrievalMapper,
                            ModelConfigService modelConfigService,
                            EmbeddingClient embeddingClient) {
        this.retrievalMapper = retrievalMapper;
        this.modelConfigService = modelConfigService;
        this.embeddingClient = embeddingClient;
    }

    public RetrievalResult retrieve(String query,
                                    KnowledgeBaseEntity kb,
                                    int topK,
                                    double similarityThreshold,
                                    int maxContextChunks,
                                    int maxContextChars,
                                    int maxSingleChunkChars) {
        if (kb == null || !KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            throw new IllegalArgumentException("Knowledge base is not ready");
        }

        long start = System.currentTimeMillis();

        ModelConfigEntity embeddingConfig = modelConfigService.findEnabledEmbeddingConfig(
                kb.getUserId(), kb.getEmbeddingModel(), kb.getEmbeddingDimension());
        if (embeddingConfig == null) {
            throw new EmbeddingException("No enabled embedding config for model="
                    + kb.getEmbeddingModel() + " dimension=" + kb.getEmbeddingDimension(), false);
        }

        String decryptedKey = modelConfigService.decryptUpstreamKey(embeddingConfig);
        if (decryptedKey == null || decryptedKey.isBlank()) {
            throw new EmbeddingException("Failed to resolve embedding upstream key", false);
        }

        List<float[]> vectors = embeddingClient.embed(
                embeddingConfig.getBaseUrl(),
                decryptedKey,
                kb.getEmbeddingModel(),
                List.of(query),
                kb.getEmbeddingDimension());

        if (vectors.isEmpty()) {
            throw new EmbeddingException("Embedding returned no vectors", false);
        }

        float[] queryVector = vectors.get(0);
        String vectorString = vectorToPgString(queryVector);

        int effectiveTopK = Math.max(0, topK);
        int effectiveMaxContextChunks = Math.max(0, maxContextChunks);
        int effectiveMaxContextChars = Math.max(0, maxContextChars);
        int effectiveMaxSingleChunkChars = Math.max(0, maxSingleChunkChars);
        int maxResultChunks = Math.min(effectiveTopK, effectiveMaxContextChunks);

        if (effectiveTopK == 0 || maxResultChunks == 0 || effectiveMaxContextChars == 0) {
            long latency = System.currentTimeMillis() - start;
            log.info("retrieval.completed kb_id={} query_length={} hit_count={} no_hits={} latency_ms={}",
                    kb.getId(), query.length(), 0, true, latency);
            return new RetrievalResult(List.of(), List.of(), true, latency);
        }

        List<ChunkRow> rows = retrievalMapper.retrieveChunks(
                vectorString, kb.getUserId(), kb.getId(), effectiveTopK);

        List<ChunkRow> aboveThreshold = new ArrayList<>();
        for (ChunkRow row : rows) {
            if (row.getSimilarity() != null && row.getSimilarity() >= similarityThreshold) {
                aboveThreshold.add(row);
            }
        }

        LinkedHashSet<Long> seenChunkIds = new LinkedHashSet<>();
        List<RetrievalResult.RetrievedChunk> chunks = new ArrayList<>();
        List<Long> hitChunkIds = new ArrayList<>();
        int totalChars = 0;

        for (ChunkRow row : aboveThreshold) {
            if (!seenChunkIds.add(row.getChunkId())) {
                continue;
            }
            if (chunks.size() >= maxResultChunks) {
                break;
            }

            String chunkContent = row.getContent() != null ? row.getContent() : "";
            if (chunkContent.length() > effectiveMaxSingleChunkChars) {
                chunkContent = chunkContent.substring(0, effectiveMaxSingleChunkChars);
            }

            int remainingChars = effectiveMaxContextChars - totalChars;
            if (remainingChars <= 0) {
                break;
            }
            if (chunkContent.length() > remainingChars) {
                chunkContent = chunkContent.substring(0, remainingChars);
            }

            totalChars += chunkContent.length();
            hitChunkIds.add(row.getChunkId());
            chunks.add(new RetrievalResult.RetrievedChunk(
                    row.getChunkId(),
                    row.getDocumentId(),
                    chunkContent,
                    row.getMetadata(),
                    row.getSimilarity() != null ? row.getSimilarity() : 0.0));
        }

        boolean noHits = chunks.isEmpty();
        long latency = System.currentTimeMillis() - start;
        log.info("retrieval.completed kb_id={} query_length={} hit_count={} no_hits={} latency_ms={}",
                kb.getId(), query.length(), chunks.size(), noHits, latency);

        return new RetrievalResult(chunks, hitChunkIds, noHits, latency);
    }

    static String vectorToPgString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(String.format(Locale.ROOT, "%.8f", vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
