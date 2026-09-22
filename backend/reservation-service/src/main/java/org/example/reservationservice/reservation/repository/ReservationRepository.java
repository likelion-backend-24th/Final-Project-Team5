package org.example.reservationservice.reservation.repository;

import jakarta.persistence.LockModeType;
import org.example.reservationservice.reservation.entity.Reservation;
import org.example.reservationservice.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByFestivalId(Long festivalId);

    //참가자 본인의 예매 목록 조회
    List<Reservation> findByUserId(Long userId);

    //QR 검증 시 스캔된 토큰으로 예매를 조회할 때 사용
    Optional<Reservation> findByQrToken(String qrToken);

    //QR 스캔이 실패했을 때 도우미가 손으로 입력한 입장 코드로 예매를 조회할 때 사용
    Optional<Reservation> findByCheckInCode(String checkInCode);

    //동시에 같은 티켓을 스캔해도 먼저 입장 시각을 기록한 요청만 성공해야 한다.
    @Modifying
    @Query("UPDATE Reservation r SET r.checkedInAt = :now WHERE r.id = :id AND r.checkedInAt IS NULL")
    int checkInIfNotCheckedIn(@Param("id") Long id, @Param("now") Instant now);

    //입장 코드 발급 시 중복 확인
    boolean existsByCheckInCode(String checkInCode);

    //입장 현황 집계 — 해당 페스티벌에서 입장 가능한 예매 전체(확정 + 부분 환불, 총 티켓 수 / 입장 인원 계산용)
    List<Reservation> findByFestivalIdAndReservationStatusIn(Long festivalId, List<ReservationStatus> statuses);

    //후보 조회 때 엔티티를 캐시하지 않아야 잠금 후 최신 상태를 읽을 수 있다.
    @Query("SELECT r.id FROM Reservation r WHERE r.reservationStatus = :status AND r.expiresAt < :now")
    List<Long> findIdsByReservationStatusAndExpiresAtBefore(
            @Param("status") ReservationStatus status, @Param("now") Instant now);

    //결제 확정과 만료 처리가 같은 예매의 이전 상태를 동시에 변경하지 못하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    //1인당 구매 제한 검증 — 취소되지 않은(PENDING·CONFIRMED) 기존 보유 수량을 합산할 때 사용
    //1인당 구매 제한 — 같은 페스티벌의 모든 티켓 종류를 합산한다
    List<Reservation> findByUserIdAndFestivalIdAndReservationStatusIn(
            Long userId, Long festivalId, List<ReservationStatus> statuses);

    List<Reservation> findByUserIdAndTicketTypeIdAndReservationStatusIn(
            Long userId, Long ticketTypeId, List<ReservationStatus> statuses);

    //부스 대기 신청 시 "이 페스티벌의 티켓을 갖고 있는지" 확인할 때 쓴다. 입장 검증과 같은 기준(CONFIRMED·
    //PARTIALLY_REFUNDED)으로 본다 — 부분 환불된 예매도 남은 장수만큼은 여전히 참가자다.
    boolean existsByUserIdAndFestivalIdAndReservationStatusIn(
            Long userId, Long festivalId, List<ReservationStatus> statuses);
}
