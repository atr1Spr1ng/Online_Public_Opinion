<template>
  <div>
    <!-- 待检测已清洗文章 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>待检测已清洗文章</span>
          <div>
            <el-button type="primary" @click="batchDetect" :disabled="!selectedCleanIds.length">
              检测选中 ({{ selectedCleanIds.length }})
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
            <el-button type="primary" link @click="detectOne(row)">检测</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="cleanPage.pageNum" v-model:page-size="cleanPage.pageSize"
        :total="cleanTotal" layout="total, prev, pager, next" @change="fetchCleanArticles"
      />
    </el-card>

    <!-- 已有检测结果 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>已有检测结果</span>
          <div style="display:flex;gap:8px">
            <el-button type="primary" @click="batchRedetect" :disabled="!selectedResultRows.length">
              批量重新检测 ({{ selectedResultRows.length }})
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
        <el-table-column prop="fakeScore" label="虚假分数" width="120">
          <template #default="{ row }">
            <el-progress :percentage="+(row.fakeScore * 100).toFixed(1)" :color="row.fakeScore > 0.5 ? '#F56C6C' : '#67C23A'" />
          </template>
        </el-table-column>
        <el-table-column prop="isFake" label="判定" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isFake ? 'danger' : 'success'">{{ row.isFake ? '虚假' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detectionMethod" label="方法" width="80" />
        <el-table-column prop="details" label="检测详情" show-overflow-tooltip min-width="250" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该检测结果？" @confirm="handleDeleteResult(row)">
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
import { getFakeResults, batchDetectFake, deleteFakeResult } from '@/api/fake'
import { ElMessage } from 'element-plus'

// ---- 待检测已清洗文章 ----
const cleanLoading = ref(false)
const cleanArticles = ref([])
const cleanTotal = ref(0)
const cleanPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedCleanIds = ref([])

async function fetchCleanArticles() {
  cleanLoading.value = true
  try {
    const res = await getCleanArticles({ pageNum: cleanPage.pageNum, pageSize: cleanPage.pageSize, excludeDetected: true })
    cleanArticles.value = res.data?.records || res.data || []
    cleanTotal.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { cleanLoading.value = false }
}

// ---- 已有检测结果 ----
const resultLoading = ref(false)
const resultData = ref([])
const resultTotal = ref(0)
const resultPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedResultRows = ref([])

async function fetchResults() {
  resultLoading.value = true
  try {
    const res = await getFakeResults({ pageNum: resultPage.pageNum, pageSize: resultPage.pageSize })
    resultData.value = res.data?.records || res.data || []
    resultTotal.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { resultLoading.value = false }
}

// ---- 检测操作 ----
async function detectOne(row) {
  try {
    await batchDetectFake([row.id])
    ElMessage.success(`「${row.title || row.id}」检测完成`)
    fetchCleanArticles()
    fetchResults()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchDetect() {
  try {
    await batchDetectFake(selectedCleanIds.value)
    ElMessage.success(`批量检测 ${selectedCleanIds.value.length} 篇文章完成`)
    selectedCleanIds.value = []
    fetchCleanArticles()
    fetchResults()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchRedetect() {
  const ids = selectedResultRows.value.map(r => r.cleanId)
  if (!ids.length) return
  try {
    await batchDetectFake(ids)
    ElMessage.success(`重新检测 ${ids.length} 篇文章完成`)
    selectedResultRows.value = []
    fetchResults()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchDeleteResults() {
  const rows = [...selectedResultRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteFakeResult(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 条检测结果完成`)
    selectedResultRows.value = []
    fetchCleanArticles()
    fetchResults()
  } catch (e) { ElMessage.error(e.message) }
}

async function handleDeleteResult(row) {
  try {
    await deleteFakeResult(row.id)
    ElMessage.success('检测结果已删除')
    fetchCleanArticles()
    fetchResults()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => {
  fetchCleanArticles()
  fetchResults()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
