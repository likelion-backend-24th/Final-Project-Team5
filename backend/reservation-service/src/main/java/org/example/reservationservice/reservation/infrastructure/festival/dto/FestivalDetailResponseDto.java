package org.example.reservationservice.infrastructure.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/** GET /api/festivals/{id} 응답에서 예매·입장 검증에 필요한 필드만 뽑아온 것. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FestivalDetailResponseDto(
        Long id,
        Long hostUserId,
        String festivalStatus,
        //현장 입장 검증에서 "아직 공연 시작 전인지" 판단할 때 쓴다.
        LocalDateTime startAt,
        LocalDateTime endAt,
        List<TicketTypeSummary> ticketTypes
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketTypeSummary(Long id, int price) {
    }
}
