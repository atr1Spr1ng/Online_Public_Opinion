<template>
  <div>
    <el-card>
      <template #header><span>原始文章列表</span></template>
      <el-table :data="tableData" v-loading="loading" border stripe>
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
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="showContent(row)">查看</el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getArticles } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })
const contentVisible = ref(false)
const currentArticle = ref(null)

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

onMounted(fetchData)
</script>

<style scoped>
.article-content {
  max-height: 400px; overflow-y: auto; white-space: pre-wrap;
  line-height: 1.8; font-size: 14px; color: #333; background: #fafafa; padding: 16px; border-radius: 4px;
}
</style>
