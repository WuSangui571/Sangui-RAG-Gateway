package com.sangui.raggateway.document;

import com.sangui.raggateway.document.config.DocumentProcessingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@ConditionalOnProperty(name = "rag.document-processing.worker.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingScheduler.class);

    private final DocumentProcessingWorker worker;
    private final DocumentProcessingProperties properties;

    public DocumentProcessingScheduler(DocumentProcessingWorker worker,
                                        DocumentProcessingProperties properties) {
        this.worker = worker;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${rag.document-processing.worker.poll-fixed-delay-ms:5000}")
    public void runProcessingLoop() {
        try {
            worker.recoverStaleProcessing();
            worker.processNext();
        } catch (Exception e) {
            log.error("Processing loop error", e);
        }
    }
}
