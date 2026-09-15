package org.example.paymentservice.domain.settlement;

import jakarta.annotation.PostConstruct;

import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 신규 정산 테이블이므로 운영 DB에 존재한 적 없는 중복 상태를 중간 빌드의 로컬 DB에서 방어한다.
 * 금액이 있는 중복은 자동 병합하지 않고 해당 페스티벌만 제외해 다른 정산의 기동을 막지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SettlementLedgerInitializer {

    private final SettlementRepository repository;
    private final SettlementAdjustmentRepository adjustments;
    private final SettlementAdjustmentAllocationRepository allocations;
    private final PlatformTransactionManager manager;

    @PostConstruct
    public void initialize() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var groups = repository.findByRetiredFalseOrderByIdAsc()
                    .stream()
                    .collect(Collectors.groupingBy(Settlement::getFestivalId));
            for (var rows : groups.values()) {
                var meaningful = rows.stream()
                        .filter(
                                s ->
                                        s.getConfirmedAt() != null ||
                                                s.getPaidAt() != null ||
                                                !s.getLines().isEmpty() ||
                                                s.getGrossPaymentAmount() != 0 ||
                                                s.isManualHold() ||
                                                !adjustments.findBySourceSettlementId(s.getId()).isEmpty() ||
                                                !allocations.findBySettlementId(s.getId()).isEmpty()
                        )
                        .toList();
                if (meaningful.size() > 1) {
                    rows.forEach(Settlement::excludeFromCalculation);
                    log.error("정산 원장 수동 병합 필요: festival={}", rows.getFirst().getFestivalId());
                    continue;
                }
                var keep = meaningful.isEmpty()
                        ? rows.stream().filter(Settlement::isTestPayment).findFirst().orElse(rows.getFirst())
                        : meaningful.getFirst();
                rows.stream()
                        .filter(s -> s != keep)
                        .forEach(Settlement::retireEmptyLedger);
                repository.flush();
                keep.activateLedger();
            }
        });
    }
}
