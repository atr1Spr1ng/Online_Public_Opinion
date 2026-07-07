#!/bin/bash
# 事件溯源与传播路径分析测试脚本
# 用法: ./test-propagation.sh
# 前提: 已在MySQL中创建event和event_article测试数据

TOKEN="eyJhbGciOiJIUzM4NCJ9.eyJqdGkiOiIxMGYwZmVhNi0yNzAyLTRiZmQtOTQ3Yy02YzhlZmNmOGQ5YWIiLCJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc4MzQyMDkzMSwiZXhwIjoxNzgzNDIxODMxfQ.5lx4MGpCrdIMUOXUoadep8f08eR5m1Qq9zLPMn8YKc1-JCmyJiya36Er_svkriST"
BASE_URL="http://localhost:8080"

echo "=== 1. 健康检查 ==="
curl -s "$BASE_URL/api/propagation/health" -H "Authorization: Bearer $TOKEN" | python -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/propagation/health" -H "Authorization: Bearer $TOKEN"
echo ""

echo "=== 2. 事件溯源 (eventId=1) ==="
curl -s "$BASE_URL/api/propagation/source/1" \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/propagation/source/1" -H "Authorization: Bearer $TOKEN"
echo ""

echo "=== 3. 传播路径分析 (eventId=1) ==="
curl -s -X POST "$BASE_URL/api/propagation/analyze" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"eventId": 1}' | python -m json.tool 2>/dev/null || curl -s -X POST "$BASE_URL/api/propagation/analyze" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"eventId": 1}'
echo ""

echo "=== 4. 按事件查询传播路径 ==="
curl -s "$BASE_URL/api/propagation/event/1" \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/propagation/event/1" -H "Authorization: Bearer $TOKEN"
echo ""

echo "=== 传播路径分析测试完成 ==="
