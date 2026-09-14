package org.example.paymentservice.domain.settlement;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "settlement.scheduling-enabled", matchIfMissing = true)
public class SettlementScheduler {

    private final SettlementService service;
    private final SettlementRepository repository;
    private final FestivalSettlementClient festivals;

    @Scheduled(cron = "${settlement.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    public void run() {
        service.refreshHostNames();
        for (var frozen : repository.findByStatusIn(
            List.of(SettlementStatus.CONFIRMED, SettlementStatus.PAID, SettlementStatus.ADJUSTMENT_REQUIRED)
        )) {
            try {
                service.reconcileFrozen(frozen.getId());
            } catch (RuntimeException e) {
                log.warn("정산 대사 재시도 필요: id={}", frozen.getId(), e);
            }
        }
        for (int page = 0; ; page++) {
            var candidates = festivals.candidates(page);
            for (var festival : candidates) {
                try {
                    service.calculateFestival(festival.festivalId(), false);
                } catch (RuntimeException e) {
                    log.warn("정산 계산 재시도 필요: festival={}", festival.festivalId(), e);
                }
            }
            if (candidates.size() < 100) return;
        }
    }
}
