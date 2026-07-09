CREATE TABLE IF NOT EXISTS event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(256)  NOT NULL COMMENT '事件标题',
    keywords        VARCHAR(1024) DEFAULT NULL COMMENT '事件关键词(逗号分隔)',
    article_count   INT           DEFAULT 0 COMMENT '关联文章数',
    hotness         DECIMAL(10,2) DEFAULT 0 COMMENT '热度指数',
    lifecycle       VARCHAR(20)   DEFAULT NULL COMMENT '生命周期:潜伏期/成长期/高潮期/衰退期',
    category        VARCHAR(32)   DEFAULT '其他' COMMENT '主题分类',
    start_time      DATETIME      DEFAULT NULL COMMENT '事件起始时间',
    end_time        DATETIME      DEFAULT NULL COMMENT '事件结束时间',
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_lifecycle (lifecycle),
    INDEX idx_category (category),
    INDEX idx_hotness (hotness)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='舆情事件表';


CREATE TABLE IF NOT EXISTS event_article (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id    BIGINT   NOT NULL COMMENT 'event.id',
    clean_id    BIGINT   NOT NULL COMMENT 'article_clean.id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id),
    INDEX idx_clean_id (clean_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件-文章关联表';
