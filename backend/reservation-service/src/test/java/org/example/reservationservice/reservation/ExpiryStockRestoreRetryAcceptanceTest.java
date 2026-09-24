package org.example.reservationservice.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueue;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueueRepository;
import org.example.reservationservice.reservation.entity.refund.StockReleaseScheduler;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.example.reservationservice.reservation.scheduler.ReservationExpiryScheduler;
import org.example.reservationservice.seat.entity.ReservationSeat;
import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResourceAccessException;

/**
 * 2026-09-21 감사 F6 회귀 테스트 — 결제 없이 만료된 예매의 재고를 되돌리는 순간 festival-service가 응답하지 않아도
 * 그 수량이 영구히 사라지지 않고, 재고 반환 대기열(StockReleaseScheduler)이 다시 복구해야 한다.
 * festival-service는 실제로 띄우지 않고 FestivalServiceClient를 모킹한다.
 */
@SpringBootTest
class ExpiryStockRestoreRetryAcceptanceTest {

    private static final long FESTIVAL_ID = 900L;
    private static final long TICKET_TYPE_ID = 901L;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private ReservationSeatRepository reservationSeatRepository;

    @Autowired
    private StockReleaseQueueRepository stockReleaseQueueRepository;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Autowired
    private StockReleaseScheduler stockReleaseScheduler;

    @MockitoBean
    private FestivalServiceClient festivalServiceClient;

    @BeforeEach
    void setUp() {
        cleanUp();
        reset(festivalServiceClient);
    }

    //같은 스프링 컨텍스트(H2)를 쓰는 다른 테스트는 예매만 지우므로, 예매-좌석 연결과 대기열을 남기지 않는다.
    @AfterEach
    void cleanUp() {
        stockReleaseQueueRepository.deleteAll();
        reservationSeatRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
    }

    private Reservation expiredPendingReservation(int quantity) {
        return reservationRepository.save(Reservation.builder()
                .userId(1L).festivalId(FESTIVAL_ID).hostUserId(10L).ticketTypeId(TICKET_TYPE_ID)
                .price(10000).quantity(quantity).reservationStatus(ReservationStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(60))
                .build());
    }

    private Seat heldSeat(int seatNumber) {
        return seatRepository.save(Seat.builder()
                .festivalId(FESTIVAL_ID).ticketTypeId(TICKET_TYPE_ID).zone("A").rowLabel("1열").seatNumber(seatNumber)
                .seatStatus(SeatStatus.HELD).heldBy(1L).heldUntil(Instant.now().minusSeconds(60))
                .build());
    }

    @Test
    void 만료_때_재고_복구가_실패하면_대기열에_남아_다음_회차에_복구된다() {
        Reservation reservation = expiredPendingReservation(2);
        //만료 배치가 도는 순간만 festival-service가 응답하지 않는다(배포 직후 재기동 등).
        doThrow(new ResourceAccessException("festival-service timeout"))
                .doNothing()
                .when(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 2);

        reservationExpiryScheduler.expireStaleReservations();

        assertThat(reservationRepository.findById(reservation.getId()).orElseThrow().getReservationStatus())
                .isEqualTo(ReservationStatus.CANCELLED);
        assertThat(stockReleaseQueueRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getReservationId()).isEqualTo(reservation.getId());
            assertThat(entry.getQuantity()).isEqualTo(2);
            assertThat(entry.getReleasedAt()).isNull();
        });

        stockReleaseScheduler.releaseDueStock();

        verify(festivalServiceClient, times(2)).restoreStock(TICKET_TYPE_ID, 2);
        assertThat(stockReleaseQueueRepository.findAll()).extracting(StockReleaseQueue::getReleasedAt).doesNotContainNull();
    }

    @Test
    void 좌석_예매는_좌석을_먼저_되돌리고_복구하지_못한_잔여_수량만_대기열로_재시도한다() {
        Reservation reservation = expiredPendingReservation(2);
        Seat first = heldSeat(1);
        Seat second = heldSeat(2);
        reservationSeatRepository.save(ReservationSeat.builder().reservation(reservation).seat(first).build());
        reservationSeatRepository.save(ReservationSeat.builder().reservation(reservation).seat(second).build());
        doThrow(new ResourceAccessException("festival-service timeout"))
                .doNothing()
                .when(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 2);

        reservationExpiryScheduler.expireStaleReservations();

        assertThat(seatRepository.findById(first.getId()).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seatRepository.findById(second.getId()).orElseThrow().getSeatStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(stockReleaseQueueRepository.findAll()).singleElement()
                .satisfies(entry -> assertThat(entry.getQuantity()).isEqualTo(2));

        stockReleaseScheduler.releaseDueStock();

        verify(festivalServiceClient, times(2)).restoreStock(TICKET_TYPE_ID, 2);
        assertThat(stockReleaseQueueRepository.findAll()).extracting(StockReleaseQueue::getReleasedAt).doesNotContainNull();
    }

    @Test
    void 재고_복구가_성공하면_대기열에_아무것도_남기지_않는다() {
        expiredPendingReservation(1);

        reservationExpiryScheduler.expireStaleReservations();

        verify(festivalServiceClient).restoreStock(TICKET_TYPE_ID, 1);
        assertThat(stockReleaseQueueRepository.findAll()).isEmpty();

        stockReleaseScheduler.releaseDueStock();
        verify(festivalServiceClient, never()).restoreStock(TICKET_TYPE_ID, 2);
    }
}
