package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRateLimitServiceTest {

    private ApiKeyLimitProperties properties;
    private ApiKeyRateLimitService service;

    private static final Long API_KEY_ID = 1L;
    private static final int MSG_CHARS = 40;
    private static final Integer MAX_TOKENS = 100;

    @BeforeEach
    void setUp() {
        properties = new ApiKeyLimitProperties();
        properties.setEnabled(true);
        properties.setDefaultRequestsPerMinute(10);
        properties.setDefaultTokensPerMinute(10000);
        properties.setDefaultDailyRequestQuota(100);
        properties.setDefaultDailyTokenQuota(100000);
        properties.setDefaultCompletionTokenReservation(1024);

        service = new TestableApiKeyRateLimitService(properties);
    }

    @Test
    void shouldEstimateTokensCorrectly() {
        int estimated = service.estimateTokens(200, 100);
        assertThat(estimated).isEqualTo(200 / 4 + 100);
    }

    @Test
    void shouldEstimateTokensWithDefaultWhenMaxTokensNull() {
        int estimated = service.estimateTokens(200, null);
        assertThat(estimated).isEqualTo(200 / 4 + properties.getDefaultCompletionTokenReservation());
    }

    @Test
    void shouldEstimateTokensWithZeroMessages() {
        int estimated = service.estimateTokens(0, 50);
        assertThat(estimated).isEqualTo(0 / 4 + 50);
    }

    @Test
    void shouldDetermineEffectiveLimitWithOverride() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        assertThat(testService.testEffectiveLimit(5, 10)).isEqualTo(5);
        assertThat(testService.testEffectiveLimit(null, 10)).isEqualTo(10);
        assertThat(testService.testEffectiveLimit(0, 10)).isEqualTo(10);
        assertThat(testService.testEffectiveLimit(-1, 10)).isEqualTo(10);
    }

    @Test
    void shouldComputeRpmResetSeconds() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 14, 30, 15);
        long reset = testService.testComputeResetSeconds("rpm", now);
        assertThat(reset).isEqualTo(45L);
    }

    @Test
    void shouldComputeTpmResetSeconds() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 14, 30, 45);
        long reset = testService.testComputeResetSeconds("tpm", now);
        assertThat(reset).isEqualTo(15L);
    }

    @Test
    void shouldComputeDailyResetSeconds() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 0, 0, 0);
        long reset = testService.testComputeResetSeconds("daily_requests", now);
        assertThat(reset).isEqualTo(86400L);
    }

    @Test
    void shouldComputeDailyTokenResetSeconds() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 23, 59, 59);
        long reset = testService.testComputeResetSeconds("daily_tokens", now);
        assertThat(reset).isEqualTo(1L);
    }

    @Test
    void shouldDefaultResetSecondsForUnknownLimitType() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 14, 30, 0);
        long reset = testService.testComputeResetSeconds("unknown", now);
        assertThat(reset).isEqualTo(60L);
    }

    @Test
    void shouldParseRejectedMixedRedisResult() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        ApiKeyRateLimitResult result = testService.testParseScriptResult(
                List.of(0L, "rpm", 10L, 100L, 20L, 1000L));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getLimitType()).isEqualTo("rpm");
        assertThat(result.getRemainingRequests()).isZero();
        assertThat(result.getRemainingTokens()).isEqualTo(9900L);
        assertThat(result.getMinuteWindow()).isEqualTo("202606151430");
        assertThat(result.getDailyWindow()).isEqualTo("20260615");
        assertThat(result.getEstimatedTokens()).isEqualTo(110);
    }

    @Test
    void shouldParseRedisBytesLimitType() {
        TestableApiKeyRateLimitService testService = (TestableApiKeyRateLimitService) service;

        ApiKeyRateLimitResult result = testService.testParseScriptResult(
                List.of(0L, "daily_tokens".getBytes(StandardCharsets.UTF_8), 1L, 100L, 2L, 100000L));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getLimitType()).isEqualTo("daily_tokens");
    }

    @Test
    void shouldRejectNonPositiveDefaultLimits() {
        ApiKeyLimitProperties invalid = new ApiKeyLimitProperties();
        invalid.setDefaultRequestsPerMinute(0);
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<ApiKeyLimitProperties>> violations = validator.validate(invalid);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("defaultRequestsPerMinute");
    }

    static class TestableApiKeyRateLimitService extends ApiKeyRateLimitService {

        TestableApiKeyRateLimitService(ApiKeyLimitProperties properties) {
            super(null, properties, null);
        }

        int testEffectiveLimit(Integer override, int defaultValue) {
            return effectiveLimit(override, defaultValue);
        }

        long testComputeResetSeconds(String limitType, java.time.LocalDateTime now) {
            return computeResetSeconds(limitType, now);
        }

        ApiKeyRateLimitResult testParseScriptResult(List<?> result) {
            java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 6, 15, 14, 30, 15);
            return parseScriptResult(API_KEY_ID, result, 10, 10000, 110,
                    now, "202606151430", "20260615");
        }
    }
}
