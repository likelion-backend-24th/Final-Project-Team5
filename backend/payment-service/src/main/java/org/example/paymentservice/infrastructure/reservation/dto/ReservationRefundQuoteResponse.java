package org.example.paymentservice.infrastructure.reservation.dto;

/**
 * Reservation-Service의 환불 견적 응답. 위약금 계산 근거(공연 일정·구매 수량)는 그쪽이 갖고 있고,
 * Payment-Service는 여기서 받은 refundAmount만큼 PortOne에 취소를 넣는다.
 */
public record ReservationRefundQuoteResponse(
        Long reservationId,
        Long userId,
        String paymentId,
        /** 원래 구매한 장수(정보용). 환불 대상 장수는 refundQuantity를 봐야 한다. */
        int quantity,
        int refundedQuantity,
        int refundableQuantity,
        /** 이번 환불에서 실제로 취소할 장수. */
        int refundQuantity,
        boolean refundable,
        String rejectReason,
        int feePercent,
        long grossAmount,
        long feeAmount,
        long refundAmount
) {
}
