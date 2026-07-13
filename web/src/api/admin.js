import request from './request'

export function listUsers(params) {
  return request.get('/admin/users', { params })
}

export function updateUserStatus(id, status) {
  return request.put(`/admin/users/${id}/status`, { status })
}

export function getAdminStats() {
  return request.get('/admin/stats')
}

export function getServicesHealth() {
  return request.get('/admin/services/health')
}
