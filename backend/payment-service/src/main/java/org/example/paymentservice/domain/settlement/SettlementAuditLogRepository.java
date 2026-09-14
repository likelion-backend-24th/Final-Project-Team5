package org.example.paymentservice.domain.settlement;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAuditLogRepository extends JpaRepository<SettlementAuditLog, Long> {
    List<SettlementAuditLog> findBySettlementIdOrderByIdAsc(Long id);
    Optional<SettlementAuditLog> findByCommandKey(String key);
}
