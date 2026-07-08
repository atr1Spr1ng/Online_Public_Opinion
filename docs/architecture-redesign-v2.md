# 网络舆情事件智能分析系统 — 架构重新设计 v2.0

> 设计日期：2026-07-09
> 参考项目：思通舆情 (stonedt-yuqing)、Wlz-Hit (Topic_and_user_profile_analysis_system)

---

## 一、当前项目全景

### 1.1 分支依赖链（现状）

```
main
├── feature/collection ─── 爬虫基础 + 项目结构
│   ├── feature/crawler ─── 爬虫管理API
│   ├── feature/denoise ─── 文本去噪 + TF-IDF向量化
│   └── feature/social-crawler ─── 微博/百度热搜
├── feature/system ─── JWT认证
├── feature/analysis ─── SnowNLP情感分析
├── feature/report ─── 报告生成 + LLM问答
│   ├── feature/event ─── SinglePass聚类
│   │   └── feature/event-api ─── 事件Controller + 前端
│   └── feature/similar-event ─── 🚫 空壳(待实现)
├── feature/dedup ─── ES搜索 + more_like_this去重
│   └── feature/user-center ─── 用户偏好 + ES匹配
├── feature/fake-detection ─── 虚假检测
└── feature/propagation-analysis ─── 传播分析
```

**核心问题：ES 出现在 feature/dedup 分支，但 feature/report 和 feature/similar-event 没有 ES，导致相似事件检索无法使用 ES。**

### 1.2 当前数据流

```
Crawler(8001) ──► Content(8002) ──► MySQL ──► Intelligence(8003) ──► Report(8004)
                      │                           │
                      └── 清洗/分词/TF-IDF         ├── 情感分析(SnowNLP)
                                                   ├── 聚类(SinglePass+Jaccard)
                                                   └── 虚假检测
                    ES(9200) ←── 仅 feature/dedup 分支存在
                      │
                      └── 全文搜索 / more_like_this / 用户关键词匹配
```

### 1.3 功能完成度 vs 选题要求

| 选题要求 | 状态 | 所在分支 |
|---------|------|---------|
| 网络爬虫(新闻) | ✅ 完成 | feature/collection + feature/crawler |
| 网络爬虫(社交) | ✅ 完成 | feature/social-crawler |
| 数据去重 | ✅ 完成 | feature/dedup (ES more_like_this) |
| 数据去噪 | ✅ 完成 | feature/denoise |
| 网页正文提取 | ✅ 完成 | crawler (news-please) |
| 自动分词 | ✅ 完成 | feature/denoise (jieba) |
| 文本特征表示 | ✅ 完成 | feature/denoise (TF-IDF) |
| 主题分类 | ❌ 未开始 | — |
| 自动识别舆情事件 | ✅ 完成 | feature/event (聚类) |
| 热点事件发现 | ✅ 完成 | feature/event |
| 事件聚合 | ✅ 完成 | feature/event |
| **历史事件检索** | ❌ 未开始 | — |
| **相似事件检索** | ❌ 空壳 | feature/similar-event |
| 情感分析 | ✅ 完成 | feature/analysis |
| 事件预测(生命周期) | ✅ 完成 | clustering (lifecycle字段) |
| 趋势预测 | ❌ 未开始 | — |
| 虚假文本检测 | ✅ 完成 | feature/fake-detection |
| 传播路径分析 | ✅ 完成 | feature/propagation-analysis |
| 报告生成 | ✅ 完成 | feature/report |
| 智能问答 | ✅ 完成 | feature/report |
| 个人中心 | ✅ 完成 | feature/user-center |
| 前端 | ✅ 完成 | feature/frontend |

---

## 二、参考项目架构对比

### 2.1 思通舆情 (stonedt-yuqing)

