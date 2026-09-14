package org.example.reservationservice.domain.refund;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReleaseQueueRepository extends JpaRepository<StockReleaseQueue, Long> {

    //풀 시각이 지났는데 아직 되돌리지 못한 항목(복구 실패 재시도 포함)
    List<StockReleaseQueue> findByReleasedAtIsNullAndReleaseAtBefore(Instant now);
}
