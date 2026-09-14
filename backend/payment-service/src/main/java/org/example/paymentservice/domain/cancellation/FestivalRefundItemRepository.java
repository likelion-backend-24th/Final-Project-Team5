package org.example.paymentservice.domain.cancellation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRefundItemRepository extends JpaRepository<FestivalRefundItem, Long> {
    List<FestivalRefundItem> findByBatchId(Long batchId);
}
