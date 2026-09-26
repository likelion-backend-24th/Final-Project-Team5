import apiClient from './client'

export function fetchPendingHostApplications() {
  return apiClient.get('/api/admin/host-applications')
}

export function reviewHostApplication(id, { status, rejectReason }) {
  return apiClient.patch(`/api/admin/host-applications/${id}`, { status, rejectReason })
}

//Auth Service가 보유한 실제 HOST 계정 목록을 조회한다(검색·상태 필터·페이지네이션).
export function fetchAdminHosts(params, signal) {
  return apiClient.get('/api/admin/hosts', { params, signal })
}

export function fetchPendingFestivals(params, signal) {
  return apiClient.get('/api/admin/festivals', { params, signal })
}

//주최자 목록의 "등록 페스티벌 N개"용 — 상태 무관 개수(요청 id가 0개여도 포함, 최대 100명)
export function fetchFestivalHostCounts(hostIds) {
  return apiClient.get('/api/admin/festivals/host-counts', { params: { hostIds: hostIds.join(',') } })
}

//운영자 운영 현황 — 공개된 적 있는 페스티벌의 운영 상태·판매 현황
export function fetchFestivalOperations(params, signal) {
  return apiClient.get('/api/admin/festivals/operations', { params, signal })
}

export function reviewFestival(id, { decision, rejectReason }) {
  return apiClient.patch(`/api/admin/festivals/${id}`, { decision, rejectReason })
}

//주최자가 요청한 행사 취소 목록(approved=true면 이미 환불 배치 진행 중)
export function fetchCancellationRequests(status = 'PENDING') {
  return apiClient.get('/api/admin/festivals/cancellation-requests', { params: { status } })
}

//행사 취소 승인 — 이후 payment-service 배치가 남은 티켓을 위약금 없이 전액 환불한다(되돌릴 수 없음)
export function approveFestivalCancellation(festivalId) {
  return apiClient.post(`/api/admin/festivals/${festivalId}/approve-cancellation`)
}

//행사 취소 요청 반려 — 요청 전 상태(공개/종료)로 되돌린다. 이미 승인된 요청은 서버가 거절한다
export function rejectFestivalCancellation(festivalId) {
  return apiClient.post(`/api/admin/festivals/${festivalId}/reject-cancellation`)
}

//전체 회원 목록(검색·필터·페이지네이션)
export function fetchAdminUsers(params, signal) {
  return apiClient.get('/api/admin/users', { params, signal })
}

//회원 계정 정지 — reason 필수
export function suspendUser(userId, reason) {
  return apiClient.patch(`/api/admin/users/${userId}/suspend`, { reason })
}

//정지 해제
export function unsuspendUser(userId) {
  return apiClient.patch(`/api/admin/users/${userId}/unsuspend`)
}

//어드민 대시보드 요약 — 운영 현황·처리 대기 개수를 한 번에 조회한다
export function fetchAdminSummary(signal) {
  return apiClient.get('/api/admin/festivals/summary', { signal })
}
