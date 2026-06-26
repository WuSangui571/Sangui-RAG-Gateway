package com.sangui.raggateway.gateway.upstream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTimeoutConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TimeoutBindingConfiguration.class);

    @Test
    void shouldBindDefaultTimeouts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean("upstreamTimeoutValues", TimeoutValues.class))
                    .isEqualTo(new TimeoutValues(5, 30));
            assertThat(context.getBean("embeddingTimeoutValues", TimeoutValues.class))
                    .isEqualTo(new TimeoutValues(5, 30));
        });
    }

    @Test
    void shouldUseLegacyTimeoutAsResponseFallbackOnly() {
        runner.withPropertyValues(
                        "rag.gateway.upstream.timeout-seconds=45",
                        "rag.gateway.embedding.timeout-seconds=55")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("upstreamTimeoutValues", TimeoutValues.class))
                            .isEqualTo(new TimeoutValues(5, 45));
                    assertThat(context.getBean("embeddingTimeoutValues", TimeoutValues.class))
                            .isEqualTo(new TimeoutValues(5, 55));
                });
    }

    @Test
    void shouldPreferResponseTimeoutOverLegacyTimeout() {
        runner.withPropertyValues(
                        "rag.gateway.upstream.timeout-seconds=45",
                        "rag.gateway.upstream.response-timeout-seconds=60",
                        "rag.gateway.embedding.timeout-seconds=55",
                        "rag.gateway.embedding.response-timeout-seconds=70")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean("upstreamTimeoutValues", TimeoutValues.class))
                            .isEqualTo(new TimeoutValues(5, 60));
                    assertThat(context.getBean("embeddingTimeoutValues", TimeoutValues.class))
                            .isEqualTo(new TimeoutValues(5, 70));
                });
    }

    record TimeoutValues(int connectTimeoutSeconds, int responseTimeoutSeconds) {
    }

    @TestConfiguration
    static class TimeoutBindingConfiguration {

        @Bean
        TimeoutValues upstreamTimeoutValues(
                @Value("${rag.gateway.upstream.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                @Value("${rag.gateway.upstream.response-timeout-seconds:${rag.gateway.upstream.timeout-seconds:30}}") int responseTimeoutSeconds) {
            return new TimeoutValues(connectTimeoutSeconds, responseTimeoutSeconds);
        }

        @Bean
        TimeoutValues embeddingTimeoutValues(
                @Value("${rag.gateway.embedding.connect-timeout-seconds:5}") int connectTimeoutSeconds,
                @Value("${rag.gateway.embedding.response-timeout-seconds:${rag.gateway.embedding.timeout-seconds:30}}") int responseTimeoutSeconds) {
            return new TimeoutValues(connectTimeoutSeconds, responseTimeoutSeconds);
        }
    }
}
