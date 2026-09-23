package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.chatbot.ChatbotErrorCode;
import org.example.festivalservice.domain.festival.FestivalAiDraftRequestDto;
import org.example.festivalservice.domain.festival.FestivalAiDraftResponseDto;
import org.example.festivalservice.domain.festival.FestivalAiDraftService;
import org.example.festivalservice.domain.festival.FestivalErrorCode;
import org.example.festivalservice.infrastructure.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FestivalAiDraftServiceTest {

    @Mock
    private GeminiClient geminiClient;

    private FestivalAiDraftService festivalAiDraftService;

    @BeforeEach
    void setUp() {
        festivalAiDraftService = new FestivalAiDraftService(geminiClient, new ObjectMapper());
    }

    @Test
    void 정상_응답이면_소개글과_티켓종류_제안을_그대로_돌려준다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"가을 정취를 즐기는 음악 축제입니다.",
                 "ticketTypeSuggestions":[
                   {"name":"입장권","description":"자유 입장","price":30000,"ticketMode":"STANDING"}
                 ]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("가을에 여는 음악 축제"));

        assertThat(response.description()).isEqualTo("가을 정취를 즐기는 음악 축제입니다.");
        assertThat(response.ticketTypeSuggestions()).hasSize(1);
        assertThat(response.ticketTypeSuggestions().get(0).name()).isEqualTo("입장권");
        assertThat(response.ticketTypeSuggestions().get(0).price()).isEqualTo(30000);
        assertThat(response.ticketTypeSuggestions().get(0).ticketMode()).isEqualTo("STANDING");
    }

    @Test
    void 제안이_3개를_넘으면_앞에서부터_3개만_남긴다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","ticketTypeSuggestions":[
                  {"name":"A","ticketMode":"STANDING"},
                  {"name":"B","ticketMode":"STANDING"},
                  {"name":"C","ticketMode":"STANDING"},
                  {"name":"D","ticketMode":"STANDING"}
                ]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제"));

        assertThat(response.ticketTypeSuggestions()).extracting(FestivalAiDraftResponseDto.TicketTypeSuggestion::name)
                .containsExactly("A", "B", "C");
    }

    @Test
    void 스키마를_벗어난_ticketMode는_STANDING으로_되돌린다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","ticketTypeSuggestions":[{"name":"A","ticketMode":"VIP"}]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제"));

        assertThat(response.ticketTypeSuggestions().get(0).ticketMode()).isEqualTo("STANDING");
    }

    @Test
    void 가격_근거가_없으면_null을_그대로_둔다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","ticketTypeSuggestions":[{"name":"A","ticketMode":"STANDING"}]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제"));

        assertThat(response.ticketTypeSuggestions().get(0).price()).isNull();
    }

    @Test
    void 날짜를_유추했으면_startDate_endDate에_그대로_담는다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","startDate":"2026-10-10","endDate":"2026-10-11",
                 "ticketTypeSuggestions":[{"name":"A","ticketMode":"STANDING"}]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("2주 뒤 이틀간 여는 축제"));

        assertThat(response.startDate()).isEqualTo("2026-10-10");
        assertThat(response.endDate()).isEqualTo("2026-10-11");
    }

    @Test
    void 날짜_단서가_없으면_startDate_endDate가_null이다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","ticketTypeSuggestions":[{"name":"A","ticketMode":"STANDING"}]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제"));

        assertThat(response.startDate()).isNull();
        assertThat(response.endDate()).isNull();
    }

    @Test
    void 형식이_잘못된_날짜는_null로_되돌린다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","startDate":"다음 달 초","endDate":"2026-13-40",
                 "ticketTypeSuggestions":[{"name":"A","ticketMode":"STANDING"}]}
                """);

        FestivalAiDraftResponseDto response = festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제"));

        assertThat(response.startDate()).isNull();
        assertThat(response.endDate()).isNull();
    }

    @Test
    void 프롬프트_앞에_오늘_날짜_컨텍스트를_붙여_보낸다() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"description":"설명","ticketTypeSuggestions":[{"name":"A","ticketMode":"STANDING"}]}
                """);

        festivalAiDraftService.generateDraft("HOST", new FestivalAiDraftRequestDto("2주 뒤에 열고 싶어요"));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<java.util.List<GeminiClient.Message>> captor =
                org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        org.mockito.Mockito.verify(geminiClient).generateJson(anyString(), captor.capture(), anyMap());
        String sentText = captor.getValue().get(0).text();
        assertThat(sentText).contains("현재 시각").contains("2주 뒤에 열고 싶어요");
    }

    @Test
    void HOST가_아니면_FORBIDDEN_HOST_ROLE() {
        assertThatThrownBy(() -> festivalAiDraftService.generateDraft(
                "PARTICIPANT", new FestivalAiDraftRequestDto("아무 축제")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(FestivalErrorCode.FORBIDDEN_HOST_ROLE);
    }

    @Test
    void Gemini_응답이_JSON이_아니면_CHATBOT_UNAVAILABLE() {
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("죄송합니다");

        assertThatThrownBy(() -> festivalAiDraftService.generateDraft(
                "HOST", new FestivalAiDraftRequestDto("아무 축제")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
    }
}
