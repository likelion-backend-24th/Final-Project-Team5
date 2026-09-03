import axios from 'axios'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401을 받은 요청들이 동시에 재발급을 호출하지 않도록 진행 중인 재발급 Promise를 공유한다.
let refreshPromise = null

function requestReissue() {
  return axios
    .post(`${API_BASE_URL}/api/auth/reissue`, null, { withCredentials: true })
    .then((response) => response.data.data.accessToken)
}

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const { config, response } = error
    if (!response || response.status !== 401 || !config || config._retry) {
      return Promise.reject(error)
    }
    config._retry = true

    if (!refreshPromise) {
      const suppressRedirect = Boolean(config.suppressAuthRedirect)
      refreshPromise = requestReissue()
        .then((newToken) => {
          setAccessToken(newToken)
          return newToken
        })
        .catch((refreshError) => {
          clearAccessToken()
          if (!suppressRedirect) {
            window.location.href = '/login'
          }
          throw refreshError
        })
        .finally(() => {
          refreshPromise = null
        })
    }

    return refreshPromise.then((newToken) => {
      config.headers.Authorization = `Bearer ${newToken}`
      return apiClient(config)
    })
  },
)

export default apiClient
