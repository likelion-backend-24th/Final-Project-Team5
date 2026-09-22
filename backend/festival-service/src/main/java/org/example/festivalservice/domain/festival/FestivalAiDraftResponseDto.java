package org.example.festivalservice.domain.festival;

import java.util.List;

/**
 * AI가 채운 초안. 값이 비었거나(null) 프롬프트에서 추론할 수 없던 필드는 그대로 비워서 돌려주고,
 * 실제 기본값(가격·수량 등)은 프론트가 채운다 — Gemini가 근거 없는 숫자를 지어내는 것을 피하기 위해서다.
 */
public record FestivalAiDraftResponseDto(
        String description,
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
