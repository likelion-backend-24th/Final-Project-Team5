package org.example.reservationservice.seat.repository;

import org.example.reservationservice.seat.entity.SeatStatus;
import org.example.reservationservice.seat.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFestivalIdAndTicketTypeIdOrderByZoneAscRowLabelAscSeatNumberAsc(Long festivalId, Long ticketTypeId);

    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.HELD, " +
            "s.heldBy = :userId, s.heldUntil = :heldUntil " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.AVAILABLE")
    int holdSeat(@Param("id") Long id, @Param("userId") Long userId, @Param("heldUntil") Instant heldUntil);

    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.AVAILABLE, " +
            "s.heldBy = null, s.heldUntil = null " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.HELD")
    int releaseSeat(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.SOLD, " +
            "s.heldBy = null, s.heldUntil = null " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.HELD")
    int markSold(@Param("id") Long id);

    boolean existsByTicketTypeId(Long ticketTypeId);

    List<Seat> findBySeatStatusAndHeldUntilBefore(SeatStatus seatStatus, Instant now);

    @Modifying
    @Query("UPDATE Seat s SET s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.AVAILABLE, " +
            "s.heldBy = null, s.heldUntil = null " +
            "WHERE s.id = :id AND s.seatStatus = org.example.reservationservice.seat.entity.SeatStatus.SOLD")
    int releaseSoldSeat(@Param("id") Long id);
}