package com.sangui.raggateway.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.apikey.ApiKeyService;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.security.ApiKeyGenerator;
import com.sangui.raggateway.common.security.ApiKeyHasher;
import com.sangui.raggateway.common.security.GatewayAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class GatewayAuthConfig {

    @Bean
    public ApiKeyGenerator apiKeyGenerator() {
        return new ApiKeyGenerator();
    }

    @Bean
    public ApiKeyHasher apiKeyHasher() {
        return new ApiKeyHasher();
    }

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(ApiKeyHasher apiKeyHasher,
                                               ApiKeyService apiKeyService,
                                               AppService appService,
                                               ObjectMapper objectMapper) {
        return new GatewayAuthFilter(apiKeyHasher, apiKeyService, appService, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<GatewayAuthFilter> gatewayAuthFilterRegistration(
            GatewayAuthFilter gatewayAuthFilter) {
        FilterRegistrationBean<GatewayAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(gatewayAuthFilter);
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
