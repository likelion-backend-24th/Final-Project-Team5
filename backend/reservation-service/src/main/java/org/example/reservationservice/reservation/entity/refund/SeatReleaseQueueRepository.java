package org.example.reservationservice.reservation.entity.refund;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatReleaseQueueRepository extends JpaRepository<SeatReleaseQueue, Long> {

    //풀 시각이 지났는데 아직 되돌리지 못한 항목(복구 실패 재시도 포함)
    List<SeatReleaseQueue> findByReleasedAtIsNullAndReleaseAtBefore(Instant now);

    //이미 환불해 반환 대기열에 넣은 좌석을 다시 고르지 않도록 예매별로 조회
    List<SeatReleaseQueue> findByReservationId(Long reservationId);
}
