<template>
  <div>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6"><el-card><div class="stat-item"><div class="stat-num">{{ stats.articles }}</div><div class="stat-label">原始文章</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-item"><div class="stat-num">{{ stats.cleaned }}</div><div class="stat-label">已清洗</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-item"><div class="stat-num">{{ stats.events }}</div><div class="stat-label">舆情事件</div></div></el-card></el-col>
      <el-col :span="6"><el-card><div class="stat-item"><div class="stat-num">{{ stats.reports }}</div><div class="stat-label">分析报告</div></div></el-card></el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="12">
        <el-card>
          <template #header><span>系统状态</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item v-for="s in serviceStatus" :key="s.name" :label="s.name">
              <el-tag :type="s.alive ? 'success' : 'danger'">{{ s.alive ? '运行中' : '离线' }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>情感分析概览</span></template>
          <div v-if="sentimentData.length" style="height:260px" ref="chartRef"></div>
          <el-empty v-else description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCrawlerHealth } from '@/api/crawler'
import { getCleanArticles } from '@/api/content'
import { getSentimentResults } from '@/api/analysis'
import { getReports } from '@/api/report'

const stats = reactive({ articles: 0, cleaned: 0, events: 0, reports: 0 })
const serviceStatus = ref([
  { name: 'Java 后端 (8080)', alive: false },
  { name: 'Python Crawler (8001)', alive: false },
  { name: 'Python Content (8002)', alive: false },
  { name: 'Python Intelligence (8003)', alive: false },
  { name: 'Python Report (8004)', alive: false }
])
const sentimentData = ref([])
const chartRef = ref(null)

onMounted(async () => {
  try {
    await getCrawlerHealth()
    serviceStatus.value[0].alive = true
  } catch (_) {}
  try {
    const res = await getCleanArticles({ pageNum: 1, pageSize: 1 })
    stats.cleaned = res.total || 0
  } catch (_) {}
  try {
    const res = await getSentimentResults({ pageNum: 1, pageSize: 100 })
    const data = res.data?.records || res.data || []
    const pos = data.filter(i => i.sentiment === 'POSITIVE').length
    const neg = data.filter(i => i.sentiment === 'NEGATIVE').length
    const neu = data.filter(i => i.sentiment === 'NEUTRAL').length
    sentimentData.value = [
      { name: '正面', value: pos, itemStyle: { color: '#67C23A' } },
      { name: '负面', value: neg, itemStyle: { color: '#F56C6C' } },
      { name: '中立', value: neu, itemStyle: { color: '#909399' } }
    ]
    await nextTick()
    if (chartRef.value) {
      const chart = echarts.init(chartRef.value)
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: ['45%', '70%'],
          data: sentimentData.value,
          label: { show: true, formatter: '{b}: {c}' }
        }]
      })
    }
  } catch (_) {}
  try {
    const res = await getReports({ pageNum: 1, pageSize: 1 })
    stats.reports = res.total || 0
  } catch (_) {}
})
</script>

<style scoped>
.stats-row .el-card { text-align: center; }
.stat-num { font-size: 32px; font-weight: bold; color: #409EFF; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
</style>
