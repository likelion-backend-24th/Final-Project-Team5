package org.example.reservationservice.domain;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.infrastructure.festival.FestivalServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 없이 유효 시간을 넘긴 PENDING 예매를 CANCELLED(EXPIRED)로 전환하고 재고를 복구한다.
 * Payment-Service가 뒤늦게 같은 건을 confirm하면 ReservationService가 RESERVATION_ALREADY_EXPIRED로 거부한다.
 */
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final ReservationRepository reservationRepository;
    private final FestivalServiceClient festivalServiceClient;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStaleReservations() {
        List<Reservation> staleReservations =
                reservationRepository.findByReservationStatusAndExpiresAtBefore(ReservationStatus.PENDING, Instant.now());

        for (Reservation reservation : staleReservations) {
            reservation.cancel(CancelReason.EXPIRED);
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
}
