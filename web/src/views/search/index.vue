<template>
  <div>
    <el-card>
      <template #header><span>事件检索</span></template>
      <div style="margin-bottom:16px;display:flex;gap:12px;align-items:center">
        <el-input v-model="keyword" placeholder="输入关键词搜索历史事件" style="width:360px" clearable @keyup.enter="doSearch" />
        <el-button type="primary" @click="doSearch" :loading="loading">
          <el-icon><Search /></el-icon> 搜索
        </el-button>
      </div>
      <el-table :data="tableData" v-loading="loading" border stripe max-height="500">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="事件标题" min-width="240" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="100">
          <template #default="{ row }">
            <el-tag :type="categoryType(row.category)" size="small">{{ row.category || '其他' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="80" />
        <el-table-column prop="hotness" label="热度" width="80" />
        <el-table-column prop="lifecycle" label="生命周期" width="90">
          <template #default="{ row }">
            <el-tag :type="lifecycleType(row.lifecycle)" size="small">{{ row.lifecycle }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/event/${row.id}`)">详情</el-button>
          </template>
        </el-table-column>
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
      <el-empty v-if="!loading && tableData.length === 0" description="输入关键词搜索历史事件" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchEvents } from '@/api/event'

const keyword = ref('')
const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

function lifecycleType(lc) {
  const map = { '潜伏期': 'info', '成长期': 'warning', '高潮期': 'danger', '衰退期': 'info' }
  return map[lc] || 'info'
}

function categoryType(cat) {
  const map = {
    '社会民生': 'primary', '科技经济': 'success', '教育文化': 'info',
    '医疗卫生': 'danger', '政治法律': 'warning', '生态环境': '',
    '娱乐体育': '', '国际时政': 'danger', '其他': 'info'
  }
  return map[cat] || 'info'
}

async function doSearch() {
  loading.value = true
  try {
    const res = await searchEvents({ keyword: keyword.value, pageNum: pageNum.value, pageSize: pageSize.value })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch {}
  finally { loading.value = false }
}
</script>
