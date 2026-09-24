package org.example.reservationservice.reservation.scheduler;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.seat.entity.ReservationSeat;
import org.example.reservationservice.seat.repository.ReservationSeatRepository;
import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.service.SeatBroadcastService;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.reservation.entity.CancelReason;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueue;
import org.example.reservationservice.reservation.entity.refund.StockReleaseQueueRepository;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 없이 유효 시간을 넘긴 PENDING 예매를 CANCELLED(EXPIRED)로 전환하고 재고를 복구한다.
 * SEATED 예매는 연결된 Seat를 로컬에서 AVAILABLE로 되돌리고, STANDING 예매는 기존처럼
 * festival-service의 재고를 복구한다. Payment-Service가 뒤늦게 같은 건을 confirm하면
 * ReservationService가 RESERVATION_ALREADY_EXPIRED로 거부한다.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;
    private final ReservationSeatRepository reservationSeatRepository;
    private final SeatRepository seatRepository;
    private final SeatBroadcastService seatBroadcastService;
    private final StockReleaseQueueRepository stockReleaseQueueRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleReservations() {
        Instant now = Instant.now();
        List<Long> staleReservationIds =
                reservationRepository.findIdsByReservationStatusAndExpiresAtBefore(ReservationStatus.PENDING, now);

        for (Long reservationId : staleReservationIds) {
            //후보를 찾은 뒤 결제 확정이나 홀드 연장이 끝났을 수 있으므로 잠금 안에서 다시 확인한다.
            Reservation reservation = reservationRepository.findByIdForUpdate(reservationId).orElse(null);
            if (reservation == null || reservation.getReservationStatus() != ReservationStatus.PENDING
                    || reservation.getExpiresAt() == null || !reservation.getExpiresAt().isBefore(now)) {
                continue;
            }
            reservation.cancel(CancelReason.EXPIRED);

            List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservation.getId());
            if (!reservationSeats.isEmpty()) {
                releaseSeats(reservation, reservationSeats);
            } else {
                restoreStandingStock(reservation);
            }
        }
    }

    //SEATED 예매 만료 — 연결된 좌석을 로컬에서 AVAILABLE로 되돌리고, 되돌린 개수만큼 festival-service 잔여 수량도 복구한다.
    private void releaseSeats(Reservation reservation, List<ReservationSeat> reservationSeats) {
        int releasedCount = 0;
        for (ReservationSeat reservationSeat : reservationSeats) {
            Seat seat = reservationSeat.getSeat();
            int updated = seatRepository.releaseSeat(seat.getId());
            if (updated == 0) {
                log.warn("만료 처리된 예매 {}의 좌석 {} 원복 실패(이미 HELD가 아님)", reservation.getId(), seat.getId());
            } else {
                releasedCount++;
                seatBroadcastService.broadcast(seat.getFestivalId(), seat.getTicketTypeId(), seat.getId(), SeatStatus.AVAILABLE);
            }
        }
        if (releasedCount > 0) {
            restoreSeatStock(reservation, releasedCount);
        }
    }

    //STANDING과 같은 실패 정책: festival-service 복구가 실패해도 만료 처리 자체는 유지하고,
    //복구하지 못한 수량은 재고 반환 대기열에 넣어 재시도한다(restoreStandingStock과 같음).
    private void restoreSeatStock(Reservation reservation, int releasedCount) {
        try {
            festivalServiceClient.restoreStock(reservation.getTicketTypeId(), releasedCount);
        } catch (RuntimeException e) {
            log.error("만료 처리된 좌석 예매 {}의 잔여 수량 복구 실패, 재시도 대기열에 등록 (ticketTypeId={}, qty={})",
                    reservation.getId(), reservation.getTicketTypeId(), releasedCount, e);
            queueStockRestoreRetry(reservation, releasedCount);
        }
    }

    //STANDING 예매 만료 — 기존 그대로 festival-service 재고 복구
    private void restoreStandingStock(Reservation reservation) {
        try {
            festivalServiceClient.restoreStock(reservation.getTicketTypeId(), reservation.getQuantity());
        } catch (RuntimeException e) {
            //재고 복구가 실패해도 예매는 만료 처리된 채로 둔다 — 만료된 예매는 다음 회차 후보에 다시 잡히지 않으므로,
            //복구하지 못한 수량은 재고 반환 대기열에 넣어 재시도 근거를 남긴다.
            log.error("만료 처리된 예매 {}의 재고 복구 실패, 재시도 대기열에 등록 (ticketTypeId={}, quantity={})",
                    reservation.getId(), reservation.getTicketTypeId(), reservation.getQuantity(), e);
            queueStockRestoreRetry(reservation, reservation.getQuantity());
        }
    }

    //festival-service 장애로 복구하지 못한 재고를 반환 시각 "지금"으로 재고 반환 대기열에 넣는다.
    //StockReleaseScheduler가 1분마다 반환 시각이 지난 항목을 복구하고, 실패하면 다음 회차에 다시 시도한다.
    //(환불 재고와 달리 만료 재고는 리셀 방지 대상이 아니라 19시까지 기다리지 않는다.)
    private void queueStockRestoreRetry(Reservation reservation, int quantity) {
        stockReleaseQueueRepository.save(new StockReleaseQueue(
                reservation.getId(), reservation.getTicketTypeId(), quantity, Instant.now()));
    }
}