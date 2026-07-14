import request from './request'

export function listEvents(params) {
  return request({ url: '/events', method: 'get', params })
}

export function searchEvents(params) {
  return request({ url: '/events/search', method: 'get', params })
}

export function findSimilarEvents(data) {
  return request({ url: '/events/similar', method: 'post', data })
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

export function getEventReport(id) {
  return request({ url: `/events/${id}/full-report`, method: 'get' })
}

export function getMyFeedEvents() {
  return request({ url: '/events/my-feed', method: 'get' })
}

export function deleteEvent(id) {
  return request({ url: `/events/${id}`, method: 'delete' })
}
