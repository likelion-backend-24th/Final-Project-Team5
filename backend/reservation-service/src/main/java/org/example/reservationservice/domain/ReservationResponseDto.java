package org.example.reservationservice.domain;

import java.time.Instant;
import java.time.LocalDateTime;

/** 참가자 본인용 예매 목록/상세 응답. */
public record ReservationResponseDto(
        Long id,
        Long ticketTypeId,
        int quantity,
        int price,
        long totalAmount,
        ReservationStatus reservationStatus,
        CancelReason cancelReason,
        Instant expiresAt,
        LocalDateTime createdAt
) {
    public static ReservationResponseDto from(Reservation reservation) {
        return new ReservationResponseDto(
                reservation.getId(),
                reservation.getTicketTypeId(),
                reservation.getQuantity(),
                reservation.getPrice(),
                reservation.totalAmount(),
                reservation.getReservationStatus(),
                reservation.getCancelReason(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt()
        );
    }
}
