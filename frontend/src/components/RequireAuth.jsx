import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

/** 로그인하지 않은 사용자가 접근하면 로그인 화면으로 보낸다. 세션 복원 중에는 아무것도 렌더링하지 않는다. */
function RequireAuth({ children }) {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) return null

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return children
}

export default RequireAuth
