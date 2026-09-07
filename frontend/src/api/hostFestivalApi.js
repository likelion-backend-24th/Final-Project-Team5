import apiClient from './client'

export function createFestival(payload) {
  return apiClient.post('/api/host/festivals', payload)
}

//등록 전 이미지를 먼저 업로드하고 URL 목록을 받는다 (최대 3장, 장당 10MB — 백엔드 FestivalErrorCode 참고)
export function uploadFestivalImages(files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return apiClient.post('/api/host/festivals/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function fetchMyFestivals() {
  return apiClient.get('/api/host/festivals')
}

export function fetchMyFestivalDetail(id) {
  return apiClient.get(`/api/host/festivals/${id}`)
}
