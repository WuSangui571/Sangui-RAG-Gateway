package com.sangui.raggateway.document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageServiceTest {

    private LocalFileStorageService storageService;
    private String tempDirPath;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        tempDirPath = tempDir.toAbsolutePath().toString();
        storageService = new LocalFileStorageService(tempDirPath);
    }

    @Test
    void shouldSaveFileUnderConfiguredRoot() {
        byte[] content = "Hello World".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content);

        StoredFile result = storageService.save("knowledge", 1L, "test.md", stream);

        assertThat(result.getStoragePath()).startsWith("knowledge/1/");
        assertThat(result.getStoragePath()).endsWith("test.md");
        assertThat(result.getFileSize()).isEqualTo(11L);
    }

    @Test
    void shouldGenerateNonGuessablePath() {
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);

        StoredFile result1 = storageService.save("knowledge", 1L, "doc.md", new ByteArrayInputStream(content));
        StoredFile result2 = storageService.save("knowledge", 1L, "doc.md", new ByteArrayInputStream(content));

        assertThat(result1.getStoragePath()).isNotEqualTo(result2.getStoragePath());
        assertThat(result1.getStoragePath()).doesNotContain("doc.md".replace(".md", "") + "/");
    }

    @Test
    void shouldSanitizeFilename() {
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content);

        StoredFile result = storageService.save("knowledge", 1L, "../../etc/passwd", stream);

        assertThat(result.getStoragePath()).doesNotContain("..");
        assertThat(result.getStoragePath()).doesNotContain("/etc/");
    }

    @Test
    void shouldRejectPathTraversalInOwnerType() {
        byte[] content = "test".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content);

        assertThatThrownBy(() -> storageService.save("../../../etc", 1L, "test.md", stream))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldSaveTxtFile() {
        byte[] content = "Plain text".getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content);

        StoredFile result = storageService.save("knowledge", 2L, "readme.txt", stream);

        assertThat(result.getStoragePath()).endsWith("readme.txt");
        assertThat(result.getFileSize()).isEqualTo(10L);
    }

    @Test
    void shouldHandleEmptyFile() {
        byte[] content = new byte[0];
        InputStream stream = new ByteArrayInputStream(content);

        StoredFile result = storageService.save("knowledge", 1L, "empty.md", stream);

        assertThat(result.getFileSize()).isEqualTo(0L);
    }
}
