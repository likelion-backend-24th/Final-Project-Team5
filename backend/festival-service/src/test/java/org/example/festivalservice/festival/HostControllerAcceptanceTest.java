package org.example.festivalservice.festival;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalCategory;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HostControllerAcceptanceTest {

    private static final String ENDPOINT = "/api/host/festivals";
    private static final String CREATE_REQUEST_BODY = """
            {
              "name": "가을 뮤직 페스티벌",
              "description": "설명",
              "startAt": "2026-10-01T10:00:00",
              "endAt": "2026-10-02T22:00:00",
              "location": "서울숲",
              "festivalCategory": "MUSIC",
              "ticketTypes": [
                {"name": "일반", "price": 50000, "quantity": 100},
                {"name": "VIP", "price": 120000, "quantity": 20}
              ]
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @BeforeEach
    void setUp() {
        ticketTypeRepository.deleteAll();
        festivalRepository.deleteAll();
    }

    @Test
    void createFestivalSucceedsWithTicketTypes() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("가을 뮤직 페스티벌")))
                .andExpect(jsonPath("$.data.hostUserId", is(1)))
                .andExpect(jsonPath("$.data.festivalStatus", is("PUBLISHED")))
                .andExpect(jsonPath("$.data.ticketTypes", hasSize(2)))
                .andExpect(jsonPath("$.data.ticketTypes[0].remainQuantity", is(100)));
    }

    @Test
    void createFestivalWithoutHostRoleIsForbidden() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_ROLE")));
    }

    @Test
    void createFestivalWithNonPositiveTicketQuantityIsBadRequest() throws Exception {
        String body = """
                {
                  "name": "가을 뮤직 페스티벌",
                  "startAt": "2026-10-01T10:00:00",
                  "endAt": "2026-10-02T22:00:00",
                  "location": "서울숲",
                  "festivalCategory": "MUSIC",
                  "ticketTypes": [
                    {"name": "일반", "price": 50000, "quantity": 0}
                  ]
                }""";

        mockMvc.perform(post(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFestivalWithoutAuthHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
    }

    @Test
    void listMyFestivalsReturnsOnlyOwnFestivals() throws Exception {
        saveFestival(1L, "내 페스티벌");
        saveFestival(2L, "남의 페스티벌");

        mockMvc.perform(get(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name", is("내 페스티벌")));
    }

    @Test
    void listMyFestivalsWithoutHostRoleIsForbidden() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_ROLE")));
    }

    @Test
    void getMyFestivalDetailIncludesTicketTypes() throws Exception {
        Festival festival = saveFestival(1L, "내 페스티벌");
        ticketTypeRepository.save(TicketType.builder()
                .festival(festival)
                .name("일반")
                .price(50000)
                .totalQuantity(100)
                .remainQuantity(100)
                .build());

        mockMvc.perform(get(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("내 페스티벌")))
                .andExpect(jsonPath("$.data.ticketTypes", hasSize(1)))
                .andExpect(jsonPath("$.data.ticketTypes[0].name", is("일반")));
    }

    @Test
    void getMyFestivalDetailNotFoundReturns404() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/999999")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("FESTIVAL_NOT_FOUND")));
    }

    @Test
    void getMyFestivalDetailOfOtherHostIsForbidden() throws Exception {
        Festival festival = saveFestival(2L, "남의 페스티벌");

        mockMvc.perform(get(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("FORBIDDEN_NOT_OWNER")));
    }

    private Festival saveFestival(Long hostUserId, String name) {
        return festivalRepository.save(Festival.builder()
                .hostUserId(hostUserId)
                .name(name)
                .description("설명")
                .startAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0))
                .endAt(java.time.LocalDateTime.of(2026, 10, 2, 22, 0))
                .location("서울숲")
                .festivalCategory(FestivalCategory.MUSIC)
                .festivalStatus(FestivalStatus.PUBLISHED)
                .build());
    }
}
