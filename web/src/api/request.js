import axios from 'axios'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { ElMessage } from 'element-plus'

NProgress.configure({ showSpinner: false, speed: 300, trickleSpeed: 200, minimum: 0.2 })

const service = axios.create({
  baseURL: '/api',
  timeout: 120000
})

let requestCount = 0

function showLoading() {
  if (requestCount === 0) NProgress.start()
  requestCount++
}

function hideLoading() {
  requestCount--
  if (requestCount <= 0) {
    requestCount = 0
    NProgress.done()
  }
}

function isAuthError(code) {
  return code === 401 || code === 403
}

function isLoginPageAuthRequest(config = {}) {
  const url = config.url || ''
  return router.currentRoute.value.path === '/login' && (
    url.includes('/auth/login') || url.includes('/auth/register')
  )
}

service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  showLoading()
  return config
}, error => {
  hideLoading()
  return Promise.reject(error)
})

service.interceptors.response.use(
  response => {
    hideLoading()
    const res = response.data
    if (res.code === 200) return res
    if (isAuthError(res.code)) {
      if (isLoginPageAuthRequest(response.config)) {
        ElMessage.error(res.message || '登录失败')
      } else {
        useUserStore().logout()
        router.push('/login')
      }
    } else {
      ElMessage.error(res.message || '请求失败')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  error => {
    hideLoading()
    if (isAuthError(error.response?.status)) {
      const serverMsg = error.response?.data?.message
      if (isLoginPageAuthRequest(error.config)) {
        ElMessage.error(serverMsg || '登录失败')
        return Promise.reject(serverMsg ? new Error(serverMsg) : error)
      }
      useUserStore().logout()
      router.push('/login')
      return Promise.reject(serverMsg ? new Error(serverMsg) : error)
    }
    const serverMsg = error.response?.data?.message
    ElMessage.error(serverMsg || error.message || '网络异常')
    return Promise.reject(serverMsg ? new Error(serverMsg) : error)
  }
)

export default service
