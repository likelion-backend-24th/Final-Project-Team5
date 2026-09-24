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

// verificationToken은 인증코드 확인 응답으로 받은 값이다. 서버가 이 토큰으로 인증한 본인의 재설정 요청인지 확인한다.
export function resetPassword({ username, verificationToken, newPassword }) {
  return apiClient.post('/api/auth/reset-password', { username, verificationToken, newPassword })
}

// 이미 아이디/비밀번호로 쓰던 이메일에 소셜 로그인을 연결하겠다는 전환 동의. 성공하면 비밀번호는
// 유지되며 그 자리에서 로그인까지 완료된다(구글 콜백이 넘겨준 token을 그대로 전달).
export function confirmOauthLink(token) {
  return apiClient.post('/api/auth/oauth/confirm-link', { token })
}

export function fetchHelperInvitation(token) {
  return apiClient.get(`/api/auth/helper-invitations/${encodeURIComponent(token)}`, { skipAuthRefresh: true })
}

export function acceptHelperInvitation(token, password, passwordConfirm) {
  return apiClient.post(`/api/auth/helper-invitations/${encodeURIComponent(token)}/accept`,
    { password, passwordConfirm }, { skipAuthRefresh: true })
}
