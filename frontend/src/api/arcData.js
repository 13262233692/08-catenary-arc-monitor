import request from '@/utils/request'

export function queryArcData(data) {
  return request.post('/arc-data/query', data)
}

export function getLatestArcData(sectionId) {
  return request.get(`/arc-data/latest/${sectionId}`)
}

export function getRawArcData(sectionId, startMs, endMs) {
  return request.get('/arc-data/raw', { params: { sectionId, startMs, endMs } })
}
