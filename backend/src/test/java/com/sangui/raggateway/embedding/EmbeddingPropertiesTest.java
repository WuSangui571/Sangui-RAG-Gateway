package com.sangui.raggateway.embedding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingPropertiesBinding.class);

    @Test
    void shouldBindDefaultBatchSize() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EmbeddingProperties.class).getBatchSize()).isEqualTo(64);
        });
    }

    @Test
    void shouldBindConfiguredBatchSize() {
        runner.withPropertyValues("rag.gateway.embedding.batch-size=128")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EmbeddingProperties.class).getBatchSize()).isEqualTo(128);
                });
    }

    @Test
    void shouldRejectBatchSizeBelowMinimum() {
        runner.withPropertyValues("rag.gateway.embedding.batch-size=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("batchSize");
                });
    }

    @Test
    void shouldRejectBatchSizeAboveMaximum() {
        runner.withPropertyValues("rag.gateway.embedding.batch-size=2049")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("batchSize");
                });
    }

    @TestConfiguration
    @EnableConfigurationProperties(EmbeddingProperties.class)
    static class EmbeddingPropertiesBinding {
    }
}
