package org.example.reservationservice.domain;

import java.time.Instant;
import java.time.LocalDateTime;

/** 참가자 본인용 예매 목록/상세 응답. */
public record ReservationResponseDto(
        Long id,
        Long festivalId,
        Long ticketTypeId,
        int quantity,
        int price,
        long totalAmount,
        ReservationStatus reservationStatus,
        CancelReason cancelReason,
        Instant expiresAt,
        //현장에서 입장 처리된 시각. 참가자 목록 화면이 "예정"과 "입장 완료"를 구분하는 데 쓴다.
        Instant checkedInAt,
        LocalDateTime createdAt
) {
    public static ReservationResponseDto from(Reservation reservation) {
        return new ReservationResponseDto(
                reservation.getId(),
                reservation.getFestivalId(),
                reservation.getTicketTypeId(),
                reservation.getQuantity(),
                reservation.getPrice(),
                reservation.totalAmount(),
                reservation.getReservationStatus(),
                reservation.getCancelReason(),
                reservation.getExpiresAt(),
                reservation.getCheckedInAt(),
                reservation.getCreatedAt()
        );
    }
}
