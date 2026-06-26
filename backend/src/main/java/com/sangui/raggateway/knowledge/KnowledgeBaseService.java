package com.sangui.raggateway.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppMapper;
import com.sangui.raggateway.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!test")
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AppMapper appMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, AppMapper appMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.appMapper = appMapper;
    }

    @Transactional
    public KnowledgeBaseEntity create(Long userId, String name, String embeddingModel, Integer embeddingDimension) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "name is required");
        }
        if (embeddingModel == null || embeddingModel.isBlank()) {
            throw new BusinessException("INVALID_REQUEST", "embeddingModel is required");
        }
        if (embeddingDimension == null || embeddingDimension <= 0) {
            throw new BusinessException("INVALID_REQUEST", "embeddingDimension must be positive");
        }

        String trimmedName = name.trim();
        if (existsByUserIdAndName(userId, trimmedName)) {
            throw new BusinessException("INVALID_REQUEST", "knowledge base name already exists");
        }

        KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
        entity.setUserId(userId);
        entity.setName(trimmedName);
        entity.setEmbeddingModel(embeddingModel.trim());
        entity.setEmbeddingDimension(embeddingDimension);
        entity.setStatus(KnowledgeBaseStatus.EMPTY.name());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.insert(entity);
        log.info("Knowledge base created: id={}, userId={}, name={}", entity.getId(), userId, entity.getName());
        return entity;
    }

    public KnowledgeBaseEntity findById(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    public KnowledgeBaseEntity findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseEntity::getId, id);
        wrapper.eq(KnowledgeBaseEntity::getUserId, userId);
        return knowledgeBaseMapper.selectOne(wrapper);
    }

    private boolean existsByUserIdAndName(Long userId, String name) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseEntity::getUserId, userId);
        wrapper.eq(KnowledgeBaseEntity::getName, name);
        return knowledgeBaseMapper.selectOne(wrapper) != null;
    }

    public List<KnowledgeBaseEntity> listByUserId(Long userId, String status) {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBaseEntity::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(KnowledgeBaseEntity::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(KnowledgeBaseEntity::getCreatedAt);
        return knowledgeBaseMapper.selectList(wrapper);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        KnowledgeBaseEntity entity = knowledgeBaseMapper.selectById(id);
        if (entity != null) {
            entity.setStatus(status);
            entity.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(entity);
            log.info("Knowledge base status updated: id={}, status={}", id, status);
        }
    }

    public void checkNotReferencedByAnyApp(Long kbId, Long userId) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppEntity::getDefaultKnowledgeBaseId, kbId);
        wrapper.eq(AppEntity::getUserId, userId);
        Long count = appMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new com.sangui.raggateway.common.exception.BusinessException(
                    "KNOWLEDGE_BASE_IN_USE",
                    "Knowledge base is referenced by " + count + " app(s) and cannot be deleted",
                    org.springframework.http.HttpStatus.CONFLICT);
        }
    }

    @Transactional
    public void deleteKbRow(Long id) {
        knowledgeBaseMapper.deleteById(id);
        log.info("Knowledge base row deleted: id={}", id);
    }
}
