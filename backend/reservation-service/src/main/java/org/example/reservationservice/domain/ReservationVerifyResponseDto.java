package org.example.reservationservice.domain;

import java.time.Instant;

/** 주최자용 QR 검증 결과. 신원 확인 목적이 아니라 입장 처리를 위한 최소 정보만 담는다. */
public record ReservationVerifyResponseDto(
        Long reservationId,
        Long ticketTypeId,
        int quantity,
        Instant checkedInAt
) {
    public static ReservationVerifyResponseDto from(Reservation reservation) {
        return new ReservationVerifyResponseDto(
                reservation.getId(),
                reservation.getTicketTypeId(),
                reservation.getQuantity(),
                reservation.getCheckedInAt()
        );
    }
}
