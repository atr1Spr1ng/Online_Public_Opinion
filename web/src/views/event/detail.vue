<template>
  <div v-loading="loading">
    <el-card v-if="error" style="text-align:center;padding:60px">
      <el-empty :description="error" />
      <el-button type="primary" @click="fetchData" style="margin-top:16px">重新加载</el-button>
    </el-card>

    <template v-else-if="data">
      <!-- 页面头部 -->
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px">
        <el-button @click="router.back()" text><el-icon><ArrowLeft /></el-icon>返回</el-button>
        <h2 style="margin:0;flex:1">{{ data.event?.title || '事件详情' }}</h2>
        <el-tag :type="lifecycleType(data.event?.lifecycle)" size="large">{{ data.event?.lifecycle }}</el-tag>
      </div>

      <!-- 第一行：基本信息 + 趋势图 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="8">
          <el-card shadow="never">
            <template #header><span style="font-weight:600">基本信息</span></template>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item label="事件ID">{{ data.event?.id }}</el-descriptions-item>
              <el-descriptions-item label="分类">
                <el-tag :type="categoryType(data.event?.category)" size="small">{{ data.event?.category || '其他' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="热度">{{ data.event?.hotness }}</el-descriptions-item>
              <el-descriptions-item label="文章数">{{ data.event?.articleCount }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ data.event?.startTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ data.event?.endTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="关键词">
                <el-tag v-for="kw in keywordList" :key="kw" size="small" style="margin:2px">{{ kw }}</el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="16">
          <el-card shadow="never">
            <template #header><span style="font-weight:600">每日报道趋势</span></template>
            <div ref="trendChartRef" style="width:100%;height:320px"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 第二行：情感分布 + 平台分布 -->
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span style="font-weight:600">情感分布</span></template>
            <div v-if="data.sentiment?.total === 0" style="text-align:center;padding:40px;color:#909399">暂无情感分析数据</div>
            <div v-else ref="sentimentChartRef" style="width:100%;height:280px"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never">
            <template #header><span style="font-weight:600">平台分布</span></template>
            <div v-if="!data.sourceDistribution?.length" style="text-align:center;padding:40px;color:#909399">暂无平台数据</div>
            <div v-else ref="sourceChartRef" style="width:100%;height:280px"></div>
          </el-card>
        </el-col>
      </el-row>

      <!-- AI 事件摘要 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-weight:600">事件概述</span>
            <el-tag :type="data.summary?.method === 'llm' ? 'success' : 'warning'" size="small">
              {{ data.summary?.method === 'llm' ? 'AI 生成' : '自动生成' }}
            </el-tag>
          </div>
        </template>
        <p style="line-height:1.8;margin-bottom:12px;color:#303133">{{ data.summary?.summary }}</p>
        <el-descriptions v-if="hasSummaryFields" :column="2" border size="small">
          <el-descriptions-item v-if="data.summary?.time" label="时间">{{ data.summary.time }}</el-descriptions-item>
          <el-descriptions-item v-if="data.summary?.location" label="地点">{{ data.summary.location }}</el-descriptions-item>
          <el-descriptions-item v-if="data.summary?.cause" label="起因" :span="2">{{ data.summary.cause }}</el-descriptions-item>
          <el-descriptions-item v-if="data.summary?.persons" label="涉事人物" :span="2">{{ data.summary.persons }}</el-descriptions-item>
          <el-descriptions-item v-if="data.summary?.key_steps" label="关键步骤" :span="2">{{ data.summary.key_steps }}</el-descriptions-item>
          <el-descriptions-item v-if="data.summary?.important_info" label="重要信息" :span="2">{{ data.summary.important_info }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 关联文章 -->
      <el-card shadow="never" style="margin-bottom:16px">
        <template #header><span style="font-weight:600">关联文章 ({{ data.articles?.length || 0 }})</span></template>
        <el-table :data="data.articles || []" border stripe max-height="400">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="title" label="标题" min-width="300" show-overflow-tooltip />
          <el-table-column prop="sourceName" label="来源" width="120" />
          <el-table-column prop="publishedAt" label="发布时间" width="170" />
        </el-table>
      </el-card>

      <!-- 智能问答 -->
      <el-card shadow="never">
        <template #header><span style="font-weight:600">智能问答</span></template>
        <div ref="qaContainer" style="max-height:400px;overflow-y:auto;margin-bottom:12px">
          <div v-if="qaHistory.length === 0" style="text-align:center;padding:30px;color:#909399">
            <el-icon :size="32"><ChatDotRound /></el-icon>
            <p>针对当前事件提问，AI 为你分析</p>
          </div>
          <div v-for="(item, idx) in qaHistory" :key="idx" style="margin-bottom:12px">
            <div style="display:flex;justify-content:flex-end;margin-bottom:8px">
              <div style="background:#409EFF;color:#fff;padding:8px 14px;border-radius:12px 12px 0 12px;max-width:70%;font-size:14px">{{ item.question }}</div>
            </div>
            <div style="display:flex;justify-content:flex-start">
              <div style="background:#f0f2f5;padding:8px 14px;border-radius:0 12px 12px 12px;max-width:85%;font-size:14px;line-height:1.6">
                <div v-if="item.loading"><el-icon class="is-loading"><Loading /></el-icon> 思考中...</div>
                <div v-else style="white-space:pre-wrap">{{ item.answer }}</div>
                <div v-if="item.source" style="margin-top:4px;font-size:11px;color:#909399">来源: {{ item.source === 'llm' ? 'AI 大模型' : '关键词匹配' }}</div>
              </div>
            </div>
          </div>
        </div>
        <div style="display:flex;gap:8px">
          <el-input v-model="qaInput" placeholder="输入问题，如：该事件的起因是什么？" @keyup.enter="doAsk" :disabled="qaLoading" />
          <el-button type="primary" @click="doAsk" :loading="qaLoading" :disabled="!qaInput.trim()">发送</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEventReport } from '@/api/event'
import { qaReport } from '@/api/report'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const error = ref('')
const data = ref(null)

const qaInput = ref('')
const qaLoading = ref(false)
const qaHistory = ref([])

// ECharts refs
const trendChartRef = ref(null)
const sentimentChartRef = ref(null)
const sourceChartRef = ref(null)

let trendChart = null
let sentimentChart = null
let sourceChart = null

const keywordList = computed(() => {
  const kw = data.value?.event?.keywords
  if (!kw) return []
  return kw.split(',').filter(Boolean)
})

const hasSummaryFields = computed(() => {
  const s = data.value?.summary
  return s && (s.time || s.location || s.cause || s.persons || s.key_steps || s.important_info)
})

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

async function fetchData() {
  loading.value = true
  error.value = ''
  try {
    const id = route.params.id
    const res = await getEventReport(id)
    if (res.code === 200) {
      data.value = res.data
      await nextTick()
      renderCharts()
    } else {
      error.value = res.message || '加载失败'
    }
  } catch (e) {
    error.value = '加载事件详情失败: ' + (e.message || '未知错误')
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderTrendChart()
  renderSentimentChart()
  renderSourceChart()
}

function renderTrendChart() {
  if (!trendChartRef.value || !data.value?.dailyTrend?.length) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)

  const trend = data.value.dailyTrend
  const dates = trend.map(t => t.date)
  const counts = trend.map(t => t.count)

  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 20, top: 20, bottom: 40 },
    xAxis: { type: 'category', data: dates, axisLabel: { rotate: 30, fontSize: 11 } },
    yAxis: { type: 'value', name: '篇', minInterval: 1 },
    series: [{
      type: 'line',
      data: counts,
      itemStyle: { color: '#409EFF' },
      lineStyle: { width: 2 },
      symbol: 'circle',
      symbolSize: 4,
      areaStyle: { color: 'rgba(64, 158, 255, 0.15)' }
    }]
  })
}

