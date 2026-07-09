import request from './request'

export function getReportHealth() {
  return request({ url: '/report/health', method: 'get' })
}

export function generateReport(data) {
  return request({ url: '/report/generate', method: 'post', data })
}

export function getReports(params) {
  return request({ url: '/report', method: 'get', params })
}

export function getReportDetail(id) {
  return request({ url: `/report/${id}`, method: 'get' })
}

export function qaReport(data) {
  return request({ url: '/report/qa', method: 'post', data })
}
