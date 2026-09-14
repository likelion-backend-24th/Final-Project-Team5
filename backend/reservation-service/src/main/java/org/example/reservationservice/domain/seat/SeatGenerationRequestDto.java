package org.example.reservationservice.domain.seat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Festival-Service → Reservation-Service: POST /internal/v1/seats 요청 바디.
 * festival-service의 SeatGenerationRequestDto와 필드명·타입이 정확히 일치해야 한다.
 */
public record SeatGenerationRequestDto(
        @NotNull Long festivalId,
        @NotNull Long ticketTypeId,
        @NotBlank String zone,
        @NotNull @Positive Integer rows,
        @NotNull @Positive Integer seatsPerRow
) {
}