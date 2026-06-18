package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!test")
public class DocumentProcessingTaskService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingTaskService.class);

    private final DocumentProcessingTaskMapper taskMapper;
    private final TransactionTemplate transactionTemplate;
    private final DocumentProcessingProperties properties;

    public DocumentProcessingTaskService(DocumentProcessingTaskMapper taskMapper,
                                          TransactionTemplate transactionTemplate,
                                          DocumentProcessingProperties properties) {
        this.taskMapper = taskMapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public DocumentProcessingTaskEntity createTask(Long userId, Long knowledgeBaseId,
                                                    Long documentId, int maxAttempts) {
        DocumentProcessingTaskEntity task = new DocumentProcessingTaskEntity();
        task.setUserId(userId);
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setDocumentId(documentId);
        task.setStatus(DocumentProcessingTaskStatus.PENDING.name());
        task.setAttemptCount(0);
        task.setMaxAttempts(maxAttempts);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("Processing task created: taskId={}, docId={}, kbId={}, status=PENDING",
                task.getId(), documentId, knowledgeBaseId);
        return task;
    }

    public DocumentProcessingTaskEntity findByDocumentId(Long documentId) {
        LambdaQueryWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getDocumentId, documentId);
        return taskMapper.selectOne(wrapper);
    }

    public DocumentProcessingTaskEntity findById(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public boolean hasProcessingTask(Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.eq(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.PROCESSING.name());
        return taskMapper.selectCount(wrapper) > 0;
    }

    public boolean hasProcessingTaskForDocument(Long documentId) {
        DocumentProcessingTaskEntity task = findByDocumentId(documentId);
        return task != null && DocumentProcessingTaskStatus.PROCESSING.name().equals(task.getStatus());
    }

    public DocumentProcessingTaskEntity claimNextEligible(String workerId) {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now();

            LambdaQueryWrapper<DocumentProcessingTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.and(w -> w
                    .eq(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.PENDING.name())
                    .or()
                    .eq(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.RETRYABLE.name())
            );
            queryWrapper.and(w -> w
                    .isNull(DocumentProcessingTaskEntity::getNextAttemptAt)
                    .or()
                    .le(DocumentProcessingTaskEntity::getNextAttemptAt, now)
            );
            queryWrapper.orderByAsc(DocumentProcessingTaskEntity::getCreatedAt);
            queryWrapper.last("LIMIT 1 FOR UPDATE SKIP LOCKED");

            DocumentProcessingTaskEntity task = taskMapper.selectOne(queryWrapper);
            if (task == null) {
                return null;
            }

            int nextAttempt = (task.getAttemptCount() == null ? 0 : task.getAttemptCount()) + 1;
            LambdaUpdateWrapper<DocumentProcessingTaskEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(DocumentProcessingTaskEntity::getId, task.getId());
            updateWrapper.eq(DocumentProcessingTaskEntity::getStatus, task.getStatus());
            updateWrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.PROCESSING.name());
            updateWrapper.set(DocumentProcessingTaskEntity::getAttemptCount, nextAttempt);
            updateWrapper.set(DocumentProcessingTaskEntity::getLockedBy, workerId);
            updateWrapper.set(DocumentProcessingTaskEntity::getLockedAt, now);
            updateWrapper.set(DocumentProcessingTaskEntity::getStartedAt, now);
            updateWrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, now);

            int rows = taskMapper.update(null, updateWrapper);
            if (rows == 0) {
                return null;
            }

            task.setStatus(DocumentProcessingTaskStatus.PROCESSING.name());
            task.setAttemptCount(nextAttempt);
            task.setLockedBy(workerId);
            task.setLockedAt(now);
            task.setStartedAt(now);
            log.info("Task claimed: taskId={}, docId={}, workerId={}, attempt={}",
                    task.getId(), task.getDocumentId(), workerId, nextAttempt);
            return task;
        });
    }

    public void transitionToSucceeded(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getId, taskId);
        wrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.SUCCEEDED.name());
        wrapper.set(DocumentProcessingTaskEntity::getFinishedAt, now);
        wrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, now);
        taskMapper.update(null, wrapper);
        log.info("Task succeeded: taskId={}", taskId);
    }

    public void transitionToRetryable(Long taskId, String errorMessage) {
        DocumentProcessingTaskEntity task = findById(taskId);
        if (task == null) return;

        LocalDateTime now = LocalDateTime.now();
        String truncated = truncateSafe(errorMessage);

        LambdaUpdateWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getId, taskId);
        wrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.RETRYABLE.name());
        wrapper.set(DocumentProcessingTaskEntity::getLastErrorMessage, truncated);
        wrapper.set(DocumentProcessingTaskEntity::getLockedBy, null);
        wrapper.set(DocumentProcessingTaskEntity::getLockedAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getNextAttemptAt, now.plus(retryBackoff()));
        wrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, now);
        taskMapper.update(null, wrapper);
        log.info("Task retryable: taskId={}, attempt={}", taskId, task.getAttemptCount());
    }

    public void transitionToFailed(Long taskId, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        String truncated = truncateSafe(errorMessage);

        LambdaUpdateWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getId, taskId);
        wrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.FAILED.name());
        wrapper.set(DocumentProcessingTaskEntity::getLastErrorMessage, truncated);
        wrapper.set(DocumentProcessingTaskEntity::getLockedBy, null);
        wrapper.set(DocumentProcessingTaskEntity::getLockedAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getNextAttemptAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getFinishedAt, now);
        wrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, now);
        taskMapper.update(null, wrapper);
        log.info("Task failed: taskId={}", taskId);
    }

    public void cancelTask(Long taskId) {
        LambdaUpdateWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getId, taskId);
        wrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.CANCELED.name());
        wrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, LocalDateTime.now());
        taskMapper.update(null, wrapper);
        log.info("Task canceled: taskId={}", taskId);
    }

    public void resetForRetry(Long taskId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getId, taskId);
        wrapper.set(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.PENDING.name());
        wrapper.set(DocumentProcessingTaskEntity::getAttemptCount, 0);
        wrapper.set(DocumentProcessingTaskEntity::getLastErrorMessage, null);
        wrapper.set(DocumentProcessingTaskEntity::getNextAttemptAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getLockedBy, null);
        wrapper.set(DocumentProcessingTaskEntity::getLockedAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getStartedAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getFinishedAt, null);
        wrapper.set(DocumentProcessingTaskEntity::getUpdatedAt, now);
        taskMapper.update(null, wrapper);
        log.info("Task reset for retry: taskId={}", taskId);
    }

    public List<DocumentProcessingTaskEntity> findPendingOrRetryableByKbId(Long knowledgeBaseId) {
        LambdaQueryWrapper<DocumentProcessingTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DocumentProcessingTaskEntity::getKnowledgeBaseId, knowledgeBaseId);
        wrapper.in(DocumentProcessingTaskEntity::getStatus,
                DocumentProcessingTaskStatus.PENDING.name(),
                DocumentProcessingTaskStatus.RETRYABLE.name());
        return taskMapper.selectList(wrapper);
    }

    public void cancelTasks(List<DocumentProcessingTaskEntity> tasks) {
        for (DocumentProcessingTaskEntity task : tasks) {
            cancelTask(task.getId());
        }
    }

    public void recoverStaleProcessing(long staleTimeoutMs, int defaultMaxAttempts) {
        LocalDateTime cutoff = LocalDateTime.now().minus(Duration.ofMillis(staleTimeoutMs));

        LambdaQueryWrapper<DocumentProcessingTaskEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DocumentProcessingTaskEntity::getStatus, DocumentProcessingTaskStatus.PROCESSING.name());
        queryWrapper.le(DocumentProcessingTaskEntity::getLockedAt, cutoff);

        List<DocumentProcessingTaskEntity> staleTasks = taskMapper.selectList(queryWrapper);
        for (DocumentProcessingTaskEntity task : staleTasks) {
            int currentAttempts = task.getAttemptCount() != null ? task.getAttemptCount() : 0;
            int maxAttempts = task.getMaxAttempts() != null ? task.getMaxAttempts() : defaultMaxAttempts;
            if (currentAttempts < maxAttempts) {
                log.warn("Recovering stale processing task: taskId={}, docId={}, lockedBy={}, lockedAt={}",
                        task.getId(), task.getDocumentId(), task.getLockedBy(), task.getLockedAt());
                transitionToRetryable(task.getId(), "Worker stalled or restarted");
            } else {
                log.warn("Failing exhausted stale task: taskId={}, docId={}",
                        task.getId(), task.getDocumentId());
                transitionToFailed(task.getId(), "Worker stalled, retries exhausted");
            }
        }
    }

    private Duration retryBackoff() {
        return Duration.ofMillis(properties.getRetryBackoffMs());
    }

    private String truncateSafe(String message) {
        if (message == null) return null;
        if (message.length() <= 500) return message;
        return message.substring(0, 500);
    }
}
