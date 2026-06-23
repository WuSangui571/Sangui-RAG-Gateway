package com.sangui.raggateway;

import com.sangui.raggateway.common.config.ProductionConfigGuard;
import com.sangui.raggateway.common.config.ProductionGuardProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigGuardTest {

    private static final String STRONG_SECRET = "prod-secret-at-least-32-bytes-long-for-hs256!!";
    private static final String LEGACY_LOCAL_PLACEHOLDER = "local-dev-hs256-secret-change-me-32chars";
    private static final String JWT_LOCAL_PLACEHOLDER = "local-dev-admin-jwt-secret-change-me-32chars";
    private static final String ENCRYPTION_LOCAL_PLACEHOLDER = "local-dev-aes-key-secret-change-me-32chars";
    private static final String DOCUMENTED_SECRET_PLACEHOLDER = "<set-a-strong-32-char-secret>";
    private static final String PROP_JWT = "rag.admin-auth.jwt-secret";
    private static final String PROP_ENC = "rag.gateway.encryption.secret-key";

    private static final String[] STRONG_JWT_AES = {
            PROP_JWT + "=" + STRONG_SECRET,
            PROP_ENC + "=" + STRONG_SECRET + "!different"
    };

    private static final String[] PROD_BASELINE = {
            PROP_JWT + "=" + STRONG_SECRET,
            PROP_ENC + "=" + STRONG_SECRET + "!!enc-different",
            "spring.datasource.url=jdbc:postgresql://prod-db:5432/sangui",
            "spring.datasource.username=prod_user",
            "spring.datasource.password=prod_password",
            "spring.data.redis.host=redis",
            "spring.data.redis.port=6379",
            "rag.gateway.storage.type=object",
            "rag.request-log.output-capture.enabled=false"
    };

    @Nested
    class NonProductionSecretKeyGuard {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(GuardTestConfig.class);

        @Test
        void testProfileSkipsGuard() {
            runner.withPropertyValues("spring.profiles.active=test").run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void devProfileWithStrongSecretsPasses() {
            runner.withPropertyValues("spring.profiles.active=dev")
                    .withPropertyValues(STRONG_JWT_AES)
                    .run(context -> assertThat(context).hasNotFailed());
        }

        @Test
        void devProfileWithNewLocalPlaceholdersPasses() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + JWT_LOCAL_PLACEHOLDER,
                    PROP_ENC + "=" + ENCRYPTION_LOCAL_PLACEHOLDER).run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void noProfileWithStrongSecretsPasses() {
            runner.withPropertyValues(STRONG_JWT_AES)
                    .run(context -> assertThat(context).hasNotFailed());
        }

        @Test
        void devProfileBlankJwtFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=",
                    PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("must not be blank");
            });
        }

        @Test
        void devProfileBlankEncryptionFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("must not be blank");
            });
        }

        @Test
        void devProfileShortJwtFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=tooshort",
                    PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("32");
            });
        }

        @Test
        void devProfileShortEncryptionFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=tooshort").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("32");
            });
        }

        @Test
        void devProfileWeakPlaceholderJwtFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=local-dev-change-me",
                    PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void devProfileWeakPlaceholderEncryptionFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=local-dev-change-me").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void devProfileDocumentedPlaceholderJwtFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + DOCUMENTED_SECRET_PLACEHOLDER,
                    PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("real secret");
            });
        }

        @Test
        void devProfileDocumentedPlaceholderEncryptionFails() {
            runner.withPropertyValues("spring.profiles.active=dev",
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=" + DOCUMENTED_SECRET_PLACEHOLDER).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("real secret");
            });
        }

        @Test
        void noProfileBlankJwtFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=", PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("must not be blank");
            });
        }

        @Test
        void noProfileBlankEncryptionFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET, PROP_ENC + "=").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("must not be blank");
            });
        }

        @Test
        void noProfileShortJwtFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=tooshort", PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("32");
            });
        }

        @Test
        void noProfileShortEncryptionFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET, PROP_ENC + "=tooshort").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("32");
            });
        }

        @Test
        void noProfileWeakPlaceholderJwtFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=local-dev-change-me",
                    PROP_ENC + "=" + STRONG_SECRET).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void noProfileWeakPlaceholderEncryptionFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=local-dev-change-me").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void noProfileWithWeakPlaceholderMessageDoesNotEchoSecret() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=local-dev-change-me").run(context -> {
                assertThat(context).hasFailed();
                String rootMessage = getRootCauseMessage(context.getStartupFailure());
                assertThat(rootMessage).doesNotContain("local-dev-change-me");
            });
        }

        @Test
        void noProfileWithDocumentedPlaceholderFails() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=" + DOCUMENTED_SECRET_PLACEHOLDER).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_ENC)
                        .hasMessageContaining("real secret");
            });
        }

        @Test
        void documentedPlaceholderMessageDoesNotEchoSecret() {
            runner.withPropertyValues(
                    PROP_JWT + "=" + STRONG_SECRET,
                    PROP_ENC + "=" + DOCUMENTED_SECRET_PLACEHOLDER).run(context -> {
                assertThat(context).hasFailed();
                String rootMessage = getRootCauseMessage(context.getStartupFailure());
                assertThat(rootMessage).doesNotContain(DOCUMENTED_SECRET_PLACEHOLDER);
            });
        }
    }

    @Nested
    class PositiveProdProfile {

        @Test
        void goodProdConfigStarts() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE);
            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void goodProdConfigWithLocalStorageAcknowledged() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "rag.gateway.storage.type=local",
                            "rag.production-guard.allow-local-file-storage=true");
            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void goodProdConfigWithOutputCaptureAcknowledged() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "rag.request-log.output-capture.enabled=true",
                            "rag.production-guard.allow-output-capture=true");
            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }
    }

    @Nested
    class NegativeSecretKey {

        private ApplicationContextRunner prodRunner() {
            return new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE);
        }

        @Test
        void failsWhenProductionProfileIsSetThroughEnvironment() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(PROP_JWT + "=local-dev-change-me");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause()
                        .hasMessageContaining(PROP_JWT)
                        .hasMessageContaining("local placeholder");
            });
        }

        @Test
        void failsWhenJwtBlank() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("blank");
                    });
        }

        @Test
        void failsWhenEncryptionBlank() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("blank");
                    });
        }

        @Test
        void failsWhenJwtIsWeakPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=local-dev-change-me")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenEncryptionIsWeakPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=local-dev-change-me")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenJwtIsLegacyLocalPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=" + LEGACY_LOCAL_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenEncryptionIsLegacyLocalPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=" + LEGACY_LOCAL_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenJwtUsesDevDefaultPlaceholderInProduction() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=" + JWT_LOCAL_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenEncryptionUsesDevDefaultPlaceholderInProduction() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=" + ENCRYPTION_LOCAL_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("local placeholder");
                    });
        }

        @Test
        void failsWhenJwtIsDocumentedPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=" + DOCUMENTED_SECRET_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("real secret");
                    });
        }

        @Test
        void failsWhenEncryptionIsDocumentedPlaceholder() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=" + DOCUMENTED_SECRET_PLACEHOLDER)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("real secret");
                    });
        }

        @Test
        void failsWhenJwtIsShort() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=short-secret")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_JWT)
                                .hasMessageContaining("32");
                    });
        }

        @Test
        void failsWhenEncryptionIsShort() {
            prodRunner()
                    .withPropertyValues(PROP_ENC + "=short-secret")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining(PROP_ENC)
                                .hasMessageContaining("32");
                    });
        }

        @Test
        void failsWhenSecretsAreEqual() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=" + STRONG_SECRET,
                            PROP_ENC + "=" + STRONG_SECRET)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).rootCause()
                                .hasMessageContaining("must not be equal");
                    });
        }

        @Test
        void failureMessageDoesNotEchoSecretValue() {
            prodRunner()
                    .withPropertyValues(PROP_JWT + "=my-insecure-key-that-is-short")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        Throwable failure = context.getStartupFailure();
                        String rootMessage = getRootCauseMessage(failure);
                        assertThat(rootMessage).doesNotContain("my-insecure-key-that-is-short");
                    });
        }
    }

    @Nested
    class NegativeDataSource {

        @Test
        void failsWhenDataSourceUrlIsLocalDefault() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "spring.datasource.url=jdbc:postgresql://localhost:5432/sangui_rag_gateway");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("spring.datasource.url");
            });
        }

        @Test
        void failsWhenDataSourceUsernameIsLocalDefault() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("spring.datasource.username=sangui");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("spring.datasource.username");
            });
        }

        @Test
        void failsWhenDataSourcePasswordIsBlank() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("spring.datasource.password=");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("spring.datasource.password")
                        .hasMessageContaining("blank");
            });
        }

        @Test
        void failsWhenDataSourcePasswordIsLocalDefault() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("spring.datasource.password=sangui_password");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("spring.datasource.password");
            });
        }

        @Test
        void passwordFailureMessageDoesNotEchoValue() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("spring.datasource.password=sangui_password");
            runner.run(context -> {
                assertThat(context).hasFailed();
                String rootMessage = getRootCauseMessage(context.getStartupFailure());
                assertThat(rootMessage).doesNotContain("sangui_password");
            });
        }
    }

    @Nested
    class NegativeRedis {

        @Test
        void failsWhenRedisIsLocalhostDefaultPort() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "spring.data.redis.host=localhost",
                            "spring.data.redis.port=6379");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("Redis");
            });
        }

        @Test
        void failsWhenRedisIs127001DefaultPort() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "spring.data.redis.host=127.0.0.1",
                            "spring.data.redis.port=6379");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("Redis");
            });
        }

        @Test
        void allowsRedisOnNonLocalHostWithDefaultPort() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE);
            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }
    }

    @Nested
    class NegativeFileStorage {

        @Test
        void failsWhenLocalFileStorageWithoutAcknowledgement() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "rag.gateway.storage.type=local",
                            "rag.production-guard.allow-local-file-storage=false");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("Local file storage");
            });
        }
    }

    @Nested
    class NegativeOutputCapture {

        @Test
        void failsWhenOutputCaptureEnabledWithoutAcknowledgement() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues(
                            "rag.request-log.output-capture.enabled=true",
                            "rag.production-guard.allow-output-capture=false");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("Output capture");
            });
        }
    }

    @Nested
    class NegativeProfileCombinations {

        @Test
        void failsWhenProdAndDevAreCombined() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=prod,dev")
                    .withPropertyValues(PROD_BASELINE);
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("prod")
                        .hasMessageContaining("dev");
            });
        }

        @Test
        void failsWhenProductionAndTestAreCombined() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=production,test")
                    .withPropertyValues(PROD_BASELINE);
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("production")
                        .hasMessageContaining("test");
            });
        }
    }

    private static String getRootCauseMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : "";
    }

    @TestConfiguration
    @org.springframework.context.annotation.Import(ProductionConfigGuard.class)
    @EnableConfigurationProperties(ProductionGuardProperties.class)
    static class GuardTestConfig {
    }
}
