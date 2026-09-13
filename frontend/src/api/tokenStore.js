// accessToken을 메모리 + localStorage에 함께 보관한다. 메모리 전용이면 새로고침마다
// accessToken이 사라져 매번 /api/auth/reissue로 리프레시 토큰을 로테이션해야 하는데, 짧은
// 시간 안에 새로고침을 연달아 하면 로테이션이 겹쳐 리프레시 토큰 재사용 탐지가 오작동해
// 로그인이 풀리는 문제가 있었다. 저장소에 캐싱해두면 accessToken이 만료되기 전까지는
// 새로고침해도 재발급을 아예 타지 않는다.
//
// sessionStorage가 아니라 localStorage를 쓰는 이유: sessionStorage는 탭마다 따로 놀아서
// 한 탭에서 로그아웃해도 다른 탭은 accessToken이 만료될 때까지 로그인 상태로 남고, 새 탭은
// 반대로 비로그인으로 열리는 등 탭마다 로그인 상태가 달라졌다. localStorage는 같은 브라우저의
// 모든 탭이 공유하고 다른 탭의 변경이 storage 이벤트로 전달되므로 로그인/로그아웃이 즉시 동기화된다.
// 보안 측면: XSS에 노출되는 정도는 두 저장소가 같고, 브라우저를 닫아도 남는 점은 리프레시 토큰
// 쿠키(수 일 유지)가 이미 그렇기 때문에 추가로 넓어지는 노출은 없다.
const STORAGE_KEY = 'accessToken'

function readStoredToken() {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    return null // 프라이버시 모드 등으로 저장소 접근이 막혀도 메모리 캐시로는 계속 동작한다.
  }
}

let accessToken = readStoredToken()
const listeners = new Set()
const externalChangeListeners = new Set()

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token) {
  accessToken = token
  try {
    if (token) {
      localStorage.setItem(STORAGE_KEY, token)
    } else {
      localStorage.removeItem(STORAGE_KEY)
    }
  } catch {
    // 저장소 접근이 막혀 있어도 메모리 캐시(accessToken 변수)는 정상 동작한다.
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

/** 다른 탭에서 로그인/로그아웃해 토큰이 바뀌었을 때만 호출된다(이 탭의 setAccessToken은 제외). */
export function subscribeExternalTokenChange(listener) {
  externalChangeListeners.add(listener)
  return () => externalChangeListeners.delete(listener)
}

// storage 이벤트는 "다른 탭"이 localStorage를 바꿨을 때만 발생한다.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key !== STORAGE_KEY && event.key !== null) return
    const next = event.key === null ? null : event.newValue
    if (next === accessToken) return
    accessToken = next
    listeners.forEach((listener) => listener(accessToken))
    externalChangeListeners.forEach((listener) => listener(accessToken))
  })
}
