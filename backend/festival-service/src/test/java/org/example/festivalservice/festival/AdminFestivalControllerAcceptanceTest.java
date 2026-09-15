package org.example.festivalservice.festival;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.festivalservice.domain.booth.BoothRepository;
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
class AdminFestivalControllerAcceptanceTest {

    private static final String ENDPOINT = "/api/admin/festivals";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private BoothRepository boothRepository;

    @BeforeEach
    void setUp() {
        //Booth가 festival_id FK를 가지므로, 다른 테스트 클래스가 남긴 부스가 있으면 festivals 삭제가
        //참조무결성 위반으로 실패한다 — 먼저 지운다.
        boothRepository.deleteAll();
        festivalRepository.deleteAll();
    }

    @Test
    void listFestivalsForAdminReturnsEveryStatusAsHistory() throws Exception {
        saveFestival("대기1", FestivalStatus.PENDING);
        saveFestival("대기2", FestivalStatus.PENDING);
        saveFestival("공개됨", FestivalStatus.PUBLISHED);

        mockMvc.perform(get(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void listPendingFestivalsWithoutAdminRoleIsForbidden() throws Exception {
        mockMvc.perform(get(ENDPOINT)
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN_ADMIN_ROLE")));
    }

    @Test
    void reviewFestivalApprovesToPublished() throws Exception {
        Festival festival = saveFestival("대기1", FestivalStatus.PENDING);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"PUBLISHED"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.festivalStatus", is("PUBLISHED")));
    }

    @Test
    void reviewFestivalRejects() throws Exception {
        Festival festival = saveFestival("대기1", FestivalStatus.PENDING);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECTED","rejectReason":"포스터 이미지가 없습니다"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.festivalStatus", is("REJECTED")))
                .andExpect(jsonPath("$.data.rejectReason", is("포스터 이미지가 없습니다")));
    }

    @Test
    void reviewFestivalRejectWithoutReasonIsBadRequest() throws Exception {
        Festival festival = saveFestival("대기1", FestivalStatus.PENDING);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECTED"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("REJECT_REASON_REQUIRED")));
    }

    @Test
    void reviewFestivalWithInvalidDecisionIsBadRequest() throws Exception {
        Festival festival = saveFestival("대기1", FestivalStatus.PENDING);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"PENDING"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("INVALID_DECISION")));
    }

    @Test
    void reviewFestivalAlreadyReviewedIsConflict() throws Exception {
        Festival festival = saveFestival("이미공개", FestivalStatus.PUBLISHED);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"REJECTED"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ALREADY_REVIEWED")));
    }

    @Test
    void reviewFestivalWithoutAdminRoleIsForbidden() throws Exception {
        Festival festival = saveFestival("대기1", FestivalStatus.PENDING);

        mockMvc.perform(patch(ENDPOINT + "/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "HOST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"PUBLISHED"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("FORBIDDEN_ADMIN_ROLE")));
    }

    private Festival saveFestival(String name, FestivalStatus status) {
        return festivalRepository.save(Festival.builder()
                .hostUserId(1L)
                .name(name)
                .description("설명")
                .startAt(java.time.LocalDateTime.of(2026, 10, 1, 10, 0))
                .endAt(java.time.LocalDateTime.of(2026, 10, 2, 22, 0))
                .region(FestivalRegion.SEOUL)
                .locationDetail("서울숲")
                .festivalCategory(FestivalCategory.MUSIC)
                .festivalStatus(status)
                .build());
    }
}
