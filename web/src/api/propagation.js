import request from './request'

export function getPropagationHealth() {
  return request({ url: '/propagation/health', method: 'get' })
}

export function traceSource(eventId) {
  return request({ url: `/propagation/source/${eventId}`, method: 'get' })
}

export function analyzePropagation(data) {
  return request({ url: '/propagation/analyze', method: 'post', data })
}

export function getPropagationByEvent(eventId) {
  return request({ url: `/propagation/event/${eventId}`, method: 'get' })
}

export function getPropagationDetail(id) {
  return request({ url: `/propagation/${id}`, method: 'get' })
}
