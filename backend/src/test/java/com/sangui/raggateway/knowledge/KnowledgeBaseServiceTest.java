package com.sangui.raggateway.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.app.AppMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock
    private AppMapper appMapper;

    @Captor
    private ArgumentCaptor<KnowledgeBaseEntity> entityCaptor;

    private KnowledgeBaseService knowledgeBaseService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService(knowledgeBaseMapper, appMapper);
    }

    @Test
    void shouldCreateKnowledgeBaseWithEmptyStatus() {
        knowledgeBaseService.create(100L, "Product Docs", "text-embedding-3-small", 1536);

        verify(knowledgeBaseMapper).insert(entityCaptor.capture());
        KnowledgeBaseEntity persisted = entityCaptor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(100L);
        assertThat(persisted.getName()).isEqualTo("Product Docs");
        assertThat(persisted.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(persisted.getEmbeddingDimension()).isEqualTo(1536);
        assertThat(persisted.getStatus()).isEqualTo(KnowledgeBaseStatus.EMPTY.name());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> knowledgeBaseService.create(100L, "  ", "text-embedding-3-small", 1536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
        verifyNoInteractions(knowledgeBaseMapper);
    }

    @Test
    void shouldRejectBlankEmbeddingModel() {
        assertThatThrownBy(() -> knowledgeBaseService.create(100L, "Product Docs", "", 1536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingModel is required");
        verifyNoInteractions(knowledgeBaseMapper);
    }

    @Test
    void shouldRejectNonPositiveDimension() {
        assertThatThrownBy(() -> knowledgeBaseService.create(100L, "Product Docs", "text-embedding-3-small", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension must be positive");
        verifyNoInteractions(knowledgeBaseMapper);
    }

    @Test
    void shouldRejectNullDimension() {
        assertThatThrownBy(() -> knowledgeBaseService.create(100L, "Product Docs", "text-embedding-3-small", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embeddingDimension must be positive");
        verifyNoInteractions(knowledgeBaseMapper);
    }

    @Test
    void shouldRejectDuplicateNameForSameUser() {
        KnowledgeBaseEntity existing = new KnowledgeBaseEntity();
        existing.setId(1L);
        existing.setUserId(100L);
        existing.setName("Product Docs");
        when(knowledgeBaseMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> knowledgeBaseService.create(100L, " Product Docs ", "text-embedding-3-small", 1536))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name already exists");
        verify(knowledgeBaseMapper, never()).insert(any(KnowledgeBaseEntity.class));
    }

    @Test
    void shouldListByUserIdWithTenantScope() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setUserId(100L);
        entity.setName("Product Docs");
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());

        when(knowledgeBaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<KnowledgeBaseEntity> result = knowledgeBaseService.listByUserId(100L, null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldListByUserIdAndStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setUserId(100L);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());

        when(knowledgeBaseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(entity));

        List<KnowledgeBaseEntity> result = knowledgeBaseService.listByUserId(100L, "EMPTY");
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindByIdAndUserIdWithTenantScope() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setUserId(100L);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(entity);

        KnowledgeBaseEntity result = knowledgeBaseService.findByIdAndUserId(1L, 100L);
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(100L);
    }

    @Test
    void shouldReturnNullWhenTenantScopeNotFound() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(null);
        KnowledgeBaseEntity result = knowledgeBaseService.findByIdAndUserId(1L, 200L);
        assertThat(result).isNull();
    }

    @Test
    void shouldUpdateStatus() {
        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setId(1L);
        entity.setUserId(100L);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());
        when(knowledgeBaseMapper.selectById(1L)).thenReturn(entity);

        knowledgeBaseService.updateStatus(1L, KnowledgeBaseStatus.READY.name());

        verify(knowledgeBaseMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getStatus()).isEqualTo(KnowledgeBaseStatus.READY.name());
    }

    @Test
    void shouldAllowDeleteWhenKnowledgeBaseIsNotReferencedByApps() {
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        knowledgeBaseService.checkNotReferencedByAnyApp(1L, 100L);

        verify(appMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    void shouldRejectDeleteWhenKnowledgeBaseIsReferencedByApps() {
        when(appMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> knowledgeBaseService.checkNotReferencedByAnyApp(1L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo("KNOWLEDGE_BASE_IN_USE");
                    assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("referenced by 2 app");
                });
    }

    @Test
    void shouldDeleteKnowledgeBaseRow() {
        knowledgeBaseService.deleteKbRow(1L);

        verify(knowledgeBaseMapper).deleteById(1L);
    }
}
