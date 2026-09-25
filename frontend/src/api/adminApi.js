import apiClient from './client'

export function fetchPendingHostApplications() {
  return apiClient.get('/api/admin/host-applications')
}

export function reviewHostApplication(id, { status, rejectReason }) {
  return apiClient.patch(`/api/admin/host-applications/${id}`, { status, rejectReason })
}

//Auth Service가 보유한 실제 HOST 계정 목록을 조회한다.
export function fetchAdminHosts() {
  return apiClient.get('/api/admin/hosts')
}

export function fetchPendingFestivals() {
  return apiClient.get('/api/admin/festivals')
}

export function reviewFestival(id, { decision, rejectReason }) {
  return apiClient.patch(`/api/admin/festivals/${id}`, { decision, rejectReason })
}

//주최자가 요청한 행사 취소 목록(approved=true면 이미 환불 배치 진행 중)
export function fetchCancellationRequests() {
  return apiClient.get('/api/admin/festivals/cancellation-requests')
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