function renderSentimentChart() {
  if (!sentimentChartRef.value || !data.value?.sentiment) return
  if (data.value.sentiment.total === 0) return
  if (sentimentChart) sentimentChart.dispose()
  sentimentChart = echarts.init(sentimentChartRef.value)

  const s = data.value.sentiment
  sentimentChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}篇 ({d}%)' },
    legend: { top: 5 },
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      label: { formatter: '{b}\n{d}%' },
      data: [
        { value: Math.round(s.positive * s.total), name: '正面', itemStyle: { color: '#67C23A' } },
        { value: Math.round(s.neutral * s.total), name: '中立', itemStyle: { color: '#909399' } },
        { value: Math.round(s.negative * s.total), name: '负面', itemStyle: { color: '#F56C6C' } },
      ]
    }]
  })
}

function renderSourceChart() {
  if (!sourceChartRef.value || !data.value?.sourceDistribution?.length) return
  if (sourceChart) sourceChart.dispose()
  sourceChart = echarts.init(sourceChartRef.value)

  const sources = data.value.sourceDistribution
  sourceChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 100, right: 20, top: 10, bottom: 20 },
    xAxis: { type: 'value', name: '篇' },
    yAxis: { type: 'category', data: sources.map(s => s.source), axisLabel: { fontSize: 11 } },
    series: [{
      type: 'bar',
      data: sources.map(s => s.count),
      itemStyle: { color: '#409EFF', borderRadius: [0, 4, 4, 0] },
      barMaxWidth: 30,
    }]
  })
}

async function doAsk() {
  const question = qaInput.value.trim()
  if (!question) return

  qaInput.value = ''
  qaHistory.value.push({ question, answer: '', loading: true, source: '' })
  qaLoading.value = true

  try {
    const res = await qaReport({
      question,
      report: { event_id: data.value?.event?.id, title: data.value?.event?.title, keywords: data.value?.event?.keywords, lifecycle: data.value?.event?.lifecycle, article_count: data.value?.event?.articleCount, hotness: data.value?.event?.hotness, sentiment: data.value?.sentiment, article_titles: (data.value?.articles || []).map(a => a.title).slice(0, 10) }
    })
    const last = qaHistory.value[qaHistory.value.length - 1]
    if (res.code === 200) {
      last.answer = res.data.answer || '暂无回答'
      last.source = res.data.source || ''
    } else {
      last.answer = '问答服务异常: ' + (res.message || '未知错误')
    }
    last.loading = false
  } catch (e) {
    const last = qaHistory.value[qaHistory.value.length - 1]
    last.answer = '问答请求失败: ' + (e.message || '未知错误')
    last.loading = false
  } finally {
    qaLoading.value = false
  }

  await nextTick()
  const container = document.querySelector('.el-card__body .qa-container')
  // scroll to bottom
  const qaDiv = document.querySelector('[ref="qaContainer"]')
  if (qaDiv) qaDiv.scrollTop = qaDiv.scrollHeight
}

function handleResize() {
  trendChart?.resize()
  sentimentChart?.resize()
  sourceChart?.resize()
}

onMounted(() => {
  fetchData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  sentimentChart?.dispose()
  sourceChart?.dispose()
})
</script>
