package org.example.reservationservice.domain.refund;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.domain.seat.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 좌석 일괄 반환. StockReleaseScheduler와 같은 팀 정책(매일 오후 7시 일괄 반환, 리셀 방지)을 좌석에 적용한다.
 * 시각 계산 로직(nextReleaseInstant)은 StockReleaseScheduler와 동일 — festival-service 호출 대신
 * 로컬 SeatRepository.releaseSeat()으로 크로스 서비스 호출 없이 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SeatReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatReleaseScheduler.class);

    private final SeatReleaseQueueRepository seatReleaseQueueRepository;
    private final SeatRepository seatRepository;
    private final StockReleaseScheduler stockReleaseScheduler;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseDueSeats() {
        Instant now = Instant.now();
        List<SeatReleaseQueue> due = seatReleaseQueueRepository.findByReleasedAtIsNullAndReleaseAtBefore(now);
        for (SeatReleaseQueue entry : due) {
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