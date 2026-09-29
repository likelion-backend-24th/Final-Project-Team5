package org.example.festivalservice.festival;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.example.festivalservice.domain.booth.BoothRepository;
import org.example.festivalservice.domain.festival.Festival;
import org.example.festivalservice.domain.festival.FestivalCategory;
import org.example.festivalservice.domain.festival.FestivalPublishRetryScheduler;
import org.example.festivalservice.domain.festival.FestivalRegion;
import org.example.festivalservice.domain.festival.FestivalRepository;
import org.example.festivalservice.domain.festival.FestivalService;
import org.example.festivalservice.domain.festival.FestivalStatus;
import org.example.festivalservice.domain.tickettype.SeatLayout;
import org.example.festivalservice.domain.tickettype.TicketMode;
import org.example.festivalservice.domain.tickettype.TicketType;
import org.example.festivalservice.domain.tickettype.TicketTypeRepository;
import org.example.festivalservice.infrastructure.reservation.ReservationServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.ResourceAccessException;

/**
 * 좌석형(SEATED) 페스티벌 공개 경로 — 운영자 승인 → 좌석 생성 요청 실패 시 PUBLISH_PENDING 유지 →
 * FestivalPublishRetryScheduler 재시도로 PUBLISHED 확정까지를 H2와 실제 서비스 계층으로 확인한다.
 * 2026-09-16·18 운영 장애(좌석 생성 호출 실패로 공개가 멈춤)가 난 바로 그 경로다. reservation-service 호출은 Client만 대역으로 바꾼다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatedFestivalPublishRetryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FestivalRepository festivalRepository;

    @Autowired
    private TicketTypeRepository ticketTypeRepository;

    @Autowired
    private BoothRepository boothRepository;

    @Autowired
    private FestivalService festivalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ReservationServiceClient reservationServiceClient;

    private Festival festival;
    private TicketType seatedTicket;

    @BeforeEach
    void setUp() {
        boothRepository.deleteAll();
        ticketTypeRepository.deleteAll();
        festivalRepository.deleteAll();
        reset(reservationServiceClient);

        festival = festivalRepository.save(Festival.builder()
                .hostUserId(1L)
                .name("좌석형 콘서트")
                .description("설명")
                .startAt(LocalDateTime.now().plusDays(30))
                .endAt(LocalDateTime.now().plusDays(31))
                .region(FestivalRegion.SEOUL)
                .locationDetail("올림픽홀")
                .festivalCategory(FestivalCategory.MUSIC)
                .festivalStatus(FestivalStatus.PENDING)
                .build());
        seatedTicket = ticketTypeRepository.save(TicketType.builder()
                .festival(festival)
                .name("R석")
                .price(99_000)
                .ticketMode(TicketMode.SEATED)
                .zone("R")
                .seatLayout(new SeatLayout(List.of(
                        new SeatLayout.RowLayout(3, List.of(2)),
                        new SeatLayout.RowLayout(2, List.of()))))
                .totalQuantity(4)
                .remainQuantity(4)
                .build());
    }

    private void approve() throws Exception {
        mockMvc.perform(patch("/api/admin/festivals/" + festival.getId())
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"PUBLISHED"}"""))
                .andExpect(status().isOk());
    }

    //배치는 30초 넘게 머문 건만 집으므로 마지막 변경 시각을 그 전으로 옮긴다.
    private void stuckForAMinute() {
        jdbcTemplate.update("UPDATE festivals SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)), festival.getId());
    }

    private FestivalStatus currentStatus() {
        return festivalRepository.findById(festival.getId()).orElseThrow().getFestivalStatus();
    }

    @Test
    void 좌석_생성_호출이_실패하면_비공개로_남고_재시도_배치가_성공하면_공개된다() throws Exception {
        doThrow(new ResourceAccessException("Connection refused")).when(reservationServiceClient).generateSeats(any());

        approve();

        assertThat(currentStatus()).isEqualTo(FestivalStatus.PUBLISH_PENDING);
        mockMvc.perform(get("/api/festivals/" + festival.getId()))
                .andExpect(status().isNotFound());

        doNothing().when(reservationServiceClient).generateSeats(any());
        stuckForAMinute();
        new FestivalPublishRetryScheduler(festivalService).retryStuckPublishes();

        assertThat(currentStatus()).isEqualTo(FestivalStatus.PUBLISHED);
        verify(reservationServiceClient, org.mockito.Mockito.times(2)).generateSeats(argThat(request ->
                request.festivalId().equals(festival.getId())
                        && request.ticketTypeId().equals(seatedTicket.getId())
                        && "R".equals(request.zone())
                        && request.seatLayout().rows().size() == 2
                        && request.seatLayout().rows().get(0).excludedSeats().equals(List.of(2))));
        mockMvc.perform(get("/api/festivals/" + festival.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.festivalStatus", is("PUBLISHED")));
    }

    @Test
    void 재시도도_실패하면_PUBLISH_PENDING을_유지하고_다음_회차를_기다린다() throws Exception {
        doThrow(new ResourceAccessException("Connection refused")).when(reservationServiceClient).generateSeats(any());

        approve();
        stuckForAMinute();
        new FestivalPublishRetryScheduler(festivalService).retryStuckPublishes();

        assertThat(currentStatus()).isEqualTo(FestivalStatus.PUBLISH_PENDING);
        verify(reservationServiceClient, org.mockito.Mockito.times(2)).generateSeats(any());
    }
}
