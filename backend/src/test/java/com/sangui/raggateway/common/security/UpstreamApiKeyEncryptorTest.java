package com.sangui.raggateway.common.security;

import com.sangui.raggateway.common.config.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UpstreamApiKeyEncryptorTest {

    private EncryptionProperties properties;
    private UpstreamApiKeyEncryptor encryptor;

    @BeforeEach
    void setUp() {
        properties = new EncryptionProperties();
        properties.setSecretKey("test-secret-key-for-encryption");
        encryptor = new UpstreamApiKeyEncryptor(properties);
    }

    @Test
    void shouldEncryptAndDecryptRoundTrip() {
        String plaintext = "sk-upstream-secret-key-12345";

        String encrypted = encryptor.encrypt(plaintext);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encrypted).startsWith("v1:");

        String decrypted = encryptor.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String plaintext = "sk-upstream-secret-key-12345";

        String encrypted1 = encryptor.encrypt(plaintext);
        String encrypted2 = encryptor.encrypt(plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    void shouldRejectBlankSecret() {
        EncryptionProperties blankProps = new EncryptionProperties();
        blankProps.setSecretKey("");

        assertThatThrownBy(() -> new UpstreamApiKeyEncryptor(blankProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldRejectNullSecret() {
        EncryptionProperties nullProps = new EncryptionProperties();

        assertThatThrownBy(() -> new UpstreamApiKeyEncryptor(nullProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldFailGracefullyForMalformedPayload() {
        assertThatThrownBy(() -> encryptor.decrypt("not-valid-format"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void shouldFailGracefullyForNullPayload() {
        assertThatThrownBy(() -> encryptor.decrypt(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldFailGracefullyForBlankPayload() {
        assertThatThrownBy(() -> encryptor.decrypt(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void shouldFailGracefullyForTamperedPayload() {
        String encrypted = encryptor.encrypt("sk-upstream-secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "xx";

        assertThatThrownBy(() -> encryptor.decrypt(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void shouldEncryptMultipleValuesWithDifferentSecrets() {
        EncryptionProperties otherProps = new EncryptionProperties();
        otherProps.setSecretKey("different-test-secret-key-here");
        UpstreamApiKeyEncryptor otherEncryptor = new UpstreamApiKeyEncryptor(otherProps);

        String plaintext = "sk-same-plaintext";
        String encrypted1 = encryptor.encrypt(plaintext);
        String encrypted2 = otherEncryptor.encrypt(plaintext);

        assertThat(encryptor.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThatThrownBy(() -> otherEncryptor.decrypt(encrypted1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
