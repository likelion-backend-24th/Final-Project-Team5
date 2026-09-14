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

// 주최자가 요청한 행사 취소 승인 대상을 조회한다.
export function fetchCancellationRequests() {
  return apiClient.get('/api/admin/festivals/cancellation-requests')
}

// 위약금 없는 전액 환불 배치를 시작하도록 행사 취소를 승인한다.
export function approveFestivalCancellation(festivalId) {
  return apiClient.post(`/api/admin/festivals/${festivalId}/approve-cancellation`, { reason: '' })
}
