import apiClient from './client'

export function createFestival(payload) {
  return apiClient.post('/api/host/festivals', payload)
}

//등록 전 이미지를 먼저 업로드하고 URL을 받는다. thumbnail은 파일 1개(선택), detailImages는 최대 2개(선택) —
//장당 10MB, 백엔드 FestivalErrorCode 참고. 응답: { thumbnailImageUrl, detailImageUrls }
export function uploadFestivalImages({ thumbnail, detailImages }) {
  const formData = new FormData()
  if (thumbnail) formData.append('thumbnail', thumbnail)
  ;(detailImages ?? []).forEach((file) => formData.append('detailImages', file))
  return apiClient.post('/api/host/festivals/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

// 신규 예매를 중단하고 운영자에게 행사 취소 승인을 요청한다.
export function requestFestivalCancellation(festivalId, reason) {
  return apiClient.post(`/api/host/festivals/${festivalId}/cancellation-request`, { reason })
}

export function fetchMyFestivals() {
  return apiClient.get('/api/host/festivals')
}

export function fetchMyFestivalDetail(id) {
  return apiClient.get(`/api/host/festivals/${id}`)
}

export function createHelperAccount(festivalId, email) {
  return apiClient.post(`/api/host/festivals/${festivalId}/helpers`, { email })
}

export function fetchHelperAccounts(festivalId) {
  return apiClient.get(`/api/host/festivals/${festivalId}/helpers`)
}

export function resendHelperInvitation(festivalId, helperUserId) {
  return apiClient.post(`/api/host/festivals/${festivalId}/helpers/${helperUserId}/resend`)
}

export function revokeHelperAccount(festivalId, helperUserId) {
  return apiClient.delete(`/api/host/festivals/${festivalId}/helpers/${helperUserId}`)
}

export function convertLegacyHelper(festivalId, helperUserId, email) {
  return apiClient.post(`/api/host/festivals/${festivalId}/helpers/${helperUserId}/invitation`, { email })
}
