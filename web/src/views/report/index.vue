<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>舆情报告</span>
          <div>
            <el-input-number v-model="eventId" :min="1" placeholder="事件ID" style="width:160px" />
            <el-button type="primary" @click="handleGenerate" :loading="generating" style="margin-left:8px">生成报告</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe @row-click="showReport">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="报告标题" show-overflow-tooltip />
        <el-table-column prop="eventId" label="事件ID" width="80" />
        <el-table-column prop="createTime" label="生成时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="showReport(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="reportVisible" :title="'报告: ' + currentReport?.title" width="700px" top="40px">
      <div v-if="reportData">
        <el-descriptions :column="2" border style="margin-bottom:16px">
          <el-descriptions-item label="事件">{{ reportData.title }}</el-descriptions-item>
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
        <el-divider>相关文章</el-divider>
        <el-tag v-for="(article, idx) in (reportData.articles || [])" :key="idx" style="margin:4px">{{ article }}</el-tag>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getReports, generateReport } from '@/api/report'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const generating = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })
const eventId = ref(1)
const reportVisible = ref(false)
const currentReport = ref(null)
const reportData = ref(null)

async function fetchData() {
  loading.value = true
  try {
    const res = await getReports({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function showReport(row) {
  currentReport.value = row
  try {
    reportData.value = JSON.parse(row.contentJson || '{}')
  } catch {
    reportData.value = { title: row.title, articles: [], sentiment: {} }
  }
  reportVisible.value = true
}

async function handleGenerate() {
  generating.value = true
  try {
    const res = await generateReport({ eventId: eventId.value })
    ElMessage.success(`报告已生成 ID=${res.data?.id}`)
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
  finally { generating.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>
