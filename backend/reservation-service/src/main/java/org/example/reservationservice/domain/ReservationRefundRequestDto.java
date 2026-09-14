package org.example.reservationservice.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * PATCH /internal/v1/reservations/{id}/refund 요청 — Payment-Service가 PortOne 취소에 성공한 뒤
 * "이만큼 환불됐다"고 확정 통보할 때 쓴다.
 * STANDING 예매는 quantity만 채운다(재고 복구 단위가 장수). SEATED 예매는 환불 대상 특정 좌석을
 * seatIds로 지정한다 — quantity는 이때 seatIds.size()와 같은 값이어야 한다(검증은 Service에서).
 */
public record ReservationRefundRequestDto(
        @NotBlank String paymentId,
        @Min(1) int quantity,
        //SEATED 예매 환불 시에만 채운다. STANDING이면 null.
        List<Long> seatIds
) {
}