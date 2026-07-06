# 网络舆情智能分析系统目录说明

本文只说明项目目录职责，供协作者快速了解代码位置。当前优先开发后端，部分目录是为后续功能预留的空目录。

## 根目录

```text
Online_Public_Opinion/
├─ backend/            Spring Boot 业务后端
├─ services/           Python 功能服务
├─ database/           数据库脚本（预留）
├─ deploy/             Docker 和部署配置（预留）
├─ docs/               项目文档（预留）
├─ web/                Web 前端（预留，当前不开发）
├─ 选题要求/           原始需求图片
└─ help.md             项目目录说明
```

## backend

`backend` 是一个独立的 Maven/Spring Boot 项目，负责统一对外 API、业务编排以及后续的数据库操作。

```text
backend/
├─ pom.xml                         Maven 依赖和构建配置
├─ mvnw / mvnw.cmd / .mvn/        Maven Wrapper
└─ src/
   ├─ main/
   │  ├─ java/com/bupt/publicopinion/
   │  │  ├─ PublicOpinionApplication.java
   │  │  ├─ config/               Spring 全局配置和外部服务客户端配置
   │  │  ├─ common/               公共返回、异常、枚举和工具
   │  │  ├─ system/               用户、权限和系统管理
   │  │  ├─ collection/           网络爬虫任务及采集业务
   │  │  ├─ content/              内容清洗、文章和数据管理
   │  │  ├─ analysis/             情感、关键词等分析结果管理
   │  │  ├─ event/                舆情事件聚合和生命周期管理
   │  │  └─ report/               舆情报告和智能问答管理
   │  └─ resources/
   │     ├─ application.properties
   │     └─ mapper/               MyBatis XML（预留）
   └─ test/                       Java 自动测试
```

业务模块统一采用以下分层：

```text
controller/    接收 HTTP 请求
dto/           请求参数
service/       业务接口与实现
client/        调用外部服务，仅在需要的模块中存在
mapper/        数据库访问
entity/        数据库实体
vo/            返回给调用方的数据
exception/     模块异常，仅在需要的模块中存在
```

当前 `collection` 已实现网络新闻抓取调用链：

```text
CrawlerController
  → CrawlerService
  → PythonCrawlerClient
  → services/crawler
```

## services

`services` 存放 Python 服务。不同业务独立目录，不互相直接导入源码，通过 HTTP 与 Spring Boot 通信。

```text
services/
├─ crawler/         网络数据采集
├─ content/         正文抽取、清洗和去重（预留）
├─ intelligence/    NLP、情感、聚类、预测和传播分析（预留）
└─ report/          检索、LLM 问答和报告生成（预留）
```

### services/crawler

当前已开发的 Python 爬虫服务。

```text
crawler/
├─ requirements.txt          Python 依赖及版本
├─ src/
│  ├─ main.py               FastAPI 服务入口
│  ├─ api/                  爬虫 HTTP 接口
│  ├─ service/              爬虫业务编排
│  ├─ adapter/              第三方爬虫项目适配层
│  ├─ engine/               Scrapy 等采集引擎（预留）
│  ├─ normalizer/           采集结果标准化（预留）
│  ├─ model/                请求和响应模型
│  ├─ config/               爬虫配置
│  └─ exception/            爬虫异常
├─ tests/                   Python 自动测试
└─ third-party/             需隔离管理的第三方项目
```

目前 `adapter/news_please_adapter.py` 使用 news-please 抽取新闻标题、正文、作者和发布时间，并使用 readability 作为正文回退方案。

## 本地生成目录

以下目录属于本机构建或运行环境，不应作为业务源码修改：

```text
backend/target/             Maven 构建产物
services/*/.venv/          Python 虚拟环境
**/__pycache__/            Python 字节码缓存
.idea/                     IDEA 本地配置
```
