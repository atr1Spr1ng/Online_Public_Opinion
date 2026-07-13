<template>
  <div>
    <!-- 快速入库：按URL采集 -->
    <el-card style="margin-bottom: 16px">
      <template #header>
        <span>快速入库</span>
      </template>
      <div style="display:flex;align-items:center;gap:12px">
        <el-input v-model="urlForm.url" placeholder="粘贴任意新闻文章链接" style="flex:1" clearable />
        <el-button type="primary" @click="handleUrlCollect" :loading="urlCollecting">开始采集</el-button>
      </div>
      <div style="margin-top:6px;color:#909399;font-size:12px">
        输入新闻文章URL，系统自动提取正文并入库，可前往内容清洗查看
      </div>
    </el-card>

    <!-- 批量采集：新闻源 + 话题 -->
    <el-card>
      <template #header>
        <span>批量采集</span>
      </template>
      <el-form :model="batchForm" label-width="90px">
        <el-form-item label="搜索关键词">
          <el-input v-model="batchForm.keyword" placeholder="留空则采集新闻源首页最新文章" style="width: 400px" clearable />
        </el-form-item>
        <el-form-item label="选择新闻源">
          <el-checkbox-group v-model="batchForm.sourceIds">
            <el-checkbox v-for="s in sources" :key="s.id" :value="s.id" border style="margin-right: 12px">
              {{ s.sourceName }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="每源采集上限">
          <el-input-number v-model="batchForm.limit" :min="1" :max="500" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleBatchCollect" :loading="batchCollecting" :disabled="batchForm.sourceIds.length === 0">
            开始采集
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSources, crawlBySource, searchByTopic, crawlSingleUrl } from '@/api/crawler'
import { ElMessage } from 'element-plus'

// ---- 快速入库 ----
const urlForm = reactive({ url: '' })
const urlCollecting = ref(false)

async function handleUrlCollect() {
  if (!urlForm.url.trim()) {
    ElMessage.warning('请输入文章URL')
    return
  }
  urlCollecting.value = true
  try {
    await crawlSingleUrl({ url: urlForm.url.trim() })
    ElMessage.success('文章已入库，可前往内容清洗处理')
    urlForm.url = ''
  } catch (e) {
    ElMessage.error(e.message || '采集失败')
  } finally {
    urlCollecting.value = false
  }
}

// ---- 批量采集 ----
const sources = ref([])
const batchForm = reactive({ keyword: '', sourceIds: [], limit: 20 })
const batchCollecting = ref(false)

async function fetchSources() {
  try {
    const res = await getSources({ pageNum: 1, pageSize: 100 })
    sources.value = res.data?.records || res.data || []
  } catch (_) {}
}

async function handleBatchCollect() {
  if (batchForm.sourceIds.length === 0) return
  batchCollecting.value = true
  try {
    if (batchForm.keyword.trim()) {
      // 按话题采集
      const res = await searchByTopic({
        keyword: batchForm.keyword.trim(),
        sourceIds: batchForm.sourceIds,
        limit: batchForm.limit
      })
      const total = res.data?.totalSuccess || 0
      if (total > 0) {
        ElMessage.success(`已采集 ${total} 篇文章，可前往内容清洗处理`)
      } else {
        ElMessage.info('未找到相关文章')
      }
    } else {
      // 按新闻源采集
      for (const id of batchForm.sourceIds) {
        await crawlBySource(id, { limit: batchForm.limit })
      }
      ElMessage.success(`已触发 ${batchForm.sourceIds.length} 个新闻源采集任务，可前往内容清洗查看`)
    }
  } catch (e) {
    ElMessage.error(e.message || '采集失败')
  } finally {
    batchCollecting.value = false
  }
}

onMounted(fetchSources)
</script>
