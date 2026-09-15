package org.example.reservationservice.reservation.scheduler;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.domain.seat.ReservationSeat;
import org.example.reservationservice.domain.seat.ReservationSeatRepository;
import org.example.reservationservice.domain.seat.Seat;
import org.example.reservationservice.domain.seat.SeatBroadcastService;
import org.example.reservationservice.domain.seat.SeatRepository;
import org.example.reservationservice.domain.seat.SeatStatus;
import org.example.reservationservice.reservation.entity.CancelReason;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
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

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleReservations() {
        List<Reservation> staleReservations =
                reservationRepository.findByReservationStatusAndExpiresAtBefore(ReservationStatus.PENDING, Instant.now());

        for (Reservation reservation : staleReservations) {
            reservation.cancel(CancelReason.EXPIRED);

            List<ReservationSeat> reservationSeats = reservationSeatRepository.findByReservationId(reservation.getId());
            if (!reservationSeats.isEmpty()) {
                releaseSeats(reservation, reservationSeats);
            } else {
                restoreStandingStock(reservation);
            }
        }
    }

    //SEATED 예매 만료 — 연결된 좌석을 로컬에서 AVAILABLE로 되돌린다(크로스 서비스 호출 불필요)
    private void releaseSeats(Reservation reservation, List<ReservationSeat> reservationSeats) {
        for (ReservationSeat reservationSeat : reservationSeats) {
            Seat seat = reservationSeat.getSeat();
            int updated = seatRepository.releaseSeat(seat.getId());
            if (updated == 0) {
                log.warn("만료 처리된 예매 {}의 좌석 {} 원복 실패(이미 HELD가 아님)", reservation.getId(), seat.getId());
            } else {
                seatBroadcastService.broadcast(seat.getFestivalId(), seat.getTicketTypeId(), seat.getId(), SeatStatus.AVAILABLE);
            }
        }
    }

    //STANDING 예매 만료 — 기존 그대로 festival-service 재고 복구
    private void restoreStandingStock(Reservation reservation) {
        try {
            festivalServiceClient.restoreStock(reservation.getTicketTypeId(), reservation.getQuantity());
        } catch (RuntimeException e) {
            //재고 복구가 실패해도 예매는 만료 처리된 채로 둔다 — 다음 회차에 재시도할 근거가 없으므로 일단 로그로 남긴다.
            //TODO: festival-service 장애 시 재시도할 방법(재시도 큐 등)은 별도로 설계 필요.
            log.error("만료 처리된 예매 {}의 재고 복구 실패 (ticketTypeId={}, quantity={})",
                    reservation.getId(), reservation.getTicketTypeId(), reservation.getQuantity(), e);
        }
    }
}