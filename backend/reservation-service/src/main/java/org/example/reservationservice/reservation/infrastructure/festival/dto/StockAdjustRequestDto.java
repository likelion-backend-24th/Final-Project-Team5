package org.example.reservationservice.reservation.infrastructure.festival.dto;

/** festival-service의 PATCH /internal/v1/ticket-types/{id}/stock(/restore) 요청 본문과 동일한 모양. */
public record StockAdjustRequestDto(int quantity) {
}
