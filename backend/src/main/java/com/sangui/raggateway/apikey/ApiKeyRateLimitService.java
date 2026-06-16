package com.sangui.raggateway.apikey;

import com.sangui.raggateway.common.config.ApiKeyLimitProperties;
import com.sangui.raggateway.common.exception.GatewayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class ApiKeyRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRateLimitService.class);

    private static final String KEY_PREFIX = "rag:api-key-limit";
    private static final int MINUTE_TTL_SECONDS = 120;
    private static final int DAILY_TTL_SECONDS = 172800;
    private static final int CHARS_PER_TOKEN = 4;

    private static final DateTimeFormatter MINUTE_WINDOW = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final DateTimeFormatter DAILY_WINDOW = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StringRedisTemplate redisTemplate;
    private final ApiKeyLimitProperties properties;
    private final ApiKeyService apiKeyService;

    public ApiKeyRateLimitService(StringRedisTemplate redisTemplate,
                                  ApiKeyLimitProperties properties,
                                  ApiKeyService apiKeyService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.apiKeyService = apiKeyService;
    }

    public ApiKeyRateLimitResult checkAndReserve(Long apiKeyId, int messagesCharCount, Integer maxTokens) {
        ApiKeyEntity apiKey = apiKeyService.findById(apiKeyId);
        return checkWithEntity(apiKeyId, apiKey, messagesCharCount, maxTokens);
    }

    public ApiKeyRateLimitResult checkWithEntity(Long apiKeyId, ApiKeyEntity apiKey,
                                                  int messagesCharCount, Integer maxTokens) {
        int rpmLimit = effectiveLimit(apiKey != null ? apiKey.getRequestsPerMinute() : null,
                properties.getDefaultRequestsPerMinute());
        int tpmLimit = effectiveLimit(apiKey != null ? apiKey.getTokensPerMinute() : null,
                properties.getDefaultTokensPerMinute());
        int dailyReqLimit = effectiveLimit(apiKey != null ? apiKey.getDailyRequestQuota() : null,
                properties.getDefaultDailyRequestQuota());
        int dailyTokLimit = effectiveLimit(apiKey != null ? apiKey.getDailyTokenQuota() : null,
                properties.getDefaultDailyTokenQuota());
        int completionTokenReservation = maxTokens != null ? maxTokens : properties.getDefaultCompletionTokenReservation();
        int estimatedTokens = (messagesCharCount / CHARS_PER_TOKEN) + completionTokenReservation;

        return executeCheck(apiKeyId, rpmLimit, tpmLimit, dailyReqLimit, dailyTokLimit, estimatedTokens);
    }

    public int estimateTokens(int messagesCharCount, Integer maxTokens) {
        int completionTokenReservation = maxTokens != null ? maxTokens : properties.getDefaultCompletionTokenReservation();
        return (messagesCharCount / CHARS_PER_TOKEN) + completionTokenReservation;
    }

    private ApiKeyRateLimitResult executeCheck(Long apiKeyId, int rpmLimit, int tpmLimit,
                                                int dailyReqLimit, int dailyTokLimit, int estimatedTokens) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String minuteSuffix = MINUTE_WINDOW.format(now);
        String dailySuffix = DAILY_WINDOW.format(now);

        String rpmKey = KEY_PREFIX + ":" + apiKeyId + ":rpm:" + minuteSuffix;
        String tpmKey = KEY_PREFIX + ":" + apiKeyId + ":tpm:" + minuteSuffix;
        String dailyReqKey = KEY_PREFIX + ":" + apiKeyId + ":daily-requests:" + dailySuffix;
        String dailyTokKey = KEY_PREFIX + ":" + apiKeyId + ":daily-tokens:" + dailySuffix;

        String script = """
                local rpm_key = KEYS[1]
                local tpm_key = KEYS[2]
                local daily_req_key = KEYS[3]
                local daily_tok_key = KEYS[4]
                local rpm_limit = tonumber(ARGV[1])
                local tpm_limit = tonumber(ARGV[2])
                local daily_req_limit = tonumber(ARGV[3])
                local daily_tok_limit = tonumber(ARGV[4])
                local estimated_tokens = tonumber(ARGV[5])
                local minute_ttl = tonumber(ARGV[6])
                local daily_ttl = tonumber(ARGV[7])

                local rpm = tonumber(redis.call('GET', rpm_key)) or 0
                local tpm = tonumber(redis.call('GET', tpm_key)) or 0
                local daily_req = tonumber(redis.call('GET', daily_req_key)) or 0
                local daily_tok = tonumber(redis.call('GET', daily_tok_key)) or 0

                if rpm + 1 > rpm_limit then
                    return {0, 'rpm', rpm, tpm, daily_req, daily_tok}
                end
                if tpm + estimated_tokens > tpm_limit then
                    return {0, 'tpm', rpm, tpm, daily_req, daily_tok}
                end
                if daily_req + 1 > daily_req_limit then
                    return {0, 'daily_requests', rpm, tpm, daily_req, daily_tok}
                end
                if daily_tok + estimated_tokens > daily_tok_limit then
                    return {0, 'daily_tokens', rpm, tpm, daily_req, daily_tok}
                end

                rpm = redis.call('INCR', rpm_key)
                tpm = redis.call('INCRBY', tpm_key, estimated_tokens)
                daily_req = redis.call('INCR', daily_req_key)
                daily_tok = redis.call('INCRBY', daily_tok_key, estimated_tokens)

                if rpm == 1 then redis.call('EXPIRE', rpm_key, minute_ttl) end
                if tpm == estimated_tokens then redis.call('EXPIRE', tpm_key, minute_ttl) end
                if daily_req == 1 then redis.call('EXPIRE', daily_req_key, daily_ttl) end
                if daily_tok == estimated_tokens then redis.call('EXPIRE', daily_tok_key, daily_ttl) end

                return {1, '', rpm, tpm, daily_req, daily_tok}
                """;

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>(script, List.class);
        try {
            List<?> result = redisTemplate.execute(redisScript,
                    List.of(rpmKey, tpmKey, dailyReqKey, dailyTokKey),
                    String.valueOf(rpmLimit),
                    String.valueOf(tpmLimit),
                    String.valueOf(dailyReqLimit),
                    String.valueOf(dailyTokLimit),
                    String.valueOf(estimatedTokens),
                    String.valueOf(MINUTE_TTL_SECONDS),
                    String.valueOf(DAILY_TTL_SECONDS));

            if (result == null || result.isEmpty()) {
                log.error("Redis rate-limit script returned null/empty for apiKeyId={}", apiKeyId);
                throw rateLimitUnavailable(null);
            }

            return parseScriptResult(apiKeyId, result, rpmLimit, tpmLimit, estimatedTokens,
                    now, minuteSuffix, dailySuffix);
        } catch (GatewayException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Redis rate-limit execution failed for apiKeyId={} errorClass={}",
                    apiKeyId, e.getClass().getSimpleName());
            throw rateLimitUnavailable(e);
        }
    }

    ApiKeyRateLimitResult parseScriptResult(Long apiKeyId, List<?> result, int rpmLimit, int tpmLimit,
                                             int estimatedTokens, LocalDateTime now,
                                             String minuteSuffix, String dailySuffix) {
        long allowed = toLong(result.get(0));
        long rpm = result.size() > 2 ? toLong(result.get(2)) : 0;
        long tpm = result.size() > 3 ? toLong(result.get(3)) : 0;

        long remainingReqs = Math.max(0, rpmLimit - rpm);
        long remainingToks = Math.max(0, tpmLimit - tpm);

        if (allowed == 1L) {
            return ApiKeyRateLimitResult.allowed(remainingReqs, remainingToks,
                    minuteSuffix, dailySuffix, estimatedTokens);
        }

        String limitType = result.size() > 1 && result.get(1) != null ? toText(result.get(1)) : "unknown";
        long resetSeconds = computeResetSeconds(limitType, now);
        log.warn("Rate limit hit: apiKeyId={} limitType={} window={}",
                apiKeyId, limitType, limitType.startsWith("daily") ? dailySuffix : minuteSuffix);
        return ApiKeyRateLimitResult.rejected(limitType, remainingReqs, remainingToks, resetSeconds,
                minuteSuffix, dailySuffix, estimatedTokens);
    }

    public void reconcileTokens(Long apiKeyId, ApiKeyRateLimitResult reservation, int actualTotalTokens) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        if (reservation.getEstimatedTokens() == actualTotalTokens) {
            return;
        }

        long diff = (long) actualTotalTokens - reservation.getEstimatedTokens();
        String tpmKey = KEY_PREFIX + ":" + apiKeyId + ":tpm:" + reservation.getMinuteWindow();
        String dailyTokKey = KEY_PREFIX + ":" + apiKeyId + ":daily-tokens:" + reservation.getDailyWindow();

        String script = """
                local tpm_key = KEYS[1]
                local daily_tok_key = KEYS[2]
                local diff = tonumber(ARGV[1])
                if diff ~= 0 then
                    redis.call('INCRBY', tpm_key, diff)
                    redis.call('INCRBY', daily_tok_key, diff)
                end
                return 1
                """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        executeReservationAdjustment(apiKeyId, redisScript, tpmKey, dailyTokKey, String.valueOf(diff));
    }

    public void releaseReservation(Long apiKeyId, ApiKeyRateLimitResult reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        String tpmKey = KEY_PREFIX + ":" + apiKeyId + ":tpm:" + reservation.getMinuteWindow();
        String dailyTokKey = KEY_PREFIX + ":" + apiKeyId + ":daily-tokens:" + reservation.getDailyWindow();

        String script = """
                local tpm_key = KEYS[1]
                local daily_tok_key = KEYS[2]
                local estimated = tonumber(ARGV[1])
                redis.call('DECRBY', tpm_key, estimated)
                redis.call('DECRBY', daily_tok_key, estimated)
                return 1
                """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);
        executeReservationAdjustment(apiKeyId, redisScript, tpmKey, dailyTokKey,
                String.valueOf(reservation.getEstimatedTokens()));
    }

    private void executeReservationAdjustment(Long apiKeyId, DefaultRedisScript<Long> redisScript,
                                               String tpmKey, String dailyTokKey, String amount) {
        try {
            redisTemplate.execute(redisScript, List.of(tpmKey, dailyTokKey), amount);
        } catch (RuntimeException e) {
            log.error("Redis rate-limit reservation adjustment failed for apiKeyId={} errorClass={}",
                    apiKeyId, e.getClass().getSimpleName());
            throw rateLimitUnavailable(e);
        }
    }

    private GatewayException rateLimitUnavailable(Throwable cause) {
        if (cause == null) {
            return new GatewayException("Rate limit service unavailable.",
                    "server_error", "internal_error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new GatewayException("Rate limit service unavailable.",
                "server_error", "internal_error", HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String toText(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    int effectiveLimit(Integer override, int defaultValue) {
        return (override != null && override > 0) ? override : defaultValue;
    }

    long computeResetSeconds(String limitType, LocalDateTime now) {
        if ("rpm".equals(limitType) || "tpm".equals(limitType)) {
            int secondOfMinute = now.getSecond();
            return 60L - secondOfMinute;
        }
        if ("daily_requests".equals(limitType) || "daily_tokens".equals(limitType)) {
            return 86400L - now.toLocalTime().toSecondOfDay();
        }
        return 60L;
    }
}
