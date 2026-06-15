package com.sangui.raggateway.common.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    private static final int BCRYPT_ROUNDS = 12;

    public String hash(String plaintextPassword) {
        if (plaintextPassword == null || plaintextPassword.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        return BCrypt.hashpw(plaintextPassword, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    public boolean verify(String plaintextPassword, String hash) {
        if (plaintextPassword == null || plaintextPassword.isBlank()) {
            return false;
        }
        if (hash == null || hash.isBlank()) {
            return false;
        }
        return BCrypt.checkpw(plaintextPassword, hash);
    }
}
