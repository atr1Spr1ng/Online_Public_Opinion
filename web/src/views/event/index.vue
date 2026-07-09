<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>舆情事件管理</span>
          <el-button type="primary" @click="showClusterDialog" :loading="clustering">
            <el-icon><DataAnalysis /></el-icon>
            执行聚类分析
          </el-button>
        </div>
      </template>
      <div style="margin-bottom:12px;display:flex;gap:12px;align-items:center">
        <span style="font-size:14px;color:#606266">分类筛选：</span>
        <el-select v-model="filterCategory" placeholder="全部" clearable style="width:160px" @change="onCategoryChange">
          <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="事件标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.category)" size="small">{{ row.category || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="80" />
        <el-table-column prop="hotness" label="热度" width="80" />
        <el-table-column prop="lifecycle" label="生命周期" width="100">
          <template #default="{ row }">
            <el-tag :type="lifecycleType(row.lifecycle)">{{ row.lifecycle }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column label="操作" width="330">
          <template #default="{ row }">
            <el-button type="info" link @click="goDetail(row)">详情</el-button>
            <el-button type="primary" link @click="doTraceSource(row)">溯源</el-button>
            <el-button type="success" link @click="doAnalyzePath(row)">传播分析</el-button>
            <el-button type="warning" link @click="doGenerateReport(row)">报告</el-button>
            <el-button type="danger" link @click="doForecastTrend(row)">趋势预测</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > 0"
        style="margin-top:16px;justify-content:flex-end"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="pageNum"
        @current-change="fetchData"
      />
    </el-card>

    <el-dialog v-model="clusterVisible" title="事件聚类" width="420px">
      <el-form label-width="100px">
        <el-form-item label="相似度阈值">
          <el-slider v-model="threshold" :min="0.05" :max="0.95" :step="0.05" show-input />
        </el-form-item>
        <el-form-item label="说明">
          <span style="color:#909399;font-size:13px">阈值越低，事件归类越宽松；阈值越高，归类越严格。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clusterVisible = false">取消</el-button>
        <el-button type="primary" @click="doCluster" :loading="clustering">开始聚类</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sourceVisible" title="事件溯源结果" width="600px">
      <el-descriptions v-if="sourceResult" :column="2" border>
        <el-descriptions-item label="事件">{{ sourceResult.eventTitle }}</el-descriptions-item>
        <el-descriptions-item label="源头来源">{{ sourceResult.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="源头文章ID">{{ sourceResult.sourceArticleId }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ sourceResult.publishedAt || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ sourceResult.summary }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="pathVisible" title="传播路径分析" width="700px">
      <el-descriptions v-if="pathResult" :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="传播深度">{{ pathResult.spreadDepth }}</el-descriptions-item>
        <el-descriptions-item label="节点总数">{{ pathResult.totalNodes }}</el-descriptions-item>
        <el-descriptions-item label="持续时间(h)">{{ pathResult.durationHours }}</el-descriptions-item>
        <el-descriptions-item label="传播速度">{{ pathResult.spreadSpeed }} 篇/小时</el-descriptions-item>
      </el-descriptions>
      <el-timeline v-if="pathResult.nodes?.length">
        <el-timeline-item
          v-for="node in pathResult.nodes"
          :key="node.id"
          :timestamp="node.publishedAt"
          :color="node.isSource ? '#409EFF' : '#67C23A'"
        >
          {{ node.articleTitle || `文章#${node.cleanId}` }}
          <el-tag size="small" :type="node.isSource ? 'primary' : 'success'">{{ node.isSource ? '源头' : '传播节点' }}</el-tag>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>

    <el-dialog v-model="trendVisible" :title="'趋势预测：' + trendTitle" width="750px" @opened="renderTrendChart">
      <div v-if="trendLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p style="margin-top:12px;color:#909399">正在分析趋势...</p>
      </div>
      <div v-else-if="trendError" style="text-align:center;padding:40px;color:#F56C6C">
        {{ trendError }}
      </div>
      <div v-else>
        <div style="margin-bottom:12px;display:flex;gap:16px;align-items:center;font-size:13px;color:#606266">
          <span>预测方法：<el-tag size="small" :type="trendMethod === 'prophet' ? 'success' : 'warning'">{{ trendMethod === 'prophet' ? 'Prophet' : trendMethod === 'moving_avg' ? '移动平均（降级）' : trendMethod }}</el-tag></span>
          <span>趋势走向：<el-tag size="small" :type="trendDirection === 'up' ? 'danger' : trendDirection === 'down' ? 'success' : 'info'">{{ trendDirection === 'up' ? '上升' : trendDirection === 'down' ? '下降' : '平稳' }}</el-tag></span>
          <span v-if="trendNote" style="color:#E6A23C">⚠ {{ trendNote }}</span>
        </div>
        <div ref="trendChartRef" style="width:100%;height:350px"></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { listEvents, clusterEvents, forecastTrend } from '@/api/event'
import { traceSource, analyzePropagation } from '@/api/propagation'
import { generateReport } from '@/api/report'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const clustering = ref(false)
const clusterVisible = ref(false)
const threshold = ref(0.25)

const filterCategory = ref('')
const categories = ['社会民生', '科技经济', '教育文化', '医疗卫生', '政治法律', '生态环境', '娱乐体育', '国际时政', '其他']

const sourceVisible = ref(false)
const sourceResult = ref(null)
const pathVisible = ref(false)
const pathResult = ref(null)

const trendVisible = ref(false)
const trendLoading = ref(false)
const trendError = ref('')
const trendTitle = ref('')
const trendMethod = ref('')
const trendDirection = ref('')
const trendNote = ref('')
const trendData = ref(null)
const trendChartRef = ref(null)

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

function onCategoryChange() {
  pageNum.value = 1
  fetchData()
}

async function fetchData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    if (filterCategory.value) params.category = filterCategory.value
    const res = await listEvents(params)
    if (res.code === 200) {
      const data = res.data
      tableData.value = data.records || data || []
      total.value = data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载事件列表失败')
  } finally {
    loading.value = false
  }
}

function goDetail(row) {
  router.push(`/event/${row.id}`)
}

function showClusterDialog() {
  threshold.value = 0.25
  clusterVisible.value = true
}

async function doCluster() {
  clustering.value = true
  try {
    const res = await clusterEvents({ threshold: threshold.value })
    if (res.code === 200 && res.data?.success) {
      const data = res.data
      ElMessage.success(
        `聚类完成：${data.events?.length || 0} 个事件，` +
        `覆盖 ${data.clusteredArticles} 篇文章，` +
        `${data.unclusteredArticles} 篇未归类`
      )
      clusterVisible.value = false
      await fetchData()
    } else {
      ElMessage.error(res.data?.message || '聚类失败')
    }
  } catch (e) {
    ElMessage.error('聚类请求失败: ' + e.message)
  } finally {
    clustering.value = false
  }
}

async function doTraceSource(row) {
  try {
    const res = await traceSource(row.id)
    sourceResult.value = res.data
    sourceVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function doAnalyzePath(row) {
  try {
    const res = await analyzePropagation({ eventId: row.id })
    pathResult.value = res.data
    pathVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function doGenerateReport(row) {
  try {
    const res = await generateReport({ eventId: row.id })
    ElMessage.success(`报告已生成 ID=${res.data?.id || '?'}`)
  } catch (e) { ElMessage.error(e.message) }
}

async function doForecastTrend(row) {
  trendVisible.value = true
  trendLoading.value = true
  trendError.value = ''
  trendTitle.value = row.title || `事件#${row.id}`
  trendData.value = null
  try {
    const res = await forecastTrend(row.id, { periods: 7 })
    if (res.code === 200) {
      trendData.value = res.data
      trendMethod.value = res.data.method || 'unknown'
      trendDirection.value = res.data.trend || 'stable'
      trendNote.value = res.data.note || ''
      await nextTick()
      renderTrendChart()
    } else {
      trendError.value = res.message || '趋势预测失败'
    }
  } catch (e) {
    trendError.value = '趋势预测请求失败: ' + e.message
  } finally {
    trendLoading.value = false
  }
}

function renderTrendChart() {
  if (!trendChartRef.value || !trendData.value) return
  const chart = echarts.init(trendChartRef.value)
  const data = trendData.value

  const historical = data.historical || []
  const forecast = data.forecast || []

  const histDates = historical.map(h => h.date)
  const histCounts = historical.map(h => h.count)
  const histYhat = historical.map(h => h.yhat !== undefined ? h.yhat : null)

  const foreDates = forecast.map(f => f.date)
  const foreCounts = forecast.map(f => f.count)
  const foreLower = forecast.map(f => f.yhat_lower)
  const foreUpper = forecast.map(f => f.yhat_upper)

  const allDates = [...histDates, ...foreDates]
  const splitIdx = histDates.length

  const option = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['实际值', '拟合值', '预测值'], top: 5 },
    grid: { left: 50, right: 30, top: 50, bottom: 40 },
    xAxis: {
      type: 'category',
      data: allDates,
      axisLabel: { rotate: 30, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      name: '文章数',
      minInterval: 1,
    },
    series: [
      {
        name: '实际值',
        type: 'line',
        data: histCounts,
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 2 },
        symbol: 'circle',
        symbolSize: 4,
      },
      {
        name: '拟合值',
        type: 'line',
        data: histYhat,
        itemStyle: { color: '#67C23A' },
        lineStyle: { type: 'dashed', width: 1.5 },
        symbol: 'none',
        connectNulls: false,
      },
      {
        name: '预测值',
        type: 'line',
        data: [...Array(splitIdx).fill(null), ...foreCounts],
        itemStyle: { color: '#E6A23C' },
        lineStyle: { type: 'dashed', width: 2 },
        symbol: 'diamond',
        symbolSize: 6,
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(230, 162, 60, 0.3)' },
              { offset: 1, color: 'rgba(230, 162, 60, 0.05)' }
            ]
          }
        },
        markArea: {
          silent: true,
          data: forecast.map((f, i) => [
            { xAxis: foreDates[i], yAxis: f.yhat_lower },
            { xAxis: foreDates[i], yAxis: f.yhat_upper }
          ]).filter(p => p[0] && p[1]),
          itemStyle: { color: 'rgba(230, 162, 60, 0.1)' }
        }
      },
    ],
  }

  chart.setOption(option)
  window.addEventListener('resize', () => chart.resize())
}

onMounted(fetchData)
</script>
