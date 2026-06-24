package com.sangui.raggateway.auth;

import com.sangui.raggateway.common.security.PasswordHasher;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserMapper;
import com.sangui.raggateway.user.UserService;
import com.sangui.raggateway.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;

@Service
@Profile("!test")
public class DefaultAdminBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminBootstrapService.class);
    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final String WEAK_LOCAL_PLACEHOLDER = "local-dev-change-me";
    private static final String DEV_DEFAULT_PASSWORD = "admin123";
    private static final int MIN_PRODUCTION_PASSWORD_LENGTH = 12;

    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordHasher passwordHasher;
    private final Environment environment;

    @Value("${rag.admin-auth.default-admin-username:}")
    private String defaultAdminUsername;

    @Value("${rag.admin-auth.default-admin-password:}")
    private String defaultAdminPassword;

    @Value("${rag.admin-auth.allow-default-admin:false}")
    private boolean allowDefaultAdmin;

    public DefaultAdminBootstrapService(UserMapper userMapper, UserService userService,
                                        PasswordHasher passwordHasher, Environment environment) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.passwordHasher = passwordHasher;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        long userCount = userService.countUsers();
        if (userCount > 0) {
            log.info("Default admin bootstrap skipped: sys_user table is non-empty ({} user(s))", userCount);
            return;
        }

        boolean isProduction = isProductionProfile();

        if (isProduction && !allowDefaultAdmin) {
            log.info("Default admin bootstrap skipped: production profile active and allow-default-admin is not enabled");
            return;
        }

        validateConfig(isProduction);

        String trimmedUsername = defaultAdminUsername.trim();
        String hash = passwordHasher.hash(defaultAdminPassword);

        UserEntity admin = new UserEntity();
        admin.setUsername(trimmedUsername);
        admin.setPasswordHash(hash);
        admin.setStatus(UserStatus.ACTIVE.name());
        LocalDateTime now = LocalDateTime.now();
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);

        userMapper.insert(admin);
        log.info("Default admin user '{}' created successfully", trimmedUsername);
    }

    private void validateConfig(boolean isProduction) {
        if (defaultAdminUsername == null || defaultAdminUsername.isBlank()) {
            throw new IllegalStateException(
                    "rag.admin-auth.default-admin-username must not be blank when default admin bootstrap is active");
        }

        if (defaultAdminPassword == null || defaultAdminPassword.isBlank()) {
            throw new IllegalStateException(
                    "rag.admin-auth.default-admin-password must not be blank when default admin bootstrap is active");
        }

        String normalizedPassword = defaultAdminPassword.trim();
        if (WEAK_LOCAL_PLACEHOLDER.equals(normalizedPassword)) {
            throw new IllegalStateException(
                    "rag.admin-auth.default-admin-password is set to a known weak placeholder. "
                    + "Set it to a strong password before startup.");
        }

        if (isProduction && isUnsafeProductionPassword(normalizedPassword)) {
            throw new IllegalStateException(
                    "rag.admin-auth.default-admin-password must be a strong explicit production password");
        }
    }

    private boolean isUnsafeProductionPassword(String normalizedPassword) {
        return DEV_DEFAULT_PASSWORD.equals(normalizedPassword)
                || normalizedPassword.length() < MIN_PRODUCTION_PASSWORD_LENGTH;
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(profile -> !profile.isBlank())
                .anyMatch(PRODUCTION_PROFILES::contains);
    }
}
