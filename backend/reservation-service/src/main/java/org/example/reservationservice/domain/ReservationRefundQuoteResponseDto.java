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
        int quantity,
        int refundedQuantity,
        int refundableQuantity,
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
                quote.refundable(),
                quote.rejectReason(),
                quote.feePercent(),
                quote.grossAmount(),
                quote.feeAmount(),
                quote.refundAmount()
        );
    }
}
