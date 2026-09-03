import apiClient from './client'

export function signup({ name, username, nickname, password }) {
  return apiClient.post('/api/auth/signup', { name, username, nickname, password })
}

export function login({ username, password }) {
  return apiClient.post('/api/auth/login', { username, password })
}

export function fetchMyInfo(config) {
  return apiClient.get('/api/users/me', config)
}
