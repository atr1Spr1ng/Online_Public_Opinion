package com.bupt.publicopinion.common.enums;

/**
 * 用户角色枚举。
 * 使用 String 类型便于未来扩展新角色，无需数据库迁移。
 */
public enum UserRole {
    ADMIN("ADMIN", "管理员"),
    USER("USER", "普通用户");

    private final String code;
    private final String description;

    UserRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 扩展点：未来新增角色只需添加新的枚举值即可。
     */
    public static UserRole fromCode(String code) {
        for (UserRole role : values()) {
            if (role.code.equalsIgnoreCase(code)) {
                return role;
            }
        }
        return USER; // 默认降级为普通用户
    }
}
