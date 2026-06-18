package com.sangui.raggateway.retrieval;

import java.util.List;

public class RetrievalResult {

    private final List<RetrievedChunk> chunks;
    private final List<Long> hitChunkIds;
    private final List<Citation> citations;
    private final RetrievalEvidence evidence;
    private final boolean noHits;
    private final long retrievalLatencyMs;

    public RetrievalResult(List<RetrievedChunk> chunks, List<Long> hitChunkIds,
                           List<Citation> citations, RetrievalEvidence evidence,
                           boolean noHits, long retrievalLatencyMs) {
        this.chunks = chunks;
        this.hitChunkIds = hitChunkIds;
        this.citations = citations;
        this.evidence = evidence;
        this.noHits = noHits;
        this.retrievalLatencyMs = retrievalLatencyMs;
    }

    public List<RetrievedChunk> getChunks() {
        return chunks;
    }

    public List<Long> getHitChunkIds() {
        return hitChunkIds;
    }

    public List<Citation> getCitations() {
        return citations;
    }

    public RetrievalEvidence getEvidence() {
        return evidence;
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
        private final Long knowledgeBaseId;
        private final Integer chunkIndex;
        private final String sourceFilename;
        private final String content;
        private final String metadata;
        private final double similarity;
        private final int contentChars;
        private final int injectedChars;
        private final String citationId;

        public RetrievedChunk(Long chunkId, Long documentId, String content,
                              String metadata, double similarity) {
            this(chunkId, documentId, null, null, null, content, metadata, similarity,
                    content != null ? content.length() : 0,
                    content != null ? content.length() : 0, null);
        }

        public RetrievedChunk(Long chunkId, Long documentId, Long knowledgeBaseId,
                              Integer chunkIndex, String sourceFilename, String content,
                              String metadata, double similarity,
                              int contentChars, int injectedChars, String citationId) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.chunkIndex = chunkIndex;
            this.sourceFilename = sourceFilename;
            this.content = content;
            this.metadata = metadata;
            this.similarity = similarity;
            this.contentChars = contentChars;
            this.injectedChars = injectedChars;
            this.citationId = citationId;
        }

        public Long getChunkId() {
            return chunkId;
        }

        public Long getDocumentId() {
            return documentId;
        }

        public Long getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public String getSourceFilename() {
            return sourceFilename;
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

        public int getContentChars() {
            return contentChars;
        }

        public int getInjectedChars() {
            return injectedChars;
        }

        public String getCitationId() {
            return citationId;
        }
    }
}
