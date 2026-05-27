package com.sangui.raggateway.app;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Profile("!test")
public class AppService {

    private final AppMapper appMapper;

    public AppService(AppMapper appMapper) {
        this.appMapper = appMapper;
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
}
