<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>原始文章列表</span>
          <div style="display:flex;gap:8px">
            <el-button type="danger" @click="batchDelete" :disabled="!selectedRows.length">
              批量删除 ({{ selectedRows.length }})
            </el-button>
            <el-button type="primary" @click="openCrawlDialog">采集文章</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="val => selectedRows = val">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250" />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="extractStatus" label="提取状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.extractStatus === 'SUCCESS' ? 'success' : 'warning'">{{ row.extractStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contentLength" label="正文字数" width="90" />
        <el-table-column prop="publishedAt" label="发布时间" width="170" />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="showContent(row)">查看</el-button>
            <el-popconfirm title="确定删除该文章？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="contentVisible" title="文章内容" width="700px">
      <h3>{{ currentArticle?.title }}</h3>
      <el-descriptions :column="2" border style="margin:16px 0">
        <el-descriptions-item label="来源">{{ currentArticle?.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ currentArticle?.publishedAt }}</el-descriptions-item>
      </el-descriptions>
      <div class="article-content">{{ currentArticle?.content }}</div>
    </el-dialog>

    <el-dialog v-model="crawlVisible" title="采集文章" width="500px">
      <el-form ref="crawlFormRef" :model="crawlForm" :rules="crawlRules" label-width="80px">
        <el-form-item label="文章URL" prop="url">
          <el-input v-model="crawlForm.url" placeholder="输入要采集的新闻文章URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="crawlVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCrawl" :loading="crawling">开始采集</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getArticles, crawlSingleUrl, deleteArticle } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])
const page = reactive({ pageNum: 1, pageSize: 10 })
const contentVisible = ref(false)
const currentArticle = ref(null)
const crawlVisible = ref(false)
const crawling = ref(false)
const crawlFormRef = ref(null)
const crawlForm = reactive({ url: '' })
const crawlRules = { url: [{ required: true, message: '请输入文章URL' }] }

async function fetchData() {
  loading.value = true
  try {
    const res = await getArticles({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function showContent(row) {
  currentArticle.value = row
  contentVisible.value = true
}

function openCrawlDialog() {
  crawlForm.url = ''
  crawlVisible.value = true
}

async function handleCrawl() {
  const valid = await crawlFormRef.value.validate().catch(() => false)
  if (!valid) return
  crawling.value = true
  try {
    await crawlSingleUrl({ url: crawlForm.url })
    ElMessage.success('文章采集成功')
    crawlVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
  finally { crawling.value = false }
}

async function handleDelete(row) {
  try {
    await deleteArticle(row.id)
    ElMessage.success('文章已删除')
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchDelete() {
  const rows = [...selectedRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteArticle(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 篇文章完成`)
    selectedRows.value = []
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.article-content {
  max-height: 400px; overflow-y: auto; white-space: pre-wrap;
  line-height: 1.8; font-size: 14px; color: #333; background: #fafafa; padding: 16px; border-radius: 4px;
}
</style>
