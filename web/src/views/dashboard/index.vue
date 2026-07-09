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

    <el-card style="margin-top:20px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>热点事件</span>
          <el-button type="primary" text @click="$router.push('/event')">查看全部</el-button>
        </div>
      </template>
      <el-table :data="hotEvents" v-loading="eventLoading" border stripe>
        <el-table-column prop="title" label="事件标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.category)" size="small">{{ row.category || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hotness" label="热度" width="80" />
        <el-table-column prop="lifecycle" label="生命周期" width="100">
          <template #default="{ row }">
            <el-tag :type="lifecycleType(row.lifecycle)" size="small">{{ row.lifecycle }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/event/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!eventLoading && hotEvents.length === 0" description="暂无热点事件" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getCrawlerHealth, getArticles } from '@/api/crawler'
import { getContentHealth, getCleanArticles } from '@/api/content'
import { getAnalysisHealth, getSentimentResults } from '@/api/analysis'
import { getReportHealth, getReports } from '@/api/report'
import { listEvents } from '@/api/event'

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
const hotEvents = ref([])
const eventLoading = ref(false)

function lifecycleType(lc) {
  const map = { '潜伏期': 'info', '成长期': 'warning', '高潮期': 'danger', '衰退期': 'info' }
  return map[lc] || 'info'
}

function categoryType(cat) {
  const map = {
    '社会民生': 'primary', '科技经济': 'success', '教育文化': 'info',
    '医疗卫生': 'danger', '政治法律': 'warning', '生态环境': '',
    '娱乐体育': '', '国际时政': 'danger', '其他': 'info'
  }
  return map[cat] || 'info'
}

onMounted(async () => {
  // Java 后端
  try { await getCrawlerHealth(); serviceStatus.value[0].alive = true } catch (_) {}
  // Python Crawler (8001)
  try { await getCrawlerHealth(); serviceStatus.value[1].alive = true } catch (_) {}
  // Python Content (8002)
  try { await getContentHealth(); serviceStatus.value[2].alive = true } catch (_) {}
  // Python Intelligence (8003)
  try { await getAnalysisHealth(); serviceStatus.value[3].alive = true } catch (_) {}
  // Python Report (8004)
  try { await getReportHealth(); serviceStatus.value[4].alive = true } catch (_) {}

  try { const res = await getArticles({ pageNum: 1, pageSize: 1 }); stats.articles = res.total || 0 } catch (_) {}
  try { const res = await getCleanArticles({ pageNum: 1, pageSize: 1 }); stats.cleaned = res.total || 0 } catch (_) {}
  try { const res = await listEvents({ pageNum: 1, pageSize: 1 }); stats.events = res.data?.total || res.total || 0 } catch (_) {}
  try { const res = await getReports({ pageNum: 1, pageSize: 1 }); stats.reports = res.total || 0 } catch (_) {}

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

  // 热点事件 Top 5
  eventLoading.value = true
  try {
    const res = await listEvents({ pageNum: 1, pageSize: 5, sortBy: 'hotness' })
    hotEvents.value = res.data?.records || res.data || []
  } catch (_) {}
  finally { eventLoading.value = false }
})
</script>

<style scoped>
.stats-row .el-card { text-align: center; }
.stat-num { font-size: 32px; font-weight: bold; color: #409EFF; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
</style>
