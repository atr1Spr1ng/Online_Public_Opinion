<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>清洗文章</span>
          <el-button type="primary" @click="batchClean" :disabled="!selected.length">批量清洗 ({{ selected.length }})</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe @selection-change="val => selected = val.map(i => i.id)">
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
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">详情</el-button>
            <el-button type="success" link @click="cleanSingle(row)">清洗</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="文章详情" width="700px">
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
import { getCleanArticles, cleanArticle, batchClean as batchCleanApi } from '@/api/content'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const selected = ref([])
const page = reactive({ pageNum: 1, pageSize: 10 })
const detailVisible = ref(false)
const detail = ref({})

async function fetchData() {
  loading.value = true
  try {
    const res = await getCleanArticles({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function showDetail(row) { detail.value = row; detailVisible.value = true }

async function cleanSingle(row) {
  try {
    await cleanArticle({ rawId: row.rawId || row.id })
    ElMessage.success('清洗完成')
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchClean() {
  try {
    await batchCleanApi(selected.value)
    ElMessage.success('批量清洗完成')
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
.content-box {
  margin-top: 12px; max-height: 300px; overflow-y: auto; white-space: pre-wrap;
  line-height: 1.8; background: #fafafa; padding: 12px; border-radius: 4px;
}
</style>
