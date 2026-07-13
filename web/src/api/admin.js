import request from './request'

// 用户管理
export function listUsers(params) {
  return request.get('/admin/users', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function updateUser(id, data) {
  return request.put(`/admin/users/${id}`, data)
}

export function resetPassword(id, password) {
  return request.put(`/admin/users/${id}/reset-password`, { password })
}

export function deleteUser(id) {
  return request.delete(`/admin/users/${id}`)
}

// 平台统计
export function getAdminStats() {
  return request.get('/admin/stats')
}

export function getServicesHealth() {
  return request.get('/admin/services/health')
}

// 数据明细
export function listAdminArticles(params) {
  return request.get('/admin/articles', { params })
}

export function listAdminCleanedArticles(params) {
  return request.get('/admin/cleaned-articles', { params })
}

export function listAdminEvents(params) {
  return request.get('/admin/events', { params })
}

export function listAdminEsArticles(params) {
  return request.get('/admin/es/articles', { params })
}

export function listAdminEsEvents(params) {
  return request.get('/admin/es/events', { params })
}
