CREATE TABLE IF NOT EXISTS crawl_news_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_name VARCHAR(100) NOT NULL COMMENT '新闻源名称',
    source_type VARCHAR(50) NOT NULL COMMENT '新闻源类型：portal/official/original',
    source_url VARCHAR(1024) NOT NULL COMMENT '首页或频道页 URL',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用，0 禁用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_news_source_url (source_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫新闻源配置表';

CREATE TABLE IF NOT EXISTS crawl_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_id BIGINT NULL COMMENT '新闻源 ID',
    source_url VARCHAR(1024) NOT NULL COMMENT '采集入口 URL',
    final_url VARCHAR(1024) NULL COMMENT '跳转后的最终 URL',
    source_name VARCHAR(100) NULL COMMENT '新闻源名称',
    source_type VARCHAR(50) NULL COMMENT '新闻源类型',
    request_limit INT NOT NULL COMMENT '本次请求采集数量',
    total_discovered INT NOT NULL DEFAULT 0 COMMENT '发现链接数量',
    total_success INT NOT NULL DEFAULT 0 COMMENT '成功抓取数量',
    total_duplicate INT NOT NULL DEFAULT 0 COMMENT '重复文章数量',
    total_failed INT NOT NULL DEFAULT 0 COMMENT '失败数量',
    status VARCHAR(30) NOT NULL COMMENT 'SUCCESS/SUCCESS_WITH_DUPLICATE/DUPLICATE/PARTIAL_FAILED/FAILED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_crawl_task_source_id (source_id),
    KEY idx_crawl_task_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务表';

CREATE TABLE IF NOT EXISTS crawl_article_raw (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_name VARCHAR(100) NULL COMMENT '新闻源名称',
    source_type VARCHAR(50) NULL COMMENT '新闻源类型',
    original_url VARCHAR(1024) NOT NULL COMMENT '原始 URL',
    final_url VARCHAR(1024) NULL COMMENT '最终 URL',
    status_code INT NULL COMMENT 'HTTP 状态码',
    title VARCHAR(512) NULL COMMENT '标题',
    authors_json TEXT NULL COMMENT '作者 JSON',
    published_at VARCHAR(64) NULL COMMENT '发布时间，保留原始字符串',
    content LONGTEXT NULL COMMENT '原始正文',
    content_length INT NOT NULL DEFAULT 0 COMMENT '正文长度',
    extract_status VARCHAR(50) NOT NULL COMMENT 'SUCCESS/EMPTY_CONTENT',
    message VARCHAR(1024) NULL COMMENT '抽取说明',
    main_image VARCHAR(1024) NULL COMMENT '主图 URL',
    language VARCHAR(50) NULL COMMENT '语言',
    fetched_at DATETIME NULL COMMENT '抓取时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_article_raw_original_url (original_url(255)),
    KEY idx_article_raw_create_time (create_time),
    KEY idx_article_raw_extract_status (extract_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='爬虫原始新闻文章表';

CREATE TABLE IF NOT EXISTS crawl_task_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL COMMENT '采集任务 ID',
    article_id BIGINT NULL COMMENT '文章 ID，失败时为空',
    title VARCHAR(512) NULL COMMENT '发现时的标题',
    url VARCHAR(1024) NOT NULL COMMENT '新闻详情页 URL',
    status VARCHAR(30) NOT NULL COMMENT 'SUCCESS/DUPLICATE/FAILED',
    failure_reason VARCHAR(1024) NULL COMMENT '失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_item_task_id (task_id),
    KEY idx_task_item_article_id (article_id),
    KEY idx_task_item_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集任务明细表';

INSERT INTO crawl_news_source (source_name, source_type, source_url, status)
VALUES
    ('新浪新闻', 'portal', 'https://news.sina.com.cn/', 1),
    ('中国新闻网', 'official', 'https://www.chinanews.com.cn/', 1),
    ('澎湃新闻', 'original', 'https://www.thepaper.cn/', 1),
    ('界面新闻', 'original', 'https://www.jiemian.com/', 1),
    ('中新网滚动新闻RSS', 'official', 'https://www.chinanews.com.cn/rss/scroll-news.xml', 1),
    ('央视新闻', 'official', 'https://news.cctv.com/', 1),
    ('新华网国际', 'official', 'https://www.news.cn/world/', 1),
    ('新华网法治', 'official', 'https://www.news.cn/legal/', 1),
    ('环球网', 'portal', 'https://www.huanqiu.com/', 1),
    ('人民网国际', 'official', 'http://world.people.com.cn/', 1),
    ('人民网社会', 'official', 'http://society.people.com.cn/', 1)
ON DUPLICATE KEY UPDATE
    source_name = VALUES(source_name),
    source_type = VALUES(source_type),
    status = VALUES(status);
