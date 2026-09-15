package org.example.reservationservice.domain.seat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationSeatRepository extends JpaRepository<ReservationSeat, Long> {

    //만료 배치·결제 확정·환불 시 특정 예매에 연결된 좌석들을 찾을 때 사용
    List<ReservationSeat> findByReservationId(Long reservationId);
}