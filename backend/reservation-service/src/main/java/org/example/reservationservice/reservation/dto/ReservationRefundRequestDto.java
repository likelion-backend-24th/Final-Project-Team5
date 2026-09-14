package org.example.reservationservice.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * PATCH /internal/v1/reservations/{id}/refund 요청 — Payment-Service가 PortOne 취소에 성공한 뒤
 * "이만큼 환불됐다"고 확정 통보할 때 쓴다. 금액이 아니라 수량을 보내는 이유는 재고 복구 단위가 장수이기 때문이다.
 */
public record ReservationRefundRequestDto(
        @NotBlank String paymentId,
        @Min(1) int quantity
) {
}
