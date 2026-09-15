package org.example.reservationservice.domain.refund;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.domain.seat.SeatRepository;
import org.example.reservationservice.domain.seat.SeatStatus;
import org.example.reservationservice.domain.seat.Seat;
import org.example.reservationservice.domain.seat.SeatBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SeatReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatReleaseScheduler.class);

    private final SeatReleaseQueueRepository seatReleaseQueueRepository;
    private final SeatRepository seatRepository;
    private final StockReleaseScheduler stockReleaseScheduler;
    private final SeatBroadcastService seatBroadcastService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseDueSeats() {
        Instant now = Instant.now();
        List<SeatReleaseQueue> due = seatReleaseQueueRepository.findByReleasedAtIsNullAndReleaseAtBefore(now);
        for (SeatReleaseQueue entry : due) {
            int updated = seatRepository.releaseSoldSeat(entry.getSeatId());
            if (updated > 0) {
                entry.markReleased(now);
                log.info("환불 좌석 반환: reservation={}, seat={}", entry.getReservationId(), entry.getSeatId());
                broadcastIfSeatExists(entry.getSeatId());
            } else {
                log.warn("환불 좌석 반환 실패(다음 회차 재시도): reservation={}, seat={}",
                        entry.getReservationId(), entry.getSeatId());
            }
        }
    }

    //브로드캐스트에는 festivalId/ticketTypeId가 필요한데 releaseSoldSeat()은 영향받은 row 수만 반환하므로,
    //성공했을 때 좌석을 다시 조회해서 그 정보를 가져온다.
    private void broadcastIfSeatExists(Long seatId) {
        seatRepository.findById(seatId).ifPresent(seat ->
                seatBroadcastService.broadcast(seat.getFestivalId(), seat.getTicketTypeId(), seat.getId(), SeatStatus.AVAILABLE));
    }
}