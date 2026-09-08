package org.example.paymentservice.infrastructure.reservation.dto;

import java.time.Instant;

/**
 * extendReservationHold(PATCH /internal/v1/reservations/{id}/extend-hold) 요청.
 * 가상계좌 발급 시 10분 고정 홀드 대신 PortOne이 내려준 입금 기한까지 재고를 보유시킨다
 * ("예매↔결제 내부 계약 논의" 11번 섹션, 2026-09-07 팀 결정).
 */
public record ExtendReservationHoldRequest(
        Instant expiresAt
) {
}
