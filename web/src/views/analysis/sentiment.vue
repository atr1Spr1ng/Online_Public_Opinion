<template>
  <div>
    <!-- 待分析已清洗文章 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>待分析已清洗文章</span>
          <div>
            <el-button type="primary" @click="batchAnalyze" :disabled="!selectedCleanIds.length">
              分析选中 ({{ selectedCleanIds.length }})
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="cleanArticles" v-loading="cleanLoading" border stripe
        @selection-change="val => selectedCleanIds = val.map(i => i.id)"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250" />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="keywords" label="关键词" show-overflow-tooltip width="200" />
        <el-table-column prop="createTime" label="清洗时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="analyzeOne(row)">分析</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="cleanPage.pageNum" v-model:page-size="cleanPage.pageSize"
        :total="cleanTotal" layout="total, prev, pager, next" @change="fetchCleanArticles"
      />
    </el-card>

    <!-- 已有分析结果 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>已有分析结果</span>
          <div style="display:flex;gap:8px">
            <el-button type="primary" @click="batchReanalyze" :disabled="!selectedResultRows.length">
              批量重新分析 ({{ selectedResultRows.length }})
            </el-button>
            <el-button type="danger" @click="batchDeleteResults" :disabled="!selectedResultRows.length">
              批量删除 ({{ selectedResultRows.length }})
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="resultData" v-loading="resultLoading" border stripe
        @selection-change="val => selectedResultRows = val"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="cleanId" label="文章ID" width="80" />
        <el-table-column prop="title" label="文章标题" show-overflow-tooltip min-width="200" />
        <el-table-column prop="sentiment" label="情感" width="100">
          <template #default="{ row }">
            <el-tag :type="sentimentType(row.sentiment)">{{ sentimentLabel(row.sentiment) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="positiveScore" label="正面分数" width="90" />
        <el-table-column prop="negativeScore" label="负面分数" width="90" />
        <el-table-column prop="confidence" label="置信度" width="90" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <el-button v-if="row.originalUrl" type="primary" link @click="openUrl(row.originalUrl)">查看原文</el-button>
            <el-popconfirm title="确定删除该分析结果？" @confirm="handleDeleteResult(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="resultPage.pageNum" v-model:page-size="resultPage.pageSize"
        :total="resultTotal" layout="total, prev, pager, next" @change="fetchResults"
      />
    </el-card>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCleanArticles } from '@/api/content'
import { batchSentiment, getSentimentResults, deleteSentimentResult } from '@/api/analysis'
import { ElMessage } from 'element-plus'

// ---- 待分析已清洗文章 ----
const cleanLoading = ref(false)
const cleanArticles = ref([])
const cleanTotal = ref(0)
const cleanPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedCleanIds = ref([])

async function fetchCleanArticles() {
  cleanLoading.value = true
  try {
    const res = await getCleanArticles({ pageNum: cleanPage.pageNum, pageSize: cleanPage.pageSize, excludeAnalyzed: true })
    cleanArticles.value = res.data?.records || res.data || []
    cleanTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { cleanLoading.value = false }
}

// ---- 已有分析结果 ----
const resultLoading = ref(false)
const resultData = ref([])
const resultTotal = ref(0)
const resultPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedResultRows = ref([])

function sentimentType(s) { return s === 'POSITIVE' ? 'success' : s === 'NEGATIVE' ? 'danger' : 'info' }
function sentimentLabel(s) { return s === 'POSITIVE' ? '正面' : s === 'NEGATIVE' ? '负面' : '中立' }
function openUrl(url) {
  window.open(url, '_blank')
}

async function fetchResults() {
  resultLoading.value = true
  try {
    const res = await getSentimentResults({ pageNum: resultPage.pageNum, pageSize: resultPage.pageSize })
    resultData.value = res.data?.records || res.data || []
    resultTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { resultLoading.value = false }
}

// ---- 分析操作 ----
async function analyzeOne(row) {
  try {
    await batchSentiment([row.id])
    ElMessage.success(`「${row.title || row.id}」分析完成`)
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

async function batchAnalyze() {
  try {
    await batchSentiment(selectedCleanIds.value)
    ElMessage.success(`批量分析 ${selectedCleanIds.value.length} 篇文章完成`)
    selectedCleanIds.value = []
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

async function batchReanalyze() {
  const ids = selectedResultRows.value.map(r => r.cleanId)
  if (!ids.length) return
  try {
    await batchSentiment(ids)
    ElMessage.success(`重新分析 ${ids.length} 篇文章完成`)
    selectedResultRows.value = []
    fetchResults()
  } catch {}
}

async function batchDeleteResults() {
  const rows = [...selectedResultRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteSentimentResult(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 条分析结果完成`)
    selectedResultRows.value = []
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

async function handleDeleteResult(row) {
  try {
    await deleteSentimentResult(row.id)
    ElMessage.success('分析结果已删除')
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

onMounted(() => {
  fetchCleanArticles()
  fetchResults()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
