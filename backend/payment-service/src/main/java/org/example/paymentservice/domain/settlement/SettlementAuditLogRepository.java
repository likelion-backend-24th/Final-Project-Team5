package org.example.paymentservice.domain.settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SettlementAuditLogRepository extends JpaRepository<SettlementAuditLog, Long> {
    List<SettlementAuditLog> findBySettlementIdOrderByIdAsc(Long id);
    Optional<SettlementAuditLog> findByCommandKey(String key);
}
