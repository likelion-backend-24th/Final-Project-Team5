package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.time.LocalTime;
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
        FestivalRegion region,
        String locationDetail,
        FestivalCategory festivalCategory,
        FestivalStatus festivalStatus,
        //구역(SEATED 티켓타입) 배치 방식 — 프론트 구역 선택 화면이 이 값으로 전면형/중앙형 렌더링을 분기한다.
        FestivalStageLayout stageLayout,
        LocalTime entryStartTime,
        LocalTime operatingStartTime,
        LocalTime operatingEndTime,
        String thumbnailImageUrl,
        List<String> detailImageUrls,
        List<TicketTypeResponseDto> ticketTypes,
        //운영자 반려 사유(REJECTED일 때만 값이 있다)
        String rejectReason,
        LocalDateTime createdAt,
        //누적 조회수(IP당 24시간 1회 집계). 홈 인기 정렬(sort=viewCount,desc)의 근거
        Long viewCount,
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
                festival.getRegion(),
                festival.getLocationDetail(),
                festival.getFestivalCategory(),
                festival.getFestivalStatus(),
                festival.getStageLayout(),
                festival.getEntryStartTime(),
                festival.getOperatingStartTime(),
                festival.getOperatingEndTime(),
                thumbnailImageUrl,
                detailImageUrls,
                ticketTypes.stream().map(TicketTypeResponseDto::from).toList(),
                festival.getRejectReason(),
                festival.getCreatedAt(),
                festival.getViewCount() == null ? 0L : festival.getViewCount(),
                host == null ? null : host.nickname()
        );
    }
}