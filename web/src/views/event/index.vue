<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>舆情事件管理</span>
          <div style="display:flex;gap:8px">
            <el-button type="danger" @click="batchDeleteEvents" :disabled="!selectedRows.length">
              批量删除 ({{ selectedRows.length }})
            </el-button>
            <el-button type="primary" @click="showClusterDialog" :loading="clustering">
              <el-icon><DataAnalysis /></el-icon>
              执行聚类分析
            </el-button>
          </div>
        </div>
      </template>
      <div style="margin-bottom:12px;display:flex;gap:12px;align-items:center">
        <span style="font-size:14px;color:#606266">分类筛选：</span>
        <el-select v-model="filterCategory" placeholder="全部" clearable style="width:160px" @change="onCategoryChange">
          <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="val => selectedRows = val">
        <el-table-column type="selection" width="50" />
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
        <el-table-column label="情感" width="150">
          <template #default="{ row }">
            <template v-if="row.sentimentPositive != null">
              <span style="display:flex;gap:4px;align-items:center;font-size:12px">
                <span style="color:#67C23A">正{{ (row.sentimentPositive * 100).toFixed(0) }}%</span>
                <span style="color:#909399">中{{ (row.sentimentNeutral * 100).toFixed(0) }}%</span>
                <span style="color:#F56C6C">负{{ (row.sentimentNegative * 100).toFixed(0) }}%</span>
              </span>
            </template>
            <span v-else style="color:#909399">-</span>
          </template>
        </el-table-column>
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
            <el-popconfirm title="确定删除该事件？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
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

    <el-dialog v-model="pathVisible" title="传播路径分析" width="800px" @opened="renderPathGraph">
      <el-descriptions v-if="pathResult" :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="传播深度">{{ pathResult.spreadDepth }}</el-descriptions-item>
        <el-descriptions-item label="节点总数">{{ pathResult.totalNodes }}</el-descriptions-item>
        <el-descriptions-item label="持续时间(h)">{{ pathResult.durationHours }}</el-descriptions-item>
        <el-descriptions-item label="传播速度">{{ pathResult.spreadSpeed }} 篇/小时</el-descriptions-item>
      </el-descriptions>
      <div v-if="pathResult" style="margin-bottom:4px;display:flex;gap:12px;align-items:center;font-size:12px;color:#606266">
        <span>分析：<el-tag size="small" :type="pathResult.method === 'llm' ? 'success' : 'warning'">{{ pathResult.method === 'llm' ? 'AI 分析' : '规则降级' }}</el-tag></span>
        <span style="margin-left:8px"><span style="display:inline-block;width:10px;height:10px;background:#F56C6C;border-radius:2px;margin-right:2px;vertical-align:middle"></span>官方</span>
        <span><span style="display:inline-block;width:10px;height:10px;background:#67C23A;border-radius:2px;margin-right:2px;vertical-align:middle"></span>社交</span>
        <span><span style="display:inline-block;width:10px;height:10px;background:#409EFF;border-radius:2px;margin-right:2px;vertical-align:middle"></span>商业</span>
        <span>⬩ 关键节点</span>
      </div>
      <div v-if="pathResult?.nodes?.length" ref="pathGraphRef" style="width:100%;height:400px"></div>
    </el-dialog>

    <el-dialog v-model="reportVisible" title="舆情报告" width="700px" @opened="renderReportTimeline">
      <template v-if="reportData">
        <div v-if="parseReportContent(reportData.contentJson).title">
          <el-descriptions :column="2" border style="margin-bottom:16px">
            <el-descriptions-item label="报告ID">{{ reportData.id }}</el-descriptions-item>
            <el-descriptions-item label="事件ID">{{ reportData.eventId }}</el-descriptions-item>
            <el-descriptions-item label="标题" :span="2">{{ parseReportContent(reportData.contentJson).title }}</el-descriptions-item>
            <el-descriptions-item label="生命周期">
              <el-tag>{{ parseReportContent(reportData.contentJson).lifecycle }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="热度">{{ parseReportContent(reportData.contentJson).hotness }}</el-descriptions-item>
            <el-descriptions-item label="文章数">{{ parseReportContent(reportData.contentJson).articleCount }}</el-descriptions-item>
            <el-descriptions-item label="关键词" :span="2">{{ parseReportContent(reportData.contentJson).keywords }}</el-descriptions-item>
          </el-descriptions>

          <el-divider>情感分析</el-divider>
          <el-row :gutter="20" v-if="parseReportContent(reportData.contentJson).sentiment">
            <el-col :span="8"><el-statistic title="正面" :value="(parseReportContent(reportData.contentJson).sentiment.positive * 100).toFixed(1)" suffix="%" /></el-col>
            <el-col :span="8"><el-statistic title="负面" :value="(parseReportContent(reportData.contentJson).sentiment.negative * 100).toFixed(1)" suffix="%" /></el-col>
            <el-col :span="8"><el-statistic title="中立" :value="(parseReportContent(reportData.contentJson).sentiment.neutral * 100).toFixed(1)" suffix="%" /></el-col>
          </el-row>

          <el-divider v-if="parseReportContent(reportData.contentJson).timeline?.length">时间线</el-divider>
          <div v-if="parseReportContent(reportData.contentJson).timeline?.length" ref="reportTimelineRef" style="width:100%;height:250px"></div>

          <el-divider>相关文章</el-divider>
          <el-tag v-for="(a, idx) in (parseReportContent(reportData.contentJson).articles || [])" :key="idx" style="margin:4px">{{ a }}</el-tag>
        </div>
      </template>
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

    <!-- 聚类结果弹窗 -->
    <el-dialog v-model="clusterResultVisible" title="聚类分析结果" width="480px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="输入文章数">{{ clusterResult?.totalArticles || 0 }}</el-descriptions-item>
        <el-descriptions-item label="聚类成功">
          <span style="color:#67C23A;font-weight:bold">{{ clusterResult?.clusteredArticles || 0 }}</span>
          <span style="color:#909399;margin-left:4px">篇（{{ clusterResult?.totalArticles ? (clusterResult.clusteredArticles / clusterResult.totalArticles * 100).toFixed(1) : 0 }}%）</span>
        </el-descriptions-item>
        <el-descriptions-item label="噪点文章">
          <span :style="{color: noiseRate > 40 ? '#F56C6C' : noiseRate > 20 ? '#E6A23C' : '#67C23A', fontWeight:'bold'}">{{ clusterResult?.unclusteredArticles || 0 }}</span>
          <span style="color:#909399;margin-left:4px">篇（噪点率 {{ noiseRate.toFixed(1) }}%）</span>
        </el-descriptions-item>
        <el-descriptions-item label="生成事件数">
          <el-tag type="primary">{{ clusterResult?.events?.length || 0 }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="clusterResultVisible = false">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { listEvents, clusterEvents, forecastTrend, deleteEvent } from '@/api/event'
import { traceSource, analyzePropagation } from '@/api/propagation'
import { generateReport } from '@/api/report'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const router = useRouter()

const loading = ref(false)
const tableData = ref([])
const selectedRows = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const clustering = ref(false)
const clusterVisible = ref(false)
const clusterResultVisible = ref(false)
const clusterResult = ref(null)
const noiseRate = computed(() => {
  if (!clusterResult.value || !clusterResult.value.totalArticles) return 0
  return clusterResult.value.unclusteredArticles / clusterResult.value.totalArticles * 100
})
const threshold = ref(0.25)

const filterCategory = ref('')
const categories = ['社会民生', '科技经济', '教育文化', '医疗卫生', '政治法律', '生态环境', '娱乐体育', '国际时政', '其他']

const sourceVisible = ref(false)
const sourceResult = ref(null)
const pathVisible = ref(false)
const pathResult = ref(null)
const pathGraphRef = ref(null)
let pathChart = null

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
      clusterResult.value = data
      clusterVisible.value = false
      clusterResultVisible.value = true
      await fetchData()
    } else {
      ElMessage.error(res.data?.message || '聚类失败')
    }
  } catch (e) {
    // error already toasted by global interceptor
  } finally {
    clustering.value = false
  }
}

