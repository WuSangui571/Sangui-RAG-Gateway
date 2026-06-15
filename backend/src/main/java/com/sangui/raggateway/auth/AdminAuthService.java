package com.sangui.raggateway.auth;

import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.PasswordHasher;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class AdminAuthService {

    private final UserService userService;
    private final PasswordHasher passwordHasher;
    private final AdminJwtService adminJwtService;

    public AdminAuthService(UserService userService, PasswordHasher passwordHasher, AdminJwtService adminJwtService) {
        this.userService = userService;
        this.passwordHasher = passwordHasher;
        this.adminJwtService = adminJwtService;
    }

    public AdminLoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        UserEntity user = userService.findByUsername(username.trim());
        if (user == null) {
            return null;
        }

        if (!passwordHasher.verify(password, user.getPasswordHash())) {
            return null;
        }

        if (!userService.isActive(user)) {
            return null;
        }

        String token = adminJwtService.createToken(user.getId(), user.getUsername());
        AdminJwtService.AdminJwtPayload payload = adminJwtService.validateToken(token);
        if (payload == null) {
            return null;
        }
        return new AdminLoginResult(token, payload, user);
    }

    public static class AdminLoginResult {
        private final String token;
        private final AdminJwtService.AdminJwtPayload payload;
        private final UserEntity user;

        public AdminLoginResult(String token, AdminJwtService.AdminJwtPayload payload, UserEntity user) {
            this.token = token;
            this.payload = payload;
            this.user = user;
        }

        public String getToken() {
            return token;
        }

        public AdminJwtService.AdminJwtPayload getPayload() {
            return payload;
        }

        public UserEntity getUser() {
            return user;
        }
    }
}
