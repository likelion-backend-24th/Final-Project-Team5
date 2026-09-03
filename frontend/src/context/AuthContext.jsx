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

  const login = useCallback(async (username, password) => {
    const loginResponse = await loginRequest({ username, password })
    setAccessToken(loginResponse.data.data.accessToken)

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
    }),
    [accessToken, user, isLoading, login, logout],
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
