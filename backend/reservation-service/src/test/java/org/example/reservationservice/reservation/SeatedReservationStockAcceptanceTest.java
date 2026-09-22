package org.example.reservationservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.infrastructure.festival.dto.FestivalDetailResponseDto;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.reservation.scheduler.ReservationExpiryScheduler;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 좌석(SEATED) 예매도 STANDING처럼 festival-service의 remainQuantity를 원자적으로 차감/복구하는지 검증한다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatedReservationStockAcceptanceTest {

    private static final String CREATE_ENDPOINT = "/api/reservations";
    private static final long FESTIVAL_ID = 300L;
    private static final long TICKET_TYPE_ID = 400L;
    private static final int TICKET_PRICE = 30000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    private long seatId1;
    private long seatId2;

    @BeforeEach
    void setUp() {
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        reset(festivalServiceClient);
        when(festivalServiceClient.getFestival(FESTIVAL_ID)).thenReturn(publishedFestival());

        seatId1 = seatRepository.save(availableSeat("A", 1)).getId();
        seatId2 = seatRepository.save(availableSeat("A", 2)).getId();
    }

    private Seat availableSeat(String rowLabel, int seatNumber) {
        return Seat.builder()
                .festivalId(FESTIVAL_ID)
                .ticketTypeId(TICKET_TYPE_ID)
                .zone("A")
                .rowLabel(rowLabel)
                .seatNumber(seatNumber)
                .seatStatus(SeatStatus.AVAILABLE)
                .build();
    }

    private FestivalDetailResponseDto publishedFestival() {
        return new FestivalDetailResponseDto(
                FESTIVAL_ID,
                999L,
                "PUBLISHED",
                java.time.LocalDateTime.now().plusDays(1),
                java.time.LocalDateTime.now().plusDays(2),
                List.of(new FestivalDetailResponseDto.TicketTypeSummary(TICKET_TYPE_ID, TICKET_PRICE, null, null, "SEATED"))
        );
    }

    private String createRequestBody(List<Long> seatIds) {
        String seatIdsJson = seatIds.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("");
        return """
                {
                  "festivalId": %d,
                  "ticketTypeId": %d,
                  "seatIds": [%s]
                }""".formatted(FESTIVAL_ID, TICKET_TYPE_ID, seatIdsJson);
    }

    // 좌석을 골라 예매를 신청하면, 잡은 좌석 수만큼 festival-service의 잔여 수량도 원자적으로 차감돼야 한다
    @Test
    void 좌석_예매_신청시_잡은_좌석_수만큼_재고가_차감된다() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(List.of(seatId1, seatId2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reservationStatus", org.hamcrest.Matchers.is("PENDING")))
                .andExpect(jsonPath("$.data.quantity", org.hamcrest.Matchers.is(2)));

        verify(festivalServiceClient).deductStock(TICKET_TYPE_ID, 2);
    }

    // 참가자가 결제 전(PENDING) 좌석 예매를 직접 취소하면, 실제로 풀린 좌석 수만큼 잔여 수량이 복구돼야 한다
    @Test
    void 좌석_예매를_본인이_취소하면_재고가_복구된다() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(List.of(seatId1, seatId2))))
                .andExpect(status().isCreated());
        long reservationId = reservationRepository.findByUserId(1L).get(0).getId();

        reset(festivalServiceClient); // 생성 시점의 deductStock 호출 기록을 지우고 취소만 검증한다.

        mockMvc.perform(patch(CREATE_ENDPOINT + "/" + reservationId + "/cancel")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());

        verify(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 2);
        assertThat(seatRepository.findById(seatId1).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatRepository.findById(seatId2).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    // 결제 없이 유효 시간을 넘긴 좌석 예매는 만료 배치가 좌석을 되돌리고, 되돌린 개수만큼 잔여 수량도 복구해야 한다
    @Test
    void 좌석_예매가_만료되면_원복된_좌석_수만큼_재고가_복구된다() throws Exception {
        mockMvc.perform(post(CREATE_ENDPOINT)
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(List.of(seatId1))))
                .andExpect(status().isCreated());
        long reservationId = reservationRepository.findByUserId(1L).get(0).getId();

        reset(festivalServiceClient);

        // 만료 시각을 과거로 강제로 당겨, 스케줄러가 이번 회차에 바로 집어가게 한다.
        setExpiresAtInPast(reservationId);

        reservationExpiryScheduler.expireStaleReservations();

        verify(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 1);
        assertThat(seatRepository.findById(seatId1).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    private void setExpiresAtInPast(long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.extendHold(Instant.now().minusSeconds(60));
        reservationRepository.save(reservation);
    }
}
