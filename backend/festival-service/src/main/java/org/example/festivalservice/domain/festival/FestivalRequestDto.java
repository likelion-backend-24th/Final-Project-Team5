package org.example.festivalservice.domain.festival;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.domain.tickettype.TicketTypeRequestDto;

public record FestivalRequestDto(
        @NotBlank String name,
        String description,
        @NotNull @Future LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @NotBlank String location,
        @NotNull FestivalCategory festivalCategory,
        @NotEmpty @Valid List<TicketTypeRequestDto> ticketTypes
) {
}
