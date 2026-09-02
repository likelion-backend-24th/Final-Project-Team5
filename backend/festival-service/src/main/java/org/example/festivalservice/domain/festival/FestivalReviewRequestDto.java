package org.example.festivalservice.domain.festival;

import jakarta.validation.constraints.NotNull;

public record FestivalReviewRequestDto(
        @NotNull FestivalStatus decision
) {
}
