<template>
  <div>
    <!-- 待清洗原始文章 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>待清洗原始文章</span>
          <el-button type="primary" @click="batchClean" :disabled="!selected.length">
            批量清洗 ({{ selected.length }})
          </el-button>
        </div>
      </template>
      <el-table
        :data="rawArticles" v-loading="rawLoading" border stripe
        @selection-change="val => selected = val.map(i => i.id)"
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
          <el-button type="danger" @click="batchDeleteCleaned" :disabled="!selectedCleanedRows.length">
            批量删除 ({{ selectedCleanedRows.length }})
          </el-button>
        </div>
      </template>
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
import { ref, reactive, onMounted } from 'vue'
import { getArticles } from '@/api/crawler'
import { getCleanArticles, cleanArticle, batchClean as batchCleanApi, deleteCleanArticle } from '@/api/content'
import { ElMessage } from 'element-plus'

// ---- 待清洗原始文章 ----
const rawLoading = ref(false)
const rawArticles = ref([])
const rawTotal = ref(0)
const rawPage = reactive({ pageNum: 1, pageSize: 10 })
const selected = ref([])

async function fetchRawArticles() {
  rawLoading.value = true
  try {
    const res = await getArticles({ pageNum: rawPage.pageNum, pageSize: rawPage.pageSize, excludeCleaned: true })
    rawArticles.value = res.data?.records || res.data || []
    rawTotal.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { rawLoading.value = false }
}

// ---- 已清洗文章 ----
const cleanLoading = ref(false)
const cleanedArticles = ref([])
const cleanTotal = ref(0)
const cleanPage = reactive({ pageNum: 1, pageSize: 10 })
const selectedCleanedRows = ref([])
const detailVisible = ref(false)
const detail = ref({})

async function fetchCleanedArticles() {
  cleanLoading.value = true
  try {
    const res = await getCleanArticles({ pageNum: cleanPage.pageNum, pageSize: cleanPage.pageSize })
    cleanedArticles.value = res.data?.records || res.data || []
    cleanTotal.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { cleanLoading.value = false }
}

// ---- 清洗操作 ----
async function cleanSingle(row) {
  try {
    await cleanArticle({ rawId: row.id })
    ElMessage.success(`「${row.title || row.id}」清洗完成`)
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchClean() {
  try {
    await batchCleanApi(selected.value)
    ElMessage.success(`批量清洗 ${selected.value.length} 篇文章完成`)
    selected.value = []
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) { ElMessage.error(e.message) }
}

function showDetail(row) { detail.value = row; detailVisible.value = true }

async function handleDelete(row) {
  try {
    await deleteCleanArticle(row.id)
    ElMessage.success('清洗记录已删除')
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchDeleteCleaned() {
  const rows = [...selectedCleanedRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteCleanArticle(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 条清洗记录完成`)
    selectedCleanedRows.value = []
    fetchRawArticles()
    fetchCleanedArticles()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => {
  fetchRawArticles()
  fetchCleanedArticles()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.content-box {
  margin-top: 12px; max-height: 300px; overflow-y: auto; white-space: pre-wrap;
  line-height: 1.8; background: #fafafa; padding: 12px; border-radius: 4px;
}
</style>
