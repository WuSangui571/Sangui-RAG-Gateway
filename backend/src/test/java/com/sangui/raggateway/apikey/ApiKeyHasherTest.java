package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.security.ApiKeyHasher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    void shouldProduceDeterministicHash() {
        String key = "sk-sangui-test-key-12345";
        String hash1 = hasher.hash(key);
        String hash2 = hasher.hash(key);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void shouldProduceDifferentHashForDifferentInputs() {
        String hash1 = hasher.hash("sk-sangui-key-a");
        String hash2 = hasher.hash("sk-sangui-key-b");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void shouldNotContainOriginalKeyInHash() {
        String key = "sk-sangui-abcdef1234567890";
        String hash = hasher.hash(key);
        assertThat(hash).doesNotContain("sk-sangui");
        assertThat(hash).doesNotContain("abcdef1234567890");
    }

    @Test
    void shouldDifferFromPlaintext() {
        String key = "sk-sangui-test-value";
        String hash = hasher.hash(key);
        assertThat(hash).isNotEqualTo(key);
    }

    @Test
    void shouldProduceHexEncodedOutput() {
        String hash = hasher.hash("test");
        assertThat(hash).matches("^[0-9a-f]+$");
        assertThat(hash.length()).isEqualTo(64);
    }
}
