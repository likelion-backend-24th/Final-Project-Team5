package org.example.paymentservice.domain.settlement;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementAdjustmentAllocationRepository extends JpaRepository<SettlementAdjustmentAllocation, Long> {
    List<SettlementAdjustmentAllocation> findBySettlementId(Long id);
}
