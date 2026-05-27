package com.sangui.raggateway.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyEntity;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.response.OpenAiErrorResponse;
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

public class GatewayAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthFilter.class);

    private static final String V1_PATH_PREFIX = "/v1/";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";

    private static final String ERROR_MESSAGE = "Invalid API key.";
    private static final String ERROR_TYPE = "invalid_request_error";
    private static final String ERROR_CODE = "invalid_api_key";

    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyService apiKeyService;
    private final AppService appService;
    private final ObjectMapper objectMapper;

    public GatewayAuthFilter(ApiKeyHasher apiKeyHasher,
                             ApiKeyService apiKeyService,
                             AppService appService,
                             ObjectMapper objectMapper) {
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyService = apiKeyService;
        this.appService = appService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith(V1_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String bearerToken = extractBearerToken(request);
            GatewayRequestContext context = authenticate(bearerToken);
            GatewayRequestContextHolder.set(context);
            filterChain.doFilter(request, response);
        } catch (AuthFailureException e) {
            log.warn("Gateway auth failed: reason={}", e.getReason());
            writeAuthError(response);
        } finally {
            GatewayRequestContextHolder.clear();
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

    GatewayRequestContext authenticate(String rawToken) {
        if (!ApiKeyGenerator.hasValidPrefix(rawToken)) {
            throw new AuthFailureException("malformed_key_prefix");
        }

        String keyHash = apiKeyHasher.hash(rawToken);
        ApiKeyEntity apiKey = apiKeyService.findByHash(keyHash);
        if (apiKey == null) {
            throw new AuthFailureException("unknown_key_hash");
        }

        if (!apiKeyService.isValid(apiKey)) {
            throw new AuthFailureException("invalid_key_status:" + apiKey.getStatus());
        }

        AppEntity app = appService.findById(apiKey.getAppId());
        if (app == null || !appService.isEnabled(app)) {
            throw new AuthFailureException("app_disabled_or_missing");
        }

        apiKeyService.updateLastUsed(apiKey.getId());

        return new GatewayRequestContext(
                app.getId(),
                app.getUserId(),
                apiKey.getId(),
                apiKey.getKeyPrefix()
        );
    }

    private void writeAuthError(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        OpenAiErrorResponse errorResponse = OpenAiErrorResponse.of(ERROR_MESSAGE, ERROR_TYPE, ERROR_CODE);
        objectMapper.writeValue(response.getWriter(), errorResponse);
    }

    static class AuthFailureException extends RuntimeException {
        private final String reason;

        AuthFailureException(String reason) {
            super("Auth failure: " + reason);
            this.reason = reason;
        }

        String getReason() {
            return reason;
        }
    }
}
