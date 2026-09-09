import apiClient from './client'

//참가자가 티켓 예매를 신청한다 (POST /api/reservations)
export function createReservation({ festivalId, ticketTypeId, quantity }) {
  return apiClient.post('/api/reservations', { festivalId, ticketTypeId, quantity })
}

//참가자 본인의 예매 목록을 조회한다 (GET /api/reservations/me)
export function fetchMyReservations() {
  return apiClient.get('/api/reservations/me')
}

//참가자 본인의 예매 상세를 조회한다 (GET /api/reservations/{id})
export function fetchReservationDetail(id) {
  return apiClient.get(`/api/reservations/${id}`)
}

//참가자 본인의 확정된 예매에 대해 입장용 QR을 발급받는다 (GET /api/reservations/{id}/qr)
export function fetchReservationQr(id) {
  return apiClient.get(`/api/reservations/${id}/qr`)
}

//참가자 본인이 결제대기 중인 예매를 직접 취소한다 (PATCH /api/reservations/{id}/cancel)
export function cancelReservation(id) {
  return apiClient.patch(`/api/reservations/${id}/cancel`)
}

//환불 버튼을 누르기 전에 위약금·환급 예상액을 미리 확인한다 (GET /api/reservations/{id}/refund-quote)
export function fetchRefundQuote(id, quantity) {
  return apiClient.get(`/api/reservations/${id}/refund-quote`, { params: { quantity } })
}
