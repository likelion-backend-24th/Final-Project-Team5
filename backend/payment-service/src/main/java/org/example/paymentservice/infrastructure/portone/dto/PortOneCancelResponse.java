package org.example.paymentservice.infrastructure.portone.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * PortOne V2 결제 취소 응답. 취소 1건 정보가 cancellation에 담겨 온다.
 *
 * 이 응답만으로 상태를 확정하지 않는다 — 가이드 9.1대로 취소 후 결제 단건을 다시 조회해
 * 전체 cancellations 목록으로 로컬을 맞춘다. 여기서는 cancellationId를 얻는 용도가 크다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneCancelResponse(
        PortOnePaymentResponse.Cancellation cancellation
) {
}
