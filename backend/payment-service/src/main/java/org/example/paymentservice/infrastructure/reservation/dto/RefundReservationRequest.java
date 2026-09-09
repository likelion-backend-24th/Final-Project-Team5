package org.example.paymentservice.infrastructure.reservation.dto;

/** PATCH /internal/v1/reservations/{id}/refund 요청 — 환불 확정 + 재고 복구 단위는 장수다. */
public record RefundReservationRequest(String paymentId, int quantity) {
}
