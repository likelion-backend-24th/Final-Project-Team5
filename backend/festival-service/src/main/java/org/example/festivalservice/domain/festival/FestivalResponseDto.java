package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeResponseDto;

public record FestivalResponseDto(
        Long id,
        Long hostUserId,
        String name,
        String description,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String location,
        FestivalCategory festivalCategory,
        FestivalStatus festivalStatus,
        List<String> imageUrls,
        List<TicketTypeResponseDto> ticketTypes
) {
    public static FestivalResponseDto from(Festival festival, List<TicketType> ticketTypes, List<FestivalImage> images) {
        return new FestivalResponseDto(
                festival.getId(),
                festival.getHostUserId(),
                festival.getName(),
                festival.getDescription(),
                festival.getStartAt(),
                festival.getEndAt(),
                festival.getLocation(),
                festival.getFestivalCategory(),
                festival.getFestivalStatus(),
                images.stream().map(FestivalImage::getImageUrl).toList(),
                ticketTypes.stream().map(TicketTypeResponseDto::from).toList()
        );
    }
}
