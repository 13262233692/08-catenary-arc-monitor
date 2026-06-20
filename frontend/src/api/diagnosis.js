import request from '@/utils/request'

export function getAlarmEvents(data) {
  return request.post('/diagnosis/events', data)
}

export function getEventDetail(eventId) {
  return request.get(`/diagnosis/event/${eventId}`)
}

export function getVideoByTimestamp(sectionId, timestamp) {
  return request.get('/diagnosis/video/by-timestamp', { params: { sectionId, timestamp } })
}

export function getDiagnosisStats(sectionId, startMs, endMs) {
  return request.get('/diagnosis/stats', { params: { sectionId, startMs, endMs } })
}
