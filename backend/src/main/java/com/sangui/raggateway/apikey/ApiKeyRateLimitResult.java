package com.sangui.raggateway.apikey;

public class ApiKeyRateLimitResult {

    private final boolean allowed;
    private final String limitType;
    private final long remainingRequests;
    private final long remainingTokens;
    private final long resetSeconds;
    private final String minuteWindow;
    private final String dailyWindow;
    private final int estimatedTokens;

    private ApiKeyRateLimitResult(boolean allowed, String limitType, long remainingRequests, long remainingTokens,
                                  long resetSeconds, String minuteWindow, String dailyWindow, int estimatedTokens) {
        this.allowed = allowed;
        this.limitType = limitType;
        this.remainingRequests = remainingRequests;
        this.remainingTokens = remainingTokens;
        this.resetSeconds = resetSeconds;
        this.minuteWindow = minuteWindow;
        this.dailyWindow = dailyWindow;
        this.estimatedTokens = estimatedTokens;
    }

    public static ApiKeyRateLimitResult allowed(long remainingRequests, long remainingTokens) {
        return allowed(remainingRequests, remainingTokens, null, null, 0);
    }

    public static ApiKeyRateLimitResult allowed(long remainingRequests, long remainingTokens,
                                                String minuteWindow, String dailyWindow, int estimatedTokens) {
        return new ApiKeyRateLimitResult(true, null, remainingRequests, remainingTokens, 0,
                minuteWindow, dailyWindow, estimatedTokens);
    }

    public static ApiKeyRateLimitResult rejected(String limitType, long remainingRequests, long remainingTokens, long resetSeconds) {
        return rejected(limitType, remainingRequests, remainingTokens, resetSeconds, null, null, 0);
    }

    public static ApiKeyRateLimitResult rejected(String limitType, long remainingRequests, long remainingTokens,
                                                 long resetSeconds, String minuteWindow, String dailyWindow,
                                                 int estimatedTokens) {
        return new ApiKeyRateLimitResult(false, limitType, remainingRequests, remainingTokens, resetSeconds,
                minuteWindow, dailyWindow, estimatedTokens);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getLimitType() {
        return limitType;
    }

    public long getRemainingRequests() {
        return remainingRequests;
    }

    public long getRemainingTokens() {
        return remainingTokens;
    }

    public long getResetSeconds() {
        return resetSeconds;
    }

    public String getMinuteWindow() {
        return minuteWindow;
    }

    public String getDailyWindow() {
        return dailyWindow;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }
}
