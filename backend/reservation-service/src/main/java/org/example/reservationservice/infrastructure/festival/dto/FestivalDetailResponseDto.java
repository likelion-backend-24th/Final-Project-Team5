package org.example.reservationservice.infrastructure.festival.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** GET /api/festivals/{id} 응답에서 예매 검증에 필요한 필드만 뽑아온 것. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FestivalDetailResponseDto(
        Long id,
        String festivalStatus,
        List<TicketTypeSummary> ticketTypes
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TicketTypeSummary(Long id, int price) {
    }
}
