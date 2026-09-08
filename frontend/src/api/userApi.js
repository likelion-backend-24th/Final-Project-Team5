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