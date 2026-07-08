import request from './request'

export function cleanArticle(data) {
  return request({ url: '/content/clean', method: 'post', data })
}

export function batchClean(ids) {
  return request({ url: '/content/batch-clean', method: 'post', data: ids })
}

export function getCleanArticles(params) {
  return request({ url: '/content/clean', method: 'get', params })
}

export function getCleanDetail(id) {
  return request({ url: `/content/clean/${id}`, method: 'get' })
}
