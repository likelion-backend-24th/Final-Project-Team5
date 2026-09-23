package org.example.festivalservice.domain.festival;

import java.util.List;

/**
 * AI가 채운 초안. 값이 비었거나(null) 프롬프트에서 추론할 수 없던 필드는 그대로 비워서 돌려주고,
 * 실제 기본값(가격·수량 등)은 프론트가 채운다 — Gemini가 근거 없는 숫자를 지어내는 것을 피하기 위해서다.
 */
public record FestivalAiDraftResponseDto(
        String description,
        //프롬프트에서 날짜를 유추할 수 있었을 때만 채워진다("yyyy-MM-dd", 시각 없음 — 프론트가 09:00/18:00을 붙인다).
        //유추할 근거가 없으면 null이고, 그때는 프론트가 오늘 날짜를 기본값으로 채운다.
        String startDate,
        String endDate,
        List<TicketTypeSuggestion> ticketTypeSuggestions
) {
    public record TicketTypeSuggestion(
            String name,
            String description,
            Integer price,
            //STANDING 또는 SEATED. 프롬프트로 좌석 배치까지 추론하긴 어려워 실질적으로 STANDING만 제안한다.
            String ticketMode
    ) {
    }
}
