CREATE TABLE IF NOT EXISTS article_fake_detection (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    clean_id        BIGINT       NOT NULL COMMENT 'article_clean.id',
    fake_score      DECIMAL(5,4) DEFAULT 0 COMMENT '虚假概率 0-1',
    is_fake         TINYINT      DEFAULT 0 COMMENT '是否虚假: 0=否 1=是',
    detection_method VARCHAR(30) DEFAULT 'rule' COMMENT '检测方法: rule/llm/hybrid',
    features_json   TEXT         DEFAULT NULL COMMENT '检测特征JSON',
    details         TEXT         DEFAULT NULL COMMENT '检测详情说明',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_clean_id (clean_id),
    INDEX idx_is_fake (is_fake)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚假文本检测结果表';
