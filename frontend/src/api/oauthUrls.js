// 카카오/구글 로그인은 별도 API 호출 없이 해당 authorize URL로 이동시키기만 하면 된다.
// 성공 시 백엔드가 쿠키를 심고 프론트 홈페이지로 리다이렉트한다.

export function getKakaoLoginUrl() {
  const clientId = import.meta.env.VITE_KAKAO_CLIENT_ID
  const redirectUri = import.meta.env.VITE_KAKAO_REDIRECT_URI
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
  })
  return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`
}

export function getGoogleLoginUrl() {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID
  const redirectUri = import.meta.env.VITE_GOOGLE_REDIRECT_URI
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'email profile',
  })
  return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`
}
