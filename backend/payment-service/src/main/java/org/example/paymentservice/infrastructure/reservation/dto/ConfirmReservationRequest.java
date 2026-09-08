package org.example.paymentservice.infrastructure.reservation.dto;

import java.time.Instant;

/**
 * confirmReservation(PATCH /internal/v1/reservations/{id}/confirm) 요청. paymentId가 멱등키다.
 * 필드명은 Reservation-Service의 ReservationConfirmRequestDto와 정확히 맞춰야 한다
 * (JSON 키 기준 매핑이라 amount를 paidAmount로 바꾸면 상대측에서 0으로 역직렬화되어 검증에 실패한다).
 */
public record ConfirmReservationRequest(
        String paymentId,
        long amount,
        String payMethod,
        Instant paidAt
) {
}
