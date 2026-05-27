package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.security.ApiKeyGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator generator = new ApiKeyGenerator();

    @Test
    void shouldGenerateKeyWithCorrectPrefix() {
        String key = generator.generate();
        assertThat(key).startsWith("sk-sangui-");
    }

    @Test
    void shouldGenerateSufficientRandomSuffix() {
        String key = generator.generate();
        String suffix = key.substring("sk-sangui-".length());
        assertThat(suffix).isNotEmpty();
        assertThat(suffix.length()).isGreaterThan(32);
    }

    @Test
    void shouldGenerateUniqueKeys() {
        String key1 = generator.generate();
        String key2 = generator.generate();
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldExtractSafePrefix() {
        String key = generator.generate();
        String prefix = generator.extractPrefix(key);
        assertThat(prefix).startsWith("sk-sangui-");
        assertThat(prefix.length()).isLessThan(key.length());
    }

    @Test
    void shouldReturnEmptyForNullKey() {
        assertThat(generator.extractPrefix(null)).isEmpty();
    }

    @Test
    void shouldValidatePrefixCorrectly() {
        assertTrue(ApiKeyGenerator.hasValidPrefix(generator.generate()));
        assertThat(ApiKeyGenerator.hasValidPrefix(null)).isFalse();
        assertThat(ApiKeyGenerator.hasValidPrefix("")).isFalse();
        assertThat(ApiKeyGenerator.hasValidPrefix("sk-sangui-")).isFalse();
        assertThat(ApiKeyGenerator.hasValidPrefix("Bearer sk-sangui-abc")).isFalse();
    }
}