async function doTraceSource(row) {
  try {
    const res = await traceSource(row.id)
    sourceResult.value = res.data
    sourceVisible.value = true
  } catch {}
}

async function doAnalyzePath(row) {
  try {
    const res = await analyzePropagation({ eventId: row.id })
    pathResult.value = res.data
    pathVisible.value = true
    await nextTick()
    renderPathGraph()
  } catch {}
}

function renderPathGraph() {
  if (!pathGraphRef.value || !pathResult.value?.nodes?.length) return
  if (pathChart) pathChart.dispose()
  pathChart = echarts.init(pathGraphRef.value)

  const typeName = { official: '官方媒体', commercial: '商业媒体', social: '社交媒体' }

  const nodeColor = n => {
    if (n.isSource) return '#E6A23C'
    if (n.nodeType === 'official') return '#F56C6C'
    if (n.nodeType === 'social') return '#67C23A'
    return '#409EFF'
  }

  const nodes = pathResult.value.nodes.map(n => ({
    id: n.cleanId,
    name: n.articleTitle || `文章#${n.cleanId}`,
    symbolSize: n.isSource ? 44 : n.isInfluencer ? 36 : Math.max(18, 32 - (n.depth || 0) * 3),
    symbol: n.isInfluencer ? 'diamond' : 'circle',
    itemStyle: { color: nodeColor(n), borderColor: n.isInfluencer ? '#333' : 'transparent', borderWidth: n.isInfluencer ? 2 : 0 },
    label: { show: true, fontSize: n.isSource ? 12 : 10, formatter: p => {
      const label = p.name.length > 10 ? p.name.slice(0, 10) + '...' : p.name
      if (n.isSource) return label + '\n源头'
      if (n.isInfluencer) return label + '\n★关键'
      return label
    } }
  }))

  const links = (pathResult.value.edges || []).map(e => ({
    source: String(e.source),
    target: String(e.target),
    lineStyle: { width: Math.max(1, (e.similarity || 0) * 4), opacity: Math.min(1, (e.similarity || 0) + 0.3) }
  }))

  if (links.length === 0 && pathResult.value.nodes.length > 1) {
    const sourceNode = pathResult.value.nodes.find(n => n.isSource)
    pathResult.value.nodes.filter(n => !n.isSource).forEach(n => {
      if (sourceNode?.cleanId) links.push({ source: String(sourceNode.cleanId), target: String(n.cleanId), lineStyle: { width: 1, opacity: 0.5 } })
    })
  }

  pathChart.setOption({
    tooltip: { formatter: p => {
      if (p.dataType === 'node') {
        const n = pathResult.value.nodes.find(x => x.cleanId == p.id) || {}
        const tags = [n.isSource && '源头', n.isInfluencer && '关键节点', typeName[n.nodeType]].filter(Boolean).join(' | ')
        return `${p.name}<br/>来源: ${n.sourceName || '未知'}<br/>${tags}`
      }
      return ''
    } },
    series: [{
      type: 'graph',
      layout: 'force',
      force: { repulsion: 350, edgeLength: [120, 300], gravity: 0.08 },
      roam: true,
      draggable: true,
      data: nodes,
      links: links,
      lineStyle: { color: '#c0c4cc', curveness: 0.25 }
    }]
  })
}

