package com.sangui.raggateway.retrieval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Bounded citation metadata shared across retrieval, prompt context, opt-in gateway response,
 * request-log evidence, and admin display. Never carries chunk content, prompts, embeddings,
 * storage paths, keys, or provider bodies.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Citation {

    @JsonProperty("citation_id")
    private final String citationId;

    @JsonProperty("chunk_id")
    private final Long chunkId;

    @JsonProperty("document_id")
    private final Long documentId;

    @JsonProperty("knowledge_base_id")
    private final Long knowledgeBaseId;

    @JsonProperty("source_filename")
    private final String sourceFilename;

    @JsonProperty("chunk_index")
    private final Integer chunkIndex;

    @JsonProperty("similarity")
    private final Double similarity;

    private final Map<String, Object> metadata;

    @JsonProperty("content_chars")
    private final Integer contentChars;

    @JsonProperty("injected_chars")
    private final Integer injectedChars;

    public Citation(String citationId, Long chunkId, Long documentId, Long knowledgeBaseId,
                    String sourceFilename, Integer chunkIndex, Double similarity,
                    Map<String, Object> metadata, Integer contentChars, Integer injectedChars) {
        this.citationId = citationId;
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.knowledgeBaseId = knowledgeBaseId;
        this.sourceFilename = sourceFilename;
        this.chunkIndex = chunkIndex;
        this.similarity = similarity;
        this.metadata = metadata;
        this.contentChars = contentChars;
        this.injectedChars = injectedChars;
    }

    public String getCitationId() { return citationId; }
    public Long getChunkId() { return chunkId; }
    public Long getDocumentId() { return documentId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getSourceFilename() { return sourceFilename; }
    public Integer getChunkIndex() { return chunkIndex; }
    public Double getSimilarity() { return similarity; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Integer getContentChars() { return contentChars; }
    public Integer getInjectedChars() { return injectedChars; }
}
