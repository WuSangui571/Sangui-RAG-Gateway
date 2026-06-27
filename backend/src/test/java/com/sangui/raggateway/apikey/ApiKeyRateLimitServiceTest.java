package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.exception.GatewayException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

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

    @Nested
    class ScriptOwnership {

        @Test
        void shouldDefineCheckScriptAsStaticFinalDefaultRedisScript() throws Exception {
            Field field = staticFinalField("CHECK_SCRIPT");
            assertThat(field.get(null))
                    .isNotNull()
                    .isInstanceOf(DefaultRedisScript.class);
        }

        @Test
        void shouldDefineReconcileScriptAsStaticFinalDefaultRedisScript() throws Exception {
            Field field = staticFinalField("RECONCILE_SCRIPT");
            assertThat(field.get(null))
                    .isNotNull()
                    .isInstanceOf(DefaultRedisScript.class);
        }

        @Test
        void shouldDefineReleaseScriptAsStaticFinalDefaultRedisScript() throws Exception {
            Field field = staticFinalField("RELEASE_SCRIPT");
            assertThat(field.get(null))
                    .isNotNull()
                    .isInstanceOf(DefaultRedisScript.class);
        }

        @Test
        void shouldDefineCheckScriptTextAsNonEmptyStaticFinal() throws Exception {
            Field field = staticFinalField("CHECK_SCRIPT_TEXT");
            String text = (String) field.get(null);
            assertThat(text).isNotNull().isNotEmpty()
                    .contains("redis.call('INCR', rpm_key)")
                    .contains("redis.call('INCRBY', tpm_key, estimated_tokens)");
        }

        @Test
        void shouldDefineReconcileScriptTextAsNonEmptyStaticFinal() throws Exception {
            Field field = staticFinalField("RECONCILE_SCRIPT_TEXT");
            String text = (String) field.get(null);
            assertThat(text).isNotNull().isNotEmpty()
                    .contains("redis.call('INCRBY', tpm_key, diff)")
                    .contains("redis.call('INCRBY', daily_tok_key, diff)");
        }

        @Test
        void shouldDefineReleaseScriptTextAsNonEmptyStaticFinal() throws Exception {
            Field field = staticFinalField("RELEASE_SCRIPT_TEXT");
            String text = (String) field.get(null);
            assertThat(text).isNotNull().isNotEmpty()
                    .contains("redis.call('DECRBY', tpm_key, estimated)")
                    .contains("redis.call('DECRBY', daily_tok_key, estimated)");
        }

        @Test
        void shouldReuseSameCheckScriptInstanceAcrossFieldAccess() throws Exception {
            Field field = staticFinalField("CHECK_SCRIPT");
            Object first = field.get(null);
            Object second = field.get(null);
            assertThat(first).isSameAs(second);
        }

        @Test
        void shouldExecuteAllowCheckWithReusableScriptAndSameRedisContract() throws Exception {
            StringRedisTemplateFixture fixture = StringRedisTemplateFixture.create();
            doReturn(List.of(1L, "", 1L, 110L, 1L, 110L))
                    .when(fixture.redisTemplate)
                    .execute(anyScript(), anyList(), any(), any(), any(), any(), any(), any(), any());

            ApiKeyRateLimitResult result = fixture.service.checkWithEntity(API_KEY_ID, null, MSG_CHARS, MAX_TOKENS);

            assertThat(result.isAllowed()).isTrue();
            assertThat(result.getRemainingRequests()).isEqualTo(9L);
            assertThat(result.getRemainingTokens()).isEqualTo(9890L);
            verify(fixture.redisTemplate).execute(same(script("CHECK_SCRIPT")),
                    anyList(),
                    eq("10"),
                    eq("10000"),
                    eq("100"),
                    eq("100000"),
                    eq("110"),
                    eq("120"),
                    eq("172800"));
        }

        @Test
        void shouldExecuteRejectCheckWithReusableScriptAndSameRedisContract() throws Exception {
            StringRedisTemplateFixture fixture = StringRedisTemplateFixture.create();
            doReturn(List.of(0L, "daily_tokens", 1L, 100L, 2L, 100000L))
                    .when(fixture.redisTemplate)
                    .execute(anyScript(), anyList(), any(), any(), any(), any(), any(), any(), any());

            ApiKeyRateLimitResult result = fixture.service.checkWithEntity(API_KEY_ID, null, MSG_CHARS, MAX_TOKENS);

            assertThat(result.isAllowed()).isFalse();
            assertThat(result.getLimitType()).isEqualTo("daily_tokens");
            verify(fixture.redisTemplate).execute(same(script("CHECK_SCRIPT")),
                    anyList(),
                    eq("10"),
                    eq("10000"),
                    eq("100"),
                    eq("100000"),
                    eq("110"),
                    eq("120"),
                    eq("172800"));
        }

        @Test
        void shouldKeepRedisFailureVisibleWhenCheckScriptFails() {
            StringRedisTemplateFixture fixture = StringRedisTemplateFixture.create();
            doThrow(new IllegalStateException("redis unavailable"))
                    .when(fixture.redisTemplate)
                    .execute(anyScript(), anyList(), any(), any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() -> fixture.service.checkWithEntity(API_KEY_ID, null, MSG_CHARS, MAX_TOKENS))
                    .isInstanceOf(GatewayException.class)
                    .extracting("code", "type")
                    .containsExactly("internal_error", "server_error");
        }

        @Test
        void shouldExecuteReconcileWithReusableScriptAndReservedWindows() throws Exception {
            StringRedisTemplateFixture fixture = StringRedisTemplateFixture.create();
            ApiKeyRateLimitResult reservation = ApiKeyRateLimitResult.allowed(
                    9, 9890, "202606271430", "20260627", 110);

            fixture.service.reconcileTokens(API_KEY_ID, reservation, 91);

            verify(fixture.redisTemplate).execute(same(script("RECONCILE_SCRIPT")),
                    eq(List.of(
                            "rag:api-key-limit:1:tpm:202606271430",
                            "rag:api-key-limit:1:daily-tokens:20260627")),
                    eq("-19"));
        }

        @Test
        void shouldExecuteReleaseWithReusableScriptAndReservedWindows() throws Exception {
            StringRedisTemplateFixture fixture = StringRedisTemplateFixture.create();
            ApiKeyRateLimitResult reservation = ApiKeyRateLimitResult.allowed(
                    9, 9890, "202606271430", "20260627", 110);

            fixture.service.releaseReservation(API_KEY_ID, reservation);

            verify(fixture.redisTemplate).execute(same(script("RELEASE_SCRIPT")),
                    eq(List.of(
                            "rag:api-key-limit:1:tpm:202606271430",
                            "rag:api-key-limit:1:daily-tokens:20260627")),
                    eq("110"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> RedisScript<T> script(String fieldName) throws Exception {
        return (RedisScript<T>) staticFinalField(fieldName).get(null);
    }

    private static Field staticFinalField(String fieldName) throws Exception {
        Field field = ApiKeyRateLimitService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
        return field;
    }

    @SuppressWarnings("unchecked")
    private static RedisScript<List> anyScript() {
        return any(RedisScript.class);
    }

    private static class StringRedisTemplateFixture {

        private final StringRedisTemplate redisTemplate;
        private final ApiKeyRateLimitService service;

        private StringRedisTemplateFixture(StringRedisTemplate redisTemplate,
                                           ApiKeyRateLimitService service) {
            this.redisTemplate = redisTemplate;
            this.service = service;
        }

        private static StringRedisTemplateFixture create() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            ApiKeyRateLimitService service = new ApiKeyRateLimitService(
                    redisTemplate, defaultProperties(), mock(ApiKeyService.class));
            return new StringRedisTemplateFixture(redisTemplate, service);
        }
    }

    private static ApiKeyLimitProperties defaultProperties() {
        ApiKeyLimitProperties properties = new ApiKeyLimitProperties();
        properties.setEnabled(true);
        properties.setDefaultRequestsPerMinute(10);
        properties.setDefaultTokensPerMinute(10000);
        properties.setDefaultDailyRequestQuota(100);
        properties.setDefaultDailyTokenQuota(100000);
        properties.setDefaultCompletionTokenReservation(1024);
        return properties;
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
