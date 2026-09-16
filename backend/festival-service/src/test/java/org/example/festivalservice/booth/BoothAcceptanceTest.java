package org.example.festivalservice.booth;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.domain.booth.Booth;
import org.example.festivalservice.domain.booth.BoothRepository;
import org.example.festivalservice.domain.booth.BoothStatus;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalCategory;
import org.example.festivalservice.domain.festival.FestivalRegion;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BoothAcceptanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private BoothRepository boothRepository;

    private Long festivalId;

    @BeforeEach
    void setUp() {
        boothRepository.deleteAll();
        festivalRepository.deleteAll();
        festivalId = festivalRepository.save(Festival.builder()
                .hostUserId(1L)
                .name("가을 뮤직 페스티벌")
                .description("설명")
                .startAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0))
                .endAt(java.time.LocalDateTime.of(2026, 10, 2, 22, 0))
                .region(FestivalRegion.SEOUL)
                .locationDetail("서울숲")
                .festivalCategory(FestivalCategory.MUSIC)
                .festivalStatus(FestivalStatus.PUBLISHED)
                .build()).getId();
    }

    private String createRequestBody() {
        return """
                {
                  "festivalId": %d,
                  "title": "수제 맥주 부스",
                  "description": "직접 만든 맥주를 팝니다",
                  "boothHostName": "홉스터"
                }""".formatted(festivalId);
    }

    @Test
    void createBoothSucceedsWithStorehostRole() throws Exception {
        mockMvc.perform(post("/api/store/booths")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STOREHOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.title", is("수제 맥주 부스")))
                .andExpect(jsonPath("$.data.hostUserId", is(10)))
                .andExpect(jsonPath("$.data.boothStatus", is("WAITING")));
    }

    @Test
    void createBoothWithoutStorehostRoleIsForbidden() throws Exception {
        mockMvc.perform(post("/api/store/booths")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN_STOREHOST_ROLE")));
    }

    @Test
    void createBoothFailsWhenFestivalAlreadyHasBooth() throws Exception {
        boothRepository.save(waitingBooth());

        mockMvc.perform(post("/api/store/booths")
                        .header("X-User-Id", "20")
                        .header("X-User-Role", "STOREHOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("DUPLICATE_BOOTH_FOR_FESTIVAL")));
    }

    @Test
    void waitingBoothIsHiddenFromPublicListAndDetail() throws Exception {
        Booth waiting = boothRepository.save(waitingBooth());

        mockMvc.perform(get("/api/festivals/" + festivalId + "/booths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/booths/" + waiting.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("BOOTH_NOT_FOUND")));
    }

    @Test
    void openBoothIsVisibleToPublic() throws Exception {
        Booth open = boothRepository.save(openBooth());

        mockMvc.perform(get("/api/festivals/" + festivalId + "/booths"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].boothStatus", is("OPEN")));

        mockMvc.perform(get("/api/booths/" + open.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(open.getId().intValue())));
    }

    @Test
    void ownerCanChangeBoothStatus() throws Exception {
        Booth booth = boothRepository.save(waitingBooth());

        mockMvc.perform(patch("/api/store/booths/" + booth.getId() + "/status")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STOREHOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boothStatus\":\"OPEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boothStatus", is("OPEN")));
    }

    @Test
    void nonOwnerCannotChangeBoothStatus() throws Exception {
        Booth booth = boothRepository.save(waitingBooth());

        mockMvc.perform(patch("/api/store/booths/" + booth.getId() + "/status")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "STOREHOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boothStatus\":\"OPEN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN_NOT_OWNER")));
    }

    private Booth waitingBooth() {
        return Booth.builder()
                .festival(festivalRepository.findById(festivalId).orElseThrow())
                .hostUserId(10L)
                .title("수제 맥주 부스")
                .description("설명")
                .boothHostName("홉스터")
                .boothStatus(BoothStatus.WAITING)
                .build();
    }

    private Booth openBooth() {
        return Booth.builder()
                .festival(festivalRepository.findById(festivalId).orElseThrow())
                .hostUserId(10L)
                .title("수제 맥주 부스")
                .description("설명")
                .boothHostName("홉스터")
                .boothStatus(BoothStatus.OPEN)
                .build();
    }
}
