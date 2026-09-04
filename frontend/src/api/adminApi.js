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

export function reviewFestival(id, { decision }) {
  return apiClient.patch(`/api/admin/festivals/${id}`, { decision })
}
