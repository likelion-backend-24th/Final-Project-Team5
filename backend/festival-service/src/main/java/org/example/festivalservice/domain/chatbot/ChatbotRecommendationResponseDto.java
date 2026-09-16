package org.example.festivalservice.domain.chatbot;

import java.util.List;

public record ChatbotRecommendationResponseDto(
        String reply,
        List<Recommendation> recommendations
) {
    public record Recommendation(
            Long festivalId,
            String name,
            //프론트 상세 라우트("/festivals/{id}") — 카드 링크에 그대로 쓴다
            String link,
            String reason
    ) {
    }
}
