package org.example.reservationservice.domain;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * PATCH /internal/v1/reservations/{id}/extend-hold 요청 — Payment-Service가 가상계좌 발급 시
 * PortOne이 내려준 입금 기한까지 재고 홀드를 연장하기 위해 호출한다.
 */
public record ReservationExtendHoldRequestDto(
        @NotNull Instant expiresAt
) {
}
