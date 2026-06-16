package com.sangui.raggateway.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.gateway.storage")
public class StorageProperties {

    private String type = "local";
    private String localPath = "./data/uploads";
    private final ObjectStorage object = new ObjectStorage();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public ObjectStorage getObject() { return object; }

    public static class ObjectStorage {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        private String region;
        private boolean pathStyleAccess = true;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public boolean isPathStyleAccess() { return pathStyleAccess; }
        public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }
    }
}