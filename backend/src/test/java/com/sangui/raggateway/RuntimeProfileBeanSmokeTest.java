package com.sangui.raggateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.config.AdminAuthConfig;
import com.sangui.raggateway.common.config.EncryptionConfig;
import com.sangui.raggateway.common.config.GatewayAuthConfig;
import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.GatewayAuthFilter;
import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.document.DocumentProcessingScheduler;
import com.sangui.raggateway.document.DocumentProcessingWorker;
import com.sangui.raggateway.document.TextNormalizer;
import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.config.DocumentConfig;
import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import com.sangui.raggateway.document.parser.MarkdownDocumentParser;
import com.sangui.raggateway.document.parser.PlainTextDocumentParser;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.embedding.EmbeddingClient;
import com.sangui.raggateway.embedding.OpenAiCompatibleEmbeddingClient;
import com.sangui.raggateway.model.ModelConfigCheckService;
import com.sangui.raggateway.model.ModelConfigService;
import com.sangui.raggateway.user.UserService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RuntimeProfileBeanSmokeTest {

    private static final String VALID_SECRET = "runtime-smoke-jwt-secret-at-least-32-bytes!!";
    private static final String PROFILE_RUNTIME_SMOKE = "spring.profiles.active=runtime-smoke";
    private static final String PROP_JWT = "rag.admin-auth.jwt-secret";
    private static final String PROP_ENC = "rag.gateway.encryption.secret-key";

    @Nested
    class PositiveNonTestProfileSmoke {

        private final ApplicationContextRunner embeddingRunner = new ApplicationContextRunner()
                .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                .withPropertyValues(PROFILE_RUNTIME_SMOKE);

        private final ApplicationContextRunner modelCheckRunner = new ApplicationContextRunner()
                .withUserConfiguration(ModelConfigCheckService.class, ModelCheckMocks.class)
                .withPropertyValues(PROFILE_RUNTIME_SMOKE);

        private final ApplicationContextRunner gatewayRunner = new ApplicationContextRunner()
                .withUserConfiguration(GatewayAuthConfig.class, GatewayMocks.class)
                .withPropertyValues(PROFILE_RUNTIME_SMOKE);

        private final ApplicationContextRunner adminAuthRunner = new ApplicationContextRunner()
                .withUserConfiguration(AdminAuthConfig.class, AdminMocks.class)
                .withPropertyValues(
                        PROFILE_RUNTIME_SMOKE,
                        PROP_JWT + "=" + VALID_SECRET,
                        "rag.admin-auth.jwt-expiration-seconds=86400");

        private final ApplicationContextRunner encryptionRunner = new ApplicationContextRunner()
                .withUserConfiguration(EncryptionConfig.class)
                .withPropertyValues(PROFILE_RUNTIME_SMOKE, PROP_ENC + "=" + VALID_SECRET);

        private final ApplicationContextRunner documentRunner = new ApplicationContextRunner()
                .withUserConfiguration(DocumentConfig.class)
                .withPropertyValues(
                        PROFILE_RUNTIME_SMOKE,
                        "rag.gateway.storage.type=local",
                        "rag.gateway.storage.local-path=./target/smoke-uploads");

        @Test
        void embeddingClientCreatedWithDefaultTimeouts() {
            embeddingRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(OpenAiCompatibleEmbeddingClient.class);
            });
        }

        @Test
        void embeddingClientCreatedWithExplicitTimeouts() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.embedding.connect-timeout-seconds=10",
                            "rag.gateway.embedding.response-timeout-seconds=60")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(OpenAiCompatibleEmbeddingClient.class);
                    });
        }

        @Test
        void modelConfigCheckServiceCreatedWithMocks() {
            modelCheckRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ModelConfigCheckService.class);
            });
        }

        @Test
        void modelConfigCheckServiceCreatedWithExplicitTimeouts() {
            new ApplicationContextRunner()
                    .withUserConfiguration(ModelConfigCheckService.class, ModelCheckMocks.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.upstream.connect-timeout-seconds=10",
                            "rag.gateway.upstream.response-timeout-seconds=60")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(ModelConfigCheckService.class);
                    });
        }

        @Test
        void gatewayAuthFilterAndRegistrationCreated() {
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
        void adminJwtServiceCreatedWithValidSecret() {
            adminAuthRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(AdminJwtService.class);
            });
        }

        @Test
        void encryptionConfigCreatesEncryptorWithValidSecret() {
            encryptionRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(UpstreamApiKeyEncryptor.class);
            });
        }

        @Test
        void documentConfigCreatesLocalFileStorageAndParsers() {
            documentRunner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(FileStorageService.class);
                assertThat(context).hasSingleBean(TextNormalizer.class);
                assertThat(context).hasSingleBean(TextChunker.class);
                assertThat(context).hasSingleBean(PlainTextDocumentParser.class);
                assertThat(context).hasSingleBean(MarkdownDocumentParser.class);
            });
        }
    }

    @Nested
    class WorkerSchedulerBoundary {

        @Test
        void documentProcessingSchedulerAbsentWhenWorkerDisabled() {
            new ApplicationContextRunner()
                    .withUserConfiguration(DocumentProcessingScheduler.class, DocumentProcessingMocks.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.document-processing.worker.enabled=false")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(DocumentProcessingScheduler.class);
                    });
        }

        @Test
        void documentProcessingSchedulerCreatedWithMockWorkerWhenEnabled() {
            new ApplicationContextRunner()
                    .withUserConfiguration(DocumentProcessingScheduler.class, DocumentProcessingMocks.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.document-processing.worker.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(DocumentProcessingScheduler.class);
                    });
        }
    }

    @Nested
    class TestProfileExclusion {

        @Test
        void embeddingClientAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues("spring.profiles.active=test")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(OpenAiCompatibleEmbeddingClient.class);
                    });
        }

        @Test
        void modelConfigCheckServiceAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(ModelConfigCheckService.class, ModelCheckMocks.class)
                    .withPropertyValues("spring.profiles.active=test")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(ModelConfigCheckService.class);
                    });
        }

        @Test
        void gatewayAuthBeansAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(GatewayAuthConfig.class, GatewayMocks.class)
                    .withPropertyValues("spring.profiles.active=test")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(GatewayAuthFilter.class);
                    });
        }

        @Test
        void adminAuthBeansAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(AdminAuthConfig.class, AdminMocks.class)
                    .withPropertyValues(
                            "spring.profiles.active=test",
                            PROP_JWT + "=" + VALID_SECRET,
                            "rag.admin-auth.jwt-expiration-seconds=86400")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(AdminJwtService.class);
                    });
        }

        @Test
        void encryptionBeansAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(EncryptionConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=test",
                            PROP_ENC + "=" + VALID_SECRET)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(UpstreamApiKeyEncryptor.class);
                    });
        }

        @Test
        void documentConfigBeansAbsentUnderTestProfile() {
            new ApplicationContextRunner()
                    .withUserConfiguration(DocumentConfig.class)
                    .withPropertyValues(
                            "spring.profiles.active=test",
                            "rag.gateway.storage.type=local")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).doesNotHaveBean(FileStorageService.class);
                        assertThat(context).doesNotHaveBean(TextChunker.class);
                    });
        }
    }

    @Nested
    class TimeoutFailFast {

        @Test
        void embeddingClientFailsWithZeroConnectTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.embedding.connect-timeout-seconds=0")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("connectTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }

        @Test
        void embeddingClientFailsWithZeroResponseTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.embedding.response-timeout-seconds=0")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("responseTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }

        @Test
        void embeddingClientFailsWithNegativeConnectTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.embedding.connect-timeout-seconds=-1")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("connectTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }

        @Test
        void embeddingClientFailsWithNegativeResponseTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(OpenAiCompatibleEmbeddingClient.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.embedding.response-timeout-seconds=-1")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("responseTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }

        @Test
        void modelCheckServiceFailsWithZeroUpstreamConnectTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(ModelConfigCheckService.class, ModelCheckMocks.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.upstream.connect-timeout-seconds=0")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("connectTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }

        @Test
        void modelCheckServiceFailsWithZeroUpstreamResponseTimeout() {
            new ApplicationContextRunner()
                    .withUserConfiguration(ModelConfigCheckService.class, ModelCheckMocks.class)
                    .withPropertyValues(
                            PROFILE_RUNTIME_SMOKE,
                            "rag.gateway.upstream.response-timeout-seconds=0")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("responseTimeoutSeconds")
                                .hasMessageContaining("must be positive");
                    });
        }
    }

    @TestConfiguration
    static class GatewayMocks {

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
    static class AdminMocks {

        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @TestConfiguration
    static class ModelCheckMocks {

        @Bean
        ModelConfigService modelConfigService() {
            return mock(ModelConfigService.class);
        }

        @Bean
        UpstreamApiKeyEncryptor upstreamApiKeyEncryptor() {
            return mock(UpstreamApiKeyEncryptor.class);
        }

        @Bean
        EmbeddingClient embeddingClient() {
            return mock(EmbeddingClient.class);
        }
    }

    @TestConfiguration
    @EnableConfigurationProperties(DocumentProcessingProperties.class)
    static class DocumentProcessingMocks {

        @Bean
        DocumentProcessingWorker documentProcessingWorker() {
            return mock(DocumentProcessingWorker.class);
        }
    }
}
