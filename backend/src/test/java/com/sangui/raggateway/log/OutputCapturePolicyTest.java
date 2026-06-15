package com.sangui.raggateway.log;

import com.sangui.raggateway.app.AppEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputCapturePolicyTest {

    private OutputCaptureProperties properties;
    private OutputCapturePolicy policy;

    @BeforeEach
    void setUp() {
        properties = new OutputCaptureProperties();
        policy = new OutputCapturePolicy(properties);
    }

    @Test
    void shouldRequireBothGlobalAndAppSwitches() {
        AppEntity app = new AppEntity();
        app.setRequestLogOutputCaptureEnabled(true);

        assertThat(policy.shouldCapture(app)).isFalse();

        properties.setEnabled(true);
        app.setRequestLogOutputCaptureEnabled(false);
        assertThat(policy.shouldCapture(app)).isFalse();

        app.setRequestLogOutputCaptureEnabled(true);
        assertThat(policy.shouldCapture(app)).isTrue();
    }

    @Test
    void shouldReturnEmptyStatusForEmptyAssistantOutput() {
        OutputCapturePolicy.OutputCaptureResult result = policy.capture("");

        assertThat(result.getOutputCaptureStatus()).isEqualTo("EMPTY");
        assertThat(result.getCompletionLength()).isZero();
        assertThat(result.getOutputPreview()).isNull();
        assertThat(result.getOutputRetentionExpiresAt()).isNull();
    }

    @Test
    void shouldCaptureBoundedRedactedPreview() {
        properties.setPreviewMaxChars(100);
        String output = "token Bearer abcdefghijklmnopqrstuvwxyz";

        OutputCapturePolicy.OutputCaptureResult result = policy.capture(output);

        assertThat(result.getOutputCaptureStatus()).isEqualTo("CAPTURED");
        assertThat(result.getCompletionLength()).isEqualTo(output.length());
        assertThat(result.getOutputPreview()).isEqualTo("token Bearer [REDACTED]");
        assertThat(result.isOutputPreviewTruncated()).isFalse();
        assertThat(result.isOutputRedacted()).isTrue();
        assertThat(result.getOutputRetentionExpiresAt()).isNotNull();
    }

    @Test
    void shouldTruncatePreviewBeforePersistence() {
        properties.setPreviewMaxChars(3);

        OutputCapturePolicy.OutputCaptureResult result = policy.capture("abcdef");

        assertThat(result.getOutputCaptureStatus()).isEqualTo("CAPTURED");
        assertThat(result.getCompletionLength()).isEqualTo(6);
        assertThat(result.getOutputPreview()).isEqualTo("abc");
        assertThat(result.isOutputPreviewTruncated()).isTrue();
    }
}
