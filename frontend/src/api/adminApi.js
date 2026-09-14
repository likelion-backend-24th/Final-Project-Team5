import apiClient from './client'

export function fetchPendingHostApplications() {
  return apiClient.get('/api/admin/host-applications')
}

export function reviewHostApplication(id, { status, rejectReason }) {
  return apiClient.patch(`/api/admin/host-applications/${id}`, { status, rejectReason })
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
