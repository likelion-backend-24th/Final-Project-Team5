import apiClient from './client'

export function updateNickname(nickname) {
  return apiClient.patch('/api/users/me/nickname', { nickname })
}

export function updatePassword({ currentPassword, newPassword, newPasswordConfirm }) {
  return apiClient.patch('/api/users/me/password', {
    currentPassword,
    newPassword,
    newPasswordConfirm,
  })
}

export function withdrawAccount(password) {
  return apiClient.delete('/api/users/me', { data: { password } })
}
//소셜 로그인 최초 진입 시 이름·닉네임 확정 + 약관 동의(1회)
export function completeProfileSetup({ name, nickname, termsAgreed }) {
  return apiClient.patch('/api/users/me/profile-setup', { name, nickname, termsAgreed })
}
