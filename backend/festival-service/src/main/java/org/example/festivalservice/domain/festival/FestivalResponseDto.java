package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.common.UserLookupClient.UserSummary;
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
        String thumbnailImageUrl,
        List<String> detailImageUrls,
        List<TicketTypeResponseDto> ticketTypes,
        //운영자 반려 사유(REJECTED일 때만 값이 있다)
        String rejectReason,
        LocalDateTime createdAt,
        //주최자 닉네임(auth-service 조회). 운영자 심사 목록에서만 채워지고 그 외에는 null
        String hostNickname
) {
    public static FestivalResponseDto from(Festival festival, List<TicketType> ticketTypes, List<FestivalImage> images) {
        return from(festival, ticketTypes, images, null);
    }

    public static FestivalResponseDto from(Festival festival, List<TicketType> ticketTypes, List<FestivalImage> images,
                                           UserSummary host) {
        String thumbnailImageUrl = images.stream()
                .filter(image -> image.getImageType() == FestivalImageType.THUMBNAIL)
                .map(FestivalImage::getImageUrl)
                .findFirst()
                .orElse(null);
        List<String> detailImageUrls = images.stream()
                .filter(image -> image.getImageType() == FestivalImageType.DETAIL)
                .map(FestivalImage::getImageUrl)
                .toList();

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
                thumbnailImageUrl,
                detailImageUrls,
                ticketTypes.stream().map(TicketTypeResponseDto::from).toList(),
                festival.getRejectReason(),
                festival.getCreatedAt(),
                host == null ? null : host.nickname()
        );
    }
}
