package com.sangui.raggateway.document.storage;

import java.io.InputStream;

public interface FileStorageService {
    StoredFile save(String ownerType, Long ownerId, String originalFilename, InputStream inputStream);

    void delete(String storageKey);
}
