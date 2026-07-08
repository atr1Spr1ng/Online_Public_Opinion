<template>
  <div>
    <el-card>
      <template #header><span>传播路径分析</span></template>
      <div class="search-bar">
        <el-input-number v-model="eventId" :min="1" placeholder="事件ID" style="width:200px" />
        <el-button type="primary" @click="handleAnalyze" :loading="analyzing" style="margin-left:12px">分析传播路径</el-button>
      </div>
      <el-divider />
      <template v-if="result">
        <el-row :gutter="20">
          <el-col :span="6"><el-statistic title="传播深度" :value="result.spreadDepth || 0" /></el-col>
          <el-col :span="6"><el-statistic title="节点总数" :value="result.totalNodes || 0" /></el-col>
          <el-col :span="6"><el-statistic title="持续时间(h)" :value="result.durationHours || 0" /></el-col>
          <el-col :span="6"><el-statistic title="传播速度" :value="result.spreadSpeed || 0" suffix="篇/h" /></el-col>
        </el-row>
        <el-divider>传播节点</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="node in (result.nodes || [])"
            :key="node.id"
            :timestamp="node.publishedAt"
            :color="node.isSource ? '#409EFF' : '#67C23A'"
            placement="top"
          >
            <el-card>
              <div class="node-header">
                <span class="node-title">{{ node.articleTitle || `文章#${node.cleanId}` }}</span>
                <el-tag size="small" :type="node.isSource ? 'primary' : 'success'">
                  {{ node.isSource ? '传播源头' : `第${node.depth}层传播` }}
                </el-tag>
              </div>
              <div class="node-source">来源: {{ node.sourceName || '未知' }}</div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </template>
      <el-empty v-if="!result && !analyzing" description="输入事件ID进行分析" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { analyzePropagation } from '@/api/propagation'
import { ElMessage } from 'element-plus'

const eventId = ref(1)
const analyzing = ref(false)
const result = ref(null)

async function handleAnalyze() {
  analyzing.value = true
  try {
    const res = await analyzePropagation({ eventId: eventId.value })
    result.value = res.data
    ElMessage.success('分析完成')
  } catch (e) { ElMessage.error(e.message) }
  finally { analyzing.value = false }
}
</script>

<style scoped>
.search-bar { display: flex; align-items: center; margin-bottom: 16px; }
.node-header { display: flex; justify-content: space-between; align-items: center; }
.node-title { font-weight: bold; }
.node-source { color: #909399; font-size: 13px; margin-top: 4px; }
</style>
