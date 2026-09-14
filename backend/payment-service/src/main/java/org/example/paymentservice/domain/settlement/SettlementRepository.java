package org.example.paymentservice.domain.settlement;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SettlementRepository extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {
    Optional<Settlement> findByFestivalIdAndTestPayment(Long festivalId, boolean testPayment);
    List<Settlement> findByStatusIn(Collection<SettlementStatus> statuses);
    Optional<Settlement> findByActiveFestivalId(Long festivalId);
    List<Settlement> findByRetiredFalseOrderByIdAsc();
}
