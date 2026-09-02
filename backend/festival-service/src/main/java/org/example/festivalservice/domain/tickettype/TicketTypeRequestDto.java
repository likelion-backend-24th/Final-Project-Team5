package org.example.festivalservice.domain.tickettype;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record TicketTypeRequestDto(
        @NotBlank String name,
        @PositiveOrZero int price,
        @Positive int quantity
) {
}
