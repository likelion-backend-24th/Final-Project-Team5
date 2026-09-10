import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { fetchMyInfo, login as loginRequest, logout as logoutRequest } from '../api/authApi'
import { requestReissue } from '../api/client'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '../api/tokenStore'

const AuthContext = createContext(null)

// accessToken은 메모리에만 있어 새로고침 직후엔 항상 비어있다 — /api/users/me를 먼저 불러 401을
// 기다렸다가 reissue하는 대신, reissue부터 바로 시도해 왕복 한 번(무조건 실패할 401 GET)을 줄인다.
// 부수 효과로 새로고침마다 세션 복원에 걸리는 시간이 짧아져, 연속 새로고침 시 이전 reissue 응답이
// 도착하기 전에 다음 새로고침이 겹칠 가능성(레이스)도 함께 줄어든다.
async function bootstrapSession() {
  const newToken = await requestReissue()
  setAccessToken(newToken)
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
