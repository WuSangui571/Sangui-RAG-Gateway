package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.document.DocumentChunkEntity;
import com.sangui.raggateway.document.DocumentEntity;

public class HitChunkSummaryVO {

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
    private String summary;

    public static HitChunkSummaryVO of(DocumentChunkEntity chunk, DocumentEntity document, int maxChars) {
        HitChunkSummaryVO vo = new HitChunkSummaryVO();
        vo.chunkId = chunk.getId();
        vo.documentId = chunk.getDocumentId();
        vo.knowledgeBaseId = chunk.getKnowledgeBaseId();
        vo.chunkIndex = chunk.getChunkIndex();
        vo.sourceFilename = document != null ? document.getOriginalFilename() : null;
        vo.summary = truncate(chunk.getContent(), maxChars);
        return vo;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }

    public Long getChunkId() { return chunkId; }
    public Long getDocumentId() { return documentId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getSourceFilename() { return sourceFilename; }
    public Integer getChunkIndex() { return chunkIndex; }
    public String getSummary() { return summary; }
}
