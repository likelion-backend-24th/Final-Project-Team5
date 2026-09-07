package org.example.paymentservice.domain.payment.dto;

/** 프론트가 PortOne Browser SDK의 requestPayment() 호출에 그대로 쓸 수 있는 값만 담는다. */
public record PaymentPrepareResponse(
        String paymentId,
        String storeId,
        String channelKey,
        long totalAmount
) {
}
