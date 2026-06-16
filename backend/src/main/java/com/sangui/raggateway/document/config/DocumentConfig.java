package com.sangui.raggateway.document.config;

import com.sangui.raggateway.document.chunk.TextChunker;
import com.sangui.raggateway.document.parser.MarkdownDocumentParser;
import com.sangui.raggateway.document.parser.PlainTextDocumentParser;
import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.LocalFileStorageService;
import com.sangui.raggateway.document.storage.ObjectFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.Locale;

@Configuration
@EnableConfigurationProperties({DocumentProperties.class, StorageProperties.class})
@Profile("!test")
public class DocumentConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentConfig.class);

    private final DocumentProperties documentProperties;
    private final StorageProperties storageProperties;

    public DocumentConfig(DocumentProperties documentProperties, StorageProperties storageProperties) {
        this.documentProperties = documentProperties;
        this.storageProperties = storageProperties;
    }

    @Bean
    public FileStorageService fileStorageService() {
        String type = storageProperties.getType().trim().toLowerCase(Locale.ROOT);
        if ("local".equals(type)) {
            return new LocalFileStorageService(storageProperties.getLocalPath());
        } else if ("object".equals(type)) {
            StorageProperties.ObjectStorage obj = storageProperties.getObject();
            validateObjectStorageConfig(obj);
            S3Client s3Client = buildS3Client(obj);
            return new ObjectFileStorageService(s3Client, obj.getBucket());
        }
        throw new IllegalStateException("Unknown storage type: " + type + ". Allowed values: local, object");
    }

    private void validateObjectStorageConfig(StorageProperties.ObjectStorage obj) {
        if (obj.getEndpoint() == null || obj.getEndpoint().isBlank()) {
            throw new IllegalStateException("rag.gateway.storage.object.endpoint is required when storage type is object");
        }
        if (obj.getBucket() == null || obj.getBucket().isBlank()) {
            throw new IllegalStateException("rag.gateway.storage.object.bucket is required when storage type is object");
        }
        if (obj.getAccessKey() == null || obj.getAccessKey().isBlank()) {
            throw new IllegalStateException("rag.gateway.storage.object.access-key is required when storage type is object");
        }
        if (obj.getSecretKey() == null || obj.getSecretKey().isBlank()) {
            throw new IllegalStateException("rag.gateway.storage.object.secret-key is required when storage type is object");
        }
    }

    private S3Client buildS3Client(StorageProperties.ObjectStorage obj) {
        String region = obj.getRegion() != null && !obj.getRegion().isBlank()
                ? obj.getRegion()
                : "us-east-1";
        return S3Client.builder()
                .region(Region.of(region))
                .endpointOverride(URI.create(obj.getEndpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(obj.getAccessKey(), obj.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(obj.isPathStyleAccess())
                        .build())
                .build();
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
