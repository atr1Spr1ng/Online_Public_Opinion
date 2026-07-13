<template>
  <div>
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>用户管理</span>
          <el-button type="primary" @click="openCreate">新增用户</el-button>
        </div>
      </template>
      <el-table :data="users" v-loading="loading" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" width="180">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <template v-if="isSelf(row) || row.id === 1">
              <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">
                {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
            <template v-else>
              <el-select v-model="row.role" size="small" style="width:100px" @change="changeRole(row)">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="普通用户" value="USER" />
              </el-select>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1" :inactive-value="0"
              :disabled="isSelf(row) || row.id === 1"
              @change="toggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="warning" link size="small" @click="openResetPwd(row)">重置密码</el-button>
            <template v-if="!isSelf(row) && row.id !== 1">
              <el-popconfirm title="确定要删除该用户吗？" @confirm="handleDelete(row)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 16px; text-align: center">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchUsers"
        />
      </div>
    </el-card>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="createDialog.visible" title="新增用户" width="400px">
      <el-form :model="createDialog.form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="createDialog.form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createDialog.form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createDialog.form.role" style="width:100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="普通用户" value="USER" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="pwdDialog.visible" title="重置密码" width="400px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="pwdDialog.password" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { listUsers, updateUserStatus, updateUser, resetPassword, deleteUser } from '@/api/admin'
import { register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const userStore = useUserStore()
const currentUserId = () => userStore.userInfo?.id

const users = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

const createDialog = reactive({
  visible: false,
  form: { username: '', password: '', role: 'USER' }
})

const pwdDialog = reactive({ visible: false, userId: null, password: '' })

function isSelf(row) {
  return row.id === currentUserId()
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await listUsers({ pageNum: pageNum.value, pageSize: pageSize.value })
    users.value = (res.data?.records || []).map(u => ({
      ...u,
      status: u.status ?? 1
    }))
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function toggleStatus(row) {
  try {
    await updateUserStatus(row.id, row.status)
    ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
  } catch (e) {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error(e.message)
  }
}

async function changeRole(row) {
  try {
    await updateUser(row.id, { role: row.role })
    ElMessage.success('角色已更新')
  } catch (e) {
    ElMessage.error(e.message)
    fetchUsers()
  }
}

function openCreate() {
  createDialog.form = { username: '', password: '', role: 'USER' }
  createDialog.visible = true
}

async function submitCreate() {
  const f = createDialog.form
  if (!f.username) { ElMessage.warning('请输入用户名'); return }
  if (!f.password || f.password.length < 6) { ElMessage.warning('密码至少6位'); return }
  try {
    await register({ username: f.username, password: f.password, role: f.role })
    ElMessage.success('用户已创建')
    createDialog.visible = false
    fetchUsers()
  } catch (e) { ElMessage.error(e.message) }
}

function openResetPwd(row) {
  pwdDialog.userId = row.id
  pwdDialog.password = ''
  pwdDialog.visible = true
}

async function submitResetPwd() {
  if (pwdDialog.password.length < 6) { ElMessage.warning('密码至少6位'); return }
  try {
    await resetPassword(pwdDialog.userId, pwdDialog.password)
    ElMessage.success('密码已重置')
    pwdDialog.visible = false
  } catch (e) { ElMessage.error(e.message) }
}

async function handleDelete(row) {
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    fetchUsers()
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(fetchUsers)
</script>
