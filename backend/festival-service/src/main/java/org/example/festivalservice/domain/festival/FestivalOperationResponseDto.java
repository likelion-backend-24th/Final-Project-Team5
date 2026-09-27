package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;

//어드민 운영 현황 목록 한 줄
public record FestivalOperationResponseDto(
        Long id,
        String name,
        Long hostUserId,
        //주최자 닉네임(auth-service 조회, 실패하면 null)
        String hostNickname,
        FestivalCategory festivalCategory,
        LocalDateTime startAt,
        LocalDateTime endAt,
        //원래 상태(PUBLISHED, CLOSED, CANCELLATION_PENDING, CANCELLED)
        FestivalStatus festivalStatus,
        //운영 상태(SCHEDULED, ONGOING, CLOSED, CANCELLED) — 날짜와 festivalStatus로 계산
        String operationStatus,
        String thumbnailImageUrl,
        //티켓 종류 전체 합계
        long totalQuantity,
        //판매 수량 = 전체 - 남은 수량(환불분 재고 복구는 매일 19시 배치 반영)
        long soldQuantity,
        //판매율(%) — 0~100, 전체 수량이 0이면 0
        int saleRate
) {
}