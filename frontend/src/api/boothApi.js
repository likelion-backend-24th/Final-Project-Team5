import apiClient from './client'

//페스티벌 상세의 부스 목록(공개, 인증 불필요) — WAITING 상태는 서버가 애초에 내려주지 않는다.
export function fetchBoothsForFestival(festivalId) {
  return apiClient.get(`/api/festivals/${festivalId}/booths`)
}

//부스 대기 신청 — 로그인 + 해당 페스티벌 티켓 보유 필요(서버가 검증)
export function requestBoothWaitlist(boothId) {
  return apiClient.post(`/api/booth-waitlists/${boothId}`)
}

//내 대기번호 조회
export function fetchMyBoothWaitlist(boothId) {
  return apiClient.get(`/api/booth-waitlists/${boothId}/me`)
}

//내가 신청한 모든 부스의 대기 현황 — 챗봇 위젯이 폴링해서 "내 차례"를 알아낼 때 쓴다.
export function fetchMyActiveBoothWaitlists() {
  return apiClient.get('/api/booth-waitlists/me/active')
}

//STOREHOST 본인 부스의 대기열 현황(호출 번호·대기 인원) 조회
export function fetchBoothQueueStatus(boothId) {
  return apiClient.get(`/api/booth-waitlists/booths/${boothId}/queue-status`)
}

//STOREHOST가 다음 대기 순번을 호출한다
export function callNextBoothWaitlist(boothId) {
  return apiClient.post(`/api/booth-waitlists/booths/${boothId}/call-next`)
}

//STOREHOST 본인이 개설한 부스 목록(상태 무관)
export function fetchMyBooths() {
  return apiClient.get('/api/store/booths')
}

//STOREHOST 본인 부스 상세(상태 무관)
export function fetchMyBoothDetail(boothId) {
  return apiClient.get(`/api/store/booths/${boothId}`)
}

//부스 개설 전 대표 이미지 업로드 — 기존 uploadFestivalImages와 동일한 FormData 패턴
export function uploadBoothImage(image) {
  const formData = new FormData()
  if (image) formData.append('image', image)
  return apiClient.post('/api/store/booths/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

//부스 개설 — 페스티벌당 1개만 허용된다(백엔드가 검증)
export function createBooth(payload) {
  return apiClient.post('/api/store/booths', payload)
}

//부스 상태 변경(대기/운영중/마감)
export function changeBoothStatus(boothId, boothStatus) {
  return apiClient.patch(`/api/store/booths/${boothId}/status`, { boothStatus })
}
