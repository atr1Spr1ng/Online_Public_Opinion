<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>舆情事件管理</span>
          <el-button type="primary" @click="showClusterDialog" :loading="clustering">
            <el-icon><DataAnalysis /></el-icon>
            执行聚类分析
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="事件标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="keywords" label="关键词" width="200" show-overflow-tooltip />
        <el-table-column prop="articleCount" label="文章数" width="80" />
        <el-table-column prop="hotness" label="热度" width="80" />
        <el-table-column prop="lifecycle" label="生命周期" width="100">
          <template #default="{ row }">
            <el-tag :type="lifecycleType(row.lifecycle)">{{ row.lifecycle }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column label="操作" width="240">
          <template #default="{ row }">
            <el-button type="primary" link @click="doTraceSource(row)">溯源</el-button>
            <el-button type="success" link @click="doAnalyzePath(row)">传播分析</el-button>
            <el-button type="warning" link @click="doGenerateReport(row)">生成报告</el-button>
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
        @current-change="fetchData"
      />
    </el-card>

    <el-dialog v-model="clusterVisible" title="事件聚类" width="420px">
      <el-form label-width="100px">
        <el-form-item label="相似度阈值">
          <el-slider v-model="threshold" :min="0.05" :max="0.95" :step="0.05" show-input />
        </el-form-item>
        <el-form-item label="说明">
          <span style="color:#909399;font-size:13px">阈值越低，事件归类越宽松；阈值越高，归类越严格。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="clusterVisible = false">取消</el-button>
        <el-button type="primary" @click="doCluster" :loading="clustering">开始聚类</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="sourceVisible" title="事件溯源结果" width="600px">
      <el-descriptions v-if="sourceResult" :column="2" border>
        <el-descriptions-item label="事件">{{ sourceResult.eventTitle }}</el-descriptions-item>
        <el-descriptions-item label="源头来源">{{ sourceResult.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="源头文章ID">{{ sourceResult.sourceArticleId }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ sourceResult.publishedAt || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ sourceResult.summary }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <el-dialog v-model="pathVisible" title="传播路径分析" width="700px">
      <el-descriptions v-if="pathResult" :column="2" border style="margin-bottom:16px">
        <el-descriptions-item label="传播深度">{{ pathResult.spreadDepth }}</el-descriptions-item>
        <el-descriptions-item label="节点总数">{{ pathResult.totalNodes }}</el-descriptions-item>
        <el-descriptions-item label="持续时间(h)">{{ pathResult.durationHours }}</el-descriptions-item>
        <el-descriptions-item label="传播速度">{{ pathResult.spreadSpeed }} 篇/小时</el-descriptions-item>
      </el-descriptions>
      <el-timeline v-if="pathResult.nodes?.length">
        <el-timeline-item
          v-for="node in pathResult.nodes"
          :key="node.id"
          :timestamp="node.publishedAt"
          :color="node.isSource ? '#409EFF' : '#67C23A'"
        >
          {{ node.articleTitle || `文章#${node.cleanId}` }}
          <el-tag size="small" :type="node.isSource ? 'primary' : 'success'">{{ node.isSource ? '源头' : '传播节点' }}</el-tag>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listEvents, clusterEvents } from '@/api/event'
import { traceSource, analyzePropagation } from '@/api/propagation'
import { generateReport } from '@/api/report'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const clustering = ref(false)
const clusterVisible = ref(false)
const threshold = ref(0.25)

const sourceVisible = ref(false)
const sourceResult = ref(null)
const pathVisible = ref(false)
const pathResult = ref(null)

function lifecycleType(lc) {
  const map = { '潜伏期': 'info', '成长期': 'warning', '高潮期': 'danger', '衰退期': 'info' }
  return map[lc] || 'info'
}

async function fetchData() {
  loading.value = true
  try {
    const res = await listEvents({ pageNum: pageNum.value, pageSize: pageSize.value })
    if (res.code === 200) {
      const data = res.data
      tableData.value = data.records || data || []
      total.value = data.total || 0
    }
  } catch (e) {
    ElMessage.error('加载事件列表失败')
  } finally {
    loading.value = false
  }
}

function showClusterDialog() {
  threshold.value = 0.25
  clusterVisible.value = true
}

async function doCluster() {
  clustering.value = true
  try {
    const res = await clusterEvents({ threshold: threshold.value })
    if (res.code === 200 && res.data?.success) {
      const data = res.data
      ElMessage.success(
        `聚类完成：${data.events?.length || 0} 个事件，` +
        `覆盖 ${data.clusteredArticles} 篇文章，` +
        `${data.unclusteredArticles} 篇未归类`
      )
      clusterVisible.value = false
      await fetchData()
    } else {
      ElMessage.error(res.data?.message || '聚类失败')
    }
  } catch (e) {
    ElMessage.error('聚类请求失败: ' + e.message)
  } finally {
    clustering.value = false
  }
}

async function doTraceSource(row) {
  try {
    const res = await traceSource(row.id)
    sourceResult.value = res.data
    sourceVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function doAnalyzePath(row) {
  try {
    const res = await analyzePropagation({ eventId: row.id })
    pathResult.value = res.data
    pathVisible.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function doGenerateReport(row) {
  try {
    const res = await generateReport({ eventId: row.id })
    ElMessage.success(`报告已生成 ID=${res.data?.id || '?'}`)
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>
