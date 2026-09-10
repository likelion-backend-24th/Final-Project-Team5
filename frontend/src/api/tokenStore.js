// accessToken을 메모리 + sessionStorage에 함께 보관한다. 메모리 전용이면 새로고침마다
// accessToken이 사라져 매번 /api/auth/reissue로 리프레시 토큰을 로테이션해야 하는데, 짧은
// 시간 안에 새로고침을 연달아 하면 로테이션이 겹쳐 리프레시 토큰 재사용 탐지가 오작동해
// 로그인이 풀리는 문제가 있었다. sessionStorage에 캐싱해두면 accessToken이 만료되기 전까지는
// 새로고침해도 재발급을 아예 타지 않는다. sessionStorage는 탭을 닫으면 사라지고 다른 탭과도
// 공유되지 않아, 노출 범위가 훨씬 넓은 localStorage보다는 안전하다.
const STORAGE_KEY = 'accessToken'

function readStoredToken() {
  try {
    return sessionStorage.getItem(STORAGE_KEY)
  } catch {
    return null // 프라이버시 모드 등으로 sessionStorage 접근이 막혀도 메모리 캐시로는 계속 동작한다.
  }
}

let accessToken = readStoredToken()
const listeners = new Set()

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token) {
  accessToken = token
  try {
    if (token) {
      sessionStorage.setItem(STORAGE_KEY, token)
    } else {
      sessionStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // sessionStorage 접근이 막혀 있어도 메모리 캐시(accessToken 변수)는 정상 동작한다.
  }
  listeners.forEach((listener) => listener(accessToken))
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function subscribeAccessToken(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
