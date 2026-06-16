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

    private static final String[] PROD_BASELINE = {
            "rag.gateway.secret-key=" + STRONG_SECRET,
            "spring.datasource.url=jdbc:postgresql://prod-db:5432/sangui",
            "spring.datasource.username=prod_user",
            "spring.datasource.password=prod_password",
            "spring.data.redis.host=redis",
            "spring.data.redis.port=6379",
            "rag.gateway.storage.type=s3",
            "rag.request-log.output-capture.enabled=false"
    };

    @Nested
    class DevAndTestProfilesSkipGuard {

        private final ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(GuardTestConfig.class);

        @Test
        void devProfileDoesNotTriggerGuard() {
            runner.withPropertyValues("spring.profiles.active=dev").run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void noProfileDoesNotTriggerGuard() {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
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

        @Test
        void failsWhenProductionProfileIsSetThroughEnvironment() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("rag.gateway.secret-key=local-dev-change-me");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void failsWhenSecretKeyIsBlank() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("rag.gateway.secret-key=");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("blank");
            });
        }

        @Test
        void failsWhenSecretKeyIsWeakPlaceholder() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("rag.gateway.secret-key=local-dev-change-me");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void failsWhenSecretKeyIsShorterThan32Chars() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("rag.gateway.secret-key=short-secret");
            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("32");
            });
        }

        @Test
        void failureMessageDoesNotEchoSecretValue() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardTestConfig.class)
                    .withPropertyValues("spring.profiles.active=prod")
                    .withPropertyValues(PROD_BASELINE)
                    .withPropertyValues("rag.gateway.secret-key=my-insecure-key-that-is-short");
            runner.run(context -> {
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
