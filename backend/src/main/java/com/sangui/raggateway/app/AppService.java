package com.sangui.raggateway.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseService;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("!test")
public class AppService {

    private final AppMapper appMapper;
    private final ModelConfigService modelConfigService;
    private final KnowledgeBaseService knowledgeBaseService;

    public AppService(AppMapper appMapper, ModelConfigService modelConfigService, KnowledgeBaseService knowledgeBaseService) {
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Transactional
    public AppEntity create(String name, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be a positive long");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }

        AppEntity app = new AppEntity();
        app.setName(name.trim());
        app.setUserId(userId);
        app.setStatus(AppStatus.ENABLED.name());
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.insert(app);
        return app;
    }

    public AppEntity findById(Long id) {
        return appMapper.selectById(id);
    }

    public boolean isEnabled(AppEntity app) {
        return app != null && AppStatus.ENABLED.name().equals(app.getStatus());
    }

    public ModelConfigEntity resolveDefaultModelConfig(AppEntity app) {
        if (app == null || app.getDefaultModelConfigId() == null) {
            return null;
        }
        return modelConfigService.findEnabledByIdAndUserId(app.getDefaultModelConfigId(), app.getUserId());
    }

    public AppEntity findByIdAndUserId(Long id, Long userId) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppEntity::getId, id);
        wrapper.eq(AppEntity::getUserId, userId);
        return appMapper.selectOne(wrapper);
    }

    public List<AppEntity> listByUserId(Long userId, String status) {
        LambdaQueryWrapper<AppEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppEntity::getUserId, userId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(AppEntity::getStatus, status.toUpperCase());
        }
        wrapper.orderByDesc(AppEntity::getCreatedAt);
        return appMapper.selectList(wrapper);
    }

    @Transactional
    public AppEntity bindDefaultModelConfig(Long appId, Long modelConfigId, Long userId) {
        AppEntity app = findById(appId);
        if (app == null || !app.getUserId().equals(userId)) {
            return null;
        }

        ModelConfigEntity modelConfig = modelConfigService.findEnabledByIdAndUserId(modelConfigId, userId);
        if (modelConfig == null) {
            return null;
        }

        app.setDefaultModelConfigId(modelConfigId);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    @Transactional
    public AppEntity bindDefaultKnowledgeBase(Long appId, Long knowledgeBaseId, Long userId) {
        AppEntity app = findById(appId);
        if (app == null || !app.getUserId().equals(userId)) {
            return null;
        }

        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(knowledgeBaseId, userId);
        if (kb == null) {
            return null;
        }

        if (!KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            return null;
        }

        app.setDefaultKnowledgeBaseId(knowledgeBaseId);
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);
        return app;
    }

    public KnowledgeBaseEntity resolveDefaultKnowledgeBase(AppEntity app) {
        if (app == null || app.getDefaultKnowledgeBaseId() == null) {
            return null;
        }
        KnowledgeBaseEntity kb = knowledgeBaseService.findByIdAndUserId(
                app.getDefaultKnowledgeBaseId(), app.getUserId());
        if (kb == null || !KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            return null;
        }
        return kb;
    }
}
