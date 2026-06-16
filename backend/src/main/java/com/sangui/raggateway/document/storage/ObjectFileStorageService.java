package com.sangui.raggateway.document.storage;

import com.sangui.raggateway.document.DocumentUploadRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

public class ObjectFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectFileStorageService.class);

    private final S3Client s3Client;
    private final String bucket;

    public ObjectFileStorageService(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        log.info("ObjectFileStorageService initialized");
    }

    @Override
    public StoredFile save(String ownerType, Long ownerId, String originalFilename, InputStream inputStream) {
        String safeName = DocumentUploadRules.sanitizeFilename(originalFilename);
        String storageKey = ownerType + "/" + ownerId + "/" + UUID.randomUUID() + "/" + safeName;

        byte[] bytes;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            bytes = buffer.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream", e);
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        log.info("File saved: storageKey={}, size={}", storageKey, bytes.length);
        return new StoredFile(storageKey, bytes.length);
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(storageKey)
                    .build());
        } catch (NoSuchKeyException e) {
            log.info("Object already absent, cleanup complete: storageKey={}", storageKey);
            return;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                log.info("Object already absent, cleanup complete: storageKey={}", storageKey);
                return;
            }
            throw e;
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());
        log.info("Object deleted: storageKey={}", storageKey);
    }
}
