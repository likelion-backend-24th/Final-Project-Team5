package org.example.reservationservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    //참가자 본인의 예매 목록 조회
    List<Reservation> findByUserId(Long userId);

    //QR 검증 시 스캔된 토큰으로 예매를 조회할 때 사용
    Optional<Reservation> findByQrToken(String qrToken);

    //QR 스캔이 실패했을 때 도우미가 손으로 입력한 입장 코드로 예매를 조회할 때 사용
    Optional<Reservation> findByCheckInCode(String checkInCode);

    //입장 코드 발급 시 중복 확인
    boolean existsByCheckInCode(String checkInCode);

    //입장 현황 집계 — 해당 페스티벌에서 입장 가능한 예매 전체(확정 + 부분 환불, 총 티켓 수 / 입장 인원 계산용)
    List<Reservation> findByFestivalIdAndReservationStatusIn(Long festivalId, List<ReservationStatus> statuses);

    //만료 배치가 PENDING 상태 중 기한을 넘긴 예매를 찾을 때 사용
    List<Reservation> findByReservationStatusAndExpiresAtBefore(ReservationStatus status, Instant now);

    //1인당 구매 제한 검증 — 취소되지 않은(PENDING·CONFIRMED) 기존 보유 수량을 합산할 때 사용
    List<Reservation> findByUserIdAndTicketTypeIdAndReservationStatusIn(
            Long userId, Long ticketTypeId, List<ReservationStatus> statuses);
}
