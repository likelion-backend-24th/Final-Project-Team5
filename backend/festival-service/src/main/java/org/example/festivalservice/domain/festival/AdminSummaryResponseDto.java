package org.example.festivalservice.domain.festival;

/**
 * 어드민 대시보드 요약 — festival-service가 가진 숫자를 한 번에 내려준다.
 * 각 숫자는 해당 탭의 필터와 같은 기준이다(대시보드 숫자 = 탭에서 보이는 개수).
 */
public record AdminSummaryResponseDto(
        //운영 현황
        long ongoingFestivalCount,
        long scheduledFestivalCount,
        //처리 대기
        long hostApplicationPendingCount,
        long festivalReviewPendingCount,
        long cancellationPendingCount,
        long refundingCount
) {
}