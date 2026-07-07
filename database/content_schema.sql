CREATE TABLE IF NOT EXISTS article_clean (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_id          BIGINT       NOT NULL COMMENT 'crawl_article_raw.id',
    title           VARCHAR(512) DEFAULT NULL,
    content         LONGTEXT     DEFAULT NULL,
    keywords        VARCHAR(1024) DEFAULT NULL COMMENT '逗号分隔关键词',
    summary         VARCHAR(2048) DEFAULT NULL COMMENT '摘要',
    simhash         BIGINT       DEFAULT NULL COMMENT '内容 SimHash 值',
    language        VARCHAR(50)  DEFAULT NULL,
    published_at    VARCHAR(64)  DEFAULT NULL,
    source_name     VARCHAR(100) DEFAULT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CLEANED/FAILED/DUPLICATE',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_raw_id (raw_id),
    INDEX idx_simhash (simhash),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='清洗后文章表';
