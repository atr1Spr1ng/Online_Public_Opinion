<template>
  <div>
    <!-- 新闻源列表 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <div class="card-header">
          <span>新闻源列表</span>
          <div style="display:flex;align-items:center;gap:8px">
            <span style="font-size:13px;color:#606266">每源采集数：</span>
            <el-input-number v-model="collectLimit" :min="1" :max="500" size="small" style="width:120px" />
            <el-button type="primary" @click="collectSelected" :disabled="selectedIds.length === 0">
              采集选中 ({{ selectedIds.length }})
            </el-button>
          </div>
        </div>
      </template>
      <el-table
        :data="sources"
        v-loading="sourcesLoading"
        @selection-change="handleSelectionChange"
        border stripe
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="sourceName" label="名称" min-width="120" />
        <el-table-column prop="sourceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'official' ? 'success' : 'primary'">
              {{ row.sourceType === 'official' ? '官方媒体' : '门户网站' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceUrl" label="URL" show-overflow-tooltip />
        <el-table-column label="操作" width="60">
          <template #default="{ row }">
            <el-button type="primary" link @click="collectOne(row)">采集</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 采集任务历史 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>最近采集任务</span>
          <div style="display:flex;gap:8px">
            <el-button type="danger" @click="batchDeleteTasks" :disabled="!selectedTaskRows.length">
              批量删除 ({{ selectedTaskRows.length }})
            </el-button>
            <el-button @click="fetchTasks">刷新</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tasks" v-loading="tasksLoading" border stripe @selection-change="val => selectedTaskRows = val">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="sourceName" label="新闻源" min-width="120" />
        <el-table-column prop="totalDiscovered" label="发现链接" width="80" />
        <el-table-column prop="totalSuccess" label="成功" width="70" />
        <el-table-column prop="totalDuplicate" label="重复" width="70" />
        <el-table-column prop="totalFailed" label="失败" width="70" />
        <el-table-column prop="status" label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该任务？" @confirm="handleDeleteTask(row)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="taskPage.pageNum" v-model:page-size="taskPage.pageSize"
        :total="taskTotal" layout="total, prev, pager, next" @change="fetchTasks"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { getSources, getTasks, crawlBySource, deleteTask } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const sourcesLoading = ref(false)
const sources = ref([])
const selectedIds = ref([])

const collectLimit = ref(20)
const tasksLoading = ref(false)
const tasks = ref([])
const selectedTaskRows = ref([])
const taskTotal = ref(0)
const taskPage = reactive({ pageNum: 1, pageSize: 10 })

let pollTimer = null

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getTasks({ pageNum: taskPage.pageNum, pageSize: taskPage.pageSize })
      tasks.value = res.data?.records || res.data || []
      taskTotal.value = res.data?.total || res.total || 0
      const hasRunning = tasks.value.some(t => t.status === 'RUNNING')
      if (!hasRunning) stopPolling()
    } catch (_) { /* polling silently fails */ }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function handleSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
}

async function fetchSources() {
  sourcesLoading.value = true
  try {
    const res = await getSources({ pageNum: 1, pageSize: 100 })
    sources.value = res.data?.records || res.data || []
  } catch (e) { ElMessage.error(e.message) }
  finally { sourcesLoading.value = false }
}

async function fetchTasks() {
  tasksLoading.value = true
  try {
    const res = await getTasks({ pageNum: taskPage.pageNum, pageSize: taskPage.pageSize })
    tasks.value = res.data?.records || res.data || []
    taskTotal.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { tasksLoading.value = false }
}

async function collectOne(row) {
  try {
    await crawlBySource(row.id, { limit: collectLimit.value })
    ElMessage.success(`已触发「${row.sourceName}」采集任务`)
    startPolling()
  } catch (e) { ElMessage.error(e.message) }
}

async function collectSelected() {
  if (selectedIds.value.length === 0) return
  try {
    for (const id of selectedIds.value) {
      await crawlBySource(id, { limit: 20 })
    }
    ElMessage.success(`已触发 ${selectedIds.value.length} 个新闻源采集任务，后台执行中`)
    startPolling()
  } catch (e) { ElMessage.error(e.message) }
}

function statusType(status) {
  const map = { SUCCESS: 'success', SUCCESS_WITH_DUPLICATE: 'warning', DUPLICATE: 'info', FAILED: 'danger', PARTIAL_FAILED: 'warning', RUNNING: 'warning' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { SUCCESS: '全部成功', SUCCESS_WITH_DUPLICATE: '部分重复', DUPLICATE: '全部重复', FAILED: '失败', PARTIAL_FAILED: '部分失败', RUNNING: '采集进行中…' }
  return map[status] || status
}

async function handleDeleteTask(row) {
  try {
    await deleteTask(row.id)
    ElMessage.success('任务已删除')
    fetchTasks()
  } catch (e) { ElMessage.error(e.message) }
}

async function batchDeleteTasks() {
  const rows = [...selectedTaskRows.value]
  if (!rows.length) return
  try {
    for (const row of rows) {
      await deleteTask(row.id)
    }
    ElMessage.success(`批量删除 ${rows.length} 个任务完成`)
    selectedTaskRows.value = []
    fetchTasks()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(() => {
  fetchSources()
  fetchTasks()
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
