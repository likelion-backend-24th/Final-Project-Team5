package org.example.festivalservice.domain.chatbot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatbotErrorCode implements ErrorCode {

    //Gemini 한도 초과·타임아웃·응답 파싱 실패를 전부 하나로 묶는다 — 사용자에겐 "잠시 후 다시" 외에 다른 안내가 없다
    CHATBOT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천 서비스를 잠시 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String message;
}
