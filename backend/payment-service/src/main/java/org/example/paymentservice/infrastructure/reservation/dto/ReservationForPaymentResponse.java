package org.example.paymentservice.infrastructure.reservation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Reservation-Service의 {@code GET /internal/v1/reservations/{id}}(getReservationForPayment) 응답.
 * "예매↔결제 내부 계약 논의" 문서 3번 섹션에서 합의한 7개 필드.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationForPaymentResponse(
        Long reservationId,
        Long userId,
        String status,
        long totalAmount,
        Long ticketTypeId,
        int quantity,
        Instant expiresAt,
        Long festivalId,
        Long hostUserId,
        Long unitPrice,
        Integer refundedQuantity,
        String paymentId
) {
    public ReservationForPaymentResponse(Long reservationId, Long userId, String status, long totalAmount,
                                         Long ticketTypeId, int quantity, Instant expiresAt) {
        this(reservationId, userId, status, totalAmount, ticketTypeId, quantity, expiresAt,
                null, null, null, null, null);
    }
}
