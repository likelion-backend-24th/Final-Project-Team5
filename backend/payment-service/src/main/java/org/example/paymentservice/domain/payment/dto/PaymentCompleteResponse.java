package org.example.paymentservice.domain.payment.dto;

public record PaymentCompleteResponse(
        String paymentId,
        String status,
        long totalAmount
) {
}
