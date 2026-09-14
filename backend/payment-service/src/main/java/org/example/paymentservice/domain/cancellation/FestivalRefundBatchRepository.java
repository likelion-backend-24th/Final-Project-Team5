package org.example.paymentservice.domain.cancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface FestivalRefundBatchRepository extends JpaRepository<FestivalRefundBatch, Long> {
    Optional<FestivalRefundBatch> findByFestivalId(Long id);
}
