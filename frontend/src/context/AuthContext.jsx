import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { fetchMyInfo, login as loginRequest, logout as logoutRequest } from '../api/authApi'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '../api/tokenStore'

const AuthContext = createContext(null)

// accessToken은 sessionStorage에 캐싱돼 있어, 만료 전이라면 새로고침해도 그대로 재사용할 수 있다.
// 그래서 무조건 reissue부터 부르는 대신 /api/users/me를 먼저 시도한다 — 캐싱된 토큰이 아직 유효하면
// 재발급 없이 바로 세션이 복원되고, 토큰이 없거나 만료됐을 때만 client.js의 401 인터셉터가 자동으로
// reissue를 호출한다. 새로고침마다 리프레시 토큰을 로테이션하지 않게 되어, 짧은 시간 안에 새로고침을
// 연달아 해도 리프레시 토큰 재사용 탐지가 오작동해 로그아웃되는 문제가 크게 줄어든다.
async function bootstrapSession() {
  const meResponse = await fetchMyInfo({ suppressAuthRedirect: true })
  return meResponse.data.data
}

/** accessToken/user를 전역 관리하고, 앱 로드 시 refreshToken 쿠키로 세션 복원을 시도한다. */
export function AuthProvider({ children }) {
  const [accessToken, setAccessTokenState] = useState(getAccessToken())
  const [user, setUser] = useState(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => subscribeAccessToken(setAccessTokenState), [])

  // StrictMode(개발 모드)는 마운트 시 이 effect를 두 번 실행한다. ref에 요청 promise를 캐싱해
  // 재실행되어도 bootstrapSession()이 실제로는 한 번만 호출되도록 한다(중복 reissue 경합 방지).
  const bootstrapRequestRef = useRef(null)

  useEffect(() => {
    let cancelled = false

    if (!bootstrapRequestRef.current) {
      bootstrapRequestRef.current = bootstrapSession()
    }

    bootstrapRequestRef.current
      .then((userData) => {
        if (!cancelled) setUser(userData)
      })
      .catch(() => {
        if (!cancelled) {
          clearAccessToken()
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username, password) => {
    const loginResponse = await loginRequest({ username, password })
    setAccessToken(loginResponse.data.data.accessToken)

    const meResponse = await fetchMyInfo()
    setUser(meResponse.data.data)
  }, [])

  // 추가: 닉네임 변경 등 내 정보가 바뀐 뒤, 서버에서 다시 조회해 전역 상태를 갱신한다.
const refreshUser = useCallback(async () => {
  const meResponse = await fetchMyInfo()
  setUser(meResponse.data.data)
}, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } catch {
      // 서버 로그아웃이 실패해도 로컬 세션은 정리한다.
    } finally {
      clearAccessToken()
      setUser(null)
    }
  }, [])

  const value = useMemo(
    () => ({
      accessToken,
      user,
      isLoading,
      isAuthenticated: Boolean(user),
      login,
      logout,
      refreshUser,
    }),
    [accessToken, user, isLoading, login, logout,refreshUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth는 AuthProvider 내부에서만 사용할 수 있습니다.')
  }
  return context
}
