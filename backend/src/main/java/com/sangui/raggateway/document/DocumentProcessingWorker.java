package com.sangui.raggateway.document;

import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class DocumentProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);

    private final DocumentProcessingTaskService taskService;
    private final DocumentService documentService;
    private final DocumentProcessingProperties properties;

    public DocumentProcessingWorker(DocumentProcessingTaskService taskService,
                                     DocumentService documentService,
                                     DocumentProcessingProperties properties) {
        this.taskService = taskService;
        this.documentService = documentService;
        this.properties = properties;
    }

    public void processNext() {
        DocumentProcessingTaskEntity task = taskService.claimNextEligible(properties.getWorkerId());
        if (task == null) {
            return;
        }

        log.info("Processing task: taskId={}, docId={}, kbId={}, attempt={}",
                task.getId(), task.getDocumentId(), task.getKnowledgeBaseId(), task.getAttemptCount());

        try {
            documentService.processDocument(task);
        } catch (Exception e) {
            log.error("Unexpected error processing task: taskId={}, docId={}, errorClass={}",
                    task.getId(), task.getDocumentId(), e.getClass().getSimpleName());
            DocumentProcessingTaskEntity current = taskService.findById(task.getId());
            if (current != null && DocumentProcessingTaskStatus.PROCESSING.name().equals(current.getStatus())) {
                int currentAttempt = current.getAttemptCount() != null ? current.getAttemptCount() : 0;
                int maxAttempts = current.getMaxAttempts() != null ? current.getMaxAttempts() : properties.getMaxAttempts();
                String errorMessage = "Unexpected processing error: " + e.getClass().getSimpleName();
                if (currentAttempt < maxAttempts) {
                    taskService.transitionToRetryable(task.getId(), errorMessage);
                } else {
                    taskService.transitionToFailed(task.getId(), errorMessage);
                }
            }
        }
    }

    public void recoverStaleProcessing() {
        taskService.recoverStaleProcessing(
                properties.getStaleProcessingTimeoutMs(),
                properties.getMaxAttempts());
    }

}
