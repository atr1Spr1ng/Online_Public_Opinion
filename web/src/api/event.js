import request from './request'

export function listEvents(params) {
  return request({ url: '/events', method: 'get', params })
}

export function getEvent(id) {
  return request({ url: `/events/${id}`, method: 'get' })
}

export function clusterEvents(data) {
  return request({ url: '/events/cluster', method: 'post', data })
}

export function forecastTrend(id, params) {
  return request({ url: `/events/${id}/trend`, method: 'get', params })
}
