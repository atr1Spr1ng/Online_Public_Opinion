<template>
  <div>
    <el-card>
      <template #header><span>文章搜索</span></template>
      <div style="margin-bottom:16px;display:flex;gap:12px;align-items:center;flex-wrap:wrap">
        <el-input v-model="keyword" placeholder="输入关键词搜索" style="width:260px" clearable @keyup.enter="doSearch" />
        <el-button type="primary" @click="doSearch" :loading="loading">
          <el-icon><Search /></el-icon> 搜索
        </el-button>
        <el-button @click="doMyKeywords" :loading="loading">我的关键词</el-button>
        <el-input v-model="sourceFilter" placeholder="按来源过滤" style="width:180px" clearable />
        <el-button type="success" @click="doFilterSearch" :loading="loading">过滤搜索</el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe max-height="500">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="sourceName" label="来源" width="130" />
        <el-table-column prop="publishedAt" label="发布时间" width="170" />
        <el-table-column prop="keywords" label="关键词" width="180" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-if="total > 0"
        style="margin-top:16px;justify-content:flex-end"
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        v-model:current-page="pageNum"
        @current-change="doSearch"
      />
      <el-empty v-if="!loading && tableData.length === 0" description="输入关键词搜索文章" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchArticles, searchByMyKeywords, searchByFilter } from '@/api/search'
import { ElMessage } from 'element-plus'

const keyword = ref('')
const sourceFilter = ref('')
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

async function doSearch() {
  loading.value = true
  try {
    const res = await searchArticles({ keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function doMyKeywords() {
  loading.value = true
  try {
    const res = await searchByMyKeywords({ pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function doFilterSearch() {
  loading.value = true
  try {
    const res = await searchByFilter({
      keyword: keyword.value,
      sourceName: sourceFilter.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}
</script>
