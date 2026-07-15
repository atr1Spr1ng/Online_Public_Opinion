<template>
  <div>
    <!-- 待清洗原始文章 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>待清洗原始文章</span>
          <div style="display:flex;gap:8px">
            <el-button type="danger" @click="batchDeleteRaw" :disabled="!hasRawSelection">
              删除选中
            </el-button>
            <el-button type="primary" @click="batchClean" :disabled="!hasRawSelection">
              清洗选中
            </el-button>
          </div>
        </div>
      </template>
      <div class="selection-toolbar">
        <el-checkbox v-model="selectAllRaw">
          选择全部待清洗原始文章（共 {{ rawTotal }} 篇）
        </el-checkbox>
      </div>
      <el-table
        :data="rawArticles" v-loading="rawLoading" border stripe
        @selection-change="val => { selected = val.map(i => i.id); selectedRawIds = val.map(i => i.id) }"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250" />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="contentLength" label="正文字数" width="90" />
        <el-table-column prop="createTime" label="采集时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="success" link @click="cleanSingle(row)">清洗</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="rawPage.pageNum" v-model:page-size="rawPage.pageSize"
        :total="rawTotal" layout="total, prev, pager, next" @change="fetchRawArticles"
      />
    </el-card>

    <!-- 已清洗文章 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>已清洗文章</span>
          <el-button type="danger" @click="batchDeleteCleaned" :disabled="!hasCleanedSelection">删除选中</el-button>
        </div>
      </template>
      <div class="selection-toolbar">
        <el-checkbox v-model="selectAllCleaned">
          选择全部已清洗文章（共 {{ cleanTotal }} 篇）
        </el-checkbox>
      </div>
      <el-table :data="cleanedArticles" v-loading="cleanLoading" border stripe @selection-change="val => selectedCleanedRows = val">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250" />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'CLEANED' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" show-overflow-tooltip width="200" />
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-popconfirm title="确定删除该清洗记录？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="cleanPage.pageNum" v-model:page-size="cleanPage.pageSize"
        :total="cleanTotal" layout="total, prev, pager, next" @change="fetchCleanedArticles"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="清洗文章详情" width="700px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ detail.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="关键词" :span="2">{{ detail.keywords }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ detail.summary }}</el-descriptions-item>
      </el-descriptions>
      <div class="content-box">{{ detail.content }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getArticles, deleteArticle } from '@/api/crawler'
import { getCleanArticles, cleanArticle, batchClean as batchCleanApi, deleteCleanArticle } from '@/api/content'
import { fetchAllPaged } from '@/utils/pagedFetch'
import { ElMessage, ElMessageBox } from 'element-plus'

// ---- 待清洗原始文章 ----
const rawLoading = ref(false)
const rawArticles = ref([])
const rawTotal = ref(0)
const rawPage = reactive({ pageNum: 1, pageSize: 10 })
const selected = ref([])
const selectedRawIds = ref([])
const selectAllRaw = ref(false)
const hasRawSelection = computed(() => selectAllRaw.value || selectedRawIds.value.length > 0)

async function fetchRawArticles() {
  rawLoading.value = true
  try {
    const res = await getArticles({ pageNum: rawPage.pageNum, pageSize: rawPage.pageSize, excludeCleaned: true })
    rawArticles.value = res.data?.records || res.data || []
    rawTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { rawLoading.value = false }
}

// ---- 已清洗文章 ----
const cleanLoading = ref(false)
const cleanedArticles = ref([])
const cleanTotal = ref(0)
const cleanPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedCleanedRows = ref([])
const selectAllCleaned = ref(false)
const hasCleanedSelection = computed(() => selectAllCleaned.value || selectedCleanedRows.value.length > 0)
const detailVisible = ref(false)
const detail = ref({})

async function fetchCleanedArticles() {
  cleanLoading.value = true
  try {
    const res = await getCleanArticles({ pageNum: cleanPage.pageNum, pageSize: cleanPage.pageSize })
    cleanedArticles.value = res.data?.records || res.data || []
    cleanTotal.value = res.data?.total || res.total || 0
  } catch {}
  finally { cleanLoading.value = false }
}

// ---- 清洗操作 ----
async function batchDeleteRaw() {
  const ids = selectAllRaw.value
    ? (await fetchAllPaged(getArticles, { excludeCleaned: true })).map(i => i.id)
    : [...selectedRawIds.value]
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 篇原始文章？`, '删除确认', { type: 'warning' })
    for (const id of ids) {
      await deleteArticle(id)
    }
    ElMessage.success(`已删除 ${ids.length} 篇文章`)
    selectedRawIds.value = []
    selected.value = []
    selectAllRaw.value = false
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

async function cleanSingle(row) {
  try {
    await cleanArticle({ rawId: row.id })
    ElMessage.success(`「${row.title || row.id}」清洗完成`)
    fetchRawArticles()
    fetchCleanedArticles()
  } catch {}
}

async function batchClean() {
  const ids = selectAllRaw.value
    ? (await fetchAllPaged(getArticles, { excludeCleaned: true })).map(i => i.id)
    : [...selected.value]
  if (!ids.length) return
  try {
    const res = await batchCleanApi(ids)
    const taskId = res.data?.id || res.id
    ElMessage.success(taskId ? `后台清洗任务已提交：#${taskId}` : `后台清洗任务已提交，共 ${ids.length} 篇`)
    selected.value = []
    selectedRawIds.value = []
    selectAllRaw.value = false
    fetchRawArticles()
    fetchCleanedArticles()
  } catch {}
}

function showDetail(row) { detail.value = row; detailVisible.value = true }

async function handleDelete(row) {
  try {
    await deleteCleanArticle(row.id)
    ElMessage.success('清洗记录已删除')
    fetchRawArticles()
    fetchCleanedArticles()
  } catch {}
}

async function batchDeleteCleaned() {
  const ids = selectAllCleaned.value
    ? (await fetchAllPaged(getCleanArticles)).map(i => i.id)
    : selectedCleanedRows.value.map(i => i.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条清洗记录？`, '删除确认', { type: 'warning' })
    for (const id of ids) {
      await deleteCleanArticle(id)
    }
    ElMessage.success(`已删除 ${ids.length} 条清洗记录`)
    selectedCleanedRows.value = []
    selectAllCleaned.value = false
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) {
    if (e !== 'cancel') {}
  }
}

onMounted(() => {
  fetchRawArticles()
  fetchCleanedArticles()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.selection-toolbar {
  display: flex; align-items: center; gap: 12px;
  margin-bottom: 12px; color: #606266; font-size: 13px;
}
.content-box {
  margin-top: 12px; max-height: 300px; overflow-y: auto; white-space: pre-wrap;
  line-height: 1.8; background: #fafafa; padding: 12px; border-radius: 4px;
}
</style>
