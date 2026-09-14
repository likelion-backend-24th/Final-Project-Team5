package org.example.reservationservice.domain;

import java.time.Instant;

/**
 * GET /internal/v1/reservations/{id}(getReservationForPayment) 응답.
 * Payment-Service의 ReservationForPaymentResponse와 필드명·타입·개수를 정확히 맞춰야 한다
 * (특히 expiresAt은 Instant — LocalDateTime으로 바꾸면 Payment-Service 쪽 파싱이 깨진다).
 */
public record ReservationForPaymentResponseDto(
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
    public static ReservationForPaymentResponseDto from(Reservation reservation) {
        return new ReservationForPaymentResponseDto(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getReservationStatus().name(),
                reservation.totalAmount(),
                reservation.getTicketTypeId(),
                reservation.getQuantity(),
                reservation.getExpiresAt(),
                reservation.getFestivalId(), reservation.getHostUserId(), (long) reservation.getPrice(),
                reservation.getRefundedQuantity(), reservation.getPaymentId()
        );
    }
}
