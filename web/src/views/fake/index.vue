<template>
  <div>
    <!-- 待检测已清洗文章 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>待检测已清洗文章</span>
          <div style="display:flex;gap:8px;align-items:center">
            <el-select v-model="detectMode" style="width:150px">
              <el-option label="自动模式" value="auto" />
              <el-option label="快速模式" value="fast" />
              <el-option label="精准模式" value="accurate" />
            </el-select>
            <el-button type="primary" @click="batchDetect" :disabled="!hasCleanSelection">检测选中</el-button>
          </div>
        </div>
      </template>
      <div class="selection-toolbar">
        <el-checkbox v-model="selectAllClean">
          选择全部待检测文章（共 {{ cleanTotal }} 篇）
        </el-checkbox>
      </div>
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
            <el-button type="primary" @click="batchRedetect" :disabled="!hasResultSelection">
              重新检测选中
            </el-button>
            <el-button type="danger" @click="batchDeleteResults" :disabled="!hasResultSelection">
              删除选中
            </el-button>
          </div>
        </div>
      </template>
      <div class="selection-toolbar">
        <el-checkbox v-model="selectAllResults">
          选择全部检测结果（共 {{ resultTotal }} 条）
        </el-checkbox>
      </div>
      <el-table
        :data="resultData" v-loading="resultLoading" border stripe
        @selection-change="val => selectedResultRows = val"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="cleanId" label="文章ID" width="80" />
        <el-table-column prop="title" label="文章标题" show-overflow-tooltip min-width="200" />
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
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-button v-if="row.originalUrl" type="primary" link @click="openUrl(row.originalUrl)">查看原文</el-button>
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

    <!-- 检测详情弹窗 -->
    <el-dialog v-model="detailVisible" title="虚假检测详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="文章ID">{{ detailRow?.cleanId }}</el-descriptions-item>
        <el-descriptions-item label="虚假分数">{{ (detailRow?.fakeScore * 100).toFixed(1) }}%</el-descriptions-item>
        <el-descriptions-item label="判定">
          <el-tag :type="detailRow?.isFake ? 'danger' : 'success'">{{ detailRow?.isFake ? '虚假' : '正常' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="检测方法">{{ detailRow?.detectionMethod }}</el-descriptions-item>
        <el-descriptions-item label="检测时间">{{ detailRow?.createTime }}</el-descriptions-item>
        <el-descriptions-item label="详情">{{ detailRow?.details || '无' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getCleanArticles } from '@/api/content'
import { getFakeResults, detectFake, batchDetectFake, deleteFakeResult } from '@/api/fake'
import { fetchAllPaged } from '@/utils/pagedFetch'
import { ElMessage, ElMessageBox } from 'element-plus'

// ---- 待检测已清洗文章 ----
const cleanLoading = ref(false)
const cleanArticles = ref([])
const cleanTotal = ref(0)
const cleanPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedCleanIds = ref([])
const selectAllClean = ref(false)
const detectMode = ref('auto')
const hasCleanSelection = computed(() => selectAllClean.value || selectedCleanIds.value.length > 0)

async function fetchCleanArticles() {
  cleanLoading.value = true
  try {
    const res = await getCleanArticles({ pageNum: cleanPage.pageNum, pageSize: cleanPage.pageSize, excludeDetected: true })
    cleanArticles.value = res.data?.records || res.data || []
    cleanTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { cleanLoading.value = false }
}

// ---- 已有检测结果 ----
const resultLoading = ref(false)
const resultData = ref([])
const resultTotal = ref(0)
const resultPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedResultRows = ref([])
const selectAllResults = ref(false)
const hasResultSelection = computed(() => selectAllResults.value || selectedResultRows.value.length > 0)

// ---- 详情弹窗 ----
const detailVisible = ref(false)
const detailRow = ref(null)
function showDetail(row) {
  detailRow.value = row
  detailVisible.value = true
}
function openUrl(url) {
  window.open(url, '_blank')
}

async function fetchResults() {
  resultLoading.value = true
  try {
    const res = await getFakeResults({ pageNum: resultPage.pageNum, pageSize: resultPage.pageSize })
    resultData.value = res.data?.records || res.data || []
    resultTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { resultLoading.value = false }
}

// ---- 检测操作 ----
async function detectOne(row) {
  try {
    await detectFake({ cleanId: row.id, mode: detectMode.value })
    ElMessage.success(`「${row.title || row.id}」检测完成`)
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

async function batchDetect() {
  const ids = selectAllClean.value
    ? (await fetchAllPaged(getCleanArticles, { excludeDetected: true })).map(i => i.id)
    : [...selectedCleanIds.value]
  if (!ids.length) return
  try {
    const res = await batchDetectFake(ids, detectMode.value)
    const taskId = res.data?.id || res.id
    ElMessage.success(taskId ? `后台虚假检测任务已提交：#${taskId}（${modeLabel(detectMode.value, ids.length)}）` : `后台虚假检测任务已提交，共 ${ids.length} 篇`)
    selectedCleanIds.value = []
    selectAllClean.value = false
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

async function batchRedetect() {
  const ids = selectAllResults.value
    ? (await fetchAllPaged(getFakeResults)).map(r => r.cleanId).filter(Boolean)
    : selectedResultRows.value.map(r => r.cleanId)
  if (!ids.length) return
  try {
    const res = await batchDetectFake(ids, detectMode.value)
    const taskId = res.data?.id || res.id
    ElMessage.success(taskId ? `后台重新检测任务已提交：#${taskId}（${modeLabel(detectMode.value, ids.length)}）` : `后台重新检测任务已提交，共 ${ids.length} 篇`)
    selectedResultRows.value = []
    selectAllResults.value = false
    fetchResults()
  } catch {}
}

async function batchDeleteResults() {
  const ids = selectAllResults.value
    ? (await fetchAllPaged(getFakeResults)).map(r => r.id)
    : selectedResultRows.value.map(r => r.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条检测结果？`, '删除确认', { type: 'warning' })
    for (const id of ids) {
      await deleteFakeResult(id)
    }
    ElMessage.success(`已删除 ${ids.length} 条检测结果`)
    selectedResultRows.value = []
    selectAllResults.value = false
    fetchCleanArticles()
    fetchResults()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

async function handleDeleteResult(row) {
  try {
    await deleteFakeResult(row.id)
    ElMessage.success('检测结果已删除')
    fetchCleanArticles()
    fetchResults()
  } catch {}
}

function modeLabel(mode, count) {
  if (mode === 'fast') return '快速模式'
  if (mode === 'accurate') return '精准模式'
  return count <= 50 ? '精准模式' : '快速模式'
}

onMounted(() => {
  fetchCleanArticles()
  fetchResults()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.selection-toolbar {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 12px; color: #606266; font-size: 13px;
}
</style>
