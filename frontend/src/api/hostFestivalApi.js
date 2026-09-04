import apiClient from './client'

export function createFestival(payload) {
  return apiClient.post('/api/host/festivals', payload)
}

export function fetchMyFestivals() {
  return apiClient.get('/api/host/festivals')
}

export function fetchMyFestivalDetail(id) {
  return apiClient.get(`/api/host/festivals/${id}`)
}
