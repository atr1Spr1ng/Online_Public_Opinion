-- 网络舆情智能分析系统 - 数据库初始化脚本

CREATE TABLE IF NOT EXISTS `users` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    `username`       VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`       VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`       VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `email`          VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `role`           VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN/USER, 预留扩展',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1=启用 0=禁用',
    `avatar`         VARCHAR(255) DEFAULT NULL COMMENT '头像URL, 预留扩展点',
    `last_login_at`  DATETIME     DEFAULT NULL COMMENT '最后登录时间, 预留扩展点',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX `uk_username` (`username`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
