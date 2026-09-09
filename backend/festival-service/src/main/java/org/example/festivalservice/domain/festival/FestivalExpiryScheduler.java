package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * reservation-service의 ReservationExpiryScheduler와 같은 패턴 — 개최 종료 일시(endAt)가 지났는데
 * 아직 PUBLISHED인 페스티벌을 CLOSED로 전환한다. reservation-service는 festivalStatus가 PUBLISHED가
 * 아니면 예매를 거부하므로, 이 전환만으로 종료된 페스티벌의 예매 신청이 자동으로 막힌다.
 */
@Component
@RequiredArgsConstructor
public class FestivalExpiryScheduler {

    private final FestivalRepository festivalRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void closeEndedFestivals() {
        List<Festival> endedFestivals =
                festivalRepository.findByFestivalStatusAndEndAtBefore(FestivalStatus.PUBLISHED, LocalDateTime.now());

        for (Festival festival : endedFestivals) {
            festival.close();
        }
    }
}
