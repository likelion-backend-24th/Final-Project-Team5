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
