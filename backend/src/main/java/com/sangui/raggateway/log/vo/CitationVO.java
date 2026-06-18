package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Safe citation metadata projection used in request-log detail retrieval evidence.
 * Mirrors the retrieval Citation shape without content, prompts, embeddings, keys, or storage paths.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CitationVO {

    @JsonProperty("citation_id")
    private String citationId;

    @JsonProperty("chunk_id")
    private Long chunkId;

    @JsonProperty("document_id")
    private Long documentId;

    @JsonProperty("knowledge_base_id")
    private Long knowledgeBaseId;

    @JsonProperty("source_filename")
    private String sourceFilename;

    @JsonProperty("chunk_index")
    private Integer chunkIndex;

    @JsonProperty("similarity")
    private Double similarity;

    private Map<String, Object> metadata;

    @JsonProperty("content_chars")
    private Integer contentChars;

    @JsonProperty("injected_chars")
    private Integer injectedChars;

    public String getCitationId() { return citationId; }
    public void setCitationId(String citationId) { this.citationId = citationId; }
    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public void setKnowledgeBaseId(Long knowledgeBaseId) { this.knowledgeBaseId = knowledgeBaseId; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Integer getContentChars() { return contentChars; }
    public void setContentChars(Integer contentChars) { this.contentChars = contentChars; }
    public Integer getInjectedChars() { return injectedChars; }
    public void setInjectedChars(Integer injectedChars) { this.injectedChars = injectedChars; }
}
