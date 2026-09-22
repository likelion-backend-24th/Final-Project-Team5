package org.example.reservationservice.reservation.entity.refund;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.reservation.infrastructure.festival.FestivalServiceClient;
import org.example.reservationservice.seat.entity.Seat;
import org.example.reservationservice.seat.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 좌석 일괄 반환. StockReleaseScheduler와 같은 팀 정책(매일 오후 7시 일괄 반환, 리셀 방지)을 좌석에 적용한다.
 * 시각 계산 로직(nextReleaseInstant)은 StockReleaseScheduler와 동일 — 좌석 자체는 로컬 SeatRepository로
 * 되돌리지만, festival-service가 노출하는 잔여 수량도 같이 복구해야 해서 크로스 서비스 호출이 필요하다.
 */
@Component
@RequiredArgsConstructor
public class SeatReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatReleaseScheduler.class);

    private final SeatReleaseQueueRepository seatReleaseQueueRepository;
    private final SeatRepository seatRepository;
    private final StockReleaseScheduler stockReleaseScheduler;
    private final FestivalServiceClient festivalServiceClient;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseDueSeats() {
        Instant now = Instant.now();
        List<SeatReleaseQueue> due = seatReleaseQueueRepository.findByReleasedAtIsNullAndReleaseAtBefore(now);
        for (SeatReleaseQueue entry : due) {
            Seat seat = seatRepository.findById(entry.getSeatId()).orElse(null);
            if (seat == null) {
                log.warn("환불 좌석 반환 실패(좌석 없음, 다음 회차 재시도): reservation={}, seat={}",
                        entry.getReservationId(), entry.getSeatId());
                continue;
            }

            //festival-service 잔여 수량 복구를 먼저 시도한다. 로컬 좌석 반환을 먼저 하면, 그 뒤 festival-service
            //호출만 실패했을 때 좌석은 이미 판매 가능 상태가 돼 releaseSoldSeat()의 재시도 조건(SOLD)이 다시는
            //맞지 않아 다음 회차에 영영 재시도되지 않는다. 순서를 뒤집어 festival-service가 실패하면 로컬은
            //그대로 둬서(releasedAt 그대로 비워둠) 다음 회차에 깨끗하게 재시도되게 한다.
            try {
                festivalServiceClient.restoreStock(seat.getTicketTypeId(), 1);
            } catch (RuntimeException e) {
                log.warn("환불 좌석 잔여 수량 복구 실패(다음 회차 재시도): reservation={}, seat={}",
                        entry.getReservationId(), entry.getSeatId(), e);
                continue;
            }

            //releaseSeat()은 HELD인 좌석만 대상으로 하지만, 환불된 좌석은 SOLD 상태이므로 직접 AVAILABLE로 되돌린다.
            //releaseSeat()과 혼용하지 않도록 다음 작업에서 정리 필요.
            int updated = seatRepository.releaseSoldSeat(entry.getSeatId());
            if (updated > 0) {
                entry.markReleased(now);
                log.info("환불 좌석 반환: reservation={}, seat={}", entry.getReservationId(), entry.getSeatId());
            } else {
                log.warn("환불 좌석 반환 실패(다음 회차 재시도): reservation={}, seat={}",
                        entry.getReservationId(), entry.getSeatId());
            }
        }
    }
}
