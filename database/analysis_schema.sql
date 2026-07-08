CREATE TABLE IF NOT EXISTS article_sentiment (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    clean_id        BIGINT       NOT NULL COMMENT 'article_clean.id',
    sentiment       VARCHAR(20)  NOT NULL COMMENT 'POSITIVE/NEGATIVE/NEUTRAL',
    positive_score  DECIMAL(5,4) DEFAULT 0 COMMENT '正面得分',
    negative_score  DECIMAL(5,4) DEFAULT 0 COMMENT '负面得分',
    confidence      DECIMAL(5,4) DEFAULT 0 COMMENT '置信度',
    details_json    TEXT         DEFAULT NULL COMMENT '分析详情JSON',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_clean_id (clean_id),
    INDEX idx_sentiment (sentiment)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='情感分析结果表';
