CREATE TABLE IF NOT EXISTS propagation_path (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id          BIGINT        NOT NULL COMMENT 'event.id',
    source_article_id BIGINT        DEFAULT NULL COMMENT '溯源文章ID (article_clean.id)',
    source_name       VARCHAR(100)  DEFAULT NULL COMMENT '溯源媒体来源',
    spread_depth      INT           DEFAULT 0 COMMENT '传播深度（层级数）',
    total_nodes       INT           DEFAULT 0 COMMENT '传播节点总数',
    duration_hours    DECIMAL(10,2) DEFAULT 0 COMMENT '传播持续时长(小时)',
    spread_speed      DECIMAL(10,2) DEFAULT 0 COMMENT '传播速度(篇/小时)',
    path_json         TEXT          DEFAULT NULL COMMENT '传播路径JSON',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传播路径分析表';

CREATE TABLE IF NOT EXISTS propagation_node (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_id       BIGINT       NOT NULL COMMENT 'propagation_path.id',
    clean_id      BIGINT       NOT NULL COMMENT 'article_clean.id',
    source_name   VARCHAR(100) DEFAULT NULL COMMENT '媒体来源',
    published_at  VARCHAR(64)  DEFAULT NULL COMMENT '发布时间',
    depth         INT          DEFAULT 0 COMMENT '传播层级（0=源头）',
    parent_node_id BIGINT      DEFAULT NULL COMMENT '父节点ID',
    is_source     TINYINT      DEFAULT 0 COMMENT '是否为溯源节点',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_path_id (path_id),
    INDEX idx_clean_id (clean_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='传播路径节点表';
