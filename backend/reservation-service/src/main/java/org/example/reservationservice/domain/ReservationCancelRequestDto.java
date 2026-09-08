package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** PATCH /internal/v1/reservations/{id}/cancel 요청 — Payment-Service가 결제 실패·취소 시 호출한다. */
public record ReservationCancelRequestDto(
        @NotBlank String paymentId,
        @NotNull CancelReason reasonCode
) {
}
