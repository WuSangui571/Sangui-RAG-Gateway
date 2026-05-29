package com.sangui.raggateway.retrieval;

import java.util.List;

public class RetrievalResult {

    private final List<RetrievedChunk> chunks;
    private final List<Long> hitChunkIds;
    private final boolean noHits;
    private final long retrievalLatencyMs;

    public RetrievalResult(List<RetrievedChunk> chunks, List<Long> hitChunkIds,
                           boolean noHits, long retrievalLatencyMs) {
        this.chunks = chunks;
        this.hitChunkIds = hitChunkIds;
        this.noHits = noHits;
        this.retrievalLatencyMs = retrievalLatencyMs;
    }

    public List<RetrievedChunk> getChunks() {
        return chunks;
    }

    public List<Long> getHitChunkIds() {
        return hitChunkIds;
    }

    public boolean isNoHits() {
        return noHits;
    }

    public long getRetrievalLatencyMs() {
        return retrievalLatencyMs;
    }

    public static class RetrievedChunk {
        private final Long chunkId;
        private final Long documentId;
        private final String content;
        private final String metadata;
        private final double similarity;

        public RetrievedChunk(Long chunkId, Long documentId, String content,
                              String metadata, double similarity) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.content = content;
            this.metadata = metadata;
            this.similarity = similarity;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public String getContent() {
            return content;
        }

        public String getMetadata() {
            return metadata;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
