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
