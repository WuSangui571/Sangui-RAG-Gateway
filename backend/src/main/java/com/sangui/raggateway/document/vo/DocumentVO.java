package com.sangui.raggateway.document.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sangui.raggateway.document.DocumentEntity;
import com.sangui.raggateway.document.DocumentProcessingTaskEntity;

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

    @JsonProperty("processing_task_id")
    private Long processingTaskId;
    @JsonProperty("processing_task_status")
    private String processingTaskStatus;
    @JsonProperty("processing_attempt_count")
    private Integer processingAttemptCount;
    @JsonProperty("processing_next_attempt_at")
    private LocalDateTime processingNextAttemptAt;
    @JsonProperty("processing_started_at")
    private LocalDateTime processingStartedAt;
    @JsonProperty("processing_finished_at")
    private LocalDateTime processingFinishedAt;

    public static DocumentVO from(DocumentEntity entity) {
        return from(entity, null);
    }

    public static DocumentVO from(DocumentEntity entity, DocumentProcessingTaskEntity task) {
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

        if (task != null) {
            vo.processingTaskId = task.getId();
            vo.processingTaskStatus = task.getStatus();
            vo.processingAttemptCount = task.getAttemptCount();
            vo.processingNextAttemptAt = task.getNextAttemptAt();
            vo.processingStartedAt = task.getStartedAt();
            vo.processingFinishedAt = task.getFinishedAt();
        }

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
    public Long getProcessingTaskId() { return processingTaskId; }
    public String getProcessingTaskStatus() { return processingTaskStatus; }
    public Integer getProcessingAttemptCount() { return processingAttemptCount; }
    public LocalDateTime getProcessingNextAttemptAt() { return processingNextAttemptAt; }
    public LocalDateTime getProcessingStartedAt() { return processingStartedAt; }
    public LocalDateTime getProcessingFinishedAt() { return processingFinishedAt; }
}
