package org.example.paymentservice.domain.settlement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SettlementRepository extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {
    Optional<Settlement> findByFestivalIdAndTestPayment(Long festivalId, boolean testPayment);
    List<Settlement> findByStatusIn(Collection<SettlementStatus> statuses);
    Optional<Settlement> findByActiveFestivalId(Long festivalId);
    List<Settlement> findByRetiredFalseOrderByIdAsc();
}
