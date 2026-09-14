package org.example.festivalservice.domain.festival;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FestivalCancellationRequestDto(@NotBlank @Size(max = 500) String reason) {}