```
┌────────────────────────────────────────────────────┐
│                    思通舆情架构                       │
├────────────────────────────────────────────────────┤
│                                                     │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│   │ 数据采集  │───►│ 数据处理  │───►│ 数据展示  │     │
│   │          │    │ (数据工厂)│    │          │     │
│   └──────────┘    └──────────┘    └──────────┘     │
│        │               │               │           │
│   Spider-flow     ES集群(24节点)   Spring Boot     │
│   WebMagic        数据清洗          Vue前端         │
│   Selenium        情感分析         图表/报告        │
│   Playwright      关键词提取                        │
│   HttpClient      事件聚合                          │
│                                                     │
│   存储: MySQL + Redis + ES                          │
│   部署: 单体 JAR (stonedt-yuqing.jar)               │
│   日处理: 2亿+ 条数据                               │
│                                                     │
└────────────────────────────────────────────────────┘
```

**核心思想：ES 是数据中枢** — 所有清洗后的数据进 ES，搜索、分析、聚合全部基于 ES。MySQL 放配置和元数据，ES 放全文数据。

**三层划分：**
- 数据采集层：Spider-flow + WebMagic（流程编排 + 爬虫引擎）
- 数据处理层：ES 集群（数据工厂，完成清洗、分词、关键词、聚合）
- 数据展示层：Spring Boot + Vue（业务逻辑 + 前端展示）

### 2.2 Wlz-Hit (Topic_and_user_profile_analysis_system)

```
┌────────────────────────────────────────────────────┐
│                  Wlz-Hit 架构                        │
├────────────────────────────────────────────────────┤
│                                                     │
│   weibo_crawler ──► MongoDB ──► Celery Worker       │
│       │                │              │             │
│   微博API抓取      原始数据存储    异步任务处理       │
│                                    │                │
│                    ┌───────────────┴───────────┐    │
│                    │       Celery 任务队列       │    │
│                    │  Redis(Broker+Backend)     │    │
│                    └───────────────┬───────────┘    │
│                                    │                │
│              ┌─────────────────────┼──────────┐     │
│              │         │          │          │     │
│          话题分析   词云生成   用户画像   传播分析   │
│              │         │          │          │     │
│              └─────────────────────┴──────────┘     │
│                          │                          │
│                     FastAPI(81) ─── ES(9200)        │
│                      前端展示      全文检索          │
│                                                     │
│   存储: MongoDB + Redis + ES                        │
│   架构: 任务驱动 + 异步处理                          │
│                                                     │
└────────────────────────────────────────────────────┘
```

**核心思想：异步任务驱动** — 爬虫抓取→MongoDB存储→Celery异步处理→结果写回MongoDB。ES 只负责搜索。

**与思通舆情的关键区别：**
- 思通舆情：ES 是数据处理的核心引擎（数据工厂模式）
- Wlz-Hit：Celery 是任务调度核心，MongoDB 是数据湖，ES 只做搜索

### 2.3 三个项目全方位对比

| 维度 | 思通舆情 | Wlz-Hit | 我们(当前) |
|------|---------|---------|-----------|
| **后端框架** | Spring Boot 单体 | FastAPI + Celery | Spring Boot + 4个Python服务 |
| **爬虫方案** | Spider-flow + WebMagic | 微博API直抓 | news-please + readability |
| **数据库** | MySQL + Redis | MongoDB + Redis | MySQL |
| **搜索引擎** | ES(核心中枢) | ES(仅搜索) | ES(仅dedup分支) |
| **任务队列** | 无(同步) | Celery(核心) | 无(同步HTTP) |
| **NLP实现** | Java内嵌 | Python Celery Task | Python FastAPI服务 |
| **架构特点** | ES一体化(数据工厂) | 任务异步化(Celery) | HTTP微服务化 |
| **规模定位** | 企业级 | 研究/学术 | 学生毕设 |
| **部署方式** | 单体JAR | 多服务Docker | 多服务(Docker可选) |

---

## 三、当前架构的六大问题

