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

export function fetchMyFestivals() {
  return apiClient.get('/api/host/festivals')
}

export function fetchMyFestivalDetail(id) {
  return apiClient.get(`/api/host/festivals/${id}`)
}

//도우미 계정 1개 발급. 응답의 password는 이 응답에서만 볼 수 있고 서버에 평문으로 남지 않는다 —
//놓치면 아래 reissueHelperPassword로 새로 받아야 한다.
export function createHelperAccount(festivalId) {
  return apiClient.post(`/api/host/festivals/${festivalId}/helpers`)
}

//발급해둔 도우미 계정 목록(개수 확인용, 비밀번호 미포함)
export function fetchHelperAccounts(festivalId) {
  return apiClient.get(`/api/host/festivals/${festivalId}/helpers`)
}

//비밀번호 분실 시 계정은 그대로 두고 비밀번호만 새로 발급한다
export function reissueHelperPassword(festivalId, helperUserId) {
  return apiClient.post(`/api/host/festivals/${festivalId}/helpers/${helperUserId}/password`)
}
