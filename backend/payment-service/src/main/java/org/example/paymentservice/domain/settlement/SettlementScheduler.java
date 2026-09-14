package org.example.paymentservice.domain.settlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.*;
import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "settlement.scheduling-enabled", matchIfMissing = true)
public class SettlementScheduler {
    private final SettlementService service;
    private final SettlementRepository repository;
    private final FestivalSettlementClient festivals;
    @org.springframework.beans.factory.annotation.Value("${settlement.allow-test-payments:false}") private boolean allowTestPayments;
    @Scheduled(cron = "${settlement.cron:0 0 2 * * *}", zone = "Asia/Seoul")
    public void run() {
        for (var frozen : repository.findByStatusIn(List.of(SettlementStatus.CONFIRMED, SettlementStatus.PAID, SettlementStatus.ADJUSTMENT_REQUIRED))) {
            try { service.reconcileFrozen(frozen.getId()); }
            catch (RuntimeException e) { log.warn("정산 대사 재시도 필요: id={}", frozen.getId(), e); }
        }
        for (int page = 0; ; page++) {
            var candidates = festivals.candidates(page);
            for (var festival : candidates) {
                try { service.calculateFestival(festival.festivalId(), false);
                    if (allowTestPayments) service.calculateFestival(festival.festivalId(), true); }
                catch (RuntimeException e) { log.warn("정산 계산 재시도 필요: festival={}", festival.festivalId(), e); }
            }
            if (candidates.size() < 100) return;
        }
    }
}
