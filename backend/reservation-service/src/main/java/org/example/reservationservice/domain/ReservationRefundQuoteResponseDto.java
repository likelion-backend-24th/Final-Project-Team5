package org.example.reservationservice.domain;

import org.example.reservationservice.domain.refund.RefundQuote;

/**
 * Payment-Service → Reservation-Service 내부 호출: 환불 견적 조회 응답.
 * 금액 계산의 근거(공연 일정·구매 수량)를 가진 쪽이 Reservation-Service라서 여기서 판정해 내려준다.
 */
public record ReservationRefundQuoteResponseDto(
        Long reservationId,
        Long userId,
        String paymentId,
        /** 원래 구매한 장수. */
        int quantity,
        /** 이미 환불된 장수. */
        int refundedQuantity,
        /** 지금 더 환불할 수 있는 장수. */
        int refundableQuantity,
        /** 이번 견적의 환불 대상 장수. quantity(구매 수량)와 헷갈리기 쉬워 따로 둔다. */
        int refundQuantity,
        boolean refundable,
        String rejectReason,
        int feePercent,
        long grossAmount,
        long feeAmount,
        long refundAmount
) {

    public static ReservationRefundQuoteResponseDto of(Reservation reservation, RefundQuote quote) {
        return new ReservationRefundQuoteResponseDto(
                reservation.getId(),
                reservation.getUserId(),
                reservation.getPaymentId(),
                reservation.getQuantity(),
                reservation.getRefundedQuantity(),
                reservation.remainingQuantity(),
                quote.quantity(),
                quote.refundable(),
                quote.rejectReason(),
                quote.feePercent(),
                quote.grossAmount(),
                quote.feeAmount(),
                quote.refundAmount()
        );
    }
}
