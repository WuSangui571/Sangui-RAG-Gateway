package com.sangui.raggateway.document.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ObjectFileStorageServiceTest {

    private S3Client s3Client;
    private ObjectFileStorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        storageService = new ObjectFileStorageService(s3Client, "documents");
    }

    @Test
    void shouldSaveObjectWithOpaqueStorageKey() {
        byte[] content = "hello object storage".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = storageService.save(
                "knowledge",
                1L,
                "中文 文档.md",
                new ByteArrayInputStream(content));

        assertThat(storedFile.getStoragePath()).startsWith("knowledge/1/");
        assertThat(storedFile.getStoragePath()).endsWith(".md");
        assertThat(storedFile.getStoragePath()).doesNotContain("中文");
        assertThat(storedFile.getFileSize()).isEqualTo(content.length);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertThat(requestCaptor.getValue().bucket()).isEqualTo("documents");
        assertThat(requestCaptor.getValue().key()).isEqualTo(storedFile.getStoragePath());
    }

    @Test
    void shouldDeleteObjectAfterHeadSucceeds() {
        storageService.delete("knowledge/1/object/test.md");

        ArgumentCaptor<HeadObjectRequest> headCaptor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        ArgumentCaptor<DeleteObjectRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).headObject(headCaptor.capture());
        verify(s3Client).deleteObject(deleteCaptor.capture());
        assertThat(headCaptor.getValue().bucket()).isEqualTo("documents");
        assertThat(headCaptor.getValue().key()).isEqualTo("knowledge/1/object/test.md");
        assertThat(deleteCaptor.getValue().bucket()).isEqualTo("documents");
        assertThat(deleteCaptor.getValue().key()).isEqualTo("knowledge/1/object/test.md");
    }

    @Test
    void shouldTreatNoSuchKeyAsCleanupComplete() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        storageService.delete("knowledge/1/object/missing.md");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldTreatHead404AsCleanupComplete() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        storageService.delete("knowledge/1/object/missing.md");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldFailVisiblyForNon404S3Errors() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(503).message("service unavailable").build());

        assertThatThrownBy(() -> storageService.delete("knowledge/1/object/test.md"))
                .isInstanceOf(S3Exception.class);
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void shouldRejectBlankStorageKey() {
        assertThatThrownBy(() -> storageService.delete(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storageKey");
    }
}
