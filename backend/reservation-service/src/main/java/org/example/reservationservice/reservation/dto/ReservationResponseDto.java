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
        //부분 환불(Story 9)로 이미 환불된 장수. 화면이 "3장 중 1장 환불" 상태를 보여주는 데 쓴다.
        int refundedQuantity,
        //환불 요청은 결제 건 기준(POST /api/payments/{paymentId}/cancellations)이라 화면이 이 값을 알아야 한다.
        String paymentId,
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
                reservation.getRefundedQuantity(),
                reservation.getPaymentId(),
                reservation.getCreatedAt()
        );
    }
}
