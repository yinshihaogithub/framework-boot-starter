package com.framework.auth.context;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class UserContextHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        LoginUser user = get();
        return user != null ? user.getUserId() : null;
    }

    public static String getUsername() {
        LoginUser user = get();
        return user != null ? user.getUsername() : null;
    }

    public static String getTenantId() {
        LoginUser user = get();
        return user != null ? user.getTenantId() : null;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
