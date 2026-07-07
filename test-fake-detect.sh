#!/bin/bash
# 虚假文本检测测试脚本
# 用法: ./test-fake-detect.sh

TOKEN="eyJhbGciOiJIUzM4NCJ9.eyJqdGkiOiIxMGYwZmVhNi0yNzAyLTRiZmQtOTQ3Yy02YzhlZmNmOGQ5YWIiLCJzdWIiOiIxIiwidXNlcm5hbWUiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6MTc4MzQyMDkzMSwiZXhwIjoxNzgzNDIxODMxfQ.5lx4MGpCrdIMUOXUoadep8f08eR5m1Qq9zLPMn8YKc1-JCmyJiya36Er_svkriST"
BASE_URL="http://localhost:8080"

echo "=== 1. 健康检查 ==="
curl -s "$BASE_URL/api/fake/health" -H "Authorization: Bearer $TOKEN" | python -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/fake/health" -H "Authorization: Bearer $TOKEN"
echo ""

echo "=== 2. 虚假检测 (cleanId=1) ==="
curl -s -X POST "$BASE_URL/api/fake/detect" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"cleanId": 1}' | python -m json.tool 2>/dev/null || curl -s -X POST "$BASE_URL/api/fake/detect" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '{"cleanId": 1}'
echo ""

echo "=== 3. 批量检测 ==="
curl -s -X POST "$BASE_URL/api/fake/batch-detect" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '[1]' | python -m json.tool 2>/dev/null || curl -s -X POST "$BASE_URL/api/fake/batch-detect" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" -d '[1]'
echo ""

echo "=== 4. 查询检测结果列表 ==="
curl -s "$BASE_URL/api/fake?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN" | python -m json.tool 2>/dev/null || curl -s "$BASE_URL/api/fake?pageNum=1&pageSize=10" -H "Authorization: Bearer $TOKEN"
echo ""

echo "=== 虚假检测测试完成 ==="
