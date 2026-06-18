package com.sangui.raggateway.document;

import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingWorkerTest {

    @Mock
    private DocumentProcessingTaskService taskService;
    @Mock
    private DocumentService documentService;

    private DocumentProcessingProperties properties;
    private DocumentProcessingWorker worker;

    @BeforeEach
    void setUp() {
        properties = new DocumentProcessingProperties();
        properties.setWorkerId("test-worker");
        properties.setMaxAttempts(3);
        properties.setStaleProcessingTimeoutMs(900000);
        worker = new DocumentProcessingWorker(taskService, documentService, properties);
    }

    @Test
    void shouldProcessClaimedTask() {
        DocumentProcessingTaskEntity task = createTask(20L, 10L, DocumentProcessingTaskStatus.PROCESSING.name());
        when(taskService.claimNextEligible("test-worker")).thenReturn(task);

        worker.processNext();

        verify(documentService).processDocument(task);
    }

    @Test
    void shouldSkipWhenNoTaskClaimed() {
        when(taskService.claimNextEligible("test-worker")).thenReturn(null);

        worker.processNext();

        verify(documentService, never()).processDocument(any());
    }

    @Test
    void shouldHandleProcessingException() {
        DocumentProcessingTaskEntity task = createTask(20L, 10L, DocumentProcessingTaskStatus.PROCESSING.name());
        when(taskService.claimNextEligible("test-worker")).thenReturn(task);
        doThrow(new RuntimeException("sensitive path C:\\secret\\doc.md")).when(documentService).processDocument(task);

        DocumentProcessingTaskEntity currentTask = createTask(20L, 10L, DocumentProcessingTaskStatus.PROCESSING.name());
        currentTask.setAttemptCount(1);
        when(taskService.findById(20L)).thenReturn(currentTask);

        worker.processNext();

        verify(taskService).transitionToRetryable(20L, "Unexpected processing error: RuntimeException");
    }

    @Test
    void shouldRecoverStaleProcessing() {
        worker.recoverStaleProcessing();

        verify(taskService).recoverStaleProcessing(properties.getStaleProcessingTimeoutMs(), properties.getMaxAttempts());
    }

    private DocumentProcessingTaskEntity createTask(Long id, Long documentId, String status) {
        DocumentProcessingTaskEntity task = new DocumentProcessingTaskEntity();
        task.setId(id);
        task.setUserId(100L);
        task.setKnowledgeBaseId(1L);
        task.setDocumentId(documentId);
        task.setStatus(status);
        task.setAttemptCount(0);
        task.setMaxAttempts(3);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        return task;
    }
}
