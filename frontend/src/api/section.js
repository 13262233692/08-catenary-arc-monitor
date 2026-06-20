import request from '@/utils/request'

export function getAllSections() {
  return request.get('/sections/all')
}

export function getSectionsByBureau(bureauId) {
  return request.get(`/sections/bureau/${bureauId}`)
}

export function getSectionsByStation(stationId) {
  return request.get(`/sections/station/${stationId}`)
}

export function getSectionsByWorkArea(workAreaId) {
  return request.get(`/sections/work-area/${workAreaId}`)
}

export function getSectionDetail(sectionId) {
  return request.get(`/sections/${sectionId}`)
}
