package org.example.festivalservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.dto.ApiResponse;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationRequestDto;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationResponseDto;
import org.example.festivalservice.domain.chatbot.ChatbotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 페스티벌 추천 챗봇 — 로그인 필수(Gateway가 /api/chatbot/**를 공개 경로에 두지 않는다).
 * X-User-Id는 인증된 요청인지 확인하는 용도로만 받고, 프롬프트에는 사용자 정보를 넣지 않는다.
 */
@RestController
@RequestMapping("/api/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/recommendations")
    public ResponseEntity<ApiResponse<ChatbotRecommendationResponseDto>> recommend(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ChatbotRecommendationRequestDto request) {
        return ResponseEntity.ok(ApiResponse.success("페스티벌 추천", chatbotService.recommend(request)));
    }
}
