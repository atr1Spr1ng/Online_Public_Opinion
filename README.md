# 网络舆情智能分析系统

项目采用 Spring Boot + Python 服务的后端架构，当前优先开发网络爬虫模块，Web 前端暂未开发。

```text
Online_Public_Opinion/
├─ backend/         Spring Boot 业务后端和统一 API
├─ services/
│  ├─ crawler/      网络爬虫服务
│  ├─ content/      内容清洗与去重服务
│  ├─ intelligence/ NLP、聚类、情感和预测服务
│  └─ report/       智能问答与报告服务
├─ database/        数据库脚本
├─ deploy/          部署配置
├─ docs/            项目文档
├─ web/             Web 前端
└─ 选题要求/        原始需求
```

Spring Boot 负责对外接口和业务编排，Python 负责爬虫及后续算法任务，服务之间通过 HTTP 通信。

当前新闻采集入库会先按 URL 去重：已存在的文章不会再次抓取正文，任务明细标记为 `DUPLICATE`。

选题功能与核心模块对应关系：

| 选题要求 | 对应模块 |
|---|---|
| 网络爬虫 | `collection` + `services/crawler` |
| 数据清洗及预处理 | `content` + `services/content` |
| 内容分析、情感分析 | `analysis` + `services/intelligence` |
| 热点发现、事件聚合和预测 | `event` + `services/intelligence` |
| 虚假文本检测、传播路径 | `analysis/event` + `services/intelligence` |
| 报告和智能问答扩展 | `report` + `services/report` |

当前网络新闻抓取链路：

```text
CrawlerController
  → CrawlerService
  → PythonCrawlerClient
  → FastAPI
  → news-please / readability
```

当前已开放的爬虫接口：

```text
GET  /api/crawler/health       检查 Spring Boot 到 Python crawler 的链路
POST /api/crawler/news/discover 从首页/频道页发现新闻详情链接
POST /api/crawler/news/crawl   抓取单条新闻 URL
POST /api/crawler/news/collect 自动发现链接并批量抓取正文
POST /api/crawler/tasks        自动采集并保存到数据库
POST /api/crawler/tasks/source/{id} 根据新闻源 ID 采集并保存
POST /api/crawler/tasks/all-enabled 采集所有启用新闻源
GET  /api/crawler/tasks        查询采集任务列表
GET  /api/crawler/tasks/{id}   查询采集任务详情
GET  /api/crawler/articles     查询已采集原始文章
GET  /api/crawler/sources      查询新闻源列表
POST /api/crawler/sources      新增或更新新闻源
POST /api/crawler/sources/{id}/status 启用或禁用新闻源
POST /api/crawler/news/test    兼容保留的测试入口
```

接口测试工具中可统一放到 `crawl` group；代码和接口路径仍使用标准命名 `crawler`。

详细目录职责见 [help.md](help.md)。
