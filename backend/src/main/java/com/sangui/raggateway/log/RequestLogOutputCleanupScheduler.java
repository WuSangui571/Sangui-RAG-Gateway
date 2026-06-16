package com.sangui.raggateway.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class RequestLogOutputCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RequestLogOutputCleanupScheduler.class);

    private final ApiRequestLogService apiRequestLogService;
    private final OutputCaptureProperties outputCaptureProperties;

    public RequestLogOutputCleanupScheduler(ApiRequestLogService apiRequestLogService,
                                             OutputCaptureProperties outputCaptureProperties) {
        this.apiRequestLogService = apiRequestLogService;
        this.outputCaptureProperties = outputCaptureProperties;
    }

    @Scheduled(fixedDelayString = "${rag.request-log.output-capture.cleanup-fixed-delay-ms:3600000}")
    public void runCleanup() {
        if (!outputCaptureProperties.isCleanupEnabled()) {
            log.debug("Output preview cleanup disabled, skipping");
            return;
        }
        int count = apiRequestLogService.cleanupExpiredOutputPreviews();
        log.info("Output preview cleanup completed: {} expired previews cleared", count);
    }
}
