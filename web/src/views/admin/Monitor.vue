<template>
  <div>
    <!-- 平台统计卡片（可点击下钻） -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="clickable-card" @click="openDialog('articles', '原始文章列表')">
          <div class="stat-item"><div class="stat-num">{{ stats.totalArticles }}</div><div class="stat-label">原始文章</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="clickable-card" @click="openDialog('cleaned', '已清洗文章列表')">
          <div class="stat-item"><div class="stat-num">{{ stats.totalCleaned }}</div><div class="stat-label">已清洗</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="clickable-card" @click="openDialog('events', '舆情事件列表')">
          <div class="stat-item"><div class="stat-num">{{ stats.totalEvents }}</div><div class="stat-label">舆情事件</div></div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="clickable-card" @click="openDialog('articles', '今日新增文章')">
          <div class="stat-item"><div class="stat-num">{{ stats.todayArticles }}</div><div class="stat-label">今日新增</div></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 今日数据 & 服务状态 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header><span>今日数据</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="今日新增文章">
              <span class="link-text" @click="openDialog('articles', '今日新增文章')">{{ stats.todayArticles }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="今日新增事件">
              <span class="link-text" @click="openDialog('events', '今日新增事件')">{{ stats.todayEvents }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="ES 文章索引">
              <span class="link-text" @click="openDialog('esArticles', 'ES article_clean 索引')">{{ stats.esArticleCount }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="ES 事件索引">
              <span class="link-text" @click="openDialog('esEvents', 'ES events 索引')">{{ stats.esEventCount }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span>微服务状态</span></template>
          <el-row :gutter="12">
            <el-col v-for="(info, name) in services" :key="name" :span="8">
              <el-card shadow="hover" class="service-card">
                <div class="service-name">{{ name }}</div>
                <el-tag :type="info.alive ? 'success' : 'danger'" size="small">
                  {{ info.alive ? '运行中' : '离线' }}
                </el-tag>
              </el-card>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- 线程池 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12" v-if="threadPool">
        <el-card>
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

    <!-- 数据明细弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.title" width="800px" @close="dialog.data=[];dialog.total=0">
      <el-table :data="dialog.data" v-loading="dialog.loading" border stripe max-height="500">
        <template v-if="dialog.type === 'events' || dialog.type === 'esEvents'">
          <el-table-column prop="_id" v-if="dialog.type==='esEvents'" label="文档ID" width="100" />
          <el-table-column prop="id" v-else label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="hotness" label="热度" width="80" />
          <el-table-column prop="articleCount" label="文章数" width="80" />
        </template>
        <template v-else>
          <el-table-column prop="_id" v-if="dialog.type.startsWith('es')" label="文档ID" width="100" />
          <el-table-column prop="id" v-else label="ID" width="60" />
          <el-table-column prop="title" label="标题" min-width="250" show-overflow-tooltip />
          <el-table-column prop="sourceName" label="来源" width="140" />
          <el-table-column label="时间" width="170">
            <template #default="{ row }">{{ row.createTime || row.published_at || '-' }}</template>
          </el-table-column>
        </template>
      </el-table>
      <template #footer>
        <el-pagination
          v-model:current-page="dialog.pageNum"
          v-model:page-size="dialog.pageSize"
          :total="dialog.total"
          layout="total, prev, pager, next"
          @current-change="fetchDialogData"
          small
        />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getAdminStats, getServicesHealth, listAdminArticles, listAdminCleanedArticles, listAdminEvents, listAdminEsArticles, listAdminEsEvents } from '@/api/admin'

const stats = reactive({
  totalUsers: 0, totalArticles: 0, totalCleaned: 0, totalEvents: 0,
  todayArticles: 0, todayEvents: 0, esArticleCount: 0, esEventCount: 0
})
const services = ref({})
const threadPool = ref(null)

const dialog = reactive({
  visible: false, title: '', type: '', loading: false,
  data: [], total: 0, pageNum: 1, pageSize: 10
})

const dialogFetchers = {
  articles: listAdminArticles,
  cleaned: listAdminCleanedArticles,
  events: listAdminEvents,
  esArticles: listAdminEsArticles,
  esEvents: listAdminEsEvents
}

async function openDialog(type, title) {
  dialog.type = type
  dialog.title = title
  dialog.pageNum = 1
  dialog.visible = true
  await fetchDialogData()
}

async function fetchDialogData() {
  const fetcher = dialogFetchers[dialog.type]
  if (!fetcher) return
  dialog.loading = true
  try {
    const res = await fetcher({ pageNum: dialog.pageNum, pageSize: dialog.pageSize })
    const d = res.data
    dialog.data = d.records || d.data || []
    dialog.total = d.total || 0
  } catch (_) {}
  finally { dialog.loading = false }
}

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
.stats-row .el-card { text-align: center; }
.stat-num { font-size: 28px; font-weight: bold; color: #409EFF; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.clickable-card { cursor: pointer; transition: box-shadow .2s; }
.clickable-card:hover { box-shadow: 0 2px 12px rgba(64,158,255,0.3); }
.clickable-card:hover .stat-num { color: #337ecc; }
.link-text { color: #409EFF; cursor: pointer; text-decoration: underline; }
.link-text:hover { color: #337ecc; }
.service-card { text-align: center; padding: 6px; margin-bottom: 10px; }
.service-name { font-size: 11px; color: #909399; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
