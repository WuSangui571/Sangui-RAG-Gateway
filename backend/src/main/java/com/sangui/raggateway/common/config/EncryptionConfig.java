package com.sangui.raggateway.common.config;

import com.sangui.raggateway.common.security.UpstreamApiKeyEncryptor;
import com.sangui.raggateway.common.security.UpstreamApiKeyMasker;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties(EncryptionProperties.class)
@Profile("!test")
public class EncryptionConfig {

    private final EncryptionProperties properties;

    public EncryptionConfig(EncryptionProperties properties) {
        this.properties = properties;
    }

    @Bean
    public UpstreamApiKeyEncryptor upstreamApiKeyEncryptor() {
        return new UpstreamApiKeyEncryptor(properties);
    }

    @Bean
    public UpstreamApiKeyMasker upstreamApiKeyMasker() {
        return new UpstreamApiKeyMasker();
    }
}
