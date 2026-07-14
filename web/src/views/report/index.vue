<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>舆情报告</span>
        </div>
      </template>
      <div class="search-bar">
        <el-input-number v-model="eventId" :min="1" placeholder="事件ID" style="width:200px" />
        <el-button type="primary" @click="handleGenerate" :loading="generating" style="margin-left:12px">生成报告</el-button>
      </div>

      <template v-if="report">
        <el-divider />
        <el-descriptions :column="2" border style="margin-bottom:16px">
          <el-descriptions-item label="报告ID">{{ report.id }}</el-descriptions-item>
          <el-descriptions-item label="事件ID">{{ report.eventId }}</el-descriptions-item>
          <el-descriptions-item label="报告标题" :span="2">{{ report.title }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ report.createTime }}</el-descriptions-item>
          <el-descriptions-item label="生命周期">
            <el-tag>{{ reportData.lifecycle }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="热度">{{ reportData.hotness }}</el-descriptions-item>
          <el-descriptions-item label="文章数">{{ reportData.articleCount }}</el-descriptions-item>
          <el-descriptions-item label="关键词" :span="2">{{ reportData.keywords }}</el-descriptions-item>
        </el-descriptions>

        <el-divider>情感分析</el-divider>
        <el-row :gutter="20" v-if="reportData.sentiment">
          <el-col :span="8"><el-statistic title="正面" :value="(reportData.sentiment.positive * 100).toFixed(1)" suffix="%" /></el-col>
          <el-col :span="8"><el-statistic title="负面" :value="(reportData.sentiment.negative * 100).toFixed(1)" suffix="%" /></el-col>
          <el-col :span="8"><el-statistic title="中立" :value="(reportData.sentiment.neutral * 100).toFixed(1)" suffix="%" /></el-col>
        </el-row>

        <el-divider v-if="reportData.timeline && reportData.timeline.length">时间线</el-divider>
        <div v-if="reportData.timeline && reportData.timeline.length" ref="timelineRef" style="width:100%;height:250px"></div>

        <el-divider>相关文章</el-divider>
        <el-tag v-for="(article, idx) in (reportData.articles || [])" :key="idx" style="margin:4px">{{ article }}</el-tag>
      </template>
      <el-empty v-if="!report && !generating" description="输入事件ID生成舆情报告" />
    </el-card>

    <el-card style="margin-top:20px">
      <template #header>
        <div class="card-header">
          <span>历史报告</span>
          <el-button type="danger" @click="batchDeleteReports" :disabled="!selectedReportRows.length">
            批量删除 ({{ selectedReportRows.length }})
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe @row-click="showReport"
        @selection-change="val => selectedReportRows = val" highlight-current-row>
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="eventId" label="事件ID" width="80" />
        <el-table-column prop="title" label="报告标题" show-overflow-tooltip />
        <el-table-column prop="createTime" label="生成时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="showReport(row)">查看</el-button>
            <el-popconfirm title="确定删除该报告？" @confirm="handleDeleteReport(row)">
              <template #reference>
                <el-button type="danger" link @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px;justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
      <el-empty v-if="!loading && tableData.length === 0" description="暂无历史报告" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { getReports, generateReport, deleteReport } from '@/api/report'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const eventId = ref(1)
const generating = ref(false)
const report = ref(null)
const reportData = ref({})
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })
const selectedReportRows = ref([])
const timelineRef = ref(null)
let timelineChart = null

async function fetchData() {
  loading.value = true
  try {
    const res = await getReports({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch {}
  finally { loading.value = false }
}

function parseReport(row) {
  try {
    return JSON.parse(row.contentJson || '{}')
  } catch {
    return { title: row.title, articles: [], sentiment: {} }
  }
}

function showReport(row) {
  report.value = row
  reportData.value = parseReport(row)
  nextTick(() => renderTimeline())
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generateReport({ eventId: eventId.value })
    report.value = res.data
    reportData.value = parseReport(res.data)
    ElMessage.success('报告已生成')
    nextTick(() => renderTimeline())
    fetchData()
  } catch {}
  finally { generating.value = false }
}

function renderTimeline() {
  if (!timelineRef.value || !reportData.value.timeline?.length) return
  if (timelineChart) timelineChart.dispose()
  timelineChart = echarts.init(timelineRef.value)
  timelineChart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: reportData.value.timeline.map(t => t.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'line', data: reportData.value.timeline.map(t => t.count),
      smooth: true, areaStyle: { opacity: 0.3 },
      itemStyle: { color: '#409EFF' }
    }]
  })
}

async function handleDeleteReport(row) {
  try {
    await deleteReport(row.id)
    ElMessage.success('报告已删除')
    fetchData()
  } catch {}
}

async function batchDeleteReports() {
  const rows = [...selectedReportRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteReport(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 个报告完成`)
    selectedReportRows.value = []
    fetchData()
  } catch {}
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.search-bar { display: flex; align-items: center; }
</style>
