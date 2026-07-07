<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>虚假文本检测</span>
          <div>
            <el-input-number v-model="cleanId" :min="1" placeholder="文章ID" style="width:160px" />
            <el-button type="primary" @click="handleDetect" :loading="detecting" style="margin-left:8px">检测</el-button>
            <el-button @click="handleBatch" :loading="batching" style="margin-left:8px">批量检测</el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="cleanId" label="文章ID" width="80" />
        <el-table-column prop="fakeScore" label="虚假分数" width="100">
          <template #default="{ row }">
            <el-progress :percentage="+(row.fakeScore * 100).toFixed(1)" :color="row.fakeScore > 0.5 ? '#F56C6C' : '#67C23A'" />
          </template>
        </el-table-column>
        <el-table-column prop="isFake" label="判定" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isFake ? 'danger' : 'success'">{{ row.isFake ? '虚假' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="detectionMethod" label="方法" width="80" />
        <el-table-column prop="details" label="检测详情" show-overflow-tooltip min-width="300" />
        <el-table-column prop="createTime" label="时间" width="170" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="showDetail(row)">特征</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="detailVisible" title="检测特征" width="500px">
      <el-table :data="featureList" border>
        <el-table-column prop="name" label="特征" width="140" />
        <el-table-column prop="score" label="得分" width="80" />
        <el-table-column prop="description" label="说明" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getFakeResults, detectFake, batchDetectFake } from '@/api/fake'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const detecting = ref(false)
const batching = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })
const cleanId = ref(1)
const detailVisible = ref(false)
const featureList = ref([])

function parseFeatures(row) {
  try { return JSON.parse(row.featuresJson) } catch { return [] }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getFakeResults({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

function showDetail(row) {
  featureList.value = parseFeatures(row).map(f => ({
    name: f.name, score: f.score?.toFixed(2), description: f.description
  }))
  detailVisible.value = true
}

async function handleDetect() {
  detecting.value = true
  try {
    const res = await detectFake({ cleanId: cleanId.value })
    ElMessage.success(`检测完成: fakeScore=${res.data?.fakeScore?.toFixed(2)}, isFake=${res.data?.isFake}`)
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
  finally { detecting.value = false }
}

async function handleBatch() {
  batching.value = true
  try {
    const ids = tableData.value.map(i => i.cleanId).filter((v, i, a) => a.indexOf(v) === i)
    await batchDetectFake(ids)
    ElMessage.success('批量检测完成')
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
  finally { batching.value = false }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
</style>
