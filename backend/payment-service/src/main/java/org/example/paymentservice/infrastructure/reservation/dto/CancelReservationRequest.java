package org.example.paymentservice.infrastructure.reservation.dto;

/** cancelReservation(PATCH /internal/v1/reservations/{id}/cancel) 요청. 사유코드는 팀 계약에서 합의한 3종만 사용한다. */
public record CancelReservationRequest(
        String paymentId,
        String reasonCode
) {
}
