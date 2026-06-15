package com.sangui.raggateway.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.common.security.AdminAuthFilter;
import com.sangui.raggateway.common.security.AdminJwtService;
import com.sangui.raggateway.common.security.PasswordHasher;
import com.sangui.raggateway.user.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class AdminAuthConfig {

    @Value("${rag.gateway.secret-key}")
    private String jwtSecret;

    @Value("${rag.admin-auth.jwt-expiration-seconds:86400}")
    private long jwtExpirationSeconds;

    @Bean
    public PasswordHasher passwordHasher() {
        return new PasswordHasher();
    }

    @Bean
    public AdminJwtService adminJwtService() {
        return new AdminJwtService(jwtSecret, jwtExpirationSeconds);
    }

    @Bean
    public AdminAuthFilter adminAuthFilter(AdminJwtService adminJwtService,
                                           UserService userService,
                                           ObjectMapper objectMapper) {
        return new AdminAuthFilter(adminJwtService, userService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilterRegistration(
            AdminAuthFilter adminAuthFilter) {
        FilterRegistrationBean<AdminAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(adminAuthFilter);
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(2);
        return registration;
    }
}
