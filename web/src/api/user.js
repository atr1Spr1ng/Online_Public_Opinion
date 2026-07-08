import request from './request'

// ── 个人信息 ──
export function getProfile() {
  return request.get('/user/profile')
}

export function updateProfile(data) {
  return request.put('/user/profile', data)
}

// ── 关键词 ──
export function listKeywords() {
  return request.get('/user/keywords')
}

export function addKeyword(data) {
  return request.post('/user/keywords', data)
}

export function deleteKeyword(id) {
  return request.delete(`/user/keywords/${id}`)
}

// ── 关注领域 ──
export function listDomains() {
  return request.get('/user/domains')
}

export function addDomain(data) {
  return request.post('/user/domains', data)
}

export function deleteDomain(id) {
  return request.delete(`/user/domains/${id}`)
}

// ── 新闻源订阅 ──
export function listSourceSubscriptions() {
  return request.get('/user/sources')
}

export function subscribeSource(sourceId) {
  return request.post(`/user/sources/${sourceId}`)
}

export function unsubscribeSource(sourceId) {
  return request.delete(`/user/sources/${sourceId}`)
}
