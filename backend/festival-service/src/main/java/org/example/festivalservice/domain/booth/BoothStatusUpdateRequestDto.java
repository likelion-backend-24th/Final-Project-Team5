package org.example.festivalservice.domain.booth;

import jakarta.validation.constraints.NotNull;

public record BoothStatusUpdateRequestDto(
        @NotNull BoothStatus boothStatus
) {
}
