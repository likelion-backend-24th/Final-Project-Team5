package org.example.festivalservice.domain.festival;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * GET /internal/v1/festivals/{id}/settlement-context 응답 — payment-service가 정산 원장을 만들 때 필요한 최소 정보.
 */
public record SettlementContextDto(Long festivalId, Long hostUserId, String name, Instant eligibleAt, String status) {
    //정산 가능 시각 = 종료(벽시계) + 24시간. 환불·취소가 정리될 시간을 두고, 타임존을 붙여 절대 시각으로 넘긴다.
    private static final Duration SETTLEMENT_GRACE = Duration.ofHours(24);

    public static SettlementContextDto from(Festival festival, ZoneId timezone) {
        return new SettlementContextDto(
                festival.getId(),
                festival.getHostUserId(),
                festival.getName(),
                festival.getEndAt().atZone(timezone).toInstant().plus(SETTLEMENT_GRACE),
                festival.getFestivalStatus().name());
    }
}
