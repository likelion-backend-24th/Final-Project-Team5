package org.example.paymentservice.domain.settlement;

import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {
    List<SettlementAdjustment> findBySourceSettlementIdAndPaymentId(Long source, Long payment);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SettlementAdjustment> findByHostUserIdAndTestPaymentAndRemainingAmountNot(
        Long host,
        boolean test,
        long remaining
    );

    List<SettlementAdjustment> findBySourceSettlementId(Long source);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<SettlementAdjustment> findByHostUserIdAndRemainingAmountNot(Long host, long remaining);
}
