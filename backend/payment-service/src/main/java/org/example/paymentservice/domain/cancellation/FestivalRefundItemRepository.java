package org.example.paymentservice.domain.cancellation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FestivalRefundItemRepository extends JpaRepository<FestivalRefundItem, Long> {
    List<FestivalRefundItem> findByBatchId(Long batchId);
}
