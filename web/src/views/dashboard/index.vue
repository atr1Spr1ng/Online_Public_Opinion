<template>
  <div>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8"><el-card><div class="stat-item"><div class="stat-num">{{ stats.articles }}</div><div class="stat-label">原始文章</div></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-item"><div class="stat-num">{{ stats.cleaned }}</div><div class="stat-label">已清洗</div></div></el-card></el-col>
      <el-col :span="8"><el-card><div class="stat-item"><div class="stat-num">{{ stats.events }}</div><div class="stat-label">舆情事件</div></div></el-card></el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="8">
        <el-card class="task-card">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span>后台任务</span>
              <el-button type="primary" text @click="fetchTasks">刷新</el-button>
            </div>
          </template>
          <div v-if="recentTasks.length" class="task-list">
            <div v-for="task in recentTasks" :key="task.uid" class="task-item">
              <div class="task-title">
                <span>{{ taskTypeLabel(task.taskType) }}</span>
                <el-tag :type="taskStatusType(task.status)" size="small">{{ taskStatusLabel(task.status) }}</el-tag>
              </div>
              <div class="task-meta">
                <span>{{ formatTime(task.startTime || task.createTime) }}</span>
                <el-button
                  v-if="isTerminalTask(task.status)"
                  type="primary"
                  link
                  @click="showTaskDetail(task)"
                >
                  查看详情
                </el-button>
                <span v-else>{{ taskStatusLabel(task.status) }}</span>
              </div>
              <div v-if="task.taskType === 'EVENT_CLUSTER' && task.unclusteredCount !== null" class="task-extra">
                未成簇 {{ task.unclusteredCount }} 篇
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无后台任务" />
        </el-card>
      </el-col>
      <el-col :span="16">
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
            <el-button type="primary" link @click="$router.push('/event/${row.id}')">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!eventLoading && hotEvents.length === 0" description="暂无热点事件" />
    </el-card>

    <el-card style="margin-top:20px">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>我的关注事件</span>
          <el-button type="primary" text @click="$router.push('/user/preferences')">偏好设置</el-button>
        </div>
      </template>
      <el-empty v-if="!hasPreferences" description="您暂未设置偏好，请前往偏好设置添加关键词或关注领域" />
      <el-table v-else :data="feedEvents" v-loading="feedLoading" border stripe>
        <el-table-column prop="title" label="事件标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="90">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.category)" size="small">{{ row.category || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="matchType" label="匹配方式" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.matchType === 'keyword'" type="warning" size="small">关键词</el-tag>
            <el-tag v-else-if="row.matchType === 'domain'" type="success" size="small">领域</el-tag>
            <el-tag v-else-if="row.matchType === 'both'" type="primary" size="small">关键词+领域</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hotness" label="热度" width="80" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push('/event/${row.id}')">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="taskDetailVisible" title="后台任务详情" width="860px">
      <el-descriptions v-if="selectedTask" :column="2" border>
        <el-descriptions-item label="任务ID">{{ selectedTask.id }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ taskTypeLabel(selectedTask.taskType) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="taskStatusType(selectedTask.status)">{{ taskStatusLabel(selectedTask.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="处理对象">{{ selectedTask.targetType || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="selectedTask.taskType === 'CRAWL' ? '候选链接' : '总数'">{{ selectedTask.totalCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item v-if="selectedTask.taskType === 'CRAWL'" label="已处理">
          {{ selectedTask.processedCount ?? 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="成功">{{ selectedTask.successCount ?? 0 }}</el-descriptions-item>
        <el-descriptions-item
          v-if="selectedTask.taskType === 'EVENT_CLUSTER' && selectedTask.unclusteredCount !== null"
          label="未成簇"
        >
          {{ selectedTask.unclusteredCount }} 篇
        </el-descriptions-item>
        <el-descriptions-item label="失败/重复">
          失败 {{ selectedTask.failedCount ?? 0 }}
          <span v-if="selectedTask.duplicateCount">，重复 {{ selectedTask.duplicateCount }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(selectedTask.startTime || selectedTask.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间" :span="2">{{ formatTime(selectedTask.finishTime) }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ selectedTask.message || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-divider v-if="taskDetailItems.length">任务明细</el-divider>
      <el-table v-if="taskDetailItems.length" :data="taskDetailItems" border stripe max-height="360">
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="taskStatusType(row.status)" size="small">{{ taskItemStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetTitle" label="对象" min-width="220" show-overflow-tooltip />
        <el-table-column prop="url" label="URL" min-width="220" show-overflow-tooltip />
        <el-table-column prop="failureReason" label="失败原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.failureReason || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="selectedTask" description="暂无任务明细" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getArticles, getTasks as getCrawlerTasks, getTaskDetail as getCrawlerTaskDetail } from '@/api/crawler'
import { getCleanArticles } from '@/api/content'
import { getSentimentResults } from '@/api/analysis'
import { listEvents, getMyFeedEvents } from '@/api/event'
import { listKeywords, listDomains } from '@/api/user'
import { getRecentTasks, getTask, getTaskItems } from '@/api/task'

const stats = reactive({ articles: 0, cleaned: 0, events: 0 })
const sentimentData = ref([])
const chartRef = ref(null)
const hotEvents = ref([])
const eventLoading = ref(false)
const feedEvents = ref([])
const feedLoading = ref(false)
const hasPreferences = ref(true)
const recentTasks = ref([])
const taskDetailVisible = ref(false)
const selectedTask = ref(null)
const taskDetailItems = ref([])
let taskTimer = null

function taskTypeLabel(type) {
  const map = {
    CLEAN: '内容清洗',
    SENTIMENT: '情感分析',
    FAKE_DETECT: '虚假检测',
    EVENT_CLUSTER: '事件聚类',
    CRAWL: '数据采集'
  }
  return map[type] || type || '后台任务'
}

function taskStatusLabel(status) {
  const map = {
    PENDING: '排队中',
    RUNNING: '执行中',
    SUCCESS: '已完成',
    PARTIAL_FAILED: '部分失败',
    SUCCESS_WITH_DUPLICATE: '已完成',
    DUPLICATE: '全部重复',
    FAILED: '失败',
    SUCCESS_WITH_DUP: '已完成',
    PARTIAL: '部分失败'
  }
  return map[status] || status || '未知'
}

function taskItemStatusLabel(status) {
  if (status === 'DUPLICATE') return '重复'
  return taskStatusLabel(status)
}

function taskStatusType(status) {
  if (status === 'SUCCESS' || status === 'SUCCESS_WITH_DUPLICATE' || status === 'DUPLICATE') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PARTIAL_FAILED' || status === 'PARTIAL') return 'warning'
  if (status === 'RUNNING') return 'primary'
  return 'info'
}

async function fetchTasks() {
  try {
    const [processingRes, crawlRes] = await Promise.all([
      getRecentTasks({ limit: 8 }).catch(() => ({ data: [] })),
      getCrawlerTasks({ pageNum: 1, pageSize: 8 }).catch(() => ({ data: { records: [] } }))
    ])
    const processing = (processingRes.data || []).map(normalizeProcessingTask)
    const crawlRecords = crawlRes.data?.records || crawlRes.data || []
    const crawl = (Array.isArray(crawlRecords) ? crawlRecords : []).map(normalizeCrawlTask)
    recentTasks.value = [...processing, ...crawl]
      .sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
      .slice(0, 5)
  } catch (_) {}
}

function isTerminalTask(status) {
  return ['SUCCESS', 'PARTIAL_FAILED', 'FAILED', 'SUCCESS_WITH_DUPLICATE', 'DUPLICATE'].includes(status)
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function showTaskDetail(task) {
  taskDetailItems.value = []
  try {
    if (task.source === 'crawl') {
      const res = await getCrawlerTaskDetail(task.id)
      const detail = res.data || res
      selectedTask.value = normalizeCrawlTask(detail.task || task)
      taskDetailItems.value = (detail.items || []).map(item => ({
        id: item.id,
        targetId: item.articleId,
        targetTitle: item.title || item.url || `URL #${item.id}`,
        url: item.url || '',
        status: item.status,
        failureReason: item.failureReason || '',
        createTime: item.createTime
      }))
    } else {
      const [taskRes, itemRes] = await Promise.all([
        getTask(task.id),
        getTaskItems(task.id).catch(() => ({ data: [] }))
      ])
      selectedTask.value = normalizeProcessingTask(taskRes.data || task)
      const rawItems = itemRes.data || []
      taskDetailItems.value = selectedTask.value.taskType === 'EVENT_CLUSTER'
        ? rawItems.filter(item => item.status === 'FAILED').map(normalizeProcessingItem)
        : rawItems.map(normalizeProcessingItem)
    }
  } catch (_) {
    selectedTask.value = task
    taskDetailItems.value = []
  }
  taskDetailVisible.value = true
}

function normalizeProcessingTask(task) {
  const unclusteredCount = task.taskType === 'EVENT_CLUSTER'
    ? extractUnclusteredCount(task.message)
    : null
  return {
    ...task,
    uid: `processing-${task.id}`,
    source: 'processing',
    totalCount: task.totalCount ?? 0,
    successCount: task.successCount ?? 0,
    failedCount: task.failedCount ?? 0,
    duplicateCount: 0,
    message: task.message || '',
    unclusteredCount
  }
}

function normalizeProcessingItem(item) {
  return {
    id: item.id,
    targetId: item.targetId,
    targetTitle: item.targetTitle || `${item.targetType || '对象'} ${item.targetId || ''}`,
    url: '',
    status: item.status,
    failureReason: item.failureReason || '',
    createTime: item.createTime
  }
}

function extractUnclusteredCount(message) {
  if (!message) return null
  const match = String(message).match(/未成簇(?:\/噪音)?\s*(\d+)\s*篇/)
  return match ? Number(match[1]) : null
}

function normalizeCrawlTask(task) {
  const failed = task.totalFailed ?? 0
  const duplicate = task.totalDuplicate ?? 0
  const discovered = task.totalDiscovered ?? task.requestLimit ?? 0
  const processed = (task.totalSuccess ?? 0) + duplicate + failed
  return {
    ...task,
    uid: `crawl-${task.id}`,
    source: 'crawl',
    taskType: 'CRAWL',
    targetType: task.sourceName || task.sourceUrl || 'NEWS_SOURCE',
    totalCount: discovered,
    processedCount: processed,
    successCount: task.totalSuccess ?? 0,
    failedCount: failed,
    duplicateCount: duplicate,
    startTime: task.createTime,
    finishTime: isTerminalTask(task.status) ? task.updateTime : null,
    message: buildCrawlMessage(task)
  }
}

function buildCrawlMessage(task) {
  const parts = []
  if (task.sourceName) parts.push(`新闻源：${task.sourceName}`)
  if (task.sourceUrl) parts.push(`URL：${task.sourceUrl}`)
  const processed = (task.totalSuccess ?? 0) + (task.totalDuplicate ?? 0) + (task.totalFailed ?? 0)
  parts.push(`请求上限 ${task.requestLimit ?? '-'}，候选链接 ${task.totalDiscovered ?? 0}，已处理 ${processed}，成功入库 ${task.totalSuccess ?? 0}，重复 ${task.totalDuplicate ?? 0}，失败 ${task.totalFailed ?? 0}`)
  return parts.join('；')
}

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
  fetchTasks()
  taskTimer = window.setInterval(fetchTasks, 5000)

  try { const res = await getArticles({ pageNum: 1, pageSize: 1, excludeCleaned: false }); stats.articles = res.data?.total || res.total || 0 } catch (_) {}
  try { const res = await getCleanArticles({ pageNum: 1, pageSize: 1 }); stats.cleaned = res.data?.total || res.total || 0 } catch (_) {}
  try { const res = await listEvents({ pageNum: 1, pageSize: 1 }); stats.events = res.data?.total || res.total || 0 } catch (_) {}

  try {
    const res = await getSentimentResults({ pageNum: 1, pageSize: 10000 })
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
    const res = await listEvents({ pageNum: 1, pageSize: 5 })
    hotEvents.value = res.data?.records || res.data || []
  } catch (_) {}
  finally { eventLoading.value = false }

  // 我的关注事件
  feedLoading.value = true
  try {
    const [kwRes, domRes] = await Promise.all([
      listKeywords().catch(() => ({ data: [] })),
      listDomains().catch(() => ({ data: [] }))
    ])
    const kws = kwRes.data || []
    const doms = domRes.data || []
    if (kws.length === 0 && doms.length === 0) {
      hasPreferences.value = false
    } else {
      const res = await getMyFeedEvents()
      feedEvents.value = res.data || []
    }
  } catch (_) {}
  finally { feedLoading.value = false }
})

onUnmounted(() => {
  if (taskTimer) window.clearInterval(taskTimer)
})
</script>

<style scoped>
.stats-row .el-card { text-align: center; }
.stat-num { font-size: 32px; font-weight: bold; color: #409EFF; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
.task-card { min-height: 326px; }
.task-list { display: flex; flex-direction: column; gap: 12px; }
.task-item { border-bottom: 1px solid #ebeef5; padding-bottom: 10px; }
.task-item:last-child { border-bottom: none; padding-bottom: 0; }
.task-title { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.task-meta { display: flex; justify-content: space-between; color: #909399; font-size: 12px; margin-top: 4px; }
.task-extra { color: #606266; font-size: 12px; margin-top: 4px; }
</style>
