<template>
  <div>
    <!-- 搜索面板 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>按话题采集</span>
          <el-button type="primary" @click="searchAndCollect" :loading="searching" :disabled="selectedSourceIds.length === 0">
            搜索并采集
          </el-button>
        </div>
      </template>
      <el-form :model="form" label-width="90px">
        <el-form-item label="搜索关键词">
          <el-input v-model="form.keyword" placeholder="输入要搜索的话题关键词" style="width: 400px" />
        </el-form-item>
        <el-form-item label="选择新闻源">
          <el-checkbox-group v-model="selectedSourceIds">
            <el-checkbox v-for="s in sources" :key="s.id" :value="s.id" border style="margin-right: 12px">
              {{ s.sourceName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="每源采集上限">
          <el-input-number v-model="form.limit" :min="1" :max="50" />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 结果面板 -->
    <el-card v-if="results.length > 0">
      <template #header>
        <span>采集结果：「{{ lastKeyword }}」</span>
      </template>
      <el-table :data="results" border stripe style="margin-bottom: 16px">
        <el-table-column prop="sourceName" label="新闻源" width="120" />
        <el-table-column prop="found" label="搜索到" width="80" />
        <el-table-column prop="success" label="成功" width="80" />
        <el-table-column prop="failed" label="失败" width="80" />
      </el-table>

      <el-divider v-if="savedArticles.length > 0" />
      <h4 v-if="savedArticles.length > 0" style="margin-bottom: 12px">
        本次采集到的文章（{{ savedArticles.length }} 篇）
      </h4>
      <el-table v-if="savedArticles.length > 0" :data="savedArticles" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250" />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="contentLength" label="正文字数" width="90" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSources, searchByTopic } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const sources = ref([])
const selectedSourceIds = ref([])
const searching = ref(false)
const results = ref([])
const savedArticles = ref([])
const lastKeyword = ref('')
const form = reactive({ keyword: '', limit: 10 })

async function fetchSources() {
  try {
    const res = await getSources({ pageNum: 1, pageSize: 100 })
    sources.value = res.data?.records || res.data || []
  } catch (e) { ElMessage.error(e.message) }
}

async function searchAndCollect() {
  if (!form.keyword.trim()) {
    ElMessage.warning('请输入搜索关键词')
    return
  }
  if (selectedSourceIds.value.length === 0) {
    ElMessage.warning('请至少选择一个新闻源')
    return
  }
  searching.value = true
  results.value = []
  savedArticles.value = []
  try {
    const res = await searchByTopic({
      keyword: form.keyword.trim(),
      sourceIds: selectedSourceIds.value,
      limit: form.limit
    })
    lastKeyword.value = form.keyword.trim()
    const data = res.data
    if (data.sourceResults) {
      results.value = data.sourceResults
    }
    if (data.savedArticles) {
      savedArticles.value = data.savedArticles
    }
    const total = data.totalSuccess || 0
    if (total > 0) {
      ElMessage.success(`成功采集 ${total} 篇文章`)
    } else {
      ElMessage.info('未找到相关文章')
    }
  } catch (e) { ElMessage.error(e.message) }
  finally { searching.value = false }
}

onMounted(fetchSources)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
