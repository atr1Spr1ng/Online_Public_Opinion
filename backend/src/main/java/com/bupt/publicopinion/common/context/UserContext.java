package com.bupt.publicopinion.common.context;

/**
 * 当前请求用户上下文。
 * 由 JwtAuthenticationFilter 写入，业务层通过 getCurrentUser() 读取。
 */
public final class UserContext {

    private static final ThreadLocal<UserContextInfo> CONTEXT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserContextInfo user) {
        CONTEXT.set(user);
    }

    public static UserContextInfo get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 轻量级用户上下文信息，避免每次从数据库查询。
     */
    public record UserContextInfo(
            Long userId,
            String username,
            String role
    ) {
    }
}
