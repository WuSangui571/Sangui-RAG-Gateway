package com.sangui.raggateway.common.security;

import com.sangui.raggateway.common.config.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class UpstreamApiKeyEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String VERSION = "v1";

    private final SecretKey aesKey;

    public UpstreamApiKeyEncryptor(EncryptionProperties properties) {
        String secret = properties.getSecretKey();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("rag.gateway.secret-key must not be blank");
        }
        this.aesKey = deriveKey(secret);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = generateRandomIv();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            String ivEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(iv);
            String ctEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
            return VERSION + ":" + ivEncoded + ":" + ctEncoded;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt upstream API key", e);
        }
    }

    public String decrypt(String encryptedPayload) {
        if (encryptedPayload == null || encryptedPayload.isBlank()) {
            throw new IllegalArgumentException("encryptedPayload must not be blank");
        }
        try {
            String[] parts = encryptedPayload.split(":", 3);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("Malformed encrypted payload");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to decrypt upstream API key", e);
        }
    }

    private SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private byte[] generateRandomIv() {
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
