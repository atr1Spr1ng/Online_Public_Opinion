import { defineStore } from 'pinia'
import { login as loginApi, refreshToken as refreshTokenApi, getUserInfo } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    refreshToken: localStorage.getItem('refreshToken') || '',
    userInfo: null
  }),
  actions: {
    async login(username, password) {
      const res = await loginApi({ username, password })
      this.token = res.data.accessToken
      this.refreshToken = res.data.refreshToken
      this.userInfo = res.data.user
      localStorage.setItem('token', this.token)
      localStorage.setItem('refreshToken', this.refreshToken)
      return res
    },
    async refresh() {
      const res = await refreshTokenApi({ refreshToken: this.refreshToken })
      this.token = res.data.accessToken
      localStorage.setItem('token', this.token)
      return res
    },
    async fetchUserInfo() {
      const res = await getUserInfo()
      this.userInfo = res.data
    },
    logout() {
      this.token = ''
      this.refreshToken = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('refreshToken')
    }
  }
})
