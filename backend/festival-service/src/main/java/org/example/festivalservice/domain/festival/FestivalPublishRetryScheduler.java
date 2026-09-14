package org.example.festivalservice.domain.festival;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 승인 처리 중 reservation-service의 좌석 생성 응답을 못 받아 PUBLISH_PENDING에 멈춘 페스티벌을
 * 주기적으로 재시도한다. HostApplicationApprovalRetryScheduler와 같은 목적·같은 패턴이다.
 * 운영자가 다시 승인 버튼을 누르지 않아도 스스로 회복되도록 한다.
 */
@Component
@RequiredArgsConstructor
public class FestivalPublishRetryScheduler {

    private static final int RETRY_AFTER_SECONDS = 30;

    private final FestivalService festivalService;

    @Scheduled(fixedDelay = 60_000)
    public void retryStuckPublishes() {
        festivalService.retryPendingPublishes(LocalDateTime.now().minusSeconds(RETRY_AFTER_SECONDS));
    }
}