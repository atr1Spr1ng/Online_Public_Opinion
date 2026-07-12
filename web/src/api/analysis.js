import request from './request'

export function getAnalysisHealth() {
  return request({ url: '/analysis/health', method: 'get' })
}

export function analyzeSentiment(data) {
  return request({ url: '/analysis/sentiment', method: 'post', data })
}

export function batchSentiment(ids) {
  return request({ url: '/analysis/batch-sentiment', method: 'post', data: ids })
}

export function getSentimentResults(params) {
  return request({ url: '/analysis/sentiment', method: 'get', params })
}

export function getSentimentDetail(id) {
  return request({ url: `/analysis/sentiment/${id}`, method: 'get' })
}

export function deleteSentimentResult(id) {
  return request({ url: `/analysis/sentiment/${id}`, method: 'delete' })
}
