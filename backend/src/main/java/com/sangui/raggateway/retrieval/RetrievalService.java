package com.sangui.raggateway.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@Profile("!test")
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private static final int EVIDENCE_VERSION = 1;
    private static final Set<String> SAFE_METADATA_KEYS = Set.of("source", "parser");

    private final RetrievalMapper retrievalMapper;
    private final ModelConfigService modelConfigService;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    public RetrievalService(RetrievalMapper retrievalMapper,
                            ModelConfigService modelConfigService,
                            EmbeddingClient embeddingClient,
                            ObjectMapper objectMapper) {
        this.retrievalMapper = retrievalMapper;
        this.modelConfigService = modelConfigService;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
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
            return buildResult(List.of(), List.of(), List.of(), true, latency,
                    effectiveTopK, similarityThreshold, effectiveMaxContextChunks);
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

            String originalContent = row.getContent() != null ? row.getContent() : "";
            String chunkContent = originalContent;
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
                    row.getKnowledgeBaseId(),
                    row.getChunkIndex(),
                    row.getSourceFilename(),
                    chunkContent,
                    row.getMetadata(),
                    row.getSimilarity() != null ? row.getSimilarity() : 0.0,
                    originalContent.length(),
                    chunkContent.length(),
                    null));
        }

        boolean noHits = chunks.isEmpty();
        long latency = System.currentTimeMillis() - start;
        log.info("retrieval.completed kb_id={} query_length={} hit_count={} no_hits={} latency_ms={}",
                kb.getId(), query.length(), chunks.size(), noHits, latency);

        return buildResult(chunks, hitChunkIds, buildCitations(chunks), noHits, latency,
                effectiveTopK, similarityThreshold, effectiveMaxContextChunks);
    }

    private RetrievalResult buildResult(List<RetrievalResult.RetrievedChunk> chunks,
                                        List<Long> hitChunkIds,
                                        List<Citation> citations,
                                        boolean noHits,
                                        long latency,
                                        int topK,
                                        double similarityThreshold,
                                        int maxContextChunks) {
        List<RetrievalResult.RetrievedChunk> labeled = labelCitations(chunks);
        List<Citation> labeledCitations = citations.isEmpty() ? citations : labelCitationIds(citations);
        RetrievalEvidence evidence = new RetrievalEvidence(
                EVIDENCE_VERSION, noHits, latency, topK, similarityThreshold, maxContextChunks, labeledCitations);
        return new RetrievalResult(labeled, hitChunkIds, labeledCitations, evidence, noHits, latency);
    }

    private List<RetrievalResult.RetrievedChunk> labelCitations(List<RetrievalResult.RetrievedChunk> chunks) {
        List<RetrievalResult.RetrievedChunk> labeled = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalResult.RetrievedChunk c = chunks.get(i);
            String citationId = "S" + (i + 1);
            labeled.add(new RetrievalResult.RetrievedChunk(
                    c.getChunkId(),
                    c.getDocumentId(),
                    c.getKnowledgeBaseId(),
                    c.getChunkIndex(),
                    c.getSourceFilename(),
                    c.getContent(),
                    c.getMetadata(),
                    c.getSimilarity(),
                    c.getContentChars(),
                    c.getInjectedChars(),
                    citationId));
        }
        return labeled;
    }

    private List<Citation> labelCitationIds(List<Citation> citations) {
        List<Citation> labeled = new ArrayList<>(citations.size());
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            String citationId = "S" + (i + 1);
            labeled.add(new Citation(
                    citationId,
                    c.getChunkId(),
                    c.getDocumentId(),
                    c.getKnowledgeBaseId(),
                    c.getSourceFilename(),
                    c.getChunkIndex(),
                    c.getSimilarity(),
                    c.getMetadata(),
                    c.getContentChars(),
                    c.getInjectedChars()));
        }
        return labeled;
    }

    private List<Citation> buildCitations(List<RetrievalResult.RetrievedChunk> chunks) {
        List<Citation> citations = new ArrayList<>(chunks.size());
        for (RetrievalResult.RetrievedChunk chunk : chunks) {
            citations.add(new Citation(
                    null,
                    chunk.getChunkId(),
                    chunk.getDocumentId(),
                    chunk.getKnowledgeBaseId(),
                    chunk.getSourceFilename(),
                    chunk.getChunkIndex(),
                    chunk.getSimilarity(),
                    filterSafeMetadata(chunk.getMetadata()),
                    chunk.getContentChars(),
                    chunk.getInjectedChars()));
        }
        return citations;
    }

    private Map<String, Object> filterSafeMetadata(String rawMetadata) {
        if (rawMetadata == null || rawMetadata.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawMetadata, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : SAFE_METADATA_KEYS) {
                if (parsed.containsKey(key)) {
                    safe.put(key, parsed.get(key));
                }
            }
            return safe.isEmpty() ? null : safe;
        } catch (Exception e) {
            return null;
        }
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
