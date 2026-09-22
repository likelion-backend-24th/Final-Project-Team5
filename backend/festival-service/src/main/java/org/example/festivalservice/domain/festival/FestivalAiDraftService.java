package org.example.festivalservice.domain.festival;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.chatbot.ChatbotErrorCode;
import org.example.festivalservice.infrastructure.gemini.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 페스티벌 등록 폼 "AI로 초안 채우기" — 호스트가 쓴 짧은 설명을 소개글·티켓 종류 제안으로 부풀린다.
 * 좌석 배치·일정처럼 텍스트만으로 추론하기 위험한 항목은 스코프에서 뺀다. 생성 결과는 폼에 채워질 뿐
 * 절대 그대로 등록되지 않는다 — 호스트가 반드시 검토·수정 후 직접 제출한다.
 */
@Service
@RequiredArgsConstructor
public class FestivalAiDraftService {

    private static final Logger log = LoggerFactory.getLogger(FestivalAiDraftService.class);
    private static final String HOST_ROLE = "HOST";
    static final int MAX_TICKET_TYPE_SUGGESTIONS = 3;

    private static final String SYSTEM_INSTRUCTION = """
            당신은 페스티벌 예매 사이트 FevalGo에서 주최자의 페스티벌 등록을 돕는 작성 도우미입니다.
            주최자가 입력한 짧은 설명을 바탕으로 구매자에게 보여줄 소개글과, 판매할 티켓 종류를 제안하세요.

            규칙:
            - 반드시 한국어로 답합니다.
            - description은 구매자가 읽을 소개글입니다. 2~4문장, 300자 이내로 자연스럽게 씁니다. 과장되거나 확인할 수 없는
              사실(가수 이름, 정확한 참가자 수 등)을 지어내지 않습니다. 입력이 너무 짧아 알 수 없는 내용은 일반적인 문구로 채웁니다.
            - ticketTypeSuggestions는 최대 %d개입니다. 입력에서 티켓 종류를 유추할 수 없으면 일반적인 "입장권" 하나만 제안합니다.
            - 각 제안의 name은 10자 이내로 짧게, description은 있다면 20자 이내 한 줄 설명입니다.
            - price는 입력에 가격 단서가 있을 때만 채우고, 전혀 근거가 없으면 null로 둡니다(임의로 지어내지 않습니다).
            - ticketMode는 특별한 이유가 없으면 항상 "STANDING"으로 답합니다. 지정석이 명확히 언급된 경우에만 "SEATED"를 씁니다.
            """.formatted(MAX_TICKET_TYPE_SUGGESTIONS);

    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "description", Map.of("type", "STRING"),
                    "ticketTypeSuggestions", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "name", Map.of("type", "STRING"),
                                            "description", Map.of("type", "STRING"),
                                            "price", Map.of("type", "INTEGER", "nullable", true),
                                            "ticketMode", Map.of("type", "STRING", "enum", List.of("STANDING", "SEATED"))),
                                    "required", List.of("name", "ticketMode")))),
            "required", List.of("description", "ticketTypeSuggestions"));

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiPayload(String description, List<GeminiTicketTypeSuggestion> ticketTypeSuggestions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiTicketTypeSuggestion(String name, String description, Integer price, String ticketMode) {
    }

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public FestivalAiDraftResponseDto generateDraft(String role, FestivalAiDraftRequestDto request) {
        if (!HOST_ROLE.equals(role)) {
            throw new ApiException(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
        }

        String raw = geminiClient.generateJson(
                SYSTEM_INSTRUCTION,
                List.of(new GeminiClient.Message("user", request.prompt())),
                RESPONSE_SCHEMA);
        GeminiPayload payload = parse(raw);

        List<FestivalAiDraftResponseDto.TicketTypeSuggestion> suggestions = payload.ticketTypeSuggestions() == null
                ? List.of()
                : payload.ticketTypeSuggestions().stream()
                        .limit(MAX_TICKET_TYPE_SUGGESTIONS)
                        .map(item -> new FestivalAiDraftResponseDto.TicketTypeSuggestion(
                                item.name(), item.description(), item.price(), normalizeTicketMode(item.ticketMode())))
                        .toList();

        return new FestivalAiDraftResponseDto(payload.description(), suggestions);
    }

    //모델이 스키마의 enum을 어겨도(드물지만) 프론트가 모르는 값을 받지 않도록 STANDING/SEATED 외에는 기본값으로 되돌린다.
    private String normalizeTicketMode(String ticketMode) {
        return "SEATED".equals(ticketMode) ? "SEATED" : "STANDING";
    }

    private GeminiPayload parse(String raw) {
        try {
            return objectMapper.readValue(raw, GeminiPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini 초안 응답 JSON 파싱 실패. raw={}", raw, e);
            throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
        }
    }
}
