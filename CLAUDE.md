# CLAUDE.md — 网络舆情智能分析系统

## 项目概要

Spring Boot 3 + Python FastAPI + Vue 3 微服务架构的网络舆情分析平台。
功能覆盖：新闻采集 → 内容清洗 → 情感分析 → 虚假检测 → 事件聚类 → 传播溯源 → 趋势预测 → 智能问答。

## 架构

```
backend/          Java 17 + Spring Boot 3 + MyBatis-Plus   (端口 8080)
services/
  crawler/        Python 爬虫服务                           (端口 8000)
  content/        Python 内容清洗                           (端口 8001)
  report/         Python 报告生成/问答                      (端口 8002)
  intelligence/   Python 聚类/情感/假新闻/趋势/摘要         (端口 8003)
web/              Vue 3 + Element Plus + ECharts            (端口 5173)
database/         SQL schema 文件 (MySQL 8)
```

## 启动命令

```bash
# 后端
cd backend && mvn spring-boot:run

# 前端
cd web && npm run dev

# Python 微服务（每个单独终端启动）
cd services/intelligence && python -m uvicorn src.main:app --host 127.0.0.1 --port 8003
cd services/crawler && python -m uvicorn src.main:app --host 127.0.0.1 --port 8000
cd services/content && python -m uvicorn src.main:app --host 127.0.0.1 --port 8001
cd services/report && python -m uvicorn src.main:app --host 127.0.0.1 --port 8002
```

## 重要配置

- **Python 路径**: `C:/Users/Lenovo/AppData/Local/Programs/Python/Python312/python.exe`
- **DeepSeek API Key**: 在 `services/report/.env` 和 `services/intelligence/.env` 中（`sk-aa2e6248788348feb7aad8faaa70c838`）
- **嵌入模型**: M3E-large (1024维)，缓存 `D:/huggingface_cache`
- **数据库**: MySQL `public_opinion`，管理员 `admin/admin123`
- **默认用户**: `admin/admin123` (role=ADMIN)，新注册用户默认 role=USER

## Python 微服务启动注意事项

- 启动前务必 `rm -rf __pycache__`，否则 .pyc 缓存可能导致代码修改不生效
- 启动 intelligence 服务时带 `HF_HOME="D:/huggingface_cache"`（如果环境变量未设置）
- 测试期间不要用 `--reload`，首次请求可能触发重载导致模型重加载
- 杀进程: `netstat -ano | grep :8003` 找 PID → `taskkill //F //PID <pid>`

## 技术约定

- **后端分层**: controller → service/impl → mapper → entity，DTO/VO 用 Java record
- **前端分层**: api/ → views/ → router/ → stores/
- **API路径**: `/api/模块名/子资源`，GET查/POST创/PUT改/DELETE删
- **分页参数**: `pageNum`, `pageSize`
- **Python API路径**（内部调用）: `/internal/event/cluster`, `/internal/trend/forecast`, `/internal/event/summary` 等
- **ES不可用不阻断业务**: ES 同步失败只打日志，主流程继续

## 常见陷阱

1. **Python .pyc 缓存**: 改代码后运行结果不变 → 先删 `__pycache__`
2. **Windows 端口残留**: `taskkill` 后端口可能仍显示占用，等几秒或检查进程是否真死了
3. **E盘权限**: E:\ 根目录 BUILTIN\Users 只有 RX，模型缓存放 D盘或手动创建可写子目录
4. **数据库代理字符**: 部分文章标题含 lone surrogates（`\udca7` 等），传给 M3E tokenizer 会报错，`_sanitize()` 已处理
5. **前后端 token 过期**: JWT 15分钟过期，刷新用 `/api/auth/refresh`
6. **hdbscan 参数**: `min_cluster_size=2`, `min_samples=1`, `cluster_selection_epsilon=0.08`（M3E-large 1024维当前最优值）

## 构建验证

```bash
# 后端编译
cd backend && mvn compile -q

# 前端构建
cd web && npx vite build --mode production
```

## 协作约定

### 改代码前先确认，不要擅自动手

发现问题后先分析原因、给出方案、说明利弊，等用户说"改"再改。用户多次强调这一点——即使方案再明显，也要先口头确认。

### 推动用户做选择题而非问答题

分析问题后给出 2-3 个具体方案让用户选，而不是直接问"你想怎么做"。方案要讲清楚各自的代价和收益。

### 主流技术方案要主动提出

用户不一定是技术专家。对于某个功能，如果业界主流方案远超当前实现（如情感分析/事件命名用 LLM 而非纯词典/词频），即使当前代码没有，也应该在方案中提出来。用户表示："LLM就是主流处理但是你之前没有提出"——这是个教训。

### 可行性分析先于实施

对于资源敏感的操作（换大模型、加数据量），先分析当前机器能否承受、推理耗时多久、改动了多少代码，再让用户决定是否执行。不要直接跳到实现。

### 不确定的地方主动提出来

用户说过"在修改之前你还有什么问题吗"——这意味着你应该在动手前把所有不确定的点都说清楚，而不是带着假设去改。尤其是技术选型中的边界条件。

### 保留可观测性 / 反查能力

处理数据时尽量保留原始状态或中间结果（如噪声簇、被剪枝的文章），方便回头验证算法效果。用户明确表示倾向保留噪声簇来反查聚类质量。

### 经验沉淀要及时

新的坑、新的约定、新的模式，及时写入 `docs/经验.md`（给人看）或 `CLAUDE.md`（给AI看）。用户要求结束一天工作前保存进度。

### 关键原则总结

1. **先分析，再提案，确认后才动手**
2. **给方案要带优劣对比，让用户选**
3. **主流方案要主动提，不管当前代码有没有**
4. **可行性先算清楚，不做拍脑袋的事**
5. **有疑问先澄清，不带着假设改代码**
