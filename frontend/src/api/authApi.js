import apiClient from './client'

export function signup({ name, username, nickname, password, termsAgreed }) {
  return apiClient.post('/api/auth/signup', { name, username, nickname, password, termsAgreed })
}

export function login({ username, password }) {
  return apiClient.post('/api/auth/login', { username, password })
}

export function fetchMyInfo(config) {
  return apiClient.get('/api/users/me', config)
}

export function sendEmailVerificationCode(email) {
  return apiClient.post('/api/auth/email/send', { email })
}

export function verifyEmailVerificationCode({ email, code }) {
  return apiClient.post('/api/auth/email/verify', { email, code })
}

export function logout() {
  return apiClient.post('/api/auth/logout')
}

export function resetPassword({ username, newPassword }) {
  return apiClient.post('/api/auth/reset-password', { username, newPassword })
}
