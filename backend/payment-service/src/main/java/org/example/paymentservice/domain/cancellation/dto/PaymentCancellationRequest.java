package org.example.paymentservice.domain.cancellation.dto;

import jakarta.validation.constraints.Min;

/**
 * 환불 요청 본문. quantity를 생략하면 남은 전량 환불이다(가이드 9.3 "전체 취소는 금액 생략").
 * 금액은 클라이언트가 정하지 않는다 — 위약금 계산은 서버(Reservation-Service 견적)만 신뢰한다.
 */
public record PaymentCancellationRequest(
        @Min(1) Integer quantity,
        String reason
) {
}
