package org.example.paymentservice.domain.settlement;

import jakarta.annotation.PostConstruct;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 이전 채널별 원장 중 빈 중복만 퇴역시킨다. 금액과 지급 이력은 변경하지 않는다. */
@Component
@RequiredArgsConstructor
public class SettlementLedgerInitializer {

    private final SettlementRepository repository;
    private final SettlementAdjustmentRepository adjustments;
    private final SettlementAdjustmentAllocationRepository allocations;
    private final PlatformTransactionManager manager;

    @PostConstruct
    public void initialize() {
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            var groups = repository
                .findByRetiredFalseOrderByIdAsc()
                .stream()
                .collect(Collectors.groupingBy(Settlement::getFestivalId));
            for (var rows : groups.values()) {
                var meaningful = rows
                    .stream()
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
                if (meaningful.size() > 1) throw new IllegalStateException(
                    "SETTLEMENT_CHANNEL_MERGE_REVIEW_REQUIRED festival=" + rows.getFirst().getFestivalId()
                );
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
