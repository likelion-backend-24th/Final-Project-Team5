package org.example.reservationservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    //참가자 본인의 예매 목록 조회
    List<Reservation> findByUserId(Long userId);

    //만료 배치가 PENDING 상태 중 기한을 넘긴 예매를 찾을 때 사용
    List<Reservation> findByReservationStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    //1인당 구매 제한 검증 — 취소되지 않은(PENDING·CONFIRMED) 기존 보유 수량을 합산할 때 사용
    List<Reservation> findByUserIdAndTicketTypeIdAndReservationStatusIn(
            Long userId, Long ticketTypeId, List<ReservationStatus> statuses);
}
