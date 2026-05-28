package com.sangui.raggateway.document.config;

import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.parser.MarkdownDocumentParser;
import com.sangui.raggateway.document.parser.PlainTextDocumentParser;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.LocalFileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(DocumentProperties.class)
@Profile("!test")
public class DocumentConfig {

    @Value("${rag.gateway.storage.local-path:./data/uploads}")
    private String localPath;

    private final DocumentProperties documentProperties;

    public DocumentConfig(DocumentProperties documentProperties) {
        this.documentProperties = documentProperties;
    }

    @Bean
    public FileStorageService fileStorageService() {
        return new LocalFileStorageService(localPath);
    }

    @Bean
    public TextChunker textChunker() {
        return new TextChunker(documentProperties.getChunkSize(), documentProperties.getChunkOverlap());
    }

    @Bean
    public PlainTextDocumentParser plainTextDocumentParser() {
        return new PlainTextDocumentParser();
    }

    @Bean
    public MarkdownDocumentParser markdownDocumentParser() {
        return new MarkdownDocumentParser();
    }
}
