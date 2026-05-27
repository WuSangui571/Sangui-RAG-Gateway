package com.sangui.raggateway.app;

import com.sangui.raggateway.model.ModelConfigEntity;
import com.sangui.raggateway.model.ModelConfigService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Profile("!test")
public class AppService {

    private final AppMapper appMapper;
    private final ModelConfigService modelConfigService;

    public AppService(AppMapper appMapper, ModelConfigService modelConfigService) {
        this.appMapper = appMapper;
        this.modelConfigService = modelConfigService;
    }

    @Transactional
    public AppEntity create(String name, Long userId) {
        AppEntity app = new AppEntity();
        app.setName(name);
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
}
