package org.example.festivalservice.domain.chatbot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatbotRecommendationRequestDto(
        @NotBlank @Size(max = 500) String message,
        //이전 대화(오래된 것부터). 서버는 최근 10개만 프롬프트에 넣는다.
        @Valid List<ChatbotMessageDto> history
) {
    public record ChatbotMessageDto(
            //"user" 또는 "assistant"
            @NotBlank String role,
            @NotBlank @Size(max = 2000) String content
    ) {
    }
}
