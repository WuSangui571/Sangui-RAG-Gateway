package com.sangui.raggateway.document.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.document.DocumentEntity;

import java.time.LocalDateTime;

public class DocumentVO {

    private Long id;
    @JsonProperty("user_id")
    private Long userId;
    @JsonProperty("knowledge_base_id")
    private Long knowledgeBaseId;
    @JsonProperty("original_filename")
    private String originalFilename;
    @JsonProperty("content_type")
    private String contentType;
    @JsonProperty("file_size")
    private Long fileSize;
    private String status;
    @JsonProperty("chunk_count")
    private Integer chunkCount;
    @JsonProperty("error_message")
    private String errorMessage;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public static DocumentVO from(DocumentEntity entity) {
        DocumentVO vo = new DocumentVO();
        vo.id = entity.getId();
        vo.userId = entity.getUserId();
        vo.knowledgeBaseId = entity.getKnowledgeBaseId();
        vo.originalFilename = entity.getOriginalFilename();
        vo.contentType = entity.getContentType();
        vo.fileSize = entity.getFileSize();
        vo.status = entity.getStatus();
        vo.chunkCount = entity.getChunkCount();
        vo.errorMessage = entity.getErrorMessage();
        vo.createdAt = entity.getCreatedAt();
        vo.updatedAt = entity.getUpdatedAt();
        return vo;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getKnowledgeBaseId() { return knowledgeBaseId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public Long getFileSize() { return fileSize; }
    public String getStatus() { return status; }
    public Integer getChunkCount() { return chunkCount; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