### 问题 1：ES 角色不明确

ES 仅在 feature/dedup 分支出现，但事件检索、全文搜索、用户匹配都依赖它。ES 应该是**基础设施**，不是某个功能分支的私有模块。

### 问题 2：Python 服务碎片化

4 个 Python 服务各自独立，但 content(8002) 和 intelligence(8003) 之间的向量化复用关系是硬编码的 HTTP 调用：

```python
# intelligence 需要向量化 → HTTP 调 content 服务
# 增加了延迟和不必要的耦合
```

### 问题 3：异步处理缺失

思通的"数据工厂"模式、Wlz-Hit 的 Celery 任务模式都说明舆情系统需要异步处理。当前全链路同步 HTTP：

```
爬虫(同步) → 清洗(同步) → 情感(同步) → 聚类(同步)
```

任何一个环节慢，整个链路阻塞。

### 问题 4：分支依赖关系混乱

```
feature/similar-event 从 feature/report 切出 → 没有 ES
feature/dedup 有 ES → 但事件模块访问不到
feature/denoise 从 feature/collection 切出 → 没有 sentiment/clustering
```

正确的分层应该是：**基础设施在最底层，业务模块在上层**。

### 问题 5：数据流单向无反馈

```
crawler → content → intelligence → report
```

单向流动，没有统一的数据管理总线。MySQL 和 ES 之间没有同步机制。

### 问题 6：相似事件检索的架构困境

当前 `feature/similar-event` 从 `feature/report` 切出，该分支没有 ES 模块。两种方案：

| 方案 | 实现 | 问题 |
|------|------|------|
| Python TF-IDF | 每次检索查全表→fit→算余弦相似度 | O(N) 性能差，重复造轮子 |
| ES more_like_this | ES 原生查询，一条搞定 | 需要 ES，当前分支没有 |

**ES more_like_this 在 feature/dedup 分支已实现**，但 feature/similar-event 访问不到。

---

## 四、新架构设计：三层一总线

### 4.1 架构全景图

