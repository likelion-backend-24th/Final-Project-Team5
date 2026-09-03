import apiClient from './client'

export function createFestival(payload) {
  return apiClient.post('/api/host/festivals', payload)
}
