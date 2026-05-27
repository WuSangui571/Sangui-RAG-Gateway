package com.sangui.raggateway.common.security;

public class GatewayRequestContextHolder {

    private static final ThreadLocal<GatewayRequestContext> CONTEXT = new ThreadLocal<>();

    public static void set(GatewayRequestContext context) {
        CONTEXT.set(context);
    }

    public static GatewayRequestContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
