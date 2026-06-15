package com.sangui.raggateway.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminJwtServiceTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-min-256-bits!!";

    @Test
    void shouldRejectBlankSecret() {
        assertThatThrownBy(() -> new AdminJwtService("", 3600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullSecret() {
        assertThatThrownBy(() -> new AdminJwtService(null, 3600))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveExpiration() {
        assertThatThrownBy(() -> new AdminJwtService(SECRET, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCreateAndValidateToken() {
        AdminJwtService service = new AdminJwtService(SECRET, 3600);
        String token = service.createToken(100L, "admin");
        assertThat(token).isNotNull();

        AdminJwtService.AdminJwtPayload payload = service.validateToken(token);
        assertThat(payload).isNotNull();
        assertThat(payload.getUserId()).isEqualTo(100L);
        assertThat(payload.getUsername()).isEqualTo("admin");
        assertThat(payload.getExpiresAt()).isNotNull();
    }

    @Test
    void shouldRejectMalformedToken() {
        AdminJwtService service = new AdminJwtService(SECRET, 3600);
        assertThat(service.validateToken("invalid-token")).isNull();
    }

    @Test
    void shouldRejectEmptyToken() {
        AdminJwtService service = new AdminJwtService(SECRET, 3600);
        assertThat(service.validateToken("")).isNull();
    }

    @Test
    void shouldRejectNullToken() {
        AdminJwtService service = new AdminJwtService(SECRET, 3600);
        assertThat(service.validateToken(null)).isNull();
    }

    @Test
    void shouldRejectExpiredToken() {
        AdminJwtService service = new AdminJwtService(SECRET, 1);
        String token = service.createToken(100L, "admin");
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertThat(service.validateToken(token)).isNull();
    }

    @Test
    void shouldRejectTokenWithDifferentSecret() {
        AdminJwtService service1 = new AdminJwtService(SECRET, 3600);
        AdminJwtService service2 = new AdminJwtService("different-secret-key-with-enough-length!!", 3600);
        String token = service1.createToken(100L, "admin");
        assertThat(service2.validateToken(token)).isNull();
    }

    @Test
    void shouldCreateTokensWithDifferentExpiry() {
        AdminJwtService service = new AdminJwtService(SECRET, 60);
        String token = service.createToken(200L, "user1");
        AdminJwtService.AdminJwtPayload payload = service.validateToken(token);
        assertThat(payload).isNotNull();
        assertThat(payload.getUserId()).isEqualTo(200L);
        assertThat(payload.getUsername()).isEqualTo("user1");
    }
}