```
┌─────────────────────────────────────────────────────────────────────┐
│                    网络舆情事件智能分析系统 v2.0                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    展示层 (Presentation)                       │   │
│  │                                                               │   │
│  │   Vue3前端 ─── Spring Boot Gateway(8080) ─── 统一API           │   │
│  │                   │         │         │                        │   │
│  │               /api/events  /api/search  /api/reports          │   │
│  └───────────────────┬──────────┬──────────┬─────────────────────┘   │
│                      │          │          │                         │
│  ┌───────────────────┴──────────┴──────────┴─────────────────────┐   │
│  │                    服务层 (Service Layer)                       │   │
│  │                                                                │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │   │
│  │  │ crawler  │  │ content  │  │intelligence│ │  report  │      │   │
│  │  │  (8001)  │  │  (8002)  │  │  (8003)   │  │  (8004)  │      │   │
│  │  │          │  │          │  │           │  │          │      │   │
│  │  │ 新闻抓取 │  │ HTML清洗 │  │ 情感分析  │  │ 报告生成 │      │   │
│  │  │ 链接发现 │  │ 分词提取 │  │ 事件聚类  │  │ LLM问答  │      │   │
│  │  │ 社交热搜 │  │ TF-IDF   │  │ 虚假检测  │  │ 趋势预测 │      │   │
│  │  │          │  │ 文本向量 │  │ 传播分析  │  │          │      │   │
│  │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘      │   │
│  │       │              │             │              │            │   │
│  └───────┼──────────────┼─────────────┼──────────────┼────────────┘   │
│          │              │             │              │                │
│  ┌───────┴──────────────┴─────────────┴──────────────┴────────────┐   │
│  │                    数据总线 (Data Bus)                           │   │
│  │                                                                 │   │
│  │   ┌─────────┐        ┌─────────┐        ┌─────────┐           │   │
│  │   │  MySQL  │        │   ES    │        │  Redis  │           │   │
│  │   │         │        │         │        │         │           │   │
│  │   │ 用户表  │        │ 文章索引│        │ 缓存    │           │   │
│  │   │ 文章表  │◄──────►│ 事件索引│        │ Session │           │   │
│  │   │ 事件表  │  同步   │ 全文搜索│        │         │           │   │
│  │   │ 报告表  │        │ MLT查询 │        │         │           │   │
│  │   │ 配置表  │        │ 聚合统计│        │         │           │   │
│  │   └─────────┘        └─────────┘        └─────────┘           │   │
│  │                                                                 │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 核心设计原则

**原则 1：ES 是基础设施，不是功能模块**

ES 是所有业务模块的共享数据中枢，不隶属于任何一个功能分支。

**原则 2：MySQL 为真相源，ES 为查询加速器**

- MySQL：持久化存储、事务、关联查询
- ES：全文搜索、相似度检索、聚合统计
- 双写同步：写入时同时更新 MySQL 和 ES

**原则 3：Python 服务专注算法，Java 专注编排**

- Python：NLP算法（分词、情感、聚类、虚假检测）
- Java：业务编排、权限控制、数据同步、对外API

**原则 4：同步用于核心链路，异步用于非阻塞任务**

- 同步：爬虫→清洗→入库（核心链路）
- 异步：情感分析、虚假检测、ES同步（解耦任务）

### 4.3 数据流设计

```
                        ┌─────────────┐
                        │  爬虫(8001)  │
                        │ 新闻+社交采集 │
                        └──────┬──────┘
                               │ URL + HTML
                               ▼
                        ┌─────────────┐
                        │ 清洗(8002)   │
                        │ HTML→纯文本  │
                        │ 分词+TF-IDF  │
                        └──────┬──────┘
                               │ CleanResult
                               ▼
                   ┌─────────────────────┐
                   │   Java ContentService │
                   │   保存到 MySQL        │
                   └────────┬────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │  MySQL   │ │ ES Index │ │ 异步分析  │
        │ article  │ │ articles │ │ (可选)    │
        │ _clean   │ │          │ │           │
        └──────────┘ └──────────┘ └─────┬─────┘
                                        │
                          ┌─────────────┼─────────────┐
                          ▼             ▼             ▼
                    ┌──────────┐ ┌──────────┐ ┌──────────┐
                    │ 情感分析  │ │ 事件聚类  │ │ 虚假检测  │
                    │ SnowNLP  │ │SinglePass│ │ 规则+ML  │
                    └────┬─────┘ └────┬─────┘ └────┬─────┘
                         │            │            │
                         ▼            ▼            ▼
                    ┌──────────────────────────────────┐
                    │          MySQL + ES 双写          │
                    │  sentiment / event / fake_result │
                    └──────────────────────────────────┘
