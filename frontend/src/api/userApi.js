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

//탈퇴 동의 문구("회원 탈퇴에 동의합니다")를 그대로 보낸다 — 서버가 정확히 일치하는지 다시 검사한다
export function withdrawAccount(confirmation) {
  return apiClient.delete('/api/users/me', { data: { confirmation } })
}
//소셜 로그인 최초 진입 시 이름·닉네임 확정 + 약관 동의(1회)
export function completeProfileSetup({ name, nickname, termsAgreed }) {
  return apiClient.patch('/api/users/me/profile-setup', { name, nickname, termsAgreed })
}
