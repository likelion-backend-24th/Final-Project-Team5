package org.example.reservationservice.reservation;

import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * festival-service가 페스티벌 공개 시 부르는 좌석 생성 내부 API(POST /internal/v1/seats)를 실제 HTTP 요청과 H2로 확인한다.
 * 이 호출이 실패하면 좌석형 페스티벌이 공개되지 않는다(2026-09-16·18 운영 장애 경로).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "internal.reservation-service.token=test-reservation-token")
class InternalSeatGenerationAcceptanceTest {

    private static final String ENDPOINT = "/internal/v1/seats";
    private static final String TOKEN = "Bearer test-reservation-token";
    private static final long FESTIVAL_ID = 70L;
    private static final long TICKET_TYPE_ID = 700L;
    //1열은 3석 중 2번이 결번(통로), 2열은 2석 — 실제로 만들어질 좌석은 4석이다.
    private static final String BODY = """
            {
              "festivalId": 70,
              "ticketTypeId": 700,
              "zone": "R",
              "seatLayout": {"rows": [{"seatCount": 3, "excludedSeats": [2]}, {"seatCount": 2}]}
            }""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @BeforeEach
    void setUp() {
        reservationSeatRepository.deleteAll();
        seatRepository.deleteAll();
    }

    private List<Seat> seats() {
        return seatRepository.findByFestivalIdAndTicketTypeIdOrderByZoneAscRowLabelAscSeatNumberAsc(FESTIVAL_ID, TICKET_TYPE_ID);
    }

    @Test
    void 올바른_내부_토큰이면_행별_좌석_수와_결번대로_좌석을_만든다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());

        assertThat(seats())
                .extracting(seat -> seat.getRowLabel() + "-" + seat.getSeatNumber())
                .containsExactly("1열-1", "1열-3", "2열-1", "2열-2");
        assertThat(seats()).allMatch(seat -> seat.getSeatStatus() == SeatStatus.AVAILABLE && "R".equals(seat.getZone()));
    }

    @Test
    void 재시도로_같은_티켓종류를_다시_요청해도_좌석을_중복으로_만들지_않는다() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(ENDPOINT)
                            .header("Authorization", TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isOk());
        }

        assertThat(seats()).hasSize(4);
    }

    @Test
    void 내부_토큰이_틀리거나_없으면_401이고_좌석을_만들지_않는다() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("Authorization", "Bearer CHANGE_ME_IN_ENV")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INTERNAL_TOKEN"));
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        assertThat(seats()).isEmpty();
    }
}
