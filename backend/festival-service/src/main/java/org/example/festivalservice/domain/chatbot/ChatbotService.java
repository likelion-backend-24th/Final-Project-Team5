package org.example.festivalservice.domain.chatbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationRequestDto.ChatbotMessageDto;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationResponseDto.Recommendation;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.example.festivalservice.infrastructure.gemini.GeminiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 페스티벌 추천 — 공개 중인 페스티벌 전부를 후보로 프롬프트에 넣고, Gemini가 고른 festivalId를
 * 후보와 대조해 지어낸 ID를 걸러낸 뒤 상세 링크를 붙여 돌려준다. 대화는 서버에 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    static final int MAX_RECOMMENDATIONS = 3;
    static final int MAX_HISTORY = 10;
    private static final int DESCRIPTION_LIMIT = 300;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String SYSTEM_INSTRUCTION = """
            당신은 페스티벌 예매 사이트 FevalGo의 추천 도우미입니다. 사용자의 요청을 읽고, 아래 "후보 목록"에 있는 페스티벌 중에서만
            조건(지역·날짜·가격·카테고리 등)에 맞고 취향에 맞을 가능성이 높은 것을 최대 %d개 골라 추천하세요.

            규칙:
            - 반드시 한국어로, 친근하고 간결하게 답합니다.
            - 후보 목록에 없는 페스티벌은 절대 지어내지 않습니다. recommendations의 festivalId는 반드시 후보 목록의 id여야 합니다.
            - 사용자가 말한 조건은 "필수 조건"입니다. 하나라도 어긋나는 페스티벌은 추천하지 않습니다. 3개를 채우려고 조건에 안 맞는 것을 끼워 넣지 마세요.
              · 날짜: "이번 주말", "이번 달", "10월", "다음 주" 같은 표현은 첫 메시지에 적힌 오늘 날짜·이번 주말 날짜를 기준으로 계산하고,
                페스티벌의 startAt~endAt 기간이 그 날짜와 하루라도 겹쳐야 합니다. 예: 이번 주말이 9/19~9/20이면 10월에 열리는 페스티벌은 제외합니다.
              · 지역: 사용자가 말한 지역(region 또는 locationDetail)에 있어야 합니다. "서울"이면 region이 SEOUL인 것만 해당합니다.
              · 가격: "N원 이하", "무료"는 minPrice 기준으로 판단합니다. minPrice가 null이면 가격을 모르는 것이니 가격 조건이 있을 때는 제외합니다.
              · 카테고리·분위기·동반자(가족·아이·연인·혼자·반려동물 등): description과 category를 근거로 판단합니다.
            - 조건에 맞는 페스티벌이 없으면 솔직하게 없다고 말하고 recommendations를 빈 배열로 둡니다. 억지로 추천하지 마세요.
              대신 reply에서 조건을 조금 넓히면 어떤 선택지가 있는지 한 줄로 제안할 수 있습니다(그 제안은 recommendations에 넣지 않습니다).
            - 사용자가 조건을 거의 말하지 않았으면 서로 다른 카테고리·지역에서 다양하게 고르고, 무엇을 더 알려주면 좋을지 되묻습니다.
            - 각 추천의 reason에는 왜 이 사용자에게 맞는지 한두 문장으로 쓰되, 날짜와 최저가처럼 조건과 직접 관련된 사실을 포함합니다.
            - reply에는 추천 요약이나 추가로 물어볼 만한 질문을 씁니다. 링크는 시스템이 따로 붙이므로 URL을 쓰지 마세요.
            - minPrice는 원(KRW) 단위 최저 티켓 가격이며 0이면 무료, null이면 가격 정보가 없는 것입니다.
            """.formatted(MAX_RECOMMENDATIONS);

    //Gemini responseSchema(OpenAPI 부분집합) — 응답을 {reply, recommendations:[{festivalId, reason}]}로 강제한다
    private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                    "reply", Map.of("type", "STRING"),
                    "recommendations", Map.of(
                            "type", "ARRAY",
                            "items", Map.of(
                                    "type", "OBJECT",
                                    "properties", Map.of(
                                            "festivalId", Map.of("type", "INTEGER"),
                                            "reason", Map.of("type", "STRING")),
                                    "required", List.of("festivalId", "reason")))),
            "required", List.of("reply", "recommendations"));

    /** 프롬프트에 넣는 후보 한 건 — 토큰을 아끼려고 필요한 필드만 담는다. */
    record Candidate(Long id, String name, String category, String region, String locationDetail,
                     String startAt, String endAt, String description, Integer minPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiPayload(String reply, List<GeminiRecommendation> recommendations) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeminiRecommendation(Long festivalId, String reason) {
    }

    private final FestivalRepository festivalRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ChatbotRecommendationResponseDto recommend(ChatbotRecommendationRequestDto request) {
        LocalDateTime now = LocalDateTime.now();
        List<Candidate> candidates = loadCandidates(now);
        if (candidates.isEmpty()) {
            return new ChatbotRecommendationResponseDto(
                    "지금은 추천할 수 있는 진행 예정 페스티벌이 없어요. 새 페스티벌이 공개되면 다시 물어봐 주세요!", List.of());
        }

        String raw = geminiClient.generateJson(SYSTEM_INSTRUCTION, buildMessages(request, candidates, now), RESPONSE_SCHEMA);
        GeminiPayload payload = parse(raw);

        Map<Long, Candidate> byId = candidates.stream()
                .collect(Collectors.toMap(Candidate::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Recommendation> recommendations = new ArrayList<>();
        if (payload.recommendations() != null) {
            for (GeminiRecommendation item : payload.recommendations()) {
                Candidate candidate = item.festivalId() == null ? null : byId.get(item.festivalId());
                if (candidate == null) {
                    log.warn("Gemini가 후보에 없는 festivalId를 추천해 제외한다. festivalId={}", item.festivalId());
                    continue;
                }
                recommendations.add(new Recommendation(candidate.id(), candidate.name(),
                        "/festivals/" + candidate.id(), item.reason()));
                if (recommendations.size() >= MAX_RECOMMENDATIONS) {
                    break;
                }
            }
        }
        String reply = payload.reply() == null || payload.reply().isBlank()
                ? "요청에 맞는 페스티벌을 찾아봤어요." : payload.reply();
        return new ChatbotRecommendationResponseDto(reply, recommendations);
    }

    //공개(PUBLISHED)이고 아직 끝나지 않은 페스티벌만 후보. 종료된 것은 예매할 수 없으니 제외한다.
    List<Candidate> loadCandidates(LocalDateTime now) {
        List<Festival> festivals = festivalRepository.findByFestivalStatusAndEndAtAfter(FestivalStatus.PUBLISHED, now);
        List<Candidate> candidates = new ArrayList<>();
        for (Festival festival : festivals) {
            Integer minPrice = ticketTypeRepository.findByFestivalId(festival.getId()).stream()
                    .map(TicketType::getPrice)
                    .min(Integer::compare)
                    .orElse(null);
            String description = festival.getDescription() == null ? "" : festival.getDescription();
            if (description.length() > DESCRIPTION_LIMIT) {
                description = description.substring(0, DESCRIPTION_LIMIT) + "…";
            }
            candidates.add(new Candidate(
                    festival.getId(),
                    festival.getName(),
                    festival.getFestivalCategory() == null ? null : festival.getFestivalCategory().name(),
                    festival.getRegion() == null ? null : festival.getRegion().name(),
                    festival.getLocationDetail(),
                    festival.getStartAt() == null ? null : festival.getStartAt().format(DATE_TIME),
                    festival.getEndAt() == null ? null : festival.getEndAt().format(DATE_TIME),
                    description,
                    minPrice));
        }
        return candidates;
    }

    //후보 목록은 첫 user 턴에 실어 보내고, 그 뒤에 최근 대화와 이번 질문을 시간순으로 붙인다.
    private List<GeminiClient.Message> buildMessages(ChatbotRecommendationRequestDto request,
                                                     List<Candidate> candidates, LocalDateTime now) {
        String candidatesJson;
        try {
            candidatesJson = objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException e) {
            log.error("후보 목록 직렬화 실패", e);
            throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
        }
        List<GeminiClient.Message> messages = new ArrayList<>();
        messages.add(new GeminiClient.Message("user",
                describeDates(now) + "\n후보 목록(JSON):\n" + candidatesJson));
        messages.add(new GeminiClient.Message("model", "{\"reply\":\"후보 목록을 확인했어요. 어떤 페스티벌을 찾으시나요?\",\"recommendations\":[]}"));

        List<ChatbotMessageDto> history = request.history() == null ? List.of() : request.history();
        int from = Math.max(0, history.size() - MAX_HISTORY);
        for (ChatbotMessageDto message : history.subList(from, history.size())) {
            String role = "assistant".equalsIgnoreCase(message.role()) ? "model" : "user";
            messages.add(new GeminiClient.Message(role, message.content()));
        }
        messages.add(new GeminiClient.Message("user", request.message()));
        return messages;
    }

    //모델이 요일·주말 날짜를 스스로 계산하다 틀리는 일이 있어("이번 주말"에 10월 행사를 추천) 미리 계산해 넣는다.
    //토·일요일에 물어보면 그 주말이 "이번 주말"이고, 평일이면 다가오는 토·일이다.
    public static String describeDates(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        int daysUntilSaturday = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        LocalDate saturday = today.getDayOfWeek() == DayOfWeek.SUNDAY ? today.minusDays(1) : today.plusDays(daysUntilSaturday);
        LocalDate sunday = saturday.plusDays(1);
        return "현재 시각: " + now.format(DATE_TIME) + " (" + KOREAN_DAY.get(today.getDayOfWeek()) + "요일)"
                + "\n이번 주말: " + saturday + "(토) ~ " + sunday + "(일)"
                + "\n다음 주말: " + saturday.plusWeeks(1) + "(토) ~ " + sunday.plusWeeks(1) + "(일)"
                + "\n이번 달: " + today.getYear() + "년 " + today.getMonthValue() + "월";
    }

    private static final Map<DayOfWeek, String> KOREAN_DAY = Map.of(
            DayOfWeek.MONDAY, "월", DayOfWeek.TUESDAY, "화", DayOfWeek.WEDNESDAY, "수", DayOfWeek.THURSDAY, "목",
            DayOfWeek.FRIDAY, "금", DayOfWeek.SATURDAY, "토", DayOfWeek.SUNDAY, "일");

    private GeminiPayload parse(String raw) {
        try {
            return objectMapper.readValue(raw, GeminiPayload.class);
        } catch (JsonProcessingException e) {
            log.warn("Gemini 응답 JSON 파싱 실패. raw={}", raw, e);
            throw new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
        }
    }
}
