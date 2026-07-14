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

    /**
     * 获取当前用户上下文，为 null 时抛出明确异常而非 NPE。
     */
    public static UserContextInfo getRequired() {
        UserContextInfo user = CONTEXT.get();
        if (user == null) {
            throw new IllegalStateException("当前未登录或会话已过期，请重新登录");
        }
        return user;
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
