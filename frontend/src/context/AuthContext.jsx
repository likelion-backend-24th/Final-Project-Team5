import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchMyInfo, login as loginRequest } from '../api/authApi'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '../api/tokenStore'

const AuthContext = createContext(null)

/** accessToken/user를 전역 관리하고, 앱 로드 시 refreshToken 쿠키로 세션 복원을 시도한다. */
export function AuthProvider({ children }) {
  const [accessToken, setAccessTokenState] = useState(getAccessToken())
  const [user, setUser] = useState(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => subscribeAccessToken(setAccessTokenState), [])

  useEffect(() => {
    let cancelled = false

    fetchMyInfo({ suppressAuthRedirect: true })
      .then((response) => {
        if (!cancelled) setUser(response.data.data)
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

  //로그인 직후 역할에 따라 보낼 화면이 달라서(도우미는 현장 검증 화면), 조회한 내 정보를 그대로 돌려준다.
  const login = useCallback(async (username, password) => {
    const loginResponse = await loginRequest({ username, password })
    setAccessToken(loginResponse.data.data.accessToken)

    const meResponse = await fetchMyInfo()
    setUser(meResponse.data.data)
    return meResponse.data.data
  }, [])

  // 추가: 닉네임 변경 등 내 정보가 바뀐 뒤, 서버에서 다시 조회해 전역 상태를 갱신한다.
const refreshUser = useCallback(async () => {
  const meResponse = await fetchMyInfo()
  setUser(meResponse.data.data)
}, [])

  const logout = useCallback(() => {
    clearAccessToken()
    setUser(null)
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
