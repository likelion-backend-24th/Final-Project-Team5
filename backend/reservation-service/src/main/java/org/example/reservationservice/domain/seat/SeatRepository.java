package org.example.reservationservice.domain.seat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    //좌석맵 조회용 — 특정 페스티벌·티켓타입의 전체 좌석을 구역/행/번호 순으로
    List<Seat> findByFestivalIdAndTicketTypeIdOrderByZoneAscRowLabelAscSeatNumberAsc(Long festivalId, Long ticketTypeId);

    //좌석 선점(hold) — AVAILABLE인 좌석만 조건부로 HELD 전환. 영향받은 row가 0이면 이미 선점/판매된 좌석.
    //TicketTypeRepository.deductStock()과 같은 패턴(DB 조건부 UPDATE로 동시성 제어, Redis 미사용).
    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.HELD, " +
            "s.heldBy = :userId, s.heldUntil = :heldUntil " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.AVAILABLE")
    int holdSeat(@Param("id") Long id, @Param("userId") Long userId, @Param("heldUntil") LocalDateTime heldUntil);

    //만료 배치·결제 확정 실패 시 좌석을 다시 AVAILABLE로 되돌린다. HELD 상태인 것만 대상으로 한다
    //(이미 SOLD로 확정된 좌석이 실수로 되돌아가는 것을 방지).
    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.AVAILABLE, " +
            "s.heldBy = null, s.heldUntil = null " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.HELD")
    int releaseSeat(@Param("id") Long id);

    //결제 확정 — HELD인 좌석만 SOLD로 전환
    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.SOLD, " +
            "s.heldBy = null, s.heldUntil = null " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.domain.seat.SeatStatus.HELD")
    int markSold(@Param("id") Long id);

    //좌석 생성 API 멱등 체크용 — 이미 해당 ticketTypeId로 좌석이 생성됐는지 확인
    boolean existsByTicketTypeId(Long ticketTypeId);

    //만료 배치 — HELD인데 heldUntil이 지난 좌석을 찾는다
    List<Seat> findBySeatStatusAndHeldUntilBefore(SeatStatus seatStatus, LocalDateTime now);
}