// PWA 설치 조건(manifest + "fetch 이벤트를 처리하는 서비스워커") 충족용.
// 의도적으로 아무것도 캐싱하지 않는다 — API 응답(로그인 세션·재발급 등)을 건드리면 인증 흐름과
// 충돌할 수 있어서, 여기서는 항상 네트워크 요청을 그대로 통과시킨다. 오프라인 캐싱이 필요해지면
// 그때 정적 자산(JS/CSS/이미지)에 한해 범위를 좁혀 추가할 것 — API 경로는 절대 캐싱하지 말 것.
self.addEventListener('install', (event) => {
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim())
})

self.addEventListener('fetch', () => {
  // 아무 것도 하지 않음 — 브라우저 기본 네트워크 동작을 그대로 사용한다.
})
