package com.sangui.raggateway.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> hasher.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankPassword() {
        assertThatThrownBy(() -> hasher.hash("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHashPassword() {
        String hash = hasher.hash("password123");
        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void shouldVerifyCorrectPassword() {
        String hash = hasher.hash("password123");
        assertThat(hasher.verify("password123", hash)).isTrue();
    }

    @Test
    void shouldRejectWrongPassword() {
        String hash = hasher.hash("password123");
        assertThat(hasher.verify("wrong-password", hash)).isFalse();
    }

    @Test
    void shouldRejectNullPlaintext() {
        String hash = hasher.hash("password123");
        assertThat(hasher.verify(null, hash)).isFalse();
    }

    @Test
    void shouldRejectBlankPlaintext() {
        String hash = hasher.hash("password123");
        assertThat(hasher.verify("  ", hash)).isFalse();
    }

    @Test
    void shouldRejectNullHash() {
        assertThat(hasher.verify("password", null)).isFalse();
    }

    @Test
    void shouldRejectBlankHash() {
        assertThat(hasher.verify("password", "  ")).isFalse();
    }

    @Test
    void shouldProduceDifferentHashesForSamePassword() {
        String hash1 = hasher.hash("password123");
        String hash2 = hasher.hash("password123");
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hasher.verify("password123", hash1)).isTrue();
        assertThat(hasher.verify("password123", hash2)).isTrue();
    }
}
