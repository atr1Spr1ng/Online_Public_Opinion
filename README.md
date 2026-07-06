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

当前网络新闻抓取链路：

```text
CrawlerController
  → CrawlerService
  → PythonCrawlerClient
  → FastAPI
  → news-please / readability
```

详细目录职责见 [help.md](help.md)。
