import request from './request'

export function getCrawlerHealth() {
  return request({ url: '/crawler/health', method: 'get' })
}

export function getSources(params) {
  return request({ url: '/crawler/sources', method: 'get', params })
}

export function addSource(data) {
  return request({ url: '/crawler/sources', method: 'post', data })
}

export function updateSource(id, data) {
  return request({ url: `/crawler/sources/${id}`, method: 'put', data })
}

export function toggleSource(id, status) {
  return request({ url: `/crawler/sources/${id}/status`, method: 'post', params: { status } })
}

export function getTasks(params) {
  return request({ url: '/crawler/tasks', method: 'get', params })
}

export function getTaskDetail(id) {
  return request({ url: `/crawler/tasks/${id}`, method: 'get' })
}

export function createCollectTask(data) {
  return request({ url: '/crawler/tasks', method: 'post', data })
}

export function crawlAllEnabled(params) {
  return request({ url: '/crawler/tasks/all-enabled', method: 'post', params })
}

export function crawlBySource(sourceId, params) {
  return request({ url: `/crawler/tasks/source/${sourceId}`, method: 'post', params })
}

export function getArticles(params) {
  return request({ url: '/crawler/articles', method: 'get', params })
}

export function crawlSingleUrl(data) {
  return request({ url: '/crawler/news/crawl', method: 'post', data })
}

export function discoverNews(data) {
  return request({ url: '/crawler/news/discover', method: 'post', data })
}

export function searchByTopic(data) {
  return request({ url: '/crawler/topics/search', method: 'post', data })
}

export function deleteArticle(id) {
  return request({ url: `/crawler/articles/${id}`, method: 'delete' })
}

export function deleteTask(id) {
  return request({ url: `/crawler/tasks/${id}`, method: 'delete' })
}

export function fetchSocialHot(platform) {
  return request({ url: `/crawler/social/${platform}/hot`, method: 'get' })
}
