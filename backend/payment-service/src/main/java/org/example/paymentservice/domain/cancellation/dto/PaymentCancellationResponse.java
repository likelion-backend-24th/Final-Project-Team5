package org.example.paymentservice.domain.cancellation.dto;

/** 환불 요청 결과. 취소가 PG에서 비동기로 확정될 수 있어 cancellationStatus를 그대로 내려준다. */
public record PaymentCancellationResponse(
        String paymentId,
        String cancellationId,
        String cancellationStatus,
        long cancelledAmount,
        int cancelledQuantity,
        String paymentStatus
) {
}
