package com.sangui.raggateway.document.storage;

public class StoredFile {

    private final String storagePath;
    private final long fileSize;

    public StoredFile(String storagePath, long fileSize) {
        this.storagePath = storagePath;
        this.fileSize = fileSize;
    }

    public String getStoragePath() { return storagePath; }
    public long getFileSize() { return fileSize; }
}
