#!/bin/bash
# 停止所有后台服务
ROOT="$(cd "$(dirname "$0")" && pwd)"
LOGS="$ROOT/logs"

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m'

for f in "$LOGS"/*.pid; do
    if [ -f "$f" ]; then
        name=$(basename "$f" .pid)
        pid=$(cat "$f")
        if kill "$pid" 2>/dev/null; then
            echo -e "${GREEN}[STOPPED]${NC} $name (pid=$pid)"
        else
            echo -e "${RED}[NOT FOUND]${NC} $name (pid=$pid)"
        fi
        rm -f "$f"
    fi
done

echo "所有服务已停止"
