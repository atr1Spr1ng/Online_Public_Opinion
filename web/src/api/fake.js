import request from './request'

export function detectFake(data) {
  return request({ url: '/fake/detect', method: 'post', data })
}

export function batchDetectFake(ids, mode = 'auto') {
  return request({ url: '/fake/batch-detect', method: 'post', data: { cleanIds: ids, mode } })
}

export function getFakeResults(params) {
  return request({ url: '/fake', method: 'get', params })
}

export function getFakeDetail(id) {
  return request({ url: `/fake/${id}`, method: 'get' })
}

export function deleteFakeResult(id) {
  return request({ url: `/fake/${id}`, method: 'delete' })
}
