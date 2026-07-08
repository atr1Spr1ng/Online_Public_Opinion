<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>情感分析结果</span>
          <el-button type="primary" @click="batchAnalyze" :disabled="!selected.length">批量分析 ({{ selected.length }})</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="val => selected = val.map(i => i.cleanId)">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="cleanId" label="文章ID" width="80" />
        <el-table-column prop="sentiment" label="情感" width="100">
          <template #default="{ row }">
            <el-tag :type="sentimentType(row.sentiment)">{{ sentimentLabel(row.sentiment) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="positiveScore" label="正面分数" width="90" />
        <el-table-column prop="negativeScore" label="负面分数" width="90" />
        <el-table-column prop="confidence" label="置信度" width="90" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="分析详情" width="500px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="情感倾向">
          <el-tag :type="sentimentType(detail.sentiment)">{{ sentimentLabel(detail.sentiment) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="置信度">{{ detail.confidence }}</el-descriptions-item>
        <el-descriptions-item label="正面分数">{{ detail.positiveScore }}</el-descriptions-item>
        <el-descriptions-item label="负面分数">{{ detail.negativeScore }}</el-descriptions-item>
        <el-descriptions-item label="详情" :span="2">
          <pre style="max-height:200px;overflow:auto">{{ detail.detailsJson }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSentimentResults, batchSentiment } from '@/api/analysis'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selected = ref([])
const page = reactive({ pageNum: 1, pageSize: 10 })
const detailVisible = ref(false)
const detail = ref({})

function sentimentType(s) { return s === 'POSITIVE' ? 'success' : s === 'NEGATIVE' ? 'danger' : 'info' }
function sentimentLabel(s) { return s === 'POSITIVE' ? '正面' : s === 'NEGATIVE' ? '负面' : '中立' }

async function fetchData() {
  loading.value = true
  try {
    const res = await getSentimentResults({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function showDetail(row) { detail.value = row; detailVisible.value = true }

async function batchAnalyze() {
  try {
    await batchSentiment(selected.value)
    ElMessage.success('批量分析完成')
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
