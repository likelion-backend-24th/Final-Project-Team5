package org.example.paymentservice.domain.settlement;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment, Long> {
    List<SettlementAdjustment> findBySourceSettlementIdAndPaymentId(Long source, Long payment);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<SettlementAdjustment> findByHostUserIdAndTestPaymentAndRemainingAmountNot(Long host, boolean test, long remaining);
    List<SettlementAdjustment> findBySourceSettlementId(Long source);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<SettlementAdjustment> findByHostUserIdAndRemainingAmountNot(Long host, long remaining);
}
