package org.example.festivalservice.domain.tickettype;

import jakarta.validation.constraints.Positive;

public record TicketTypeDeductRequestDto(
        @Positive int quantity
) {
}
