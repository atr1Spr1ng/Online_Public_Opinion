-- 用户关键词
CREATE TABLE IF NOT EXISTS user_keyword (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT 'users.id',
    keyword     VARCHAR(100) NOT NULL COMMENT '关键词',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_keyword (user_id, keyword),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关键词表';

-- 用户关注领域
CREATE TABLE IF NOT EXISTS user_domain (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT 'users.id',
    domain_name VARCHAR(100) NOT NULL COMMENT '领域名称',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_domain (user_id, domain_name),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注领域表';

-- 用户新闻源订阅
CREATE TABLE IF NOT EXISTS user_source_subscription (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL COMMENT 'users.id',
    source_id   BIGINT       NOT NULL COMMENT 'crawl_news_source.id',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_source (user_id, source_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户新闻源订阅表';
