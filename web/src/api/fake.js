import request from './request'

export function detectFake(data) {
  return request({ url: '/fake/detect', method: 'post', data })
}

export function batchDetectFake(ids) {
  return request({ url: '/fake/batch-detect', method: 'post', data: ids })
}

export function getFakeResults(params) {
  return request({ url: '/fake', method: 'get', params })
}

export function getFakeDetail(id) {
  return request({ url: `/fake/${id}`, method: 'get' })
}
