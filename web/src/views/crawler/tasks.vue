<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采集任务</span>
          <div>
            <el-button type="primary" @click="crawlAll">一键采集所有新闻源</el-button>
            <el-button @click="fetchData">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="sourceName" label="新闻源" />
        <el-table-column prop="totalDiscovered" label="发现链接" width="80" />
        <el-table-column prop="totalSuccess" label="成功" width="70" />
        <el-table-column prop="totalDuplicate" label="重复" width="70" />
        <el-table-column prop="totalFailed" label="失败" width="70" />
        <el-table-column prop="status" label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getTasks, crawlAllEnabled } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })

function statusType(status) {
  const map = { SUCCESS: 'success', SUCCESS_WITH_DUPLICATE: 'warning', DUPLICATE: 'info', FAILED: 'danger', PARTIAL_FAILED: 'warning' }
  return map[status] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getTasks({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

async function crawlAll() {
  try {
    const res = await crawlAllEnabled({ limit: 20 })
    ElMessage.success(`已触发 ${res.totalSources || 0} 个新闻源采集`)
    setTimeout(fetchData, 5000)
    setTimeout(fetchData, 15000)
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
