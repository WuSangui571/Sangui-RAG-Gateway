package com.sangui.raggateway.document.config;

import com.sangui.raggateway.document.storage.FileStorageService;
import com.sangui.raggateway.document.storage.LocalFileStorageService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentConfigTest {

    @Test
    void shouldSelectLocalStorageByDefault() {
        StorageProperties storageProperties = new StorageProperties();
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        FileStorageService service = config.fileStorageService();

        assertThat(service).isInstanceOf(LocalFileStorageService.class);
    }

    @Test
    void shouldRejectUnknownStorageType() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setType("s3");
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        assertThatThrownBy(config::fileStorageService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown storage type")
                .hasMessageContaining("local, object");
    }

    @Test
    void shouldRequireObjectEndpointWhenObjectStorageSelected() {
        StorageProperties storageProperties = objectStorageProperties();
        storageProperties.getObject().setEndpoint(" ");
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        assertThatThrownBy(config::fileStorageService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rag.gateway.storage.object.endpoint")
                .hasMessageNotContaining("test-access-key")
                .hasMessageNotContaining("test-secret-key");
    }

    @Test
    void shouldRequireObjectBucketWhenObjectStorageSelected() {
        StorageProperties storageProperties = objectStorageProperties();
        storageProperties.getObject().setBucket(null);
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        assertThatThrownBy(config::fileStorageService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rag.gateway.storage.object.bucket")
                .hasMessageNotContaining("test-access-key")
                .hasMessageNotContaining("test-secret-key");
    }

    @Test
    void shouldRequireObjectAccessKeyWhenObjectStorageSelected() {
        StorageProperties storageProperties = objectStorageProperties();
        storageProperties.getObject().setAccessKey("");
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        assertThatThrownBy(config::fileStorageService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rag.gateway.storage.object.access-key")
                .hasMessageNotContaining("test-secret-key");
    }

    @Test
    void shouldRequireObjectSecretKeyWhenObjectStorageSelected() {
        StorageProperties storageProperties = objectStorageProperties();
        storageProperties.getObject().setSecretKey("");
        DocumentConfig config = new DocumentConfig(new DocumentProperties(), storageProperties);

        assertThatThrownBy(config::fileStorageService)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rag.gateway.storage.object.secret-key")
                .hasMessageNotContaining("test-access-key");
    }

    private StorageProperties objectStorageProperties() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setType("object");
        storageProperties.getObject().setEndpoint("http://localhost:9000");
        storageProperties.getObject().setBucket("documents");
        storageProperties.getObject().setAccessKey("test-access-key");
        storageProperties.getObject().setSecretKey("test-secret-key");
        storageProperties.getObject().setRegion("us-east-1");
        return storageProperties;
    }
}
