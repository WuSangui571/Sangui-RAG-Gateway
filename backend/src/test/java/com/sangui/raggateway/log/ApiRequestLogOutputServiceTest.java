package com.sangui.raggateway.log;

import com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiRequestLogOutputServiceTest {

    @Mock
    private ApiRequestLogMapper apiRequestLogMapper;

    @Mock
    private RequestLogOutputAccessAuditMapper auditMapper;

    private OutputCaptureProperties outputCaptureProperties;
    private ApiRequestLogService service;

    @BeforeEach
    void setUp() {
        outputCaptureProperties = new OutputCaptureProperties();
        service = new ApiRequestLogService(
                apiRequestLogMapper,
                null,
                null,
                auditMapper,
                null,
                outputCaptureProperties);
    }

    @Test
    void shouldPersistOutputMetadataOnRequestLogRecord() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        when(apiRequestLogMapper.insertRequestLog(any(ApiRequestLogEntity.class))).thenReturn(1);

        service.record(CreateRequestLogCommand.builder()
                .requestId("req-output-001")
                .userId(100L)
                .appId(1L)
                .apiKeyId(30L)
                .status("success")
                .latencyMs(123L)
                .completionLength(1200)
                .outputCaptureStatus("CAPTURED")
                .outputPreview("safe redacted preview")
                .outputPreviewTruncated(true)
                .outputRedacted(true)
                .outputRetentionExpiresAt(expiresAt)
                .build());

        ArgumentCaptor<ApiRequestLogEntity> captor = ArgumentCaptor.forClass(ApiRequestLogEntity.class);
        verify(apiRequestLogMapper).insertRequestLog(captor.capture());
        ApiRequestLogEntity entity = captor.getValue();

        assertThat(entity.getCompletionLength()).isEqualTo(1200);
        assertThat(entity.getOutputCaptureStatus()).isEqualTo("CAPTURED");
        assertThat(entity.getOutputPreview()).isEqualTo("safe redacted preview");
        assertThat(entity.getOutputPreviewTruncated()).isTrue();
        assertThat(entity.getOutputRedacted()).isTrue();
        assertThat(entity.getOutputRetentionExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldMapOutputPreviewResponseFromTenantScopedLog() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 6, 22, 10, 0);
        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setRequestId("req-output-002");
        entity.setOutputCaptureStatus("CAPTURED");
        entity.setCompletionLength(42);
        entity.setOutputPreview("preview");
        entity.setOutputPreviewTruncated(false);
        entity.setOutputRedacted(false);
        entity.setOutputRetentionExpiresAt(expiresAt);
        when(apiRequestLogMapper.selectByRequestIdAndUserAndApp(100L, 1L, "req-output-002"))
                .thenReturn(entity);

        RequestLogOutputPreviewVO preview = service.getOutputPreview(100L, 1L, "req-output-002");

        assertThat(preview.getRequestId()).isEqualTo("req-output-002");
        assertThat(preview.getOutputCaptureStatus()).isEqualTo("CAPTURED");
        assertThat(preview.getCompletionLength()).isEqualTo(42);
        assertThat(preview.getOutputPreview()).isEqualTo("preview");
        assertThat(preview.getOutputPreviewTruncated()).isFalse();
        assertThat(preview.getOutputRedacted()).isFalse();
        assertThat(preview.getOutputRetentionExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void shouldWriteAccessAuditWithBoundedReasonAndNoPreviewContent() {
        outputCaptureProperties.setReasonMaxChars(10);
        String longReason = "investigating upstream truncation";

        service.writeAccessAudit(100L, 1L, 9L, "req-output-003", "GRANTED", longReason);

        ArgumentCaptor<RequestLogOutputAccessAuditEntity> captor =
                ArgumentCaptor.forClass(RequestLogOutputAccessAuditEntity.class);
        verify(auditMapper).insertAudit(captor.capture());
        RequestLogOutputAccessAuditEntity audit = captor.getValue();

        assertThat(audit.getUserId()).isEqualTo(100L);
        assertThat(audit.getAppId()).isEqualTo(1L);
        assertThat(audit.getRequestLogId()).isEqualTo(9L);
        assertThat(audit.getRequestId()).isEqualTo("req-output-003");
        assertThat(audit.getAccessResult()).isEqualTo("GRANTED");
        assertThat(audit.getReason()).isEqualTo("investigat");
        assertThat(audit.toString()).doesNotContain("preview");
    }

    @Test
    void shouldExpireOutputPreviewsWithoutDeletingRequestLogs() {
        ApiRequestLogEntity first = new ApiRequestLogEntity();
        first.setId(1L);
        ApiRequestLogEntity second = new ApiRequestLogEntity();
        second.setId(2L);
        when(apiRequestLogMapper.selectExpiredOutputPreviews(any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));
        when(apiRequestLogMapper.expireOutputPreview(1L)).thenReturn(1);
        when(apiRequestLogMapper.expireOutputPreview(2L)).thenReturn(1);

        int expired = service.cleanupExpiredOutputPreviews();

        assertThat(expired).isEqualTo(2);
        verify(apiRequestLogMapper).expireOutputPreview(1L);
        verify(apiRequestLogMapper).expireOutputPreview(2L);
    }
}
