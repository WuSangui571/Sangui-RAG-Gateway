package com.sangui.raggateway.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingTaskServiceTest {

    @Mock
    private DocumentProcessingTaskMapper taskMapper;

    private DocumentProcessingTaskService taskService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(new NoopTransactionManager());
        taskService = new DocumentProcessingTaskService(taskMapper, transactionTemplate, new DocumentProcessingProperties());
    }

    @Test
    void shouldCreateTaskWithPendingStatus() {
        doAnswer(inv -> {
            DocumentProcessingTaskEntity entity = inv.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(taskMapper).insert(any(DocumentProcessingTaskEntity.class));

        DocumentProcessingTaskEntity task = taskService.createTask(100L, 1L, 10L, 3);

        assertThat(task.getStatus()).isEqualTo(DocumentProcessingTaskStatus.PENDING.name());
        assertThat(task.getAttemptCount()).isEqualTo(0);
        assertThat(task.getMaxAttempts()).isEqualTo(3);
        assertThat(task.getUserId()).isEqualTo(100L);
        assertThat(task.getKnowledgeBaseId()).isEqualTo(1L);
        assertThat(task.getDocumentId()).isEqualTo(10L);
    }

    @Test
    void shouldFindTaskByDocumentId() {
        DocumentProcessingTaskEntity task = createTask(20L, 10L, DocumentProcessingTaskStatus.PENDING.name());
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        DocumentProcessingTaskEntity result = taskService.findByDocumentId(10L);

        assertThat(result).isNotNull();
        assertThat(result.getDocumentId()).isEqualTo(10L);
    }

    @Test
    void shouldReturnNullWhenNoTaskFound() {
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        DocumentProcessingTaskEntity result = taskService.findByDocumentId(999L);

        assertThat(result).isNull();
    }

    @Test
    void shouldDetectProcessingTask() {
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        boolean hasProcessing = taskService.hasProcessingTask(1L);

        assertThat(hasProcessing).isTrue();
    }

    @Test
    void shouldNotDetectProcessingTaskWhenNone() {
        when(taskMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        boolean hasProcessing = taskService.hasProcessingTask(1L);

        assertThat(hasProcessing).isFalse();
    }

    @Test
    void shouldFindPendingOrRetryableTasks() {
        DocumentProcessingTaskEntity task = createTask(20L, 10L, DocumentProcessingTaskStatus.PENDING.name());
        when(taskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(task));

        List<DocumentProcessingTaskEntity> result = taskService.findPendingOrRetryableByKbId(1L);

        assertThat(result).hasSize(1);
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

    static class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