const reportVisible = ref(false)
const reportData = ref(null)
const reportTimelineRef = ref(null)
let reportChart = null

async function doGenerateReport(row) {
  try {
    const res = await generateReport({ eventId: row.id })
    reportData.value = res.data
    reportVisible.value = true
    await nextTick()
    renderReportTimeline()
  } catch {}
}

function parseReportContent(json) {
  try { return JSON.parse(json || '{}') }
  catch { return {} }
}

function renderReportTimeline() {
  if (!reportTimelineRef.value || !reportData.value) return
  const content = parseReportContent(reportData.value.contentJson)
  if (!content.timeline?.length) return
  if (reportChart) reportChart.dispose()
  reportChart = echarts.init(reportTimelineRef.value)
  reportChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: content.timeline.map(t => t.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line', data: content.timeline.map(t => t.count),
      smooth: true, areaStyle: { opacity: 0.3 },
      itemStyle: { color: '#409EFF' }
    }]
  })
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
      trendLoading.value = false
      await nextTick()
      renderTrendChart()
    } else {
      trendError.value = res.message || '趋势预测失败'
      trendLoading.value = false
    }
  } catch (e) {
    trendError.value = '趋势预测请求失败'
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

async function handleDelete(row) {
  try {
    await deleteEvent(row.id)
    ElMessage.success('事件已删除')
    fetchData()
  } catch {}
}

async function batchDeleteEvents() {
  const rows = [...selectedRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteEvent(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 个事件完成`)
    selectedRows.value = []
    fetchData()
  } catch {}
}

onMounted(fetchData)
</script>