```

### 4.4 ES 索引设计

#### articles 索引

```json
{
  "mappings": {
    "properties": {
      "id":            { "type": "long" },
      "title":         { "type": "text", "analyzer": "ik_max_word" },
      "content":       { "type": "text", "analyzer": "ik_max_word" },
      "keywords":      { "type": "text", "analyzer": "ik_smart" },
      "summary":       { "type": "text", "analyzer": "ik_smart" },
      "sourceName":    { "type": "keyword" },
      "sourceUrl":     { "type": "keyword" },
      "publishedAt":   { "type": "date" },
      "language":      { "type": "keyword" },
      "status":        { "type": "keyword" },
      "createTime":    { "type": "date" }
    }
  }
}
```

#### events 索引（新增）

```json
{
  "mappings": {
    "properties": {
      "eventId":       { "type": "long" },
      "title":         { "type": "text", "analyzer": "ik_max_word" },
      "keywords":      { "type": "text", "analyzer": "ik_smart" },
      "summary":       { "type": "text", "analyzer": "ik_smart" },
      "articleCount":  { "type": "integer" },
      "hotness":       { "type": "float" },
      "lifecycle":     { "type": "keyword" },
      "startTime":     { "type": "date" },
      "endTime":       { "type": "date" },
      "createTime":    { "type": "date" }
    }
  }
}
```

### 4.5 相似事件检索 — 新方案

不再使用 Python TF-IDF 计算，直接用 ES more_like_this：

```
┌─────────────────────────────────────────────────────┐
│              相似事件检索流程 (新)                     │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ① 事件聚类完成后：                                   │
│     Event → ES events 索引                           │
│     { event_id, title, keywords, summary, ... }      │
│                                                      │
│  ② 用户查询相似事件：                                 │
│     POST /api/events/similar                         │
│     { keywords: "华为,芯片,发布" }                     │
│                                                      │
│  ③ Java EventService:                                │
│     searchSyncService.findSimilarEvents(              │
│         keywords, topK                                │
│     )                                                │
│                                                      │
│  ④ ES more_like_this query:                          │
│     {                                                │
│       "query": {                                     │
│         "more_like_this": {                          │
│           "fields": ["title", "keywords", "summary"], │
│           "like": "华为芯片发布",                      │
│           "min_term_freq": 1,                         │
│           "min_doc_freq": 1                           │
│         }                                            │
│       }                                              │
│     }                                                │
│                                                      │
│  ⑤ 返回: [{ eventId, title, similarity, ... }]       │
│                                                      │
│  全程不需要调 Python！                                 │
│                                                      │
└─────────────────────────────────────────────────────┘
```

**方案对比：**

| 维度 | Python TF-IDF (原方案) | ES more_like_this (新方案) |
|------|----------------------|---------------------------|
| 每次检索 | 查全表→fit→算余弦 | 一条 ES query |
| 时间复杂度 | O(N) | O(log N) |
| 性能 | 事件越多越慢 | 几乎不随数据量增长 |
| 调用链 | Java→MySQL→Java→Python | Java→ES |
| 跳数 | 4跳 | 1跳 |
| 代码复用 | 无 | 复用已有 SearchSyncService |

### 4.6 功能与模块映射（新版）

| 选题要求 | Java 模块 | Python 服务 | 数据存储 |
|---------|----------|-----------|---------|
| 网络爬虫 | `collection` | `crawler(8001)` | MySQL(raw) |
| 数据清洗 | `content` | `content(8002)` | MySQL(clean) + ES(articles) |
| 内容分析 | `analysis` | `intelligence(8003)` | MySQL(sentiment) |
| 热点事件发现 | `event` | `intelligence(8003)` | MySQL(event) + ES(events) |
| 事件聚合 | `event` | `intelligence(8003)` | MySQL + ES(events) |
| **相似事件检索** | `event` | **ES only** | ES(events) |
| **历史事件检索** | `event` | **ES only** | ES(events) |
| 情感分析 | `analysis` | `intelligence(8003)` | MySQL(sentiment) |
| 生命周期预测 | `event` | `intelligence(8003)` | MySQL(event) |
| 趋势预测 | `event` | `intelligence(8003)` | MySQL |
| 虚假文本检测 | `fake` | `intelligence(8003)` | MySQL(fake) |
| 传播路径分析 | `event` | `intelligence(8003)` | MySQL(propagation) |
| 报告生成 | `report` | `report(8004)` | MySQL(report) |
| 智能问答 | `report` | `report(8004)` | — |
| 用户中心 | `system` | — | MySQL |
| 全文搜索 | `search` | **ES only** | ES(articles) |

---

## 五、分支重构计划

### 5.1 目标分支结构

```
main (基础结构: pom.xml, application.properties, 基础配置)
│
├── feature/infra-es ─── ES 基础设施 (从 feature/dedup 提取)
│   ├── ES 配置 (application.properties)
│   ├── ArticleDocument (文章索引文档)
│   ├── EventDocument (事件索引文档，新增)
│   ├── SearchSyncService (扩展: 文章搜索 + 事件相似检索)
│   └── SearchController (搜索 + 用户偏好匹配)
│
├── feature/collection ─── 爬虫 + 清洗 (合并已有)
│   ├── feature/crawler (爬虫管理)
│   ├── feature/denoise (去噪 + TF-IDF)
│   └── feature/social-crawler (社交热搜)
│
├── feature/system ─── 认证 + 用户 (已有)
│   └── feature/user-center (用户偏好，依赖 feature/infra-es)
│
├── feature/intelligence ─── NLP 全部 (合并 analysis+event+fake+propagation)
│   ├── 情感分析 (SnowNLP)
│   ├── 事件聚类 (SinglePass + Jaccard)
│   ├── 虚假检测 (规则 + 置信度)
│   └── 传播分析 (关键节点 + 路径图)
│
├── feature/report ─── 报告 + 问答 (已有)
│
└── feature/event-service ─── 事件 API (依赖 feature/infra-es)
    ├── EventController (CRUD)
    ├── 相似事件检索 (ES more_like_this)
    └── 历史事件检索 (ES search)
