package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/** PATCH /internal/v1/reservations/{id}/confirm 요청 — Payment-Service가 결제 성공 시 호출한다. */
public record ReservationConfirmRequestDto(
        @NotBlank String paymentId,
        @Positive long amount,
        String payMethod,
        @NotNull Instant paidAt
) {
}
