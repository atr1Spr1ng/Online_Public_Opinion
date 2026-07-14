<template>
  <div class="preferences-page">
    <el-tabs v-model="activeTab" type="border-card">
      <!-- === 关键词管理 === -->
      <el-tab-pane label="关键词管理" name="keywords">
        <div class="tab-header">
          <span class="tab-desc">设置关注关键词，系统将按关键词过滤文章和事件</span>
          <el-button type="primary" @click="showKeywordDialog = true">
            <el-icon><Plus /></el-icon> 添加
          </el-button>
        </div>

        <el-table :data="keywords" v-loading="kwLoading">
          <el-table-column prop="keyword" label="关键词" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="danger" link @click="handleDeleteKeyword(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!kwLoading && keywords.length === 0" description="暂无关键词" />
      </el-tab-pane>

      <!-- === 关注领域 === -->
      <el-tab-pane label="关注领域" name="domains">
        <div class="tab-header">
          <span class="tab-desc">选择关注领域，也可以自定义输入</span>
          <el-button type="primary" @click="showDomainDialog = true">
            <el-icon><Plus /></el-icon> 添加
          </el-button>
        </div>

        <div class="preset-tags">
          <span class="preset-label">预设领域：</span>
          <el-tag
            v-for="d in presetDomains"
            :key="d"
            :type="myDomainSet.has(d) ? '' : 'info'"
            :effect="myDomainSet.has(d) ? 'dark' : 'plain'"
            style="cursor: pointer; margin: 4px"
            @click="togglePresetDomain(d)"
          >
            {{ d }}
          </el-tag>
        </div>

        <el-divider />

        <el-table :data="domains" v-loading="dmLoading">
          <el-table-column prop="domainName" label="领域" />
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="danger" link @click="handleDeleteDomain(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-empty v-if="!dmLoading && domains.length === 0" description="暂无关注领域" />
      </el-tab-pane>

      <!-- === 新闻源订阅 === -->
      <el-tab-pane label="新闻源订阅" name="sources">
        <div class="tab-header">
          <span class="tab-desc">订阅关注的新闻源，系统将优先展示已订阅来源的文章</span>
        </div>

        <el-table :data="sources" v-loading="srcLoading">
          <el-table-column prop="sourceName" label="新闻源名称" />
          <el-table-column prop="sourceType" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small">
                {{ sourceTypeMap[row.sourceType] || row.sourceType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sourceUrl" label="网址" min-width="200" show-overflow-tooltip />
          <el-table-column label="订阅" width="100" align="center">
            <template #default="{ row }">
              <el-switch
                :model-value="row.subscribed"
                @change="(val) => handleToggleSource(row, val)"
              />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 添加关键词弹窗 -->
    <el-dialog v-model="showKeywordDialog" title="添加关键词" width="400px">
      <el-form @submit.prevent="handleAddKeyword">
        <el-form-item label="关键词">
          <el-input v-model="newKeyword" maxlength="100" placeholder="输入关键词" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showKeywordDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddKeyword" :disabled="!newKeyword.trim()">
          确认
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加领域弹窗 -->
    <el-dialog v-model="showDomainDialog" title="添加关注领域" width="400px">
      <el-form @submit.prevent="handleAddDomain">
        <el-form-item label="领域名称">
          <el-input v-model="newDomain" maxlength="100" placeholder="例如：人工智能、新能源" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDomainDialog = false">取消</el-button>
        <el-button type="primary" @click="handleAddDomain" :disabled="!newDomain.trim()">
          确认
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  listKeywords, addKeyword, deleteKeyword,
  listDomains, addDomain, deleteDomain,
  listSourceSubscriptions, subscribeSource, unsubscribeSource
} from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const activeTab = ref('keywords')

// ── 关键词 ──
const keywords = ref([])
const kwLoading = ref(false)
const showKeywordDialog = ref(false)
const newKeyword = ref('')

async function loadKeywords() {
  kwLoading.value = true
  try {
    const res = await listKeywords()
    keywords.value = res.data
  } finally {
    kwLoading.value = false
  }
}

async function handleAddKeyword() {
  const kw = newKeyword.value.trim()
  if (!kw) return
  try {
    await addKeyword({ keyword: kw })
    ElMessage.success('已添加')
    newKeyword.value = ''
    showKeywordDialog.value = false
    await loadKeywords()
  } catch {}
}

async function handleDeleteKeyword(id) {
  await ElMessageBox.confirm('确认删除该关键词？', '提示', { type: 'warning' })
  await deleteKeyword(id)
  ElMessage.success('已删除')
  await loadKeywords()
}

// ── 领域 ──
const presetDomains = ['科技', '财经', '军事', '教育', '社会', '国际', '体育', '娱乐']
const domains = ref([])
const dmLoading = ref(false)
const showDomainDialog = ref(false)
const newDomain = ref('')

const myDomainSet = computed(() => new Set(domains.value.map(d => d.domainName)))

async function loadDomains() {
  dmLoading.value = true
  try {
    const res = await listDomains()
    domains.value = res.data
  } finally {
    dmLoading.value = false
  }
}

async function togglePresetDomain(name) {
  if (myDomainSet.value.has(name)) {
    const found = domains.value.find(d => d.domainName === name)
    if (found) {
      await deleteDomain(found.id)
      ElMessage.success(`已移除「${name}」`)
      await loadDomains()
    }
  } else {
    await addDomain({ domainName: name })
    ElMessage.success(`已添加「${name}」`)
    await loadDomains()
  }
}

async function handleAddDomain() {
  const name = newDomain.value.trim()
  if (!name) return
  try {
    await addDomain({ domainName: name })
    ElMessage.success('已添加')
    newDomain.value = ''
    showDomainDialog.value = false
    await loadDomains()
  } catch {}
}

async function handleDeleteDomain(id) {
  await ElMessageBox.confirm('确认删除该领域？', '提示', { type: 'warning' })
  await deleteDomain(id)
  ElMessage.success('已删除')
  await loadDomains()
}

// ── 新闻源订阅 ──
const sources = ref([])
const srcLoading = ref(false)
const sourceTypeMap = { portal: '门户', official: '官方', original: '原创' }

async function loadSources() {
  srcLoading.value = true
  try {
    const res = await listSourceSubscriptions()
    sources.value = res.data
  } finally {
    srcLoading.value = false
  }
}

async function handleToggleSource(row, val) {
  try {
    if (val) {
      await subscribeSource(row.sourceId)
      ElMessage.success('已订阅')
    } else {
      await unsubscribeSource(row.sourceId)
      ElMessage.success('已取消订阅')
    }
    await loadSources()
  } catch {}
}

onMounted(() => {
  loadKeywords()
  loadDomains()
  loadSources()
})
</script>

<style scoped>
.preferences-page { max-width: 900px; margin: 0 auto; }
.tab-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.tab-desc { color: #909399; font-size: 13px; }
.preset-tags { margin-bottom: 8px; }
.preset-label { color: #606266; font-size: 13px; margin-right: 8px; }
</style>
