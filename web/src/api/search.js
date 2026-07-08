import request from './request'

export function searchArticles(params) {
  return request.get('/search/articles', { params })
}

export function searchByMyKeywords(params) {
  return request.get('/search/articles/my-keywords', { params })
}

export function searchByFilter(params) {
  return request.get('/search/articles/filter', { params })
}
