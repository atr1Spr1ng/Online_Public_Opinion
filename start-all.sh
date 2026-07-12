#!/bin/bash
# 一键启动所有后台服务
# 用法: bash start-all.sh

set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
LOGS="$ROOT/logs"
mkdir -p "$LOGS"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ── Elasticsearch ──
log_info "检查 Elasticsearch (9200)..."
if curl -s http://localhost:9200 > /dev/null 2>&1; then
    log_info "Elasticsearch 已在运行"
else
    log_warn "Elasticsearch 未启动，请手动启动: C:/Users/Lenovo/Desktop/elasticsearch-8.17.4/bin/elasticsearch.bat"
fi

# ── Python 爬虫 (8001) ──
log_info "启动 Python Crawler (8001)..."
cd "$ROOT/services/crawler/src"
nohup python -c "import uvicorn; uvicorn.run('main:app', host='127.0.0.1', port=8001)" > "$LOGS/crawler.log" 2>&1 &
echo $! > "$LOGS/crawler.pid"
cd "$ROOT"

# ── Python 内容清洗 (8002) ──
log_info "启动 Python Content (8002)..."
cd "$ROOT/services/content"
nohup python -m src.main > "$LOGS/content.log" 2>&1 &
echo $! > "$LOGS/content.pid"

# ── Python 智能分析 (8003) ──
log_info "启动 Python Intelligence (8003)..."
cd "$ROOT/services/intelligence"
nohup python -m src.main > "$LOGS/intelligence.log" 2>&1 &
echo $! > "$LOGS/intelligence.pid"

# ── Python 报告服务 (8004) ──
log_info "启动 Python Report (8004)..."
cd "$ROOT/services/report"
nohup python -m src.main > "$LOGS/report.log" 2>&1 &
echo $! > "$LOGS/report.pid"

# ── Spring Boot (8080) ──
log_info "启动 Spring Boot (8080)..."
cd "$ROOT/backend"
nohup ./mvnw spring-boot:run > "$LOGS/backend.log" 2>&1 &
echo $! > "$LOGS/backend.pid"

# ── Vite 前端 (5173) ──
log_info "启动 Vite 前端 (5173)..."
cd "$ROOT/web"
nohup npx vite --host > "$LOGS/frontend.log" 2>&1 &
echo $! > "$LOGS/frontend.pid"

# ── 等待并检查 ──
log_info "等待服务启动..."
sleep 8

check_port() {
    local name=$1 port=$2
    if netstat -ano 2>/dev/null | grep -q ":$port .*LISTENING"; then
        echo -e "  ${GREEN}✓${NC} $name (:$port)"
        return 0
    else
        echo -e "  ${RED}✗${NC} $name (:$port) — 查看 $LOGS/${name,,}.log"
        return 1
    fi
}

echo ""
log_info "服务状态:"
check_port "Elasticsearch" 9200
check_port "Crawler"       8001
check_port "Content"       8002
check_port "Intelligence"  8003
check_port "Report"        8004
check_port "Backend"       8080
check_port "Frontend"      5173

echo ""
log_info "日志目录: $LOGS"
log_info "前端地址: http://localhost:5173"
log_info "后端地址: http://localhost:8080"
echo ""
log_info "测试步骤:"
echo "  1. 打开 http://localhost:5173"
echo "  2. 点「注册」创建一个普通用户"
echo "  3. 点「登录」用 admin / admin123 登录（管理员）"
echo "  4. 对比管理员和普通用户的首页差异"
