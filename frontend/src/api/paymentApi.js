import apiClient from './client'

//PENDING 예매의 결제를 준비한다. 응답의 storeId/channelKey/paymentId/totalAmount를
//PortOne Browser SDK의 requestPayment()에 그대로 사용한다.
export function preparePayment(reservationId) {
  return apiClient.post('/api/payments/prepare', { reservationId })
}

//PortOne 결제창에서 성공 응답을 받은 뒤, 서버가 PortOne을 재조회해 예매를 확정하도록 요청한다.
export function completePayment(paymentId) {
  return apiClient.post(`/api/payments/${paymentId}/complete`)
}

//전체·부분 환불을 요청한다 (POST /api/payments/{paymentId}/cancellations).
//같은 요청을 두 번 눌러도 중복 취소되지 않도록 요청마다 멱등 키를 만들어 보낸다(PortOne 가이드 5.4).
//quantity를 생략하면 남은 전량 환불이다. 금액은 서버가 정하므로 보내지 않는다.
export function requestRefund(paymentId, { quantity, reason } = {}) {
  return apiClient.post(
    `/api/payments/${paymentId}/cancellations`,
    { quantity, reason },
    { headers: { 'Idempotency-Key': crypto.randomUUID() } },
  )
}
