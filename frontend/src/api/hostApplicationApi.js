import apiClient from './client'

export function fetchMyHostApplication(config) {
  return apiClient.get('/api/host-applications/me', config)
}

export function submitHostApplication({ introduction, contact }) {
  return apiClient.post('/api/host-applications', { introduction, contact })
}
