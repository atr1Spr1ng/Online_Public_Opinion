<template>
  <div class="profile-page">
    <el-card class="profile-card">
      <template #header>
        <span>个人信息</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
        style="max-width: 500px"
        @submit.prevent="handleSubmit"
      >
        <el-form-item label="用户名">
          <el-input :model-value="form.username" disabled />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" maxlength="50" show-word-limit />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="form.email" maxlength="100" />
        </el-form-item>

        <el-form-item label="角色">
          <el-tag :type="form.role === 'ADMIN' ? 'danger' : 'primary'">
            {{ form.role === 'ADMIN' ? '管理员' : '普通用户' }}
          </el-tag>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">
            保存修改
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getProfile, updateProfile } from '@/api/user'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)
const form = ref({ username: '', nickname: '', email: '', role: '' })

const rules = {
  nickname: [
    { max: 50, message: '昵称长度不能超过50个字符', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ]
}

onMounted(async () => {
  try {
    const res = await getProfile()
    Object.assign(form.value, res.data)
  } catch {}
})

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await updateProfile({ nickname: form.value.nickname, email: form.value.email })
    ElMessage.success('个人信息已更新')
    await userStore.fetchUserInfo()
  } catch {} finally {
    loading.value = false
  }
}
</script>

<style scoped>
.profile-page { max-width: 800px; margin: 0 auto; }
.profile-card { margin-bottom: 20px; }
</style>
