package org.example.festivalservice.domain.festival;

public record FestivalCancellationRequestResponseDto(Long festivalId, String name, String reason, boolean approved) {}
