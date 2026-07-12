import axios from 'axios'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

service.interceptors.request.use(config => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
}, error => Promise.reject(error))

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code === 200) return res
    if (res.code === 401) {
      useUserStore().logout()
      router.push('/login')
    }
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  error => {
    if (error.response?.status === 401) {
      useUserStore().logout()
      router.push('/login')
    }
    const serverMsg = error.response?.data?.message
    if (serverMsg) {
      return Promise.reject(new Error(serverMsg))
    }
    return Promise.reject(error)
  }
)

export default service
