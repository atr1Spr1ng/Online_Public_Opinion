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
        输入单篇新闻详情页URL，系统将抓取正文并保存为原始文章，后续可在内容清洗中处理
      </div>
    </el-card>

    <!-- 批量采集：新闻源 + 话题 -->
    <el-card>
      <template #header>
        <span>批量采集</span>
      </template>
      <el-form :model="batchForm" label-width="110px">
        <el-form-item label="搜索关键词">
          <el-input v-model="batchForm.keyword" placeholder="留空则按所选新闻源/频道/RSS采集最新文章" style="width: 400px" clearable />
        </el-form-item>
        <el-form-item label="选择新闻源">
          <el-checkbox-group v-model="batchForm.sourceIds">
            <el-checkbox
              v-for="s in sources"
              :key="s.id"
              :value="s.id"
              :disabled="isKeywordMode && !isTopicSearchSupported(s)"
              border
              style="margin-right: 12px; margin-bottom: 6px"
            >
              {{ s.sourceName }}
              <el-tag
                v-if="isKeywordMode && !isTopicSearchSupported(s)"
                size="small"
                type="info"
                style="margin-left: 6px"
              >
                仅源头采集
              </el-tag>
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="isKeywordMode" style="color:#909399;font-size:12px;margin-top:6px">
            关键词搜索当前支持新浪、中新网、澎湃、界面；央视、新华网、环球网、人民网等新增源请留空关键词后按源头采集。
          </div>
        </el-form-item>
        <el-form-item label="每源采集上限">
          <el-input-number v-model="batchForm.limit" :min="1" :max="500" />
        </el-form-item>
        <el-form-item class="collect-action">
          <el-button type="primary" @click="handleBatchCollect" :loading="batchCollecting" :disabled="submitDisabled">
            开始采集
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getSources, crawlBySource, createTopicSearchTask, crawlSingleUrl } from '@/api/crawler'
import { isTopicSearchSupported, normalizeSourceList } from '@/utils/sourceSupport'
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
    // error already toasted by global interceptor
  } finally {
    urlCollecting.value = false
  }
}

// ---- 批量采集 ----
const sources = ref([])
const batchForm = reactive({ keyword: '', sourceIds: [], limit: 20 })
const batchCollecting = ref(false)
const isKeywordMode = computed(() => Boolean(batchForm.keyword.trim()))
const selectedTopicSearchIds = computed(() => {
  const supportMap = new Map(sources.value.map(s => [s.id, isTopicSearchSupported(s)]))
  return batchForm.sourceIds.filter(id => supportMap.get(id))
})
const submitDisabled = computed(() => {
  if (batchForm.sourceIds.length === 0) return true
  if (isKeywordMode.value && selectedTopicSearchIds.value.length === 0) return true
  return false
})

async function fetchSources() {
  try {
    const res = await getSources({ pageNum: 1, pageSize: 500 })
    sources.value = normalizeSourceList(res.data?.records || res.data || [])
  } catch (_) {}
}

async function handleBatchCollect() {
  if (batchForm.sourceIds.length === 0) return
  batchCollecting.value = true
  try {
    if (batchForm.keyword.trim()) {
      const supportedIds = selectedTopicSearchIds.value
      if (supportedIds.length !== batchForm.sourceIds.length) {
        ElMessage.warning('已跳过暂不支持关键词搜索的新闻源')
      }
      // 按关键词创建后台采集任务，保持“每源采集上限”语义
      const res = await createTopicSearchTask({
        keyword: batchForm.keyword.trim(),
        sourceIds: supportedIds,
        limit: batchForm.limit
      })
      const taskId = res.data?.taskId
      ElMessage.success(`已创建关键词采集任务${taskId ? ` #${taskId}` : ''}，可在首页后台任务查看`)
    } else {
      // 按新闻源采集
      for (const id of batchForm.sourceIds) {
        await crawlBySource(id, { limit: batchForm.limit })
      }
      ElMessage.success(`已触发 ${batchForm.sourceIds.length} 个新闻源采集任务，可前往内容清洗查看`)
    }
  } catch (e) {
    // error already toasted by global interceptor
  } finally {
    batchCollecting.value = false
  }
}

onMounted(fetchSources)
</script>

<style scoped>
.collect-action {
  margin-top: 18px;
}
</style>
