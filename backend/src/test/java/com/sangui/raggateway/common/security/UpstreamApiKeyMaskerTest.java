package com.sangui.raggateway.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamApiKeyMaskerTest {

    private UpstreamApiKeyMasker masker;

    @BeforeEach
    void setUp() {
        masker = new UpstreamApiKeyMasker();
    }

    @Test
    void shouldMaskNormalKey() {
        String plaintext = "sk-upstream-secret-key-12345";

        String masked = masker.mask(plaintext);

        assertThat(masked).isNotNull();
        assertThat(masked).isNotEqualTo(plaintext);
        assertThat(masked).startsWith("sk-");
        String middle = masked.substring(3, masked.length() - 4);
        assertThat(middle).matches("\\*+");
    }

    @Test
    void shouldNotReturnPlaintext() {
        String plaintext = "sk-upstream-secret-key-12345";

        String masked = masker.mask(plaintext);

        assertThat(masked).isNotEqualTo(plaintext);
    }

    @Test
    void shouldFullyMaskShortKey() {
        String shortKey = "ab";

        String masked = masker.mask(shortKey);

        assertThat(masked).isEqualTo("**");
    }

    @Test
    void shouldFullyMaskShortKeyUnder8Chars() {
        String shortKey = "1234567";

        String masked = masker.mask(shortKey);

        assertThat(masked).isEqualTo("*******");
    }

    @Test
    void shouldMaskBoundaryLengthKey() {
        String key = "12345678";

        String masked = masker.mask(key);

        assertThat(masked).isNotEqualTo(key);
        assertThat(masked).startsWith("123");
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertThat(masker.mask(null)).isNull();
    }

    @Test
    void shouldPreserveEndCharacters() {
        String plaintext = "sk-abcdefghijklmnop";

        String masked = masker.mask(plaintext);

        assertThat(masked).endsWith("mnop");
    }
}
