import apiClient from './client'

export function fetchMyHostApplication(config) {
  return apiClient.get('/api/host-applications/me', config)
}

export function submitHostApplication({ introduction, contact }) {
  return apiClient.post('/api/host-applications', { introduction, contact })
}

//본인의 주최자 신청 이력 전체(반려됐다가 재신청한 것까지), 최신순
export function fetchMyHostApplicationHistory(config) {
  return apiClient.get('/api/host-applications/me/history', config)
}
