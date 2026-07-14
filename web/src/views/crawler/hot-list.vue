<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>热搜榜单</span>
          <el-button type="primary" :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="微博热搜" name="weibo" />
        <el-tab-pane label="百度热搜" name="baidu" />
      </el-tabs>

      <el-table :data="items" v-loading="loading" border stripe>
        <el-table-column prop="rank" label="排名" width="60" align="center" />
        <el-table-column prop="title" label="标题" min-width="280" show-overflow-tooltip />
        <el-table-column prop="hotScore" label="热度" width="100" align="right">
          <template #default="{ row }">{{ formatHotScore(row.hotScore) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="openCollect(row)">采集</el-button>
            <el-button v-if="row.url" link size="small"
              @click="openUrl(row.url)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="fetchedAt" style="margin-top:12px;color:#909399;font-size:12px">
        数据更新时间：{{ fetchedAt }}
      </div>
    </el-card>

    <!-- 热搜采集对话框 -->
    <el-dialog v-model="collectVisible" title="热搜话题采集" width="520px">
      <el-form :model="collectForm" label-width="100px">
        <el-form-item label="热词标题">
          <el-input :model-value="collectForm.title" disabled />
        </el-form-item>
        <el-form-item label="选择新闻源">
          <el-checkbox-group v-model="collectForm.sourceIds">
            <el-checkbox v-for="s in sources" :key="s.id" :value="s.id" border style="margin-right: 12px; margin-bottom: 6px">
              {{ s.sourceName }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="sources.length === 0" style="color:#909399;font-size:13px">加载新闻源中…</div>
        </el-form-item>
        <el-form-item label="采集上限">
          <el-input-number v-model="collectForm.limit" :min="1" :max="50" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="collectVisible = false">取消</el-button>
        <el-button type="primary" @click="doCollect" :loading="collecting" :disabled="collectForm.sourceIds.length === 0">
          开始采集
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { fetchSocialHot, getSources, searchByTopic } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const activeTab = ref('weibo')
const items = ref([])
const loading = ref(false)
const fetchedAt = ref('')

// --- 热搜采集 ---
const sources = ref([])
const collectVisible = ref(false)
const collecting = ref(false)
const collectForm = reactive({ title: '', sourceIds: [], limit: 10 })

function formatHotScore(val) {
  if (val == null) return '-'
  if (typeof val === 'number') return val.toLocaleString()
  return val
}

function openUrl(url) {
  window.open(url, '_blank')
}

async function load(platform) {
  loading.value = true
  try {
    const res = await fetchSocialHot(platform)
    const data = res.data || res
    items.value = (data.items || []).map(item => ({
      rank: item.rank,
      title: item.title,
      hotScore: item.hotScore ?? item.hot_score,
      url: item.url,
      summary: item.summary
    }))
    fetchedAt.value = data.fetchedAt || data.fetched_at || ''
  } catch (_) {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function fetchSources() {
  try {
    const res = await getSources({ pageNum: 1, pageSize: 100 })
    sources.value = res.data?.records || res.data || []
  } catch (_) {}
}

function openCollect(row) {
  collectForm.title = row.title
  collectForm.sourceIds = sources.value.map(s => s.id)
  collectForm.limit = 10
  collectVisible.value = true
}

async function doCollect() {
  if (collectForm.sourceIds.length === 0) return
  collecting.value = true
  try {
    const res = await searchByTopic({
      keyword: collectForm.title,
      sourceIds: collectForm.sourceIds,
      limit: collectForm.limit
    })
    const total = res.data?.totalSuccess || 0
    if (total > 0) {
      ElMessage.success(`已采集「${collectForm.title}」相关文章 ${total} 篇，进入数据流水线`)
    } else {
      ElMessage.info(`未找到与「${collectForm.title}」相关的文章`)
    }
    collectVisible.value = false
  } catch {} finally {
    collecting.value = false
  }
}

function onTabChange(name) {
  load(name)
}

function refresh() {
  load(activeTab.value)
}

onMounted(() => {
  load('weibo')
  fetchSources()
})
</script>
