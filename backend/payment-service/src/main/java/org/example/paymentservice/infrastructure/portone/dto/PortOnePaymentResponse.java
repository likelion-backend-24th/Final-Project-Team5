package org.example.paymentservice.infrastructure.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * PortOne V2 "결제 단건 조회"(GET /payments/{paymentId}) 응답.
 *
 * ⚠️ PortOne 공식 문서는 결제 상태별로 다른 응답 타입(ReadyPayment, PaidPayment,
 * FailedPayment, CancelledPayment 등)을 반환하는 유니언 스키마이며, 전체 필드 목록이
 * 문서에 상세히 명시돼 있지 않다. 아래 필드는 상태와 무관하게 공통적으로 쓰이는 값만
 * 담았고, {@link #status()}로 상태를 구분해 사용한다. 7-3·7-4 구현 전에 실제 PortOne
 * 테스트 채널 응답으로 필드명을 한 번 더 검증해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(
        String id,
        String status,
        String transactionId,
        String storeId,
        Channel channel,
        Amount amount,
        String currency,
        Instant requestedAt,
        Instant updatedAt
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Channel(String id, String key, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(long total, long cancelled) {
    }
}
