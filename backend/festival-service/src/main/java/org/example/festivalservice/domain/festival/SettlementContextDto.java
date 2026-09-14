package org.example.festivalservice.domain.festival;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

public record SettlementContextDto(Long festivalId, Long hostUserId, String name, Instant eligibleAt, String status) {
    public static SettlementContextDto from(Festival festival, ZoneId timezone) {
        return new SettlementContextDto(
            festival.getId(),
            festival.getHostUserId(),
            festival.getName(),
            festival.getEndAt().atZone(timezone).toInstant().plus(Duration.ofHours(24)),
            festival.getFestivalStatus().name()
        );
    }
}
