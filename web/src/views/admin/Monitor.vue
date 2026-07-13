<template>
  <div>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card><div class="stat-item"><div class="stat-num">{{ stats.totalUsers }}</div><div class="stat-label">总用户数</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-item"><div class="stat-num">{{ stats.totalArticles }}</div><div class="stat-label">原始文章</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-item"><div class="stat-num">{{ stats.totalEvents }}</div><div class="stat-label">舆情事件</div></div></el-card>
      </el-col>
      <el-col :span="6">
        <el-card><div class="stat-item"><div class="stat-num">{{ stats.todayArticles }}</div><div class="stat-label">今日新增</div></div></el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 20px">
      <template #header><span>微服务状态</span></template>
      <el-row :gutter="16">
        <el-col v-for="(info, name) in services" :key="name" :span="4">
          <el-card shadow="hover" class="service-card">
            <div class="service-name">{{ name }}</div>
            <el-tag :type="info.alive ? 'success' : 'danger'" size="large">
              {{ info.alive ? '运行中' : '离线' }}
            </el-tag>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header><span>ES 索引详情</span></template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="article_clean 文档数">
              {{ stats.esArticleCount }}
            </el-descriptions-item>
            <el-descriptions-item label="events 文档数">
              {{ stats.esEventCount }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card v-if="threadPool">
          <template #header><span>爬虫线程池</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="核心线程数">{{ threadPool.corePoolSize }}</el-descriptions-item>
            <el-descriptions-item label="最大线程数">{{ threadPool.maximumPoolSize }}</el-descriptions-item>
            <el-descriptions-item label="活跃线程">{{ threadPool.activeCount }}</el-descriptions-item>
            <el-descriptions-item label="当前池大小">{{ threadPool.poolSize }}</el-descriptions-item>
            <el-descriptions-item label="队列积压">{{ threadPool.queueSize }}</el-descriptions-item>
            <el-descriptions-item label="已完成任务">{{ threadPool.completedTaskCount }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAdminStats, getServicesHealth } from '@/api/admin'

const stats = reactive({
  totalUsers: 0, totalArticles: 0, totalCleaned: 0, totalEvents: 0,
  todayArticles: 0, todayEvents: 0, esArticleCount: 0, esEventCount: 0
})
const services = ref({})
const threadPool = ref(null)

onMounted(async () => {
  try {
    const res = await getAdminStats()
    Object.assign(stats, res.data || {})
  } catch (_) {}

  try {
    const res = await getServicesHealth()
    services.value = res.data?.services || {}
    threadPool.value = res.data?.crawlerThreadPool || null
  } catch (_) {}
})
</script>

<style scoped>
.stats-row { text-align: center; }
.stat-num { font-size: 28px; font-weight: bold; color: #409EFF; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.service-card { text-align: center; padding: 8px; }
.service-name { font-size: 12px; color: #909399; margin-bottom: 8px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