```

### 5.2 合并策略

```
第一阶段: 基础设施下沉
  main ← feature/dedup 中提取 ES 模块 → feature/infra-es
  使 ES 成为所有上层模块可用的基础设施

第二阶段: 业务模块合并
  feature/intelligence ← feature/analysis + feature/event + feature/fake-detection + feature/propagation-analysis
  将 4 个 NLP 分支合并为统一的 intelligence 模块

第三阶段: 事件服务重构
  feature/event-service ← 从 feature/report 重建
  依赖 feature/infra-es，实现相似事件和历史事件检索

第四阶段: 用户中心升级
  feature/user-center ← 依赖 feature/infra-es
  搜索 + 偏好 + 订阅 统一管理
```

---

## 六、待实现功能清单

| 优先级 | 功能 | 需依赖 | 实现方案 |
|--------|------|--------|---------|
| P0 | 相似事件检索 | ES(events索引) | ES more_like_this |
| P0 | 历史事件检索 | ES(events索引) | ES multi_match search |
| P1 | 趋势预测 | 历史数据 | Python 时序模型 |
| P1 | 主题分类 | 文本特征 | ML 分类器 |
| P2 | 异步任务队列 | Redis | Celery 或 Spring @Async |
| P2 | ES 全量重建索引 | — | SearchSyncService.rebuildIndex() |
| P3 | 数据大屏 | 前端 | ECharts 实时刷新 |

---

## 七、技术选型依据

| 技术 | 理由 |
|------|------|
| ES 作为数据中枢 | 思通舆情已验证：24节点ES集群日产2亿+数据。学生项目无需集群，单节点足够 |
| IK 分词器 | 中文分词标准方案，ES + IK = 开箱即用的中文全文搜索 |
| more_like_this | ES 原生功能，基于 TF-IDF，比手写 Python 版本更稳定高效 |
| Spring Data ES | 与 Spring Boot 深度集成，Repository 模式减少样板代码 |
| jieba + TF-IDF | 保留在 content 服务，用于关键词提取和文本特征表示（清洗阶段） |
| SnowNLP | 轻量级中文情感分析，无需 GPU，适合学生项目 |
| FastAPI | Python 服务统一框架，异步支持好，自动生成 OpenAPI 文档 |

---

## 八、参考资料

- 思通舆情 Gitee: https://gitee.com/stonedtx/yuqing
- 思通舆情 GitHub: https://github.com/javabloger/yuqing
- Wlz-Hit 舆情系统: https://github.com/Wlz-Hit/Topic_and_user_profile_analysis_system
- ES more_like_this 文档: https://www.elastic.co/guide/en/elasticsearch/reference/current/query-dsl-mlt-query.html
