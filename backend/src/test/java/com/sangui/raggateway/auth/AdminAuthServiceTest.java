package com.sangui.raggateway.auth;

import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.PasswordHasher;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private UserService userService;

    private PasswordHasher passwordHasher;
    private AdminJwtService adminJwtService;
    private AdminAuthService adminAuthService;

    private UserEntity activeUser;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
        adminJwtService = new AdminJwtService("test-secret-key-for-jwt-signing-min-256-bits!!", 3600);
        adminAuthService = new AdminAuthService(userService, passwordHasher, adminJwtService);

        String passwordHash = passwordHasher.hash("correct-password");
        activeUser = new UserEntity();
        activeUser.setId(100L);
        activeUser.setUsername("admin");
        activeUser.setPasswordHash(passwordHash);
        activeUser.setStatus("ACTIVE");
    }

    @Test
    void shouldLoginWithValidCredentials() {
        when(userService.findByUsername("admin")).thenReturn(activeUser);
        when(userService.isActive(activeUser)).thenReturn(true);

        AdminAuthService.AdminLoginResult result = adminAuthService.login("admin", "correct-password");
        assertThat(result).isNotNull();
        assertThat(result.getToken()).isNotBlank();
        assertThat(result.getPayload().getUserId()).isEqualTo(100L);
        assertThat(result.getPayload().getUsername()).isEqualTo("admin");
        assertThat(result.getPayload().getExpiresAt()).isNotNull();
        assertThat(result.getUser()).isSameAs(activeUser);
    }

    @Test
    void shouldReturnNullForWrongPassword() {
        when(userService.findByUsername("admin")).thenReturn(activeUser);

        AdminAuthService.AdminLoginResult result = adminAuthService.login("admin", "wrong-password");
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForUnknownUsername() {
        when(userService.findByUsername("unknown")).thenReturn(null);

        AdminAuthService.AdminLoginResult result = adminAuthService.login("unknown", "any-password");
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForDisabledUser() {
        UserEntity disabledUser = new UserEntity();
        disabledUser.setId(200L);
        disabledUser.setUsername("disabled");
        disabledUser.setPasswordHash(passwordHasher.hash("password"));
        disabledUser.setStatus("DISABLED");
        when(userService.findByUsername("disabled")).thenReturn(disabledUser);

        AdminAuthService.AdminLoginResult result = adminAuthService.login("disabled", "password");
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForBlankUsername() {
        assertThat(adminAuthService.login(null, "password")).isNull();
        assertThat(adminAuthService.login("  ", "password")).isNull();
    }

    @Test
    void shouldReturnNullForBlankPassword() {
        assertThat(adminAuthService.login("admin", null)).isNull();
        assertThat(adminAuthService.login("admin", "  ")).isNull();
    }
}
