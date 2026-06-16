package com.sangui.raggateway.log;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestLogOutputCleanupSchedulerTest {

    @Mock
    private ApiRequestLogService apiRequestLogService;

    private OutputCaptureProperties outputCaptureProperties;
    private RequestLogOutputCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        outputCaptureProperties = new OutputCaptureProperties();
        scheduler = new RequestLogOutputCleanupScheduler(apiRequestLogService, outputCaptureProperties);
    }

    @Test
    void shouldCallCleanupServiceWhenEnabled() {
        outputCaptureProperties.setCleanupEnabled(true);
        when(apiRequestLogService.cleanupExpiredOutputPreviews()).thenReturn(3);

        scheduler.runCleanup();

        verify(apiRequestLogService).cleanupExpiredOutputPreviews();
    }

    @Test
    void shouldSkipCleanupServiceWhenDisabled() {
        outputCaptureProperties.setCleanupEnabled(false);

        scheduler.runCleanup();

        verify(apiRequestLogService, never()).cleanupExpiredOutputPreviews();
    }

    @Test
    void shouldSkipCleanupServiceWhenNoExpiredPreview() {
        outputCaptureProperties.setCleanupEnabled(true);
        when(apiRequestLogService.cleanupExpiredOutputPreviews()).thenReturn(0);

        scheduler.runCleanup();

        verify(apiRequestLogService).cleanupExpiredOutputPreviews();
    }

    @Test
    void shouldRejectNonPositiveCleanupFixedDelay() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(OutputCapturePropertiesBinding.class)
                .withPropertyValues("rag.request-log.output-capture.cleanup-fixed-delay-ms=0");

        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("cleanupFixedDelayMs");
        });
    }

    @TestConfiguration
    @EnableConfigurationProperties(OutputCaptureProperties.class)
    static class OutputCapturePropertiesBinding {
    }
}
