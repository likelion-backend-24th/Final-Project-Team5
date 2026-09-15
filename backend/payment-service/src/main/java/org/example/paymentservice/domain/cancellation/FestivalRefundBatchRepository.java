package org.example.paymentservice.domain.cancellation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FestivalRefundBatchRepository extends JpaRepository<FestivalRefundBatch, Long> {
    Optional<FestivalRefundBatch> findByFestivalId(Long id);
}
