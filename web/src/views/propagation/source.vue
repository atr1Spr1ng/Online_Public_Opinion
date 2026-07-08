<template>
  <div>
    <el-card>
      <template #header><span>事件溯源</span></template>
      <div class="search-bar">
        <el-input-number v-model="eventId" :min="1" placeholder="输入事件ID" style="width:200px" />
        <el-button type="primary" @click="handleTrace" :loading="tracing" style="margin-left:12px">开始溯源</el-button>
      </div>
      <el-divider />
      <el-descriptions v-if="result" :column="2" border>
        <el-descriptions-item label="事件ID">{{ result.eventId }}</el-descriptions-item>
        <el-descriptions-item label="事件标题">{{ result.eventTitle }}</el-descriptions-item>
        <el-descriptions-item label="源头文章ID">{{ result.sourceArticleId }}</el-descriptions-item>
        <el-descriptions-item label="源头文章标题">{{ result.sourceArticleTitle }}</el-descriptions-item>
        <el-descriptions-item label="源头来源">{{ result.sourceName }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ result.publishedAt || '未知' }}</el-descriptions-item>
        <el-descriptions-item label="摘要" :span="2">{{ result.summary }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-if="!result && !tracing" description="输入事件ID进行溯源分析" />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { traceSource } from '@/api/propagation'
import { ElMessage } from 'element-plus'

const eventId = ref(1)
const tracing = ref(false)
const result = ref(null)

async function handleTrace() {
  tracing.value = true
  try {
    const res = await traceSource(eventId.value)
    result.value = res.data
    ElMessage.success('溯源完成')
  } catch (e) { ElMessage.error(e.message) }
  finally { tracing.value = false }
}
</script>

<style scoped>
.search-bar { display: flex; align-items: center; }
</style>
