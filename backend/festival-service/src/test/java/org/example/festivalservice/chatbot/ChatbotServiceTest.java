package org.example.festivalservice.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.common.exception.ApiException;
import org.example.festivalservice.domain.chatbot.ChatbotErrorCode;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationRequestDto;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationRequestDto.ChatbotMessageDto;
import org.example.festivalservice.domain.chatbot.ChatbotRecommendationResponseDto;
import org.example.festivalservice.domain.chatbot.ChatbotService;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalCategory;
import org.example.festivalservice.domain.festival.FestivalRegion;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.example.festivalservice.infrastructure.gemini.GeminiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatbotServiceTest {

    @Mock
    private FestivalRepository festivalRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private GeminiClient geminiClient;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        chatbotService = new ChatbotService(festivalRepository, ticketTypeRepository, geminiClient, new ObjectMapper());
    }

    private Festival festival(long id, String name) {
        return Festival.builder()
                .id(id)
                .name(name)
                .description("설명 " + name)
                .festivalCategory(FestivalCategory.MUSIC)
                .region(FestivalRegion.SEOUL)
                .locationDetail("올림픽공원")
                .startAt(LocalDateTime.of(2026, 10, 3, 18, 0))
                .endAt(LocalDateTime.of(2026, 10, 3, 22, 0))
                .festivalStatus(FestivalStatus.PUBLISHED)
                .build();
    }

    private TicketType ticket(int price) {
        return TicketType.builder().price(price).build();
    }

    private void givenCandidates(Festival... festivals) {
        when(festivalRepository.findByFestivalStatusAndEndAtAfter(eq(FestivalStatus.PUBLISHED), any()))
                .thenReturn(List.of(festivals));
    }

    @Test
    void 후보와_최저가가_프롬프트에_들어가고_추천에_링크가_붙는다() {
        givenCandidates(festival(1L, "서울 재즈 페스티벌"), festival(2L, "부산 록 페스티벌"));
        when(ticketTypeRepository.findByFestivalId(1L)).thenReturn(List.of(ticket(55000), ticket(30000)));
        when(ticketTypeRepository.findByFestivalId(2L)).thenReturn(List.of());
        when(geminiClient.generateJson(anyString(), anyList(), anyMap()))
                .thenReturn("{\"reply\":\"재즈 어떠세요?\",\"recommendations\":[{\"festivalId\":1,\"reason\":\"서울이고 저렴해요\"}]}");

        ChatbotRecommendationResponseDto response = chatbotService.recommend(
                new ChatbotRecommendationRequestDto("서울에서 싼 음악 페스티벌", List.of()));

        assertThat(response.reply()).isEqualTo("재즈 어떠세요?");
        assertThat(response.recommendations()).hasSize(1);
        assertThat(response.recommendations().get(0).festivalId()).isEqualTo(1L);
        assertThat(response.recommendations().get(0).name()).isEqualTo("서울 재즈 페스티벌");
        assertThat(response.recommendations().get(0).link()).isEqualTo("/festivals/1");
        assertThat(response.recommendations().get(0).reason()).isEqualTo("서울이고 저렴해요");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiClient).generateJson(anyString(), captor.capture(), anyMap());
        List<GeminiClient.Message> messages = captor.getValue();
        String candidatesTurn = messages.get(0).text();
        assertThat(candidatesTurn).contains("\"id\":1").contains("서울 재즈 페스티벌").contains("\"minPrice\":30000");
        assertThat(candidatesTurn).contains("\"id\":2").contains("\"minPrice\":null");
        assertThat(messages.get(messages.size() - 1).text()).isEqualTo("서울에서 싼 음악 페스티벌");
    }

    @Test
    void 후보에_없는_festivalId는_버리고_최대_3건만_남긴다() {
        givenCandidates(festival(1L, "A"), festival(2L, "B"), festival(3L, "C"), festival(4L, "D"));
        when(ticketTypeRepository.findByFestivalId(any())).thenReturn(List.of());
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("""
                {"reply":"ok","recommendations":[
                  {"festivalId":999,"reason":"지어낸 것"},
                  {"festivalId":1,"reason":"a"},
                  {"festivalId":2,"reason":"b"},
                  {"festivalId":3,"reason":"c"},
                  {"festivalId":4,"reason":"d"}
                ]}
                """);

        ChatbotRecommendationResponseDto response = chatbotService.recommend(
                new ChatbotRecommendationRequestDto("아무거나", null));

        assertThat(response.recommendations()).extracting(ChatbotRecommendationResponseDto.Recommendation::festivalId)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void 후보가_없으면_Gemini를_호출하지_않고_고정_답변을_돌려준다() {
        givenCandidates();

        ChatbotRecommendationResponseDto response = chatbotService.recommend(
                new ChatbotRecommendationRequestDto("추천해줘", List.of()));

        assertThat(response.recommendations()).isEmpty();
        assertThat(response.reply()).contains("추천할 수 있는");
        verifyNoInteractions(geminiClient);
    }

    @Test
    void 대화_이력은_최근_10개만_역할을_바꿔_전달한다() {
        givenCandidates(festival(1L, "A"));
        when(ticketTypeRepository.findByFestivalId(any())).thenReturn(List.of());
        when(geminiClient.generateJson(anyString(), anyList(), anyMap()))
                .thenReturn("{\"reply\":\"ok\",\"recommendations\":[]}");
        List<ChatbotMessageDto> history = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            history.add(new ChatbotMessageDto(i % 2 == 0 ? "user" : "assistant", "m" + i));
        }

        chatbotService.recommend(new ChatbotRecommendationRequestDto("마지막 질문", history));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GeminiClient.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(geminiClient).generateJson(anyString(), captor.capture(), anyMap());
        List<GeminiClient.Message> messages = captor.getValue();
        //후보 턴 2개(user/model) + 이력 10개 + 이번 질문 1개
        assertThat(messages).hasSize(13);
        assertThat(messages.get(2).text()).isEqualTo("m2");
        assertThat(messages.get(3).role()).isEqualTo("model");
        assertThat(messages.get(12).text()).isEqualTo("마지막 질문");
    }

    @Test
    void Gemini_응답이_JSON이_아니면_CHATBOT_UNAVAILABLE() {
        givenCandidates(festival(1L, "A"));
        when(ticketTypeRepository.findByFestivalId(any())).thenReturn(List.of());
        when(geminiClient.generateJson(anyString(), anyList(), anyMap())).thenReturn("죄송합니다, 추천할 수 없어요");

        assertThatThrownBy(() -> chatbotService.recommend(new ChatbotRecommendationRequestDto("q", List.of())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
    }

    @Test
    void Gemini_클라이언트_장애는_그대로_CHATBOT_UNAVAILABLE로_전달된다() {
        givenCandidates(festival(1L, "A"));
        when(ticketTypeRepository.findByFestivalId(any())).thenReturn(List.of());
        when(geminiClient.generateJson(anyString(), anyList(), anyMap()))
                .thenThrow(new ApiException(ChatbotErrorCode.CHATBOT_UNAVAILABLE));

        assertThatThrownBy(() -> chatbotService.recommend(new ChatbotRecommendationRequestDto("q", List.of())))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ChatbotErrorCode.CHATBOT_UNAVAILABLE);
    }
}
