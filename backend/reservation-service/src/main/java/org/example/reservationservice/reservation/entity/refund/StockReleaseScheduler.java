package org.example.reservationservice.domain.refund;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.reservationservice.infrastructure.festival.FestivalServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 재고 일괄 반환. 팀 정책: 환불로 돌아온 재고는 모아 두었다가 매일 오후 7시(기본)에 한꺼번에 푼다.
 * 7시 전에 생긴 재고는 그날 7시에, 7시 이후에 생긴 재고는 다음 날 7시에 풀린다 — 환불 직후 재판매를 노리는 리셀 방지.
 */
@Component
@RequiredArgsConstructor
public class StockReleaseScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockReleaseScheduler.class);

    private final StockReleaseQueueRepository stockReleaseQueueRepository;
    private final FestivalServiceClient festivalServiceClient;

    @Value("${app.timezone:Asia/Seoul}")
    private String appTimezone;

    @Value("${reservation.refund-stock-release-hour:19}")
    private int releaseHour;

    /** 지금 환불된 재고가 실제로 풀릴 시각. */
    public Instant nextReleaseInstant(Instant now) {
        ZoneId zone = ZoneId.of(appTimezone);
        LocalDateTime local = LocalDateTime.ofInstant(now, zone);
        LocalDateTime todayRelease = LocalDateTime.of(local.toLocalDate(), LocalTime.of(releaseHour, 0));
        LocalDateTime release = local.isBefore(todayRelease) ? todayRelease : todayRelease.plusDays(1);
        return release.atZone(zone).toInstant();
    }

    public LocalDate releaseDateFor(Instant now) {
        return LocalDateTime.ofInstant(nextReleaseInstant(now), ZoneId.of(appTimezone)).toLocalDate();
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void releaseDueStock() {
        Instant now = Instant.now();
        List<StockReleaseQueue> due = stockReleaseQueueRepository.findByReleasedAtIsNullAndReleaseAtBefore(now);
        for (StockReleaseQueue entry : due) {
            try {
                festivalServiceClient.restoreStock(entry.getTicketTypeId(), entry.getQuantity());
                entry.markReleased(now);
                log.info("환불 재고 반환: reservation={}, ticketType={}, qty={}",
                        entry.getReservationId(), entry.getTicketTypeId(), entry.getQuantity());
            } catch (RuntimeException e) {
                //실패한 항목은 releasedAt이 비어 있어 다음 회차에 다시 시도된다.
                log.warn("환불 재고 반환 실패(다음 회차 재시도): reservation={}, ticketType={}, qty={}",
                        entry.getReservationId(), entry.getTicketTypeId(), entry.getQuantity(), e);
            }
        }
    }
}
