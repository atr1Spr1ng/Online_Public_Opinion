<template>
  <div>
    <el-card>
      <template #header>
        <div class="card-header">
          <span>新闻源管理</span>
          <el-button type="primary" @click="openDialog()">新增新闻源</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="sourceName" label="名称" />
        <el-table-column prop="sourceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.sourceType === 'official' ? 'success' : 'primary'">{{ row.sourceType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sourceUrl" label="URL" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click="crawlSource(row)">采集</el-button>
            <el-button type="warning" link @click="openDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        style="margin-top:16px; justify-content:flex-end"
        v-model:current-page="page.pageNum" v-model:page-size="page.pageSize"
        :total="total" layout="total, prev, pager, next" @change="fetchData"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑新闻源' : '新增新闻源'" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="sourceName">
          <el-input v-model="form.sourceName" />
        </el-form-item>
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="form.sourceType">
            <el-option label="门户网站" value="portal" />
            <el-option label="官方媒体" value="official" />
          </el-select>
        </el-form-item>
        <el-form-item label="URL" prop="sourceUrl">
          <el-input v-model="form.sourceUrl" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getSources, addSource, updateSource, toggleSource, crawlBySource } from '@/api/crawler'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = reactive({ pageNum: 1, pageSize: 10 })
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ sourceName: '', sourceType: 'portal', sourceUrl: '' })
const rules = {
  sourceName: [{ required: true, message: '请输入名称' }],
  sourceUrl: [{ required: true, message: '请输入URL' }]
}

async function fetchData() {
  loading.value = true
  try {
    const res = await getSources({ pageNum: page.pageNum, pageSize: page.pageSize })
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || res.total || 0
  } catch (e) { ElMessage.error(e.message) }
  finally { loading.value = false }
}

let editingId = null

function openDialog(row) {
  isEdit.value = !!row
  if (row) {
    editingId = row.id
    Object.assign(form, { sourceName: row.sourceName, sourceType: row.sourceType, sourceUrl: row.sourceUrl })
  } else {
    editingId = null
    Object.assign(form, { sourceName: '', sourceType: 'portal', sourceUrl: '' })
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateSource(editingId, { ...form })
    } else {
      await addSource({ ...form })
    }
    ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}

async function toggle(row) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await toggleSource(row.id, newStatus)
    row.status = newStatus
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
  } catch (e) { ElMessage.error(e.message) }
}

async function crawlSource(row) {
  try {
    await crawlBySource(row.id, { limit: 20 })
    ElMessage.success('采集任务已创建')
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchData)
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
