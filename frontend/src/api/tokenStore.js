// 메모리에만 accessToken을 보관한다 (새로고침 시 /api/auth/reissue로 세션 복원).
let accessToken = null
const listeners = new Set()

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token) {
  accessToken = token
  listeners.forEach((listener) => listener(accessToken))
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function subscribeAccessToken(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
