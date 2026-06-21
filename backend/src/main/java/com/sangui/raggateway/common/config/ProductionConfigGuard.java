package com.sangui.raggateway.common.config;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(ProductionGuardProperties.class)
public class ProductionConfigGuard implements InitializingBean {

    private static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");
    private static final Set<String> FORBIDDEN_COMBO_PROFILES = Set.of("dev", "test");
    private static final String TEST_PROFILE = "test";

    private static final String WEAK_LOCAL_SECRET = "local-dev-change-me";
    private static final String DOCUMENTED_SECRET_PLACEHOLDER = "<set-a-strong-32-char-secret>";
    private static final int MIN_SECRET_LENGTH = 32;

    private static final String DEFAULT_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/sangui_rag_gateway";
    private static final String DEFAULT_DATASOURCE_USERNAME = "sangui";
    private static final String DEFAULT_DATASOURCE_PASSWORD = "sangui_password";

    private static final Set<String> LOCAL_REDIS_HOSTS = Set.of("localhost", "127.0.0.1");
    private static final String DEFAULT_REDIS_PORT = "6379";

    private static final String LOCAL_STORAGE_TYPE = "local";
    private static final String OBJECT_STORAGE_TYPE = "object";
    private static final Set<String> KNOWN_STORAGE_TYPES = Set.of(LOCAL_STORAGE_TYPE, OBJECT_STORAGE_TYPE);

    private final Environment environment;
    private final ProductionGuardProperties guardProperties;

    public ProductionConfigGuard(Environment environment, ProductionGuardProperties guardProperties) {
        this.environment = environment;
        this.guardProperties = guardProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (isProductionProfile()) {
            validateProfileCombinations();
        }
        if (isTestProfile()) {
            return;
        }
        validateSecretKey();
        if (isProductionProfile()) {
            validateDataSource();
            validateRedis();
            validateFileStorage();
            validateOutputCapture();
        }
    }

    private boolean isProductionProfile() {
        return activeProfiles().stream().anyMatch(PRODUCTION_PROFILES::contains);
    }

    private boolean isTestProfile() {
        return activeProfiles().contains(TEST_PROFILE);
    }

    private void validateProfileCombinations() {
        Set<String> activeProfiles = activeProfiles();
        boolean hasProduction = activeProfiles.stream().anyMatch(PRODUCTION_PROFILES::contains);
        boolean hasForbidden = activeProfiles.stream().anyMatch(FORBIDDEN_COMBO_PROFILES::contains);
        if (hasProduction && hasForbidden) {
            throw new IllegalStateException(
                    "Production profile (prod/production) must not be combined with dev or test profile");
        }
    }

    private Set<String> activeProfiles() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(profile -> !profile.isBlank())
                .collect(Collectors.toSet());
    }

    private void validateSecretKey() {
        String secretKey = environment.getProperty("rag.gateway.secret-key", "");
        boolean isProduction = isProductionProfile();

        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("rag.gateway.secret-key must not be blank");
        }

        if (DOCUMENTED_SECRET_PLACEHOLDER.equals(secretKey)) {
            throw new IllegalStateException(
                    "rag.gateway.secret-key must be replaced with a real secret before startup");
        }

        if (isProduction) {
            if (WEAK_LOCAL_SECRET.equals(secretKey)) {
                throw new IllegalStateException("rag.gateway.secret-key must not be a known weak placeholder value");
            }
            if (secretKey.length() < MIN_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "rag.gateway.secret-key must be at least " + MIN_SECRET_LENGTH + " characters in production");
            }
        } else {
            if (WEAK_LOCAL_SECRET.equals(secretKey) && !guardProperties.isAllowWeakLocalSecret()) {
                throw new IllegalStateException(
                        "rag.gateway.secret-key is set to a known weak placeholder. "
                        + "Set rag.production-guard.allow-weak-local-secret=true to acknowledge this for local development only, "
                        + "or set RAG_GATEWAY_SECRET_KEY to a strong secret of at least " + MIN_SECRET_LENGTH + " characters.");
            }
        }
    }

    private void validateDataSource() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (DEFAULT_DATASOURCE_URL.equals(url)) {
            throw new IllegalStateException("spring.datasource.url must not use the local default value in production");
        }

        String username = environment.getProperty("spring.datasource.username", "");
        if (DEFAULT_DATASOURCE_USERNAME.equals(username)) {
            throw new IllegalStateException(
                    "spring.datasource.username must not use the local default value in production");
        }

        String password = environment.getProperty("spring.datasource.password", "");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("spring.datasource.password must not be blank in production");
        }
        if (DEFAULT_DATASOURCE_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "spring.datasource.password must not use the local default value in production");
        }
    }

    private void validateRedis() {
        String host = environment.getProperty("spring.data.redis.host", "");
        String port = environment.getProperty("spring.data.redis.port", DEFAULT_REDIS_PORT);
        if (host != null && LOCAL_REDIS_HOSTS.contains(host.trim().toLowerCase())
                && DEFAULT_REDIS_PORT.equals(port.trim())) {
            throw new IllegalStateException(
                    "Redis must not use a local default host and port (localhost/127.0.0.1:6379) in production");
        }
    }

    private void validateFileStorage() {
        String storageType = environment.getProperty("rag.gateway.storage.type", "");
        String normalized = storageType.trim().toLowerCase();
        if (!KNOWN_STORAGE_TYPES.contains(normalized)) {
            throw new IllegalStateException(
                    "Unknown storage type: " + storageType + ". Allowed values: local, object");
        }
        if (LOCAL_STORAGE_TYPE.equals(normalized)
                && !guardProperties.isAllowLocalFileStorage()) {
            throw new IllegalStateException(
                    "Local file storage (rag.gateway.storage.type=local) requires explicit acknowledgement "
                            + "via rag.production-guard.allow-local-file-storage=true in production");
        }
    }

    private void validateOutputCapture() {
        boolean outputCaptureEnabled = Boolean.parseBoolean(
                environment.getProperty("rag.request-log.output-capture.enabled", "false"));
        if (outputCaptureEnabled && !guardProperties.isAllowOutputCapture()) {
            throw new IllegalStateException(
                    "Output capture (rag.request-log.output-capture.enabled=true) requires explicit acknowledgement "
                            + "via rag.production-guard.allow-output-capture=true in production");
        }
    }
}
