package org.example.reservationservice.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.entity.refund.SeatReleaseQueue;
import org.example.reservationservice.reservation.entity.refund.SeatReleaseQueueRepository;
import org.example.reservationservice.reservation.entity.refund.SeatReleaseScheduler;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueueRepository;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.seat.entity.ReservationSeat;
import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 2026-09-21 감사 F2 회귀 테스트 — Payment-Service는 환불을 장수로만 알려주므로(좌석 id 없음),
 * 좌석(SEATED) 예매 환불도 실제 좌석을 반환 대기열에 넣어야 19시 일괄 반환 때 다시 팔 수 있는 좌석이 된다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SeatedRefundAcceptanceTest {

    private static final long FESTIVAL_ID = 500L;
    private static final long TICKET_TYPE_ID = 600L;
    private static final String PAYMENT_ID = "seated-refund-p";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private SeatReleaseQueueRepository seatReleaseQueueRepository;

    @Autowired
    private StockReleaseQueueRepository stockReleaseQueueRepository;

    @Autowired
    private SeatReleaseScheduler seatReleaseScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(festivalServiceClient);
    }

    //같은 스프링 컨텍스트(H2)를 쓰는 다른 테스트는 예매만 지우므로, 예매-좌석 연결을 남기면 외래키 때문에 실패한다.
    @AfterEach
    void cleanUp() {
        seatReleaseQueueRepository.deleteAll();
        stockReleaseQueueRepository.deleteAll();
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
    }

    private Seat soldSeat(int seatNumber) {
        return seatRepository.save(Seat.builder()
                .festivalId(FESTIVAL_ID)
                .ticketTypeId(TICKET_TYPE_ID)
                .zone("A")
                .rowLabel("1열")
                .seatNumber(seatNumber)
                .seatStatus(SeatStatus.SOLD)
                .build());
    }

    private Reservation confirmedReservation(int quantity) {
        return reservationRepository.save(Reservation.builder()
                .userId(1L).festivalId(FESTIVAL_ID).hostUserId(10L).ticketTypeId(TICKET_TYPE_ID)
                .price(30000).quantity(quantity).reservationStatus(ReservationStatus.CONFIRMED).paymentId(PAYMENT_ID)
                .build());
    }

    private void link(Reservation reservation, Seat seat) {
        reservationSeatRepository.save(ReservationSeat.builder().reservation(reservation).seat(seat).build());
    }

    //Payment-Service가 실제로 보내는 형태 그대로(paymentId·quantity·cancellationId, seatIds 없음) 환불을 반영한다.
    //취소 영수증은 테스트 사이에 남으므로 예매 id를 붙여 테스트마다 다른 취소 id가 되게 한다.
    private void refund(Reservation reservation, int quantity, String cancellationId) throws Exception {
        mockMvc.perform(patch("/internal/v1/reservations/" + reservation.getId() + "/refund")
                        .header("Authorization", "Bearer test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentId": "%s", "quantity": %d, "cancellationId": "%s"}"""
                                .formatted(PAYMENT_ID, quantity, cancellationId + "-" + reservation.getId())))
                .andExpect(status().isOk());
    }

    private List<Long> queuedSeatIds(Reservation reservation) {
        return seatReleaseQueueRepository.findByReservationId(reservation.getId()).stream()
                .map(SeatReleaseQueue::getSeatId)
                .toList();
    }

    @Test
    void 좌석_id_없이_장수만_받아도_좌석_예매_환불은_좌석을_반환하고_19시_배치가_다시_팔_수_있게_한다() throws Exception {
        Seat first = soldSeat(1);
        Seat second = soldSeat(2);
        Reservation reservation = confirmedReservation(2);
        link(reservation, first);
        link(reservation, second);

        refund(reservation, 1, "seated-cancel-1");

        assertThat(queuedSeatIds(reservation)).containsExactly(first.getId());
        //좌석 예매는 좌석 반환 배치가 잔여 수량까지 복구하므로 재고 대기열에는 넣지 않는다(이중 복구 방지).
        assertThat(stockReleaseQueueRepository.findAll()).isEmpty();

        //19시 일괄 반환 시각이 지난 상황을 만든다.
        jdbcTemplate.update("update seat_release_queue set release_at = ?", Instant.now().minusSeconds(60));
        seatReleaseScheduler.releaseDueSeats();

        assertThat(seatRepository.findById(first.getId()).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatRepository.findById(second.getId()).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.SOLD);
        verify(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 1);
    }

    @Test
    void 나눠서_환불하면_이미_반환한_좌석은_다시_고르지_않는다() throws Exception {
        Seat first = soldSeat(1);
        Seat second = soldSeat(2);
        Reservation reservation = confirmedReservation(2);
        link(reservation, first);
        link(reservation, second);

        refund(reservation, 1, "seated-cancel-1");
        refund(reservation, 1, "seated-cancel-2");

        assertThat(queuedSeatIds(reservation)).containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.REFUNDED);
    }

    @Test
    void 같은_취소가_두_번_알려와도_좌석은_한_번만_반환_대기열에_들어간다() throws Exception {
        Seat first = soldSeat(1);
        Seat second = soldSeat(2);
        Reservation reservation = confirmedReservation(2);
        link(reservation, first);
        link(reservation, second);

        refund(reservation, 1, "seated-cancel-1");
        refund(reservation, 1, "seated-cancel-1"); //웹훅과 API 응답이 같은 취소를 각각 알려주는 상황

        assertThat(queuedSeatIds(reservation)).containsExactly(first.getId());
    }

    @Test
    void 주최자_귀책_전액_환불도_좌석을_모두_반환한다() throws Exception {
        Seat first = soldSeat(1);
        Seat second = soldSeat(2);
        Reservation reservation = confirmedReservation(2);
        link(reservation, first);
        link(reservation, second);

        refund(reservation, 2, "seated-organizer-cancel");

        assertThat(queuedSeatIds(reservation)).containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    void 스탠딩_예매_환불은_기존처럼_재고_대기열로_반환한다() throws Exception {
        Reservation reservation = confirmedReservation(2);

        refund(reservation, 1, "standing-cancel-1");

        assertThat(seatReleaseQueueRepository.findAll()).isEmpty();
        assertThat(stockReleaseQueueRepository.findAll()).hasSize(1);
        verify(festivalServiceClient, never()).restoreStock(TICKET_TYPE_ID, 1);
    }
}
