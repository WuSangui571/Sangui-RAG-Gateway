package com.sangui.raggateway.document.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sangui.raggateway.document.DocumentUploadRules;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    private final Path rootPath;

    public LocalFileStorageService(String localPath) {
        this.rootPath = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory", e);
        }
        log.info("LocalFileStorageService initialized");
    }

    @Override
    public StoredFile save(String ownerType, Long ownerId, String originalFilename, InputStream inputStream) {
        String safeName = DocumentUploadRules.sanitizeFilename(originalFilename);
        String storageKey = ownerType + "/" + ownerId + "/" + UUID.randomUUID() + "/" + safeName;
        Path targetPath = rootPath.resolve(storageKey).normalize();

        if (!targetPath.startsWith(rootPath)) {
            throw new IllegalArgumentException("Path traversal detected for file: " + originalFilename);
        }

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            long fileSize = Files.size(targetPath);
            log.info("File saved: storageKey={}, size={}", storageKey, fileSize);
            return new StoredFile(storageKey, fileSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new IllegalArgumentException("Path traversal detected for storageKey: " + storageKey);
        }
        try {
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("File deleted: storageKey={}", storageKey);
            } else {
                log.info("File already absent, cleanup complete: storageKey={}", storageKey);
            }
        } catch (IOException e) {
            log.error("Failed to delete file: storageKey={}", storageKey, e);
            throw new RuntimeException("Failed to delete file: " + storageKey, e);
        }
    }
}
