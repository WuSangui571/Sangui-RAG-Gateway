package com.sangui.raggateway.common.security;

public class AdminAuthContextHolder {

    private static final ThreadLocal<AdminAuthContext> CONTEXT = new ThreadLocal<>();

    public static void set(AdminAuthContext context) {
        CONTEXT.set(context);
    }

    public static AdminAuthContext get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        AdminAuthContext ctx = CONTEXT.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
