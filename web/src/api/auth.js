import request from './request'

export function login(data) {
  return request({ url: '/auth/login', method: 'post', data })
}

export function refreshToken(data) {
  return request({ url: '/auth/refresh', method: 'post', data })
}

export function getUserInfo() {
  return request({ url: '/auth/me', method: 'get' })
}
