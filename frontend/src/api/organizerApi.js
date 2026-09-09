import apiClient from './client'

//현장 입장 검증 API. 주최자(HOST) 본인과 주최자가 발급한 도우미(HELPER) 계정이 함께 쓴다.
//도우미가 다룰 수 있는 페스티벌은 JWT에 박혀 있어 Gateway가 X-Festival-Id로 넘겨주므로,
//프론트에서 담당 페스티벌을 따로 실어 보내지 않는다(보내도 Gateway가 지운다).

//스캔한 QR 원문(qrToken)을 검증하고 입장 처리한다 (POST /api/organizer/reservations/verify)
export function verifyQrToken(qrToken) {
  return apiClient.post('/api/organizer/reservations/verify', { qrToken })
}

//QR 스캔이 안 될 때 입장 코드(2-4-4)로 검증하고 입장 처리한다 (POST /api/organizer/reservations/verify-code)
export function verifyCheckInCode(checkInCode) {
  return apiClient.post('/api/organizer/reservations/verify-code', { checkInCode })
}

//총 티켓 수 대비 입장 인원 (GET /api/organizer/reservations/check-in-stats)
export function fetchCheckInStats(festivalId) {
  return apiClient.get('/api/organizer/reservations/check-in-stats', { params: { festivalId } })
}
