package org.example.festivalservice.infrastructure.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.chatbot.ChatbotErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Gemini generateContent REST 호출(SDK 미사용). 응답은 responseSchema로 강제한 JSON 문자열 그대로 돌려주고,
 * 파싱은 호출자(ChatbotService)가 맡는다. 무료 티어라 한도 초과(429)가 흔하므로 로그에서 바로 구분되게 남긴다.
 */
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /** Gemini contents 한 턴. role은 "user" 또는 "model". */
    public record Message(String role, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Part(String text) {
    }

    //systemInstruction에는 role이 없으므로 null 필드는 직렬화에서 뺀다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Content(String role, List<Part> parts) {
    }

    private record GenerationConfig(String responseMimeType, Map<String, Object> responseSchema, double temperature) {
    }

    private record GenerateRequest(Content systemInstruction, List<Content> contents, GenerationConfig generationConfig) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GenerateResponse(List<Candidate> candidates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Candidate(Content content) {
    }

    private final RestClient geminiRestClient;

    @Value("${gemini.model:gemini-flash-lite-latest}")
    private String model;

    /**
     * @param systemInstruction 역할·규칙을 담은 시스템 프롬프트
     * @param messages          user/model 턴을 시간순으로 나열한 대화(마지막이 이번 사용자 질문)
     * @param responseSchema    Gemini responseSchema(OpenAPI 부분집합) — 응답 JSON 형태를 강제한다
     * @return 모델이 생성한 JSON 문자열
     */
    public String generateJson(String systemInstruction, List<Message> messages, Map<String, Object> responseSchema) {
        GenerateRequest request = new GenerateRequest(
                new Content(null, List.of(new Part(systemInstruction))),
                messages.stream().map(m -> new Content(m.role(), List.of(new Part(m.text())))).toList(),
                new GenerationConfig(MediaType.APPLICATION_JSON_VALUE, responseSchema, 0.4));
        try {
            GenerateResponse response = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateResponse.class);
            String text = extractText(response);
            if (text == null || text.isBlank()) {
                log.warn("Gemini 응답에 텍스트가 없다. model={}", model);
                throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
            }
            return text;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                log.warn("Gemini 한도 초과(429) — 무료 티어 RPM/RPD를 확인할 것. model={}", model);
            } else {
                log.warn("Gemini 호출 실패. model={}, status={}, body={}", model, e.getStatusCode(),
                        e.getResponseBodyAsString());
            }
            throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
        } catch (RestClientException e) {
            log.warn("Gemini 호출 중 네트워크 오류(타임아웃 등). model={}", model, e);
            throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
        }
    }

    private static String extractText(GenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Content content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return null;
        }
        return content.parts().get(0).text();
    }
}
