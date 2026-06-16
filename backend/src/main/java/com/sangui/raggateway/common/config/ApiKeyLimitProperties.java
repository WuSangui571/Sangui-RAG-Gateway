package com.sangui.raggateway.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "rag.gateway.api-key-limits")
@Validated
public class ApiKeyLimitProperties {

    private boolean enabled = true;
    @Min(1)
    private int defaultRequestsPerMinute = 60;
    @Min(1)
    private int defaultTokensPerMinute = 60000;
    @Min(1)
    private int defaultDailyRequestQuota = 1000;
    @Min(1)
    private int defaultDailyTokenQuota = 1000000;
    @Min(1)
    private int defaultCompletionTokenReservation = 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultRequestsPerMinute() {
        return defaultRequestsPerMinute;
    }

    public void setDefaultRequestsPerMinute(int defaultRequestsPerMinute) {
        this.defaultRequestsPerMinute = defaultRequestsPerMinute;
    }

    public int getDefaultTokensPerMinute() {
        return defaultTokensPerMinute;
    }

    public void setDefaultTokensPerMinute(int defaultTokensPerMinute) {
        this.defaultTokensPerMinute = defaultTokensPerMinute;
    }

    public int getDefaultDailyRequestQuota() {
        return defaultDailyRequestQuota;
    }

    public void setDefaultDailyRequestQuota(int defaultDailyRequestQuota) {
        this.defaultDailyRequestQuota = defaultDailyRequestQuota;
    }

    public int getDefaultDailyTokenQuota() {
        return defaultDailyTokenQuota;
    }

    public void setDefaultDailyTokenQuota(int defaultDailyTokenQuota) {
        this.defaultDailyTokenQuota = defaultDailyTokenQuota;
    }

    public int getDefaultCompletionTokenReservation() {
        return defaultCompletionTokenReservation;
    }

    public void setDefaultCompletionTokenReservation(int defaultCompletionTokenReservation) {
        this.defaultCompletionTokenReservation = defaultCompletionTokenReservation;
    }
}
