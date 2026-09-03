package org.example.festivalservice.festival;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FestivalControllerAcceptanceTest {

    private static final String ENDPOINT = "/api/festivals";

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
    void listFestivalsReturnsOnlyPublishedWithPaginationMeta() throws Exception {
        saveFestival("공개1", FestivalStatus.PUBLISHED);
        saveFestival("공개2", FestivalStatus.PUBLISHED);
        saveFestival("심사중", FestivalStatus.PENDING);
        saveFestival("반려됨", FestivalStatus.REJECTED);

        mockMvc.perform(get(ENDPOINT).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.pagination.totalItems", is(2)))
                .andExpect(jsonPath("$.meta.pagination.page", is(0)));
    }

    @Test
    void listFestivalsRespectsPageSize() throws Exception {
        for (int i = 0; i < 3; i++) {
            saveFestival("공개" + i, FestivalStatus.PUBLISHED);
        }

        mockMvc.perform(get(ENDPOINT).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.pagination.totalItems", is(3)))
                .andExpect(jsonPath("$.meta.pagination.totalPages", is(2)))
                .andExpect(jsonPath("$.meta.pagination.hasNext", is(true)));
    }

    @Test
    void getFestivalDetailIncludesTicketTypes() throws Exception {
        Festival festival = saveFestival("공개1", FestivalStatus.PUBLISHED);
        ticketTypeRepository.save(TicketType.builder()
                .festival(festival)
                .name("일반")
                .price(50000)
                .totalQuantity(100)
                .remainQuantity(100)
                .build());

        mockMvc.perform(get(ENDPOINT + "/" + festival.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("공개1")))
                .andExpect(jsonPath("$.data.ticketTypes", hasSize(1)))
                .andExpect(jsonPath("$.data.ticketTypes[0].remainQuantity", is(100)));
    }

    @Test
    void getFestivalDetailOfPendingFestivalIsNotFound() throws Exception {
        Festival festival = saveFestival("심사중", FestivalStatus.PENDING);

        mockMvc.perform(get(ENDPOINT + "/" + festival.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("FESTIVAL_NOT_FOUND")));
    }

    @Test
    void getFestivalDetailOfMissingFestivalIsNotFound() throws Exception {
        mockMvc.perform(get(ENDPOINT + "/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("FESTIVAL_NOT_FOUND")));
    }

    private Festival saveFestival(String name, FestivalStatus status) {
        return festivalRepository.save(Festival.builder()
                .hostUserId(1L)
                .name(name)
                .description("설명")
                .startAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0))
                .endAt(java.time.LocalDateTime.of(2026, 10, 2, 22, 0))
                .location("서울숲")
                .festivalCategory(FestivalCategory.MUSIC)
                .festivalStatus(status)
                .build());
    }
}
