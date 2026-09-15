import apiClient from './client'

//SEATED 티켓의 좌석 목록을 조회한다 (GET /api/festivals/{festivalId}/ticket-types/{ticketTypeId}/seats, 인증 불필요)
export function fetchSeats(festivalId, ticketTypeId) {
  return apiClient.get(`/api/festivals/${festivalId}/ticket-types/${ticketTypeId}/seats`)
}
