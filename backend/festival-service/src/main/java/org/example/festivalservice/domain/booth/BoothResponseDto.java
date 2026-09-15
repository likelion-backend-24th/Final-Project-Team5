package org.example.festivalservice.domain.booth;

import java.time.LocalDateTime;

public record BoothResponseDto(
        Long id,
        Long festivalId,
        Long hostUserId,
        String title,
        String description,
        String boothHostName,
        String imageUrl,
        BoothStatus boothStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BoothResponseDto from(Booth booth) {
        return new BoothResponseDto(
                booth.getId(),
                booth.getFestival().getId(),
                booth.getHostUserId(),
                booth.getTitle(),
                booth.getDescription(),
                booth.getBoothHostName(),
                booth.getImageUrl(),
                booth.getBoothStatus(),
                booth.getCreatedAt(),
                booth.getUpdatedAt()
        );
    }
}
