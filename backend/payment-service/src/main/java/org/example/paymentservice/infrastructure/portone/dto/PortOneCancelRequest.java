package org.example.paymentservice.infrastructure.portone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * PortOne V2 "결제 취소"(POST /payments/{paymentId}/cancel) 요청.
 *
 * amount를 보내지 않으면 전액 취소, 보내면 그 금액만 부분 취소된다(가이드 9.3).
 * null을 그대로 실어 보내면 PG가 0원 취소로 해석할 여지가 있어 NON_NULL로 빼고 보낸다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneCancelRequest(
        String storeId,
        Long amount,
        String reason
) {
}
