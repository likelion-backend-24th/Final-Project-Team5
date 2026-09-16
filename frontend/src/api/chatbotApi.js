import apiClient from './client'

//AI 페스티벌 추천 — 로그인 필수(게이트웨이가 401). history는 [{role:'user'|'assistant', content}] 오래된 순.
export function requestRecommendations(message, history) {
  return apiClient.post('/api/chatbot/recommendations', { message, history })
}
