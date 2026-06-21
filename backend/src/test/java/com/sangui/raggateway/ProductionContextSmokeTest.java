package com.sangui.raggateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.config.AdminAuthConfig;
import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.config.EncryptionConfig;
import com.sangui.raggateway.common.config.GatewayAuthConfig;
import com.sangui.raggateway.common.config.ProductionConfigGuard;
import com.sangui.raggateway.common.config.ProductionGuardProperties;
import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.GatewayAuthFilter;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.user.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductionContextSmokeTest {

    private static final String VALID_SECRET = "prod-smoke-jwt-secret-at-least-32-bytes-long-for-hs256!!";
    private static final String DOCUMENTED_SECRET_PLACEHOLDER = "<set-a-strong-32-char-secret>";

    @Nested
    class PositiveNonTestProfileSmoke {

        private final ApplicationContextRunner gatewayRunner = new ApplicationContextRunner()
                .withUserConfiguration(GatewayAuthConfig.class, MockDependencies.class)
                .withPropertyValues("rag.gateway.secret-key=" + VALID_SECRET);

        private final ApplicationContextRunner encryptionRunner = new ApplicationContextRunner()
                .withUserConfiguration(EncryptionConfig.class)
                .withPropertyValues("rag.gateway.secret-key=" + VALID_SECRET);

        private final ApplicationContextRunner adminAuthRunner = new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthConfig.class, MockUserServiceConfig.class, MockObjectMapperConfig.class)
                .withPropertyValues(
                        "rag.gateway.secret-key=" + VALID_SECRET,
                        "rag.admin-auth.jwt-expiration-seconds=86400");

        private final ApplicationContextRunner apiKeyLimitRunner = new ApplicationContextRunner()
                .withUserConfiguration(ApiKeyLimitPropertiesBinding.class)
                .withPropertyValues(
                        "rag.gateway.api-key-limits.enabled=true",
                        "rag.gateway.api-key-limits.default-requests-per-minute=60",
                        "rag.gateway.api-key-limits.default-tokens-per-minute=60000",
                        "rag.gateway.api-key-limits.default-daily-request-quota=1000",
                        "rag.gateway.api-key-limits.default-daily-token-quota=1000000",
                        "rag.gateway.api-key-limits.default-completion-token-reservation=1024");

        @Test
        void gatewayAuthFilterAndRegistrationAreCreated() {
            gatewayRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(GatewayAuthFilter.class);

                FilterRegistrationBean<?> registration = context.getBean(
                        "gatewayAuthFilterRegistration", FilterRegistrationBean.class);
                assertThat(registration.getUrlPatterns()).contains("/v1/*");
                assertThat(registration.getOrder()).isEqualTo(1);
            });
        }

        @Test
        void upstreamApiKeyEncryptorIsCreatedWithValidSecret() {
            encryptionRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(UpstreamApiKeyEncryptor.class);
            });
        }

        @Test
        void adminJwtServiceIsCreatedWithValidSecret() {
            adminAuthRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AdminJwtService.class);
            });
        }

        @Test
        void apiKeyLimitPropertiesBindsPositiveDefaults() {
            apiKeyLimitRunner.run(context -> {
                assertThat(context).hasNotFailed();
                ApiKeyLimitProperties props = context.getBean(ApiKeyLimitProperties.class);
                assertThat(props.isEnabled()).isTrue();
                assertThat(props.getDefaultRequestsPerMinute()).isEqualTo(60);
                assertThat(props.getDefaultTokensPerMinute()).isEqualTo(60000);
                assertThat(props.getDefaultDailyRequestQuota()).isEqualTo(1000);
                assertThat(props.getDefaultDailyTokenQuota()).isEqualTo(1000000);
                assertThat(props.getDefaultCompletionTokenReservation()).isEqualTo(1024);
            });
        }

        @Test
        void gatewayAuthFilterHasBuiltCorrectDependencyChain() {
            gatewayRunner.run(context -> {
                GatewayAuthFilter filter = context.getBean(GatewayAuthFilter.class);
                assertThat(filter).isNotNull();
            });
        }
    }

    @Nested
    class NegativeBlankSecretKey {

        @Test
        void encryptionConfigFailsWhenSecretKeyIsBlank() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(EncryptionConfig.class)
                    .withPropertyValues("rag.gateway.secret-key=");

            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key must not be blank");
            });
        }

        @Test
        void adminJwtServiceFailsWhenSecretIsBlank() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(AdminAuthConfig.class, MockUserServiceConfig.class,
                            MockObjectMapperConfig.class)
                    .withPropertyValues(
                            "rag.gateway.secret-key=",
                            "rag.admin-auth.jwt-expiration-seconds=86400");

            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("JWT secret must not be blank");
            });
        }
    }

    @Nested
    class NegativeWeakSecretKey {

        @Test
        void guardFailsWhenDevProfileHasWeakPlaceholderWithoutAck() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=dev",
                            "rag.gateway.secret-key=local-dev-change-me");

            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("weak placeholder");
            });
        }

        @Test
        void guardPassesWhenDevProfileHasWeakPlaceholderWithAck() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=dev",
                            "rag.gateway.secret-key=local-dev-change-me",
                            "rag.production-guard.allow-weak-local-secret=true");

            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void guardFailsWhenNoProfileWithWeakPlaceholder() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
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
        void guardFailsWhenNoProfileUsesDocumentedPlaceholder() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
                    .withPropertyValues("rag.gateway.secret-key=" + DOCUMENTED_SECRET_PLACEHOLDER);

            runner.run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                        .rootCause()
                        .hasMessageContaining("rag.gateway.secret-key")
                        .hasMessageContaining("real secret");
            });
        }

        @Test
        void guardPassesWhenNoProfileWithStrongSecret() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
                    .withPropertyValues("rag.gateway.secret-key=" + VALID_SECRET);

            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }

        @Test
        void guardSkipsWhenTestProfile() {
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(GuardSmokeConfig.class)
                    .withPropertyValues("spring.profiles.active=test");

            runner.run(context -> {
                assertThat(context).hasNotFailed();
            });
        }
    }

    @TestConfiguration
    @Import(ProductionConfigGuard.class)
    @EnableConfigurationProperties(ProductionGuardProperties.class)
    static class GuardSmokeConfig {
    }

    @TestConfiguration
    static class MockDependencies {

        @Bean
        ApiKeyService apiKeyService() {
            return mock(ApiKeyService.class);
        }

        @Bean
        AppService appService() {
            return mock(AppService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @TestConfiguration
    static class MockUserServiceConfig {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }
    }

    @TestConfiguration
    static class MockObjectMapperConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties(ApiKeyLimitProperties.class)
    static class ApiKeyLimitPropertiesBinding {
    }
}
