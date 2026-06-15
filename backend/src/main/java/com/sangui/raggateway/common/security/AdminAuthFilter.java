package com.sangui.raggateway.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.user.UserEntity;
import com.sangui.raggateway.user.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String LOGIN_PATH = "/api/admin/auth/login";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private final AdminJwtService adminJwtService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AdminAuthFilter(AdminJwtService adminJwtService, UserService userService, ObjectMapper objectMapper) {
        this.adminJwtService = adminJwtService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(ADMIN_PATH_PREFIX) || LOGIN_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String bearerToken = extractBearerToken(request);
            AdminJwtService.AdminJwtPayload payload = adminJwtService.validateToken(bearerToken);
            if (payload == null) {
                writeAuthError(response, "UNAUTHORIZED", "Invalid or expired token");
                return;
            }

            UserEntity user = userService.findById(payload.getUserId());
            if (user == null || !userService.isActive(user)) {
                writeAuthError(response, "UNAUTHORIZED", "User not found or disabled");
                return;
            }

            AdminAuthContext context = new AdminAuthContext(user.getId(), user.getUsername());
            AdminAuthContextHolder.set(context);
            filterChain.doFilter(request, response);
        } catch (AuthFailureException e) {
            log.warn("Admin auth failed: reason={}", e.getReason());
            writeAuthError(response, "UNAUTHORIZED", "Authentication required");
        } finally {
            AdminAuthContextHolder.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || header.isBlank()) {
            throw new AuthFailureException("missing_authorization");
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            throw new AuthFailureException("non_bearer_scheme");
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new AuthFailureException("empty_token");
        }
        return token;
    }

    private void writeAuthError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> errorResponse = ApiResponse.error(code, message);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    static class AuthFailureException extends RuntimeException {
        private final String reason;

        AuthFailureException(String reason) {
            super("Admin auth failure: " + reason);
            this.reason = reason;
        }

        String getReason() {
            return reason;
        }
    }
}
