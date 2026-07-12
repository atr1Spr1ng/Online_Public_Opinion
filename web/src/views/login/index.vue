<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">网络舆情智能分析系统</h2>

      <div class="mode-tabs">
        <span
          :class="['mode-tab', { active: mode === 'login' }]"
          @click="switchMode('login')"
        >登录</span>
        <span
          :class="['mode-tab', { active: mode === 'register' }]"
          @click="switchMode('register')"
        >注册</span>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" @keyup.enter="handleSubmit" show-password />
        </el-form-item>
        <template v-if="mode === 'register'">
          <el-form-item prop="nickname">
            <el-input v-model="form.nickname" placeholder="昵称（选填）" />
          </el-form-item>
          <el-form-item prop="email">
            <el-input v-model="form.email" placeholder="邮箱（选填）" />
          </el-form-item>
        </template>
        <el-form-item>
          <el-button type="primary" style="width:100%" :loading="loading" @click="handleSubmit">
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>
        </el-form-item>
        <el-form-item v-if="mode === 'login'" style="text-align:center;margin-bottom:0">
          <span class="tip">还没有账号？</span>
          <el-button type="primary" link @click="switchMode('register')">立即注册</el-button>
        </el-form-item>
        <el-form-item v-else style="text-align:center;margin-bottom:0">
          <span class="tip">已有账号？</span>
          <el-button type="primary" link @click="switchMode('login')">去登录</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { login as loginApi, register as registerApi } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const formRef = ref(null)
const mode = ref('login')

const loginForm = { username: 'admin', password: 'admin123', nickname: '', email: '' }
const registerForm = { username: '', password: '', nickname: '', email: '' }
const form = reactive({ ...loginForm })

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50之间', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 100, message: '密码长度需在6-100之间', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}
const rules = ref(loginRules)

function switchMode(newMode) {
  mode.value = newMode
  if (newMode === 'login') {
    Object.assign(form, loginForm)
    rules.value = loginRules
  } else {
    Object.assign(form, registerForm)
    rules.value = registerRules
  }
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login(form.username, form.password)
      ElMessage.success('登录成功')
      router.push('/dashboard')
    } else {
      await registerApi({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
        email: form.email || undefined
      })
      ElMessage.success('注册成功，请登录')
      switchMode('login')
    }
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh; display: flex; align-items: center;
  justify-content: center; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 420px; padding: 40px; background: #fff;
  border-radius: 8px; box-shadow: 0 4px 24px rgba(0,0,0,0.15);
}
.login-title { text-align: center; margin-bottom: 24px; color: #303133; font-size: 20px; }
.mode-tabs {
  display: flex; margin-bottom: 24px; border-bottom: 1px solid #e4e7ed;
}
.mode-tab {
  flex: 1; text-align: center; padding: 8px 0; cursor: pointer;
  color: #909399; font-size: 15px; border-bottom: 2px solid transparent;
  transition: all 0.2s;
}
.mode-tab.active {
  color: #409EFF; border-bottom-color: #409EFF; font-weight: 500;
}
.tip { color: #909399; font-size: 13px; }
</style>
